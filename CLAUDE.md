# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current State

**Phases 1–6 + 9 shipped as features.** Most plan checkboxes for those phases are ticked; the unchecked items in those phases are the per-phase test bullets (only `LoginUseCaseTest` + `PasswordHasherTest` unit tests and three DAO instrumented tests exist so far) and one deliberately deferred item: a per-screen "Archived" tab in Sold/Layaway/Paluwagan/Damaged ([Implementation-Plan.md:425](docs/project/Implementation-Plan.md:425)). Schema is at **v8** in [YkfjDatabase.kt](app/src/main/java/com/ykfj/inventory/data/local/db/YkfjDatabase.kt) with auto-migrations 2→3, 4→5, 5→6, 6→7, 7→8 plus manual `MIGRATION_1_2` and `MIGRATION_3_4`.

Phase highlights:
- **Phase 2** — Full inventory CRUD: `AddItemModal` / `AddItemForm` / `AddItemWidgets`, `ProductIdGenerator`, `ImageCompressor` + `ImageStorageManager`, `InventoryScreen` + `ProductCard` + `ProductDetailScreen` (read-only variant via `product_detail_readonly/{id}`), status-change dialogs (`SoldDialog`, `LayawayDialog`, `DamagedDialog`, `RevertDialog`).
- **Phase 3** — Sold archive, full layaway flow (`LayawayScreen`, `LayawayDetailScreen`, `CustomerLayawayDetailScreen`, payments + split + cancel + complete + revert + update), full paluwagan flow (`PaluwaganScreen` + `PaluwaganDetailScreen` with create/swap/advance/complete/reorder + multi-slot payments), `DamagedScreen` + a **melt sub-flow** (`MeltDamagedProductUseCase`, `GetMeltedRecordsUseCase`, `RevertMeltUseCase`) that isn't in the original plan but ships in-tree.
- **Phase 4** — `AnalyticsScreen` (daily/monthly), `AnalyticsDao`, `ExportSalesUseCase` (CSV via `CsvWriter`), `ExportDailySalesPdfUseCase` (PDF with iText password protection), `GetInventorySummaryUseCase`, `GetGoldTradingSummaryUseCase`.
- **Phase 5** — Ktor embedded server on tablet ([SyncServer.kt](app/src/main/java/com/ykfj/inventory/data/remote/sync/SyncServer.kt), `SyncForegroundService` / `PhoneSyncForegroundService` started from `YkfjApp.onCreate` based on `DeviceRoleManager.getRole()`), NSD discovery + `ConnectionResolver`, JWT auth (`JwtConfig`, `AuthRoutes`), CRUD/sync/image routes, `SyncClient` + `SyncManager` + `SyncEnqueuer`, `PendingSyncManagerImpl` for offline queueing.
- **Phase 6** — `UserManagementScreen` + dialogs, `ArchiveManagerScreen`, `BackupScreen` backed by `BackupManager` + `BackupRestoreHelper` + `BackupWorker.scheduleDaily()`, `ActivityLogScreen` + `CleanupActivityLogsUseCase` (90-day prune on launch), `SettingsScreen.SessionAppInfoSection` (idle-timeout chooser, admin-only daily-export PDF password, read-only `AppInfoBlock`), `PermissionChecker` util.
- **Phase 9 — Gold Purchases Core (complete)** — New module not in the original 1–8 phases. `GoldPurchasesScreen` + `AddGoldPurchaseModal` + `GoldPurchaseDetailScreen`, `GoldPurchaseRepositoryImpl` (atomic record-plus-items insert via `withTransaction`), six use cases (`Add`/`Get`/`GetDetail`/`Revert`/`MarkSoldToSupplier`/`UnmarkSoldToSupplier`), `cash_movements` table created (data only — Phase 11 will use it), `PaymentMethodPicker` composable added to sell/layaway dialogs (`SoldRecordEntity.payment_method` + `LayawayTransactionEntity.payment_method` columns, default `CASH`).

**Next up:** Phase 10 (Trade-in / Swap — link a `GoldPurchaseRecord` to a `SoldRecord` atomically inside the sell dialog) and Phase 11 (Daily Cash screen — uses the already-migrated `cash_movements` table). Then Phase 7 QA + Phase 8 launch. See [docs/project/Implementation-Plan.md](docs/project/Implementation-Plan.md) lines 776–872 for the Phase 10/11 task lists.

**Always begin a session by reading [docs/project/Implementation-Plan.md](docs/project/Implementation-Plan.md)** to find the next unchecked task. The plan has checkbox-tracked phases; work top-down and mark items as they land.

⚠️ **Heads-up on git state:** as of this writing, every feature past `fd6325b` ("core domain use cases…") lives in the working tree but is uncommitted. Run `git status` at the start of a session to see what's already on disk before assuming a feature is missing.

## Project Overview

YKFJ is an Android inventory management app for a small gold jewelry shop in the Philippines. The tablet is the in-store primary device; phones are secondary and access data via LAN sync (same WiFi, Ktor + NSD auto-discovery) or remotely via Tailscale. **Zero recurring cost, zero cloud services, sideload APK only.**

## Tech Stack

| Layer | Technology |
|---|---|
| Language / UI | Kotlin + Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| Local DB | Room (SQLite) — source of truth, FTS4 for search |
| DI | Hilt |
| LAN Sync | Ktor embedded server on tablet (port 8080) + NSD discovery |
| Remote Access | Tailscale (free VPN mesh) |
| Auth | Local bcrypt passwords in Room + JWT (24h expiry) — no Firebase |
| Images | Local storage + Compressor, served via Ktor, loaded via Coil |
| Pagination | Paging 3 |
| Export | CSV + password-protected PDF (Android PdfDocument + iText for encryption) |
| Min SDK | API 31 (Android 12) |

## Architecture

```
UI (Composables + ViewModels)
  ↓
Domain (Use Cases + Repository Interfaces) — pure Kotlin, no Android imports
  ↓
Data (Repository Impls + Room DAOs + Ktor + Local Image Storage)
```

Reactive flow: `Room DAO (Flow) → Repository (Flow) → UseCase (Flow) → ViewModel (StateFlow<UiState>) → Compose (collectAsStateWithLifecycle)`. Every screen auto-refreshes when underlying data changes — no manual reload.

### Package layout (`com.ykfj.inventory`)

```
di/                    # Hilt modules (AppModule, RepositoryModule)
data/
  local/
    AppSettingKeys.kt          # Centralised keys for app_settings table
    backup/                    # BackupManager, BackupRestoreHelper, BackupWorker
    db/                        # YkfjDatabase, entities, DAOs
      converters/ enums/ entity/ dao/ migration/
    image/                     # ImageStorageManager
  mapper/                      # Entity ↔ domain mappers
  remote/sync/                 # Ktor server, NSD, JWT, sync client, foreground services
  repository/                  # Repository impls + DeviceRoleManager + PendingSyncManagerImpl
domain/
  model/                       # Domain models
  repository/                  # Repository interfaces
  sync/                        # DeviceRole, PendingSyncManager interface
  usecase/                     # One class per business action, grouped by feature
    activitylog/ analytics/ archive/ auth/ category/ customer/
    damaged/ goldpurchase/ layaway/ metalrate/ paluwagan/
    product/ sold/ supplier/ user/
ui/
  theme/ components/ navigation/
  auth/                        # LoginScreen + SessionManager (+ IdleTimeout)
  inventory/                   # InventoryScreen, AddItemModal, ProductDetailScreen, status dialogs
  sold/ layaway/ paluwagan/ damaged/
  metalrates/ categories/ customers/ suppliers/
  goldpurchase/                # GoldPurchasesScreen, AddGoldPurchaseModal, detail screen
  analytics/
  settings/                    # SettingsScreen + sub-screens:
    activity/ archive/ backup/ users/
util/                          # CurrencyFormatter, ProductIdGenerator, PasswordHasher,
                               # PermissionChecker, ImageCompressor, CsvWriter
```

## Coding Standards

- **Single responsibility, ~200 lines per file max.** Break larger composables into smaller focused ones. No god classes.
- **Naming:** `*Screen.kt`, `*ViewModel.kt`, `*UseCase.kt`, `*Repository.kt`, `*Dao.kt`, `*Entity.kt`. Meaningful names (`calculateProfit`, not `calc`).
- **Repository pattern is mandatory.** UI/ViewModels never touch DAOs directly.
- **One use case per business action** (e.g. `AddProductUseCase`, `MarkAsSoldUseCase`, `RevertStatusUseCase`).
- **State management:** `UiState` sealed class per screen, exposed as `StateFlow`.
- **Soft deletes only** (`is_deleted` flag). Exception: archive purge after CSV export is a hard delete.
- **Indexes from day 1** (`@Index` on status, category_id, updated_at, sold_date, is_deleted, etc. — see [docs/architecture/System-Design.md](docs/architecture/System-Design.md) for the full list).
- **Room migrations versioned from v1** with `autoMigrations` array ready for future changes.
- **No unnecessary abstractions.** Three similar lines beat a premature abstraction.

## Business Rules You Will Get Wrong If You Skim

These are the rules that are non-obvious and bite when forgotten. Full rules in [docs/business/](docs/business/).

### Pricing ([Pricing-and-Discounts.md](docs/business/Pricing-and-Discounts.md))
- **Weighted items: selling price is NEVER stored.** Always computed as `weight_grams × metalRate.price_per_gram` at display time. Changing a metal rate instantly repriced every weighted product using it.
- **Fixed items:** selling price stored in `products.selling_price`.
- **Sold records snapshot prices** (`sold_price`, `capital_price`) at time of sale — preserves historical accuracy even when rates change later.
- **Discount cap:** max 20% of *profit* (`selling_price - capital_price`). Admin/Manager only — Staff cannot discount. `sold_price` in the record is the final price **after** discount.
- All money formatted as `₱3,200.00` via `CurrencyFormatter` (Locale `en-PH`).

### Inventory ([Inventory-Rules.md](docs/business/Inventory-Rules.md))
- **Product ID format:** `{NAME}-{METALRATE}-{CATEGORY}-{6-digit-seq}` (e.g. `CUBAN-18KSAUDI-NECKLACE-000001`). Sequence is scoped per `name+rate+category` combo.
- **Mixed quantity/status model:** a product with `qty=5` can have units sold, damaged, and on layaway simultaneously. `status=AVAILABLE` while at least 1 unit is free, flips to `SOLD` only when every unit is accounted for. `available = quantity - (sold + damaged + layaway)`.
- **Restocking:** Admin can *increase* quantity via edit form. Quantity can never be *decreased* manually — only through sell/layaway/damage.
- **Only one active layaway per product** at a time.
- **Damaged records are always 1 unit.**
- **Reverting sold/damaged** (Admin/Manager) requires a confirmation dialog with a **mandatory reason**, soft-deletes the record, and restores the quantity.
- **Status-change UI lives on the product detail screen only** — no quick actions on list cards.
- **Default inventory sort:** newest first by `date_acquired` descending. SOLD products (qty=0) are hidden by default; revealed via filter toggle.
- **Deletion guards:**
  - Metal rates / categories: cannot delete if any active product references them.
  - Products: cannot delete if any sold/layaway/damaged records reference them — must revert first.

### Roles ([Roles-and-Permissions.md](docs/business/Roles-and-Permissions.md))

Three roles: **Admin**, **Manager**, **Staff**. Unauthorized actions must be **hidden**, not just disabled. Summary:

- **Admin only:** add/edit/delete products, view profit margin, edit layaway (all fields + payments), manage users, export data, export+purge archives, backup/restore, export activity log.
- **Admin + Manager:** apply discounts, revert sold/damaged, manage paluwagan groups, swap paluwagan positions, manage metal rates/categories/suppliers, edit customers, view customer history, view analytics, archive records, view all activity logs.
- **All roles:** view inventory, change item status (sell/layaway/damage), view sold archive, add layaway payments, add paluwagan payments, add customers, view own activity log.

Sidebar shows red alert badges on **Layaway** (overdue count) and **Paluwagan** (payments due today). No other badges.

### Sync ([System-Design.md](docs/architecture/System-Design.md), [LAN-Sync-API.md](docs/api/LAN-Sync-API.md))
- Tablet runs Ktor on **port 8080** inside a **foreground service** (survives screen off / backgrounding, persistent notification).
- **Delta sync** by `updated_at`. Tablet is always the source of truth.
- **Conflict resolution:** last-write-wins by `updated_at`.
- **Phone offline:** changes queue in `pending_sync_queue`, auto-push on reconnect.
- **JWT expires after 24 hours** — phone must re-login.
- Image sync: thumbnails first (fast list view), full images in background.

### Images
- **1 image per product.** Two versions generated at save time: full (max 1024px, ~200KB) and thumbnail (200×200, ~15KB). Lists load thumbnails; detail view loads full.

### Payment methods & Gold Purchases (Phase 9)
- Every sold record and layaway transaction carries a `payment_method` (`CASH` / `GCASH` / `ONLINE_BANKING` / `OTHER`, default `CASH`). Use the shared `PaymentMethodPicker` composable in [ui/components/](app/src/main/java/com/ykfj/inventory/ui/components/PaymentMethodPicker.kt) — don't reinvent.
- **Gold Purchases** (the shop buying scrap/jewellery from customers) is a first-class module. A `GoldPurchaseRecord` has 1+ `GoldPurchaseItem` rows; per-item value is `weight × buy_rate_per_gram` unless overridden manually. All roles can record a purchase; only Admin can revert.
- `GoldPurchaseRecord.linkedSoldRecordId` is reserved for **Phase 10 trade-ins** — leave null in Phase 9 flows. `RevertGoldPurchaseUseCase` returns `IsTradeIn` if the link is set, so trade-in reverts must go through the (not-yet-built) `RevertTradeInUseCase`.
- `cash_movements` table exists (migrated in v5→v6) but is unused until Phase 11 (Daily Cash) ships.

## Build & Run

- Open in Android Studio (JDK 17+ required; Android Studio's bundled JBR works).
- Tablet emulator: Pixel C (or similar tablet AVD). Phone emulator: Pixel 7.
- Debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Release APK: `./gradlew :app:assembleRelease` (R8 shrinking + resource shrinking enabled).
- Unit tests: `./gradlew test`. Instrumented tests: `./gradlew connectedAndroidTest`.
- Versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml) (Kotlin 2.0.21, AGP 8.7.3, KSP, Hilt, Room, Ktor 2.3.12).
- **Guava capability conflict:** Ktor JWT pulls in guava proper while AndroidX libs declare the empty `listenablefuture:1.0` marker. Resolved in [app/build.gradle.kts](app/build.gradle.kts) via a `modules { module("com.google.guava:listenablefuture") { replacedBy(...) } }` block. Do not remove that block when adding dependencies.

## References
- [docs/README.md](docs/README.md) — documentation index
- [docs/project/Implementation-Plan.md](docs/project/Implementation-Plan.md) — **read first every session**, phase-by-phase checklist
- [docs/architecture/System-Design.md](docs/architecture/System-Design.md) — sync, performance, image pipeline, backups
- [docs/database/Schema.md](docs/database/Schema.md) — Room entities and virtual tables
- [docs/api/LAN-Sync-API.md](docs/api/LAN-Sync-API.md) — Ktor REST endpoints
- [docs/business/](docs/business/) — pricing, layaway, paluwagan, inventory, customers, roles, archiving
