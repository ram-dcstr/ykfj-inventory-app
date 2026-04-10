# YKFJ Architecture Reference

## System Diagram

```
┌──────────────────┐       Same WiFi (LAN)       ┌──────────────────┐
│     TABLET       │◄──── Ktor REST API ─────────►│      PHONE       │
│   (Primary)      │    NSD auto-discovery         │   (Secondary)    │
│                  │                               │                  │
│  Room DB (truth) │◄── delta sync (updated_at) ──►│  Room DB (copy)  │
│  Ktor Server     │                               │  Ktor Client     │
│  Local Images    │──── GET /api/images/:id ──────►│  Cached Images   │
│  Full UI         │                               │  Full UI         │
└──────────────────┘                               └──────────────────┘

Remote access (outside store):
  Phone ──── Tailscale VPN tunnel ────► Tablet Ktor Server
```

## Sync Architecture

- Tablet = primary (runs Ktor server in foreground service). Phone = secondary.
- Ktor server runs on **port 8080**.
- Connection priority: LAN (NSD) → Tailscale → offline cache.
- Delta sync using `updated_at` timestamps. Tablet is always source of truth.
- **Conflict resolution:** Last-write-wins by `updated_at` timestamp. Whichever change has the newer timestamp wins, regardless of device.
- Phone queues changes offline in `pending_sync_queue`, auto-pushes on reconnect.
- **JWT auth tokens expire after 24 hours.** Phone must re-login after expiry.

### Foreground Service (Tablet Only)

Ktor server runs inside a foreground service to survive:
- Screen off
- App backgrounded
- Other apps opening

Persistent notification shows: "YKFJ Server Running · Last sync: X min ago"

### Offline Sync Queue (Phone)

Phone saves changes to `pending_sync_queue` when tablet is unreachable. Auto-pushes when connection restores. Prevents data loss on WiFi drops.

## Performance Optimizations

### Database Indexes

All frequently queried columns are indexed from v1:
- `products`: status, category_id, metal_rate_id, updated_at, is_deleted
- `sold_records`: sold_date, updated_at
- `layaway_records`: status, updated_at
- `customers`: name, updated_at
- `product_images`: product_id, updated_at
- `paluwagan_groups`: status, updated_at
- `paluwagan_slots`: group_id, customer_id
- `paluwagan_payments`: group_id, slot_id, round_number, status

### FTS (Full-Text Search)

Room FTS4 virtual table `products_fts` mirrors products for instant search across name, product_id, and notes. Keeps search fast at any inventory size.

### Reactive Data Flow

```
Room DAO (Flow<List<T>>)
  → Repository (Flow)
    → UseCase (Flow)
      → ViewModel (StateFlow<UiState>)
        → Compose (collectAsStateWithLifecycle)
```

All screens auto-refresh when underlying data changes. No manual reload needed.

### DB Migration Strategy

Room versioned migrations from v1 with `autoMigrations`. Future schema changes migrate cleanly without data loss.

## Image Pipeline

```
Camera/Gallery
  → Compressor library:
      Full:  max 1024px width, ~200KB → files/images/full/{image_id}.jpg
      Thumb: 200x200px, ~15KB        → files/images/thumb/{image_id}.jpg
  → Store file_name in product_images table
  → Display via Coil:
      List/cards → loads thumbnail (instant scrolling)
      Detail view → loads full image
  → Phone sync:
      GET /api/images/{image_id}?type=thumb → downloads thumbnail first
      GET /api/images/{image_id}?type=full  → downloads full in background
  → Phone caches both versions locally in its own app storage
```

Only 1 image per product. Storage estimate: 1,000 products × 215KB = ~215MB (well within tablet storage)

### Release APK

R8 shrinking + resource shrinking enabled for release builds. Reduces APK size by 30-50%.

## Session Management

- Login creates in-memory session via SessionManager (survives config changes)
- Logout clears session → navigates to login screen
- Auto-timeout after configurable idle period (default: 30 min)
- Timeout options: 15 min / 30 min / 1 hour / Never
- Idle = no user interaction (taps, scrolls, inputs)

## Backup System

### Auto Backup (Incremental)
- Runs nightly via WorkManager
- **DB backup:** Daily, tiny (<1MB). Keeps last 3 auto backups, deletes older.
- **Image backup:** Incremental — only backs up images added/changed since last backup.
- Filename: `ykfj-auto-YYYY-MM-DD-HHmmss.zip`

### Manual Backup
- Triggered from Settings (Admin only)
- Full backup: ZIP containing Room `.db` file + all images
- Filename: `ykfj-backup-YYYY-MM-DD-HHmmss.zip`
- Saved to Downloads folder

### Restore
- Select `.zip` file, validate contents, replace current DB + images, restart app
