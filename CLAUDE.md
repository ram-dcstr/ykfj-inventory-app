# YKFJ Gold Jewelry Inventory App

## Project Overview
Android inventory management app for a small gold jewelry shop (YKFJ) in the Philippines. Runs on a tablet in-store as the primary device, with phone access via LAN sync (same WiFi) or Tailscale (remote). Zero recurring cost. Sideload APK only — no Play Store.

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Local DB | Room (SQLite) — source of truth |
| DI | Hilt |
| LAN Sync | Ktor embedded server (tablet) + NSD auto-discovery |
| Remote Access | Tailscale (free VPN mesh) |
| Images | Local storage + Compressor library, served via Ktor |
| Auth | Local bcrypt-hashed passwords in Room (no Firebase) |
| Image Loading | Coil |
| Pagination | Paging 3 |
| Export | CSV + password-protected PDF (Android PdfDocument + iText for encryption) |
| Min SDK | API 31 (Android 12) |

## Architecture

### Layer Structure
```
UI Layer (Composables + ViewModels)
    ↓
Domain Layer (Use Cases + Repository Interfaces) — pure Kotlin, no Android imports
    ↓
Data Layer (Repository Impls + Room DAOs + Ktor + Local Image Storage)
```

### Package Structure
```
com.ykfj.inventory/
├── di/                    # Hilt modules
├── data/
│   ├── local/
│   │   ├── db/            # Room database, entities, DAOs
│   │   └── backup/        # Backup + archive manager
│   ├── remote/
│   │   └── sync/          # Ktor server + NSD + sync logic
│   └── repository/        # Repository implementations
├── domain/
│   ├── model/             # Domain models
│   ├── usecase/           # Use case classes (grouped by feature)
│   └── repository/        # Repository interfaces
├── ui/
│   ├── theme/             # Material 3 theme
│   ├── components/        # Shared composables
│   ├── navigation/        # Sidebar, NavGraph, routes
│   ├── auth/              # Login
│   ├── inventory/         # Inventory list + add/edit item
│   ├── sold/              # Sold archive
│   ├── layaway/           # Layaway management
│   ├── paluwagan/         # Paluwagan rotating savings
│   ├── damaged/           # Damaged items
│   ├── metalrates/        # Metal rates CRUD
│   ├── categories/        # Categories CRUD
│   ├── customers/         # Customer directory
│   ├── suppliers/         # Supplier directory
│   ├── analytics/         # Dashboard + export
│   ├── archive/           # Archive manager + CSV export
│   └── settings/          # User management + backup
└── util/                  # CurrencyFormatter, ProductIdGenerator, PasswordHasher, DateUtils
```

## Coding Standards

### Code Style
- **Single Responsibility:** One file does one thing
- **Naming:** `*Screen.kt`, `*ViewModel.kt`, `*UseCase.kt`, `*Repository.kt`, `*Dao.kt`, `*Entity.kt`
- **File size limit:** ~200 lines max per file. Split if larger.
- **No god classes:** Break large composables into smaller focused composables
- **Meaningful names:** `calculateProfit()` not `calc()`, `isWeightedPricing` not `flag1`
- **No unnecessary abstractions:** Three similar lines > premature abstraction

### Data Layer Rules
- **Repository pattern:** All data access through repositories. UI never touches DAOs directly.
- **Use cases:** Each business action = its own class (e.g., `AddProductUseCase`, `MarkAsSoldUseCase`). Product add/edit/delete is admin-only; status changes (sell/layaway/damage) are available to all roles.
- **Reactive UI:** Room DAOs return `Flow<List<T>>` → Repositories return `Flow` → ViewModels expose `StateFlow<UiState>` → Compose collects via `collectAsStateWithLifecycle()`
- **State management:** `UiState` sealed classes per screen
- **Soft deletes:** `is_deleted` flag on all tables — never hard delete (exception: archive purge after CSV export)
- **DB Migrations:** Room versioned migrations from v1 with `autoMigrations` array

### Performance Rules
- **DB Indexes:** `@Index` on frequently queried columns from day 1 (status, category_id, updated_at, sold_date, is_deleted)
- **FTS Search:** Room FTS4 virtual table for product search — instant results even with 10K+ items
- **Image strategy:** 1 image per product. Pre-generate 2 versions at save time: full (1024px, ~200KB) + thumbnail (200x200, ~15KB). Lists load thumbnails.
- **Release APK:** R8 shrinking + resource shrinking in release builds

## Build & Run
- Open project in Android Studio
- Tablet emulator: Pixel C or similar tablet AVD
- Phone emulator: Pixel 7 or similar phone AVD
- Release APK: `./gradlew assembleRelease`

## References
- `docs/README.md` — Central documentation index
- `docs/project/Implementation-Plan.md` — Implementation plan with phase tracking and checkboxes
- `docs/architecture/System-Design.md` — System architecture, performance, sync, image pipeline
- `docs/database/Schema.md` — Database schema (all Room entities and tables)
- `docs/api/LAN-Sync-API.md` — LAN sync API endpoints (Ktor REST)
- `docs/business/` — Folder containing specialized rules for pricing, layaway, permissions, and more.
