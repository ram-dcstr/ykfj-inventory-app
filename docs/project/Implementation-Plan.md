# YKFJ Implementation Plan

> Track progress by checking off tasks. Each Claude Code session should read this file
> to know where we left off and what to build next.

## Phase 1 — Project Setup & Foundation (Week 1-2)

### 1.1 Project Initialization
- [ ] Create Android project in Android Studio (Kotlin, Compose, Gradle KTS)
- [ ] Configure `build.gradle.kts` with all dependencies (Room, Hilt, Ktor, Coil, Paging 3, Compressor)
- [ ] Set up `.gitignore` for Android project
- [ ] Initialize git repo, create `main` and `develop` branches
- [ ] Configure `AndroidManifest.xml` (minSdk 31, permissions: INTERNET, CAMERA, READ/WRITE_STORAGE, ACCESS_NETWORK_STATE, NSD, FOREGROUND_SERVICE, POST_NOTIFICATIONS)

### 1.2 Room Database
- [ ] Create all Room entities (`UserEntity`, `ProductEntity`, `ProductImageEntity`, `MetalRateEntity`, `CategoryEntity`, `CustomerEntity`, `SupplierEntity`, `SoldRecordEntity`, `LayawayRecordEntity`, `LayawayTransactionEntity`, `DamagedRecordEntity`, `PaluwaganGroupEntity`, `PaluwaganSlotEntity`, `PaluwaganPaymentEntity`, `ActivityLogEntity`)
  - `ProductEntity`: include `supplier_id` (FK → suppliers, nullable) + `date_acquired` (Long, required)
  - `SupplierEntity`: name, representative_name, mobile, address, notes
  - `CustomerEntity`: include `credit_score` (Int, default 100)
  - `SoldRecordEntity`: include `sold_by` (FK → users) + `discount_amount` + `discount_type` (NONE/FIXED/PERCENTAGE) + `is_archived` (Boolean, default false)
  - `LayawayRecordEntity`: include `created_by` (FK → users) + `due_date` (nullable Long) + `completion_date` (nullable Long) + `forfeited_amount` (nullable Double — set to total_paid when CANCELLED) + `is_archived` (Boolean, default false)
  - `DamagedRecordEntity`: include `recorded_by` (FK → users) + `is_archived` (Boolean, default false)
  - `PaluwaganGroupEntity`: include `is_archived` (Boolean, default false)
  - `ProductImageEntity`: 1 image per product (unique product_id), no display_order
- [ ] Add `@Index` annotations on all entities for frequently queried columns:
  - products: status, category_id, metal_rate_id, updated_at, is_deleted
  - sold_records: sold_date, updated_at
  - layaway_records: status, updated_at
  - customers: name, updated_at
  - product_images: product_id, updated_at
  - paluwagan_groups: status, updated_at
  - paluwagan_slots: group_id, customer_id
  - paluwagan_payments: group_id, slot_id, round_number, status
- [ ] Create FTS4 virtual table `ProductFts` for full-text search (name, product_id, notes)
- [ ] Create `PendingSyncEntity` for offline change queue (phone side)
- [ ] Create all DAOs (`UserDao`, `ProductDao`, `MetalRateDao`, `CategoryDao`, `CustomerDao`, `SupplierDao`, `SoldRecordDao`, `LayawayRecordDao`, `LayawayTransactionDao`, `DamagedRecordDao`, `ProductImageDao`, `PendingSyncDao`, `PaluwaganGroupDao`, `PaluwaganSlotDao`, `PaluwaganPaymentDao`, `ActivityLogDao`)
- [ ] DAOs return `Flow<List<T>>` for all observe/list queries (reactive UI) — ensure list queries filter `WHERE is_archived = false` by default
- [ ] Create `YkfjDatabase.kt` with all entities registered, version = 1, empty `autoMigrations` array (ready for future)
- [ ] Add Room type converters for enums and dates
- [ ] Seed default admin user on first launch (username: admin, password: admin123 — document that admin should change password after first login)

### 1.3 Dependency Injection
- [ ] Create `AppModule.kt` (provides Room database, DAOs)
- [ ] Create `RepositoryModule.kt` (binds repository interfaces to implementations)
- [ ] Create `YkfjApp.kt` application class with `@HiltAndroidApp`

### 1.4 Domain Layer Setup
- [ ] Create domain models (`User`, `Product`, `MetalRate`, `Category`, `Customer`, `Supplier`, etc.)
- [ ] Create repository interfaces (`UserRepository`, `ProductRepository`, `SupplierRepository`, etc.) — return `Flow<List<T>>` for observe queries
- [ ] Create `ActivityLogRepository` interface and `LogActivityUseCase`
- [ ] Create `PendingSyncManager.kt` interface/logic for offline change queueing (called by Repositories)
- [ ] Create mappers between entities and domain models

### 1.5 Theme, Navigation & Utilities
- [ ] Create Material 3 theme (`Theme.kt`, `Color.kt`, `Type.kt`) — gold/warm color palette
- [ ] Create `Screen` sealed class with all routes (Inventory, Sold Archive, Layaway, Paluwagan, Damaged, Metal Rates, Categories, Customers, Suppliers, Analytics, Settings)
- [ ] Create `Sidebar.kt` composable (adaptive: permanent on tablet, modal on phone)
  - Navigation items: Inventory, Sold Archive, Layaway, Paluwagan, Damaged, Metal Rates, Categories, Customers, Suppliers, Analytics, Settings
  - **Alert badges:** Red badge on Layaway (overdue count) and Paluwagan (due payments count)
  - Current user display + role badge at bottom
  - Logout button
- [ ] Create `NavGraph.kt` with all screen routes (placeholder screens)
- [ ] Create `MainActivity.kt` as single-activity Compose host
- [ ] Create `CurrencyFormatter.kt` utility — formats all money values as ₱3,200.00 (Philippine Peso)

### 1.6 Authentication
- [ ] Create `PasswordHasher.kt` utility (bcrypt)
- [ ] Create `LoginUseCase.kt`
- [ ] Create `LoginViewModel.kt` with `LoginUiState` sealed class (StateFlow pattern)
- [ ] Create `LoginScreen.kt` (username + password + role display)
- [ ] Create `SessionManager.kt` (holds current user in memory, survives config changes)
  - Logout function clears session → navigates to login
  - Auto-timeout after configurable idle period (default 30 min)
  - Timeout settings: 15 min / 30 min / 1 hour / Never
- [ ] Implement role-based navigation gating

### 1.7 Phase 1 Testing
- [ ] Unit test: PasswordHasher
- [ ] Unit test: LoginUseCase
- [ ] Unit test: Room DAOs (in-memory DB) — verify indexes, FTS search, Flow emission
- [ ] Manual test: app boots → login → sidebar visible → navigate between empty screens

**Phase 1 Deliverable:** App boots in emulator, admin logs in, sees sidebar navigation with empty placeholder screens.

---

## Phase 2 — Core Inventory (Week 3-4)

### 2.1 Metal Rates
- [ ] Create `MetalRateRepository` implementation
- [ ] Create `AddMetalRateUseCase`, `UpdateMetalRateUseCase`, `DeleteMetalRateUseCase`, `GetMetalRatesUseCase`
- [ ] Note: when a metal rate price changes, all weighted products using that rate auto-update (selling price is calculated dynamically: weight × rate, never stored for weighted items)
- [ ] Create `MetalRatesViewModel.kt` with `MetalRatesUiState`
- [ ] Create `MetalRatesScreen.kt` — list with add/edit/delete
- [ ] Create `MetalRateFormDialog.kt` — modal for add/edit

### 2.2 Categories
- [ ] Create `CategoryRepository` implementation
- [ ] Create `AddCategoryUseCase`, `UpdateCategoryUseCase`, `DeleteCategoryUseCase`, `GetCategoriesUseCase`
- [ ] Create `CategoriesViewModel.kt` with `CategoriesUiState`
- [ ] Create `CategoriesScreen.kt` — list with add/edit/delete
- [ ] Create `CategoryFormDialog.kt` — modal for add/edit

### 2.3 Suppliers
- [ ] Create `SupplierRepository` implementation
- [ ] Create `AddSupplierUseCase`, `UpdateSupplierUseCase`, `DeleteSupplierUseCase`, `GetSuppliersUseCase`
- [ ] Create `SuppliersViewModel.kt` with `SuppliersUiState`
- [ ] Create `SuppliersScreen.kt` — list with add/edit/delete
- [ ] Create `SupplierFormDialog.kt` — modal for add/edit (name, representative, mobile, address, notes)

### 2.4 Customer Directory
- [ ] Create `CustomerRepository` implementation
- [ ] Create `AddCustomerUseCase`, `UpdateCustomerUseCase`, `SearchCustomersUseCase`, `GetCustomersUseCase`
- [ ] Create `CustomersViewModel.kt`
- [ ] Create `CustomersScreen.kt` — searchable customer list with credit score badges (Excellent/Good/Fair/Poor)
- [ ] Create `CustomerFormDialog.kt` — add/edit (name, mobile, phone, birthday, address, notes). All roles can add customers. Only Admin/Manager can edit.
- [ ] Create `CustomerDetailScreen.kt` (admin/manager only) — customer info + transaction history tabs:
  - Sales tab: all sold_records for this customer
  - Layaway tab: all layaway_records for this customer
  - Paluwagan tab: all paluwagan_slots for this customer
- [ ] Create `CustomerAutoSuggest.kt` — reusable composable for customer selection with auto-suggest

### 2.5 Inventory — Add & Edit Item (Admin Only)
- [ ] Create `ProductRepository` implementation
- [ ] Create `ProductIdGenerator.kt` utility (NAME-RATE-CAT-000001 format, sequence scoped per combo)
- [ ] Create `AddProductUseCase`, `UpdateProductUseCase`
- [ ] Create `AddItemViewModel.kt`
- [ ] Create `AddItemModal.kt` composable (admin only, shared for add + edit, pre-fills fields when editing):
  - Item name input → auto-generate product ID preview
  - Photo upload (camera + gallery picker) — 1 image per product
  - Category dropdown (from categories table)
  - Supplier dropdown (from suppliers table, optional)
  - Date acquired picker (defaults to today, required)
  - Pricing type toggle: Weighted | Fixed
  - If Weighted: metal rate dropdown → auto-calc selling price display
  - If Fixed: manual selling price input
  - Capital price input
  - Weight (grams) input for weighted items
  - Size input (optional)
  - Quantity input (admin can increase qty for restock; decrease only via sell/layaway/damage actions)
  - Notes input
- [ ] Create `ImageStorageManager.kt` — compress + save two versions to app internal storage (1 image per product):
  - Full: max 1024px, ~200KB → `files/images/full/{image_id}.jpg`
  - Thumbnail: 200x200px, ~15KB → `files/images/thumb/{image_id}.jpg`
- [ ] Create `ImageCompressor.kt` — Compressor library wrapper (configurable target size + dimensions)
- [ ] Load images via Coil: thumbnails for list cards, full for detail view

### 2.6 Product Detail Screen
- [ ] Create `ProductDetailScreen.kt`:
  - Full product info display (all fields, full-size image, supplier, date acquired)
  - Profit margin display (capital vs selling price, margin %) — Admin only
  - Edit button → opens AddItemModal pre-filled (reuse same form)
  - Status change actions (Sold, Layaway, Damaged)
- [ ] Create `ProductDetailViewModel.kt`
- [ ] Navigate from product card tap → detail screen

### 2.7 Inventory — List & Search
- [ ] Create `GetProductsUseCase` with Paging 3 support (returns Flow)
- [ ] Create `SearchProductsUseCase` using FTS4 table for instant search
- [ ] Create `InventoryViewModel.kt` with `InventoryUiState` (StateFlow, auto-refreshes via Room Flow)
- [ ] Create `InventoryScreen.kt`:
  - Search bar at top
  - Filter chips (by status, category) — SOLD hidden by default, visible via filter toggle
  - Default sort: newest first (by date_acquired descending)
  - Paginated grid/list of product cards (20 per page)
- [ ] Create `ProductCard.kt` composable:
  - Product image thumbnail
  - Name, product ID
  - Price (selling price or price/gram)
  - Status badge (Available, Sold, Layaway, Damaged)
  - Quick info: category, metal rate, quantity

### 2.8 Inventory — Status Changes
- [ ] Create `MarkAsSoldUseCase` — any role can sell, creates sold_record with quantity (1 to available qty), decreases product quantity, sets SOLD when qty reaches 0
- [ ] Create `MarkAsLayawayUseCase`
- [ ] Create `MarkAsDamagedUseCase`
- [ ] Create `RevertStatusUseCase` — admin/manager can revert Sold/Damaged back to Available (restores qty, soft-deletes record, requires reason)
- [ ] Add status change actions to **product detail screen only** (not on inventory list cards):
  - "Mark as Sold" → opens SoldDialog (qty picker + price confirm + optional discount), creates sold_record, decreases available qty, status → SOLD only when all units accounted for
  - "Mark as Layaway" → opens LayawayDialog (customer selection + qty picker + per-unit price), creates layaway_record. Only one active layaway per product.
  - "Mark as Damaged" → opens DamagedDialog (reason input), creates damaged_record, always 1 unit at a time
- [ ] Create `SoldDialog.kt` — quantity picker (1 to available qty), confirm per-unit sale price, optional discount (fixed/percentage, admin/manager only, max 20% of profit), snapshot prices at time of sale
- [ ] Create `LayawayDialog.kt` — customer auto-suggest + qty picker (1 to available) + per-unit price + optional due date
- [ ] Create `RevertDialog.kt` — confirmation with mandatory reason field (admin/manager only)
- [ ] Create `DamagedDialog.kt` — reason input

### 2.9 Phase 2 Testing
- [ ] Unit test: Customer search and auto-suggest logic
- [ ] Unit test: ProductIdGenerator
- [ ] Unit test: AddProductUseCase
- [ ] Unit test: All status change use cases (including quantity decrease logic)
- [ ] Unit test: RevertStatusUseCase (qty restore, record soft-delete)
- [ ] Unit test: Weighted price calculation (dynamic: weight × rate)
- [ ] Unit test: ProductDao queries (FTS search, pagination, filters)
- [ ] Unit test: ImageCompressor output (verify full ~200KB, thumb ~15KB)
- [ ] Manual test: full flow — add metal rate → add category → add product → view in list → search → change status
- [ ] Manual test: edit product — tap card → detail screen → edit → verify changes saved
- [ ] Manual test: quantity — add product with qty 5 → sell 2 → verify qty 3 + still AVAILABLE → sell 3 → verify qty 0 + SOLD
- [ ] Manual test: weighted price — change metal rate → verify all weighted products show updated price
- [ ] Manual test: revert sold — revert from sold archive → verify product back in inventory with qty restored
- [ ] Manual test: delete product — attempt delete with sold records → verify blocked with error message
- [ ] Manual test: restock — admin increases qty from 3 to 5 → verify available qty updated

**Phase 2 Deliverable:** Full inventory management — add items with auto-generated IDs, view paginated list, search, filter, change status to sold/layaway/damaged.

---

## Phase 3 — Sales, Customers & Status Screens (Week 5-6)

### 3.1 Sold Archive
- [ ] Create `GetSoldRecordsUseCase` (with date filter, default: today)
- [ ] Create `RevertSoldUseCase` — admin/manager can revert sold item back to Available (restores qty +1, soft-deletes sold_record)
- [ ] Create `ExportDailySalesPdfUseCase` — generates password-protected PDF of today's sales
  - Branded header: "YKFJ Gold Jewelry — Daily Sales Report — {date}"
  - Table: product name, grams, size, qty, capital, selling price, discount (if any), final price
  - Footer: total items sold, total revenue, total capital, total profit
  - PDF password from `app_settings.daily_export_password`
  - Filename: `ykfj-sales-YYYY-MM-DD.pdf` saved to Downloads
- [ ] Create `SoldArchiveViewModel.kt`
- [ ] Create `SoldArchiveScreen.kt`:
  - Date picker filter (default: current date)
  - List of sold items with: product info, sale price, capital, profit, customer, date
  - Daily summary: total revenue, total capital, total profit, items count
  - "Export Today's Sales" button → generates password-protected PDF (staff, manager, admin)
  - "Revert to Available" action per item (admin/manager only)

### 3.2 Layaway Management
- [ ] Create `GetActiveLayawaysUseCase`, `AddLayawayPaymentUseCase`, `SplitLayawayPaymentUseCase`, `UpdateLayawayUseCase`, `CompleteLayawayUseCase`, `CancelLayawayUseCase`, `DeleteLayawayPaymentUseCase`
- [ ] Create `LayawayViewModel.kt`
- [ ] Create `LayawayScreen.kt`:
  - Flat list of all active layaways, searchable/filterable by customer name
  - Each row: customer, product, qty, total (unit_price × qty), paid, remaining, due date
  - Overdue layaways flagged/highlighted
  - Tap to view full transaction history with dates
- [ ] Create `LayawayDetailScreen.kt` or expandable section:
  - All transactions listed with date and amount
  - Add payment button → `AddPaymentDialog.kt` (updates customer credit score: +1 on-time, -3 late)
  - Split payment button → `SplitPaymentDialog.kt` — pay across multiple of the customer's active layaways, allocate amounts per layaway
  - Edit layaway (admin only): can edit customer, qty, unit_price, due_date, notes — everything except linked product
  - Edit/delete individual payment records (admin only) — deleting a payment reduces total_paid
  - Admin can manually mark as COMPLETED or CANCELLED
  - Cancellation: sets forfeited_amount = total_paid, no refunds (forfeited payments become shop profit)
  - Due date display with overdue warning
  - Auto-complete layaway when fully paid (sets completion_date)
  - Downpayment is optional — layaway can start with ₱0 paid

### 3.3 Paluwagan (Rotating Savings)
- [ ] Create `PaluwaganGroupEntity`, `PaluwaganSlotEntity`, `PaluwaganPaymentEntity` (Room entities with indexes)
- [ ] Create `PaluwaganGroupDao`, `PaluwaganSlotDao`, `PaluwaganPaymentDao` (Flow-based queries)
- [ ] Create `PaluwaganRepository` implementation
- [ ] Create use cases:
  - `CreatePaluwaganGroupUseCase` — name, contribution amount, frequency, total slots, start date
  - `AddPaluwaganSlotUseCase` — assign customer from directory to a position (supports multiple slots per customer)
  - `SwapPaluwaganPositionsUseCase` — admin/manager swaps two slots' positions
  - `RecordPaluwaganPaymentUseCase` — mark slot as PAID/LATE for a round
  - `AdvancePaluwaganRoundUseCase` — move to next round
  - `CompletePaluwaganGroupUseCase` — mark group as COMPLETED when all rounds done
  - `GetActivePaluwaganGroupsUseCase`, `GetPaluwaganGroupDetailUseCase`
- [ ] Create `PaluwaganViewModel.kt` with `PaluwaganUiState`
- [ ] Create `PaluwaganScreen.kt`:
  - List of active paluwagan groups with: name, contribution, frequency, current round/total, member count
  - "Create Group" button (admin/manager)
- [ ] Create `PaluwaganDetailScreen.kt`:
  - Group info header (name, contribution, frequency, status)
  - Member list with position numbers and customer names
  - Current round indicator — who collects this round
  - Payment grid: rows = slots, columns = rounds, cells = PAID/UNPAID/LATE status
  - "Record Payment" action per slot (updates customer credit score: +1 on-time, -2 late)
  - "Swap Positions" action (admin/manager)
  - "Advance Round" button
- [ ] Create `CreatePaluwaganDialog.kt` — group setup form
- [ ] Create `AddPaluwaganMemberDialog.kt` — customer auto-suggest + position assignment

### 3.4 Damaged Items
- [ ] Create `GetDamagedRecordsUseCase`
- [ ] Create `RevertDamagedUseCase` — admin/manager can revert damaged item back to Available (restores qty +1, soft-deletes damaged_record)
- [ ] Create `DamagedViewModel.kt`
- [ ] Create `DamagedScreen.kt`:
  - List of all damaged items with: product info, reason, date recorded, notes
  - "Revert to Available" action per item (admin/manager only)

### 3.5 Phase 3 Testing
- [ ] Unit test: Layaway payment calculations (total paid, remaining)
- [ ] Unit test: Sold archive date filtering
- [ ] Unit test: Paluwagan round advancement and completion logic
- [ ] Unit test: Paluwagan position swapping
- [ ] Unit test: Paluwagan multi-slot payment tracking (customer with 2 slots pays 2x)
- [ ] Manual test: full sales flow — sell item → appears in sold archive with correct date
- [ ] Manual test: full layaway flow — create layaway → add payments → auto-complete
- [ ] Manual test: multi-product layaway — customer creates 2 separate layaways → split payment across both → verify allocations
- [ ] Manual test: overdue layaway — set due date in past → verify flagged/highlighted
- [ ] Manual test: full paluwagan flow — create group → add members → record payments → advance rounds → complete
- [ ] Manual test: paluwagan multi-slot — add customer with 2 slots → verify pays 2x per round, collects 2x
- [ ] Manual test: paluwagan swap — swap positions mid-cycle → verify collection order updated
- [ ] Manual test: damage flow — mark damaged → appears in damaged screen
- [ ] Manual test: revert damaged — admin/manager reverts → product back in inventory
- [ ] Manual test: cancel layaway — admin cancels → product back to Available → forfeited_amount set to total_paid
- [ ] Manual test: edit layaway — admin edits unit_price, due_date, notes → verify changes saved
- [ ] Manual test: delete layaway payment — admin deletes a payment → verify total_paid reduced
- [ ] Manual test: sidebar badges — create overdue layaway → verify red badge appears on Layaway nav item

**Phase 3 Deliverable:** Complete sales workflow — sell items (tracked in archive), manage layaways with payment history, paluwagan rotating savings groups, track damaged items, full customer directory.

---

## Phase 4 — Analytics & Export (Week 7)

### 4.1 Analytics Dashboard
- [ ] Create `GetDailySalesUseCase`, `GetMonthlySalesUseCase`, `GetInventorySummaryUseCase`
- [ ] Create `AnalyticsViewModel.kt`
- [ ] Create `AnalyticsScreen.kt`:
  - **Daily Sales Card:** date picker, revenue, capital cost, profit, items sold count
  - **Monthly Sales Card:** month picker, same metrics aggregated
  - **Inventory Summary Card:** total items, total inventory value (capital), total potential revenue
  - **Top Categories:** items sold per category (current month)
  - **Layaway Outstanding:** total outstanding balance across all active layaways
  - **Paluwagan Summary:** active groups count, total contribution collected, current round progress

### 4.2 Export to CSV
- [ ] Create `ExportSalesUseCase` — generates CSV from sold_records for selected date range
- [ ] CSV format: Date, Product ID, Product Name, Category, Capital, Sold Price, Profit, Customer
- [ ] Save CSV to Downloads folder
- [ ] Share intent to open in Google Sheets or other apps
- [ ] Add export button to Analytics screen

### 4.3 Phase 4 Testing
- [ ] Unit test: Daily/monthly aggregation calculations
- [ ] Unit test: CSV generation format
- [ ] Manual test: sell several items → check analytics numbers match → export CSV → open in Google Sheets
- [ ] Manual test: daily sales PDF — sell items with/without discounts → export → verify PDF layout, totals, and password protection

**Phase 4 Deliverable:** Analytics dashboard showing daily/monthly financials, inventory value, and CSV export for Google Sheets.

---

## Phase 5 — Sync & Remote Access (Week 8-9)

### 5.1 Ktor Embedded Server (Tablet)
- [ ] Create `SyncServer.kt` — Ktor embedded server setup
- [ ] Create `AuthRoutes.kt` — POST /api/auth/login, GET /api/auth/me
- [ ] Create `SyncRoutes.kt` — GET /api/sync/status, GET /api/sync/changes, POST /api/sync/push
- [ ] Create `CrudRoutes.kt` — CRUD endpoints for all entities
- [ ] Create JWT token generation/validation (local, no cloud)
- [ ] Create `ImageRoutes.kt` — GET /api/images/{image_id}, GET /api/images/sync?since=
- [ ] Create `SyncServerManager.kt` — start/stop server lifecycle tied to app
- [ ] Create `SyncForegroundService.kt` — foreground service to keep Ktor server alive when screen is off
  - Persistent notification: "YKFJ Server Running · Last sync: X min ago"
  - Starts automatically when app opens on tablet
  - Survives screen off, app backgrounded
  - Add FOREGROUND_SERVICE permission to AndroidManifest

### 5.2 Network Service Discovery
- [ ] Create `NsdManager.kt` — register tablet as service on network
- [ ] Create `NsdDiscovery.kt` — phone discovers tablet service
- [ ] Create `ConnectionResolver.kt`:
  - Try NSD (same WiFi) first
  - Fall back to Tailscale IP (stored in settings)
  - Fall back to offline cache

### 5.3 Sync Client (Phone)
- [ ] Create `SyncClient.kt` — Ktor HTTP client
- [ ] Create `SyncManager.kt`:
  - Pull changes from tablet since last sync timestamp
  - Push local changes (including pending queue) to tablet
  - Update local Room DB with received changes
  - Store last sync timestamp
- [ ] Integrate with `PendingSyncManager` (from Phase 1):
  - On reconnect, auto-push all PENDING actions to tablet in order
  - Mark as SYNCED on success, FAILED on error (retry on next sync)
  - Clear SYNCED entries after confirmation
- [ ] Auto-sync on app open when connection available
- [ ] Manual pull-to-refresh sync
- [ ] Image sync: download thumbnails first (fast list view), then full images in background
- [ ] Sync status indicator in UI (last synced: X minutes ago, pending changes count)

### 5.4 Device Role Configuration
- [ ] Add setting: "This device is: Tablet (Primary) / Phone (Secondary)"
- [ ] Tablet mode: starts Ktor server, registers NSD
- [ ] Phone mode: runs sync client, discovers via NSD

### 5.5 Phase 5 Testing
- [ ] Unit test: SyncClient delta logic
- [ ] Unit test: JWT generation/validation
- [ ] Unit test: PendingSyncManager queue logic (enqueue, push, retry, clear)
- [ ] Integration test: Ktor routes (in-process test client)
- [ ] Manual test (two emulators): add product on tablet → sync to phone → verify data matches
- [ ] Manual test: edit on tablet → phone sees update after sync
- [ ] Manual test: phone offline → make changes → reconnect → verify pending queue pushes to tablet
- [ ] Manual test: phone offline → still shows cached data (read-only browsing)
- [ ] Manual test: add product with image on tablet → sync → thumbnail loads fast on phone, full image loads in detail
- [ ] Manual test: verify image compression (raw photo → full ~200KB + thumb ~15KB)

**Phase 5 Deliverable:** Tablet and phone sync over LAN (data + images), phone can access inventory remotely via Tailscale. Zero cloud dependencies.

---

## Phase 6 — Settings & Admin (Week 10)

### 6.1 User Management
- [ ] Create `ManageUsersUseCase` (create, update, deactivate)
- [ ] Create `SettingsViewModel.kt`
- [ ] Create `UserManagementScreen.kt` (admin only):
  - List of all users with role badges
  - Add user: username, name, password, role selection
  - Edit user: change username, name, role, reset password
  - Deactivate user (soft delete)

### 6.2 Role Permission Enforcement
- [ ] Create `PermissionChecker.kt` utility
- [ ] Enforce permissions across all screens:
  - Staff: no add/edit/delete products, no edit customers, no delete, no metal rates/categories, no analytics, no export, no settings. Can sell/layaway/damage, add customers, add payments.
  - Manager: no add/edit/delete products, no edit layaway, no export, no user management, no backup, no archive purge
  - Admin: full access (only role that can add/edit/delete products)
- [ ] Hide unauthorized actions in UI (don't just disable — hide)

### 6.3 Archive Manager
- [ ] Add "Archived" tab/filter to Sold Archive, Layaway, Paluwagan, and Damaged screens
- [ ] Create `ArchiveRecordUseCase` — sets `is_archived = true` on completed records (admin/manager)
- [ ] Create `ExportArchiveUseCase` — generates CSV files for archived records by date range
- [ ] Create `PurgeArchivedRecordsUseCase` — hard deletes archived records after successful CSV export (admin only)
- [ ] Create `ArchiveManagerViewModel.kt`
- [ ] Create `ArchiveManagerScreen.kt` (admin only, in Settings):
  - Select record type: Sold / Layaway / Paluwagan / Damaged
  - Date range picker
  - Preview count of records to export
  - "Export to CSV" button → saves to Downloads folder
  - "Export & Delete" button → exports CSV then purges from DB
  - CSV filenames: `ykfj-archive-{type}-{date-range}.csv`

### 6.4 Backup & Restore
- [ ] Create `BackupManager.kt`:
  - **Auto backup (incremental):** DB-only backup nightly via WorkManager, keep last 3. Images backed up incrementally (only new/changed).
  - **Manual backup (full):** ZIP containing Room DB file + all images to Downloads as `ykfj-backup-YYYY-MM-DD-HHmmss.zip`
  - Restore: pick `.zip` file, validate contents, replace current DB + images, restart app
- [ ] Create `BackupScreen.kt`:
  - Manual backup button with last backup date
  - Auto backup status (last auto backup date, next scheduled)
  - Restore button with file picker
  - List of available backups on device (auto + manual)

### 6.5 Activity Log
- [ ] Create `ActivityLogRepository` implementation (from interface in Phase 1)
- [ ] Create `GetActivityLogsUseCase` — filterable by user, action type, date range
- [ ] Create `ExportActivityLogUseCase` — CSV export with date range (admin only)
- [ ] Create `CleanupActivityLogsUseCase` — auto-delete logs older than 90 days (runs on app start)
- [ ] Create `ActivityLogViewModel.kt`
- [ ] Create `ActivityLogScreen.kt` (in Settings):
  - Staff sees own actions only. Manager/Admin sees all.
  - Filter by: user, action type, date range
  - Each entry shows: timestamp, user, action icon, description
  - Expandable detail: old/new values for edits
  - Export CSV button (admin only)
- [ ] Verify logging integration across all existing use cases (should have been integrated directly during Phases 2-5 development)

### 6.6 Session & App Info
- [ ] Add session timeout setting (15 min / 30 min / 1 hour / Never, default 30 min)
- [ ] Add default layaway due date setting (configurable, default: 3 days) — used as default when creating layaways
- [ ] Add daily sales export password setting (admin only) — password required to open exported PDF
- [ ] Implement idle detection → auto-logout after timeout
- [ ] Display app version in Settings
- [ ] Display device role (Tablet/Phone)
- [ ] Display sync status and last sync time
- [ ] Tailscale IP configuration field (for remote access setup)

### 6.7 Phase 6 Testing
- [ ] Unit test: PermissionChecker for all role combinations
- [ ] Manual test: login as staff → verify restricted actions are hidden
- [ ] Manual test: login as manager → verify restricted actions are hidden
- [ ] Manual test: logout → verify navigates to login, session cleared
- [ ] Manual test: session timeout → leave app idle → verify auto-logout
- [ ] Manual test: archive sold records → verify hidden from active view → visible in "Archived" tab
- [ ] Manual test: export archived records to CSV → open in Excel/Google Sheets → verify format and data
- [ ] Manual test: export & delete → verify records removed from DB after CSV saved
- [ ] Manual test: backup → uninstall app → reinstall → restore → verify all data intact
- [ ] Manual test: activity log — perform various actions → verify all logged with correct details
- [ ] Manual test: activity log — login as staff → verify only own actions visible
- [ ] Manual test: activity log — edit metal rate → verify old/new values captured
- [ ] Manual test: activity log — export CSV → verify format
- [ ] Manual test: activity log — verify auto-cleanup of logs older than 90 days

**Phase 6 Deliverable:** Full admin controls, role-based permissions enforced, archive manager with CSV export & purge, manual backup and restore working.

---

## Phase 7 — Testing & QA (Week 11-12)

### 7.1 Unit Tests
- [ ] All use cases have unit tests
- [ ] All ViewModels have unit tests (using test dispatchers)
- [ ] ProductIdGenerator edge cases
- [ ] Price calculation edge cases (weighted vs fixed)
- [ ] Date filtering logic

### 7.2 Integration Tests
- [ ] Room DAO integration tests (all queries, joins, pagination)
- [ ] Ktor server route tests (sync, auth, CRUD)
- [ ] Sync flow integration test (push + pull)

### 7.3 UI Tests
- [ ] Login flow (valid + invalid credentials)
- [ ] Add product flow (weighted + fixed)
- [ ] Sell product flow
- [ ] Layaway creation + payment flow
- [ ] Search and filter inventory

### 7.4 Manual QA Checklist
- [ ] Enter 50+ real products with images — test performance
- [ ] Pagination: verify smooth scrolling with 50+ items
- [ ] Search: verify results are correct and fast
- [ ] Sell 10 items → check sold archive + analytics numbers
- [ ] Create 5 layaways with multiple payments → verify balances
- [ ] Create 2 paluwagan groups with 5+ members → record payments → advance rounds → complete
- [ ] Archive completed records → export CSV → purge → verify clean
- [ ] Mark 3 items damaged → verify damaged screen
- [ ] Export sales CSV → open in Google Sheets → verify format
- [ ] Two emulators: sync test with 50+ products
- [ ] Offline test: turn off network → use app → reconnect → sync
- [ ] Backup → delete app data → restore → verify everything
- [ ] Test on tablet emulator (landscape layout)
- [ ] Test on phone emulator (portrait layout)

### 7.5 Performance & Polish
- [ ] Profile app with Android Studio Profiler — fix any jank
- [ ] Verify image compression: full ~200KB, thumb ~15KB
- [ ] Verify thumbnail scrolling: inventory list should scroll at 60fps with 50+ items
- [ ] Verify FTS search speed: instant results with 500+ products
- [ ] Verify reactive UI: add/edit/delete product → list auto-refreshes without manual reload
- [ ] Verify pending sync queue: offline changes queue up, push on reconnect, no data loss
- [ ] Check memory usage with large inventory (50+ products with images)
- [ ] Add loading states for all async operations
- [ ] Add error handling for network failures (sync)
- [ ] Add empty states for all lists (no products yet, no sales today, etc.)
- [ ] Verify DB migration path: bump version to 2 with a test column, confirm auto-migration works

**Phase 7 Deliverable:** All tests passing, app stable with real-world data volume, performance verified.

---

## Phase 8 — Launch (Week 13)

### 8.1 Build Release APK
- [ ] Configure signing config (create keystore)
- [ ] Enable R8 shrinking + resource shrinking in release build type
- [ ] Create `proguard-rules.pro` (keep Room entities, domain models, Ktor serialization)
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Verify APK size reduced (expect 30-50% smaller than debug)
- [ ] Test release APK on emulator — verify R8 didn't break anything

### 8.2 Deploy to Real Devices
- [ ] Install on store tablet
- [ ] Install on phone
- [ ] Configure tablet as "Primary" device
- [ ] Configure phone as "Secondary" device
- [ ] Set up Tailscale on both devices
- [ ] Test LAN sync on store WiFi
- [ ] Test remote sync via Tailscale

### 8.3 Initial Data Entry
- [ ] Set up admin account (change default password)
- [ ] Create manager/staff accounts
- [ ] Enter all metal rates
- [ ] Enter all categories
- [ ] Start entering existing inventory

### 8.4 Go Live Checklist
- [ ] All data entered and verified
- [ ] Backup created
- [ ] Staff trained on app usage
- [ ] Tailscale working from outside store
- [ ] Tag release as v1.0.0

**Phase 8 Deliverable: YKFJ Inventory App is live and running in-store.**

---

## Future Enhancements (Post-Launch)
- [ ] Barcode/QR code scanning for product lookup
- [ ] Receipt printing (Bluetooth thermal printer)
- [ ] WhatsApp integration for layaway payment reminders
- [ ] Multiple store support
- [ ] Dashboard widgets for quick overview
- [ ] Dark mode
