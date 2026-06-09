# YKFJ Implementation Plan

> Track progress by checking off tasks. Each Claude Code session should read this file
> to know where we left off and what to build next.

## Phase 1 — Project Setup & Foundation (Week 1-2)

### 1.1 Project Initialization
- [x] Create Android project in Android Studio (Kotlin, Compose, Gradle KTS)
- [x] Configure `build.gradle.kts` with all dependencies (Room, Hilt, Ktor, Coil, Paging 3, Compressor)
- [x] Set up `.gitignore` for Android project
- [x] Initialize git repo, create `main` and `develop` branches
- [x] Configure `AndroidManifest.xml` (minSdk 31, permissions: INTERNET, CAMERA, READ/WRITE_STORAGE, ACCESS_NETWORK_STATE, NSD, FOREGROUND_SERVICE, POST_NOTIFICATIONS)

### 1.2 Room Database
- [x] Create all Room entities (`UserEntity`, `ProductEntity`, `ProductImageEntity`, `MetalRateEntity`, `CategoryEntity`, `CustomerEntity`, `SupplierEntity`, `SoldRecordEntity`, `LayawayRecordEntity`, `LayawayTransactionEntity`, `DamagedRecordEntity`, `PaluwaganGroupEntity`, `PaluwaganSlotEntity`, `PaluwaganPaymentEntity`, `ActivityLogEntity`)
  - `ProductEntity`: include `supplier_id` (FK → suppliers, nullable) + `date_acquired` (Long, required)
  - `SupplierEntity`: name, representative_name, mobile, address, notes
  - `CustomerEntity`: include `credit_score` (Int, default 100)
  - `SoldRecordEntity`: include `sold_by` (FK → users) + `discount_amount` + `discount_type` (NONE/FIXED/PERCENTAGE) + `is_archived` (Boolean, default false)
  - `LayawayRecordEntity`: include `created_by` (FK → users) + `due_date` (nullable Long) + `completion_date` (nullable Long) + `forfeited_amount` (nullable Double — set to total_paid when CANCELLED) + `is_archived` (Boolean, default false)
  - `DamagedRecordEntity`: include `recorded_by` (FK → users) + `is_archived` (Boolean, default false)
  - `PaluwaganGroupEntity`: include `is_archived` (Boolean, default false)
  - `ProductImageEntity`: 1 image per product (unique product_id), no display_order
- [x] Add `@Index` annotations on all entities for frequently queried columns:
  - products: status, category_id, metal_rate_id, updated_at, is_deleted
  - sold_records: sold_date, updated_at
  - layaway_records: status, updated_at
  - customers: name, updated_at
  - product_images: product_id, updated_at
  - paluwagan_groups: status, updated_at
  - paluwagan_slots: group_id, customer_id
  - paluwagan_payments: group_id, slot_id, round_number, status
- [x] Create FTS4 virtual table `ProductFts` for full-text search (name, product_id, notes)
- [x] Create `PendingSyncEntity` for offline change queue (phone side)
- [x] Create all DAOs (`UserDao`, `ProductDao`, `MetalRateDao`, `CategoryDao`, `CustomerDao`, `SupplierDao`, `SoldRecordDao`, `LayawayRecordDao`, `LayawayTransactionDao`, `DamagedRecordDao`, `ProductImageDao`, `PendingSyncDao`, `PaluwaganGroupDao`, `PaluwaganSlotDao`, `PaluwaganPaymentDao`, `ActivityLogDao`)
- [x] DAOs return `Flow<List<T>>` for all observe/list queries (reactive UI) — ensure list queries filter `WHERE is_archived = false` by default
- [x] Create `YkfjDatabase.kt` with all entities registered, version = 1, empty `autoMigrations` array (ready for future)
- [x] Add Room type converters for enums and dates
- [x] Seed default admin user on first launch (username: admin, password: admin123 — document that admin should change password after first login)

### 1.3 Dependency Injection
- [x] Create `AppModule.kt` (provides Room database, DAOs)
- [x] Create `RepositoryModule.kt` (binds repository interfaces to implementations)
- [x] Create `YkfjApp.kt` application class with `@HiltAndroidApp`

### 1.4 Domain Layer Setup
- [x] Create domain models (`User`, `Product`, `MetalRate`, `Category`, `Customer`, `Supplier`, etc.)
- [x] Create repository interfaces (`UserRepository`, `ProductRepository`, `SupplierRepository`, etc.) — return `Flow<List<T>>` for observe queries
- [x] Create `ActivityLogRepository` interface and `LogActivityUseCase`
- [x] Create `PendingSyncManager.kt` interface/logic for offline change queueing (called by Repositories)
- [x] Create mappers between entities and domain models

### 1.5 Theme, Navigation & Utilities
- [x] Create Material 3 theme (`Theme.kt`, `Color.kt`, `Type.kt`) — gold/warm color palette
- [x] Create `Screen` sealed class with all routes (Inventory, Sold Archive, Layaway, Paluwagan, Damaged, Metal Rates, Categories, Customers, Suppliers, Analytics, Settings)
- [x] Create `Sidebar.kt` composable (adaptive: permanent on tablet, modal on phone)
  - Navigation items: Inventory, Sold Archive, Layaway, Paluwagan, Damaged, Metal Rates, Categories, Customers, Suppliers, Analytics, Settings
  - **Alert badges:** Red badge on Layaway (overdue count) and Paluwagan (due payments count)
  - Current user display + role badge at bottom
  - Logout button
- [x] Create `NavGraph.kt` with all screen routes (placeholder screens)
- [x] Create `MainActivity.kt` as single-activity Compose host
- [x] Create `CurrencyFormatter.kt` utility — formats all money values as ₱3,200.00 (Philippine Peso)

### 1.6 Authentication
- [x] Create `PasswordHasher.kt` utility (bcrypt)
- [x] Create `LoginUseCase.kt`
- [x] Create `LoginViewModel.kt` with `LoginUiState` sealed class (StateFlow pattern)
- [x] Create `LoginScreen.kt` (username + password + role display)
- [x] Create `SessionManager.kt` (holds current user in memory, survives config changes)
  - Logout function clears session → navigates to login
  - Auto-timeout after configurable idle period (default 30 min)
  - Timeout settings: 15 min / 30 min / 1 hour / Never
- [x] Implement role-based navigation gating

### 1.7 Phase 1 Testing
- [x] Unit test: PasswordHasher
- [x] Unit test: LoginUseCase
- [x] Unit test: Room DAOs (in-memory DB) — verify indexes, FTS search, Flow emission (instrumented)
- [x] Manual test: app boots → login → sidebar visible → navigate between empty screens

**Phase 1 Deliverable:** App boots in emulator, admin logs in, sees sidebar navigation with empty placeholder screens.

---

## Phase 2 — Core Inventory (Week 3-4)

### 2.1 Metal Rates
- [x] Create `MetalRateRepository` implementation
- [x] Create `AddMetalRateUseCase`, `UpdateMetalRateUseCase`, `DeleteMetalRateUseCase`, `GetMetalRatesUseCase`
- [x] Note: when a metal rate price changes, all weighted products using that rate auto-update (selling price is calculated dynamically: weight × rate, never stored for weighted items)
- [x] Create `MetalRatesViewModel.kt` with `MetalRatesUiState`
- [x] Create `MetalRatesScreen.kt` — list with add/edit/delete
- [x] Create `MetalRateFormDialog.kt` — modal for add/edit

### 2.2 Categories
- [x] Create `CategoryRepository` implementation
- [x] Create `AddCategoryUseCase`, `UpdateCategoryUseCase`, `DeleteCategoryUseCase`, `GetCategoriesUseCase`
- [x] Create `CategoriesViewModel.kt` with `CategoriesUiState`
- [x] Create `CategoriesScreen.kt` — list with add/edit/delete
- [x] Create `CategoryFormDialog.kt` — modal for add/edit

### 2.3 Suppliers
- [x] Create `SupplierRepository` implementation
- [x] Create `AddSupplierUseCase`, `UpdateSupplierUseCase`, `DeleteSupplierUseCase`, `GetSuppliersUseCase`
- [x] Create `SuppliersViewModel.kt` with `SuppliersUiState`
- [x] Create `SuppliersScreen.kt` — list with add/edit/delete
- [x] Create `SupplierFormDialog.kt` — modal for add/edit (name, representative, mobile, address, notes)

### 2.4 Customer Directory
- [x] Create `CustomerRepository` implementation
- [x] Create `AddCustomerUseCase`, `UpdateCustomerUseCase`, `SearchCustomersUseCase`, `GetCustomersUseCase`
- [x] Create `CustomersViewModel.kt`
- [x] Create `CustomersScreen.kt` — searchable customer list with credit score badges (Excellent/Good/Fair/Poor)
- [x] Create `CustomerFormDialog.kt` — add/edit (name, mobile, phone, birthday, address, notes). All roles can add customers. Only Admin/Manager can edit.
- [x] Create `CustomerDetailScreen.kt` (admin/manager only) — customer info + transaction history tabs:
  - Sales tab: all sold_records for this customer
  - Layaway tab: all layaway_records for this customer
  - Paluwagan tab: per-group card showing position, contribution, round progress, scrollable round-by-round payment status chips (PAID=green / LATE=red / UNPAID=grey), and "X on-time · Y late" summary — wired via `PaluwaganRepository.observeSlotsForCustomer` + `observePaymentsForSlot` in `CustomerDetailViewModel`
- [x] Create `CustomerAutoSuggest.kt` — reusable composable for customer selection with auto-suggest

### 2.5 Inventory — Add & Edit Item (Admin Only)
- [x] Create `ProductRepository` implementation
- [x] Create `ProductIdGenerator.kt` utility (NAME-RATE-CAT-000001 format, sequence scoped per combo)
- [x] Create `AddProductUseCase`, `UpdateProductUseCase`
- [x] Create `AddItemViewModel.kt`
- [x] Create `AddItemModal.kt` composable (admin only, shared for add + edit, pre-fills fields when editing):
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
- [x] Create `ImageStorageManager.kt` — compress + save two versions to app internal storage (1 image per product):
  - Full: max 1024px, ~200KB → `files/images/full/{image_id}.jpg`
  - Thumbnail: 200x200px, ~15KB → `files/images/thumb/{image_id}.jpg`
- [x] Create `ImageCompressor.kt` — Compressor library wrapper (configurable target size + dimensions)
- [x] Load images via Coil: thumbnails for list cards, full for detail view

### 2.6 Product Detail Screen
- [x] Create `ProductDetailScreen.kt`:
  - Full product info display (all fields, full-size image, supplier, date acquired)
  - Profit margin display (capital vs selling price, margin %) — Admin only
  - Edit button → opens AddItemModal pre-filled (reuse same form)
  - Status change actions (Sold, Layaway, Damaged) — buttons scaffolded, dialogs wired in Phase 2.8
- [x] Create `ProductDetailViewModel.kt`
- [ ] Navigate from product card tap → detail screen (wired in Phase 2.7 with the product list)

### 2.7 Inventory — List & Search
- [x] Create `GetProductsUseCase` with Paging 3 support (returns Flow)
- [x] Create `SearchProductsUseCase` using FTS4 table for instant search
- [x] Create `InventoryViewModel.kt` with StateFlow, auto-refreshes via Room Flow
- [x] Create `InventoryScreen.kt`:
  - Search bar at top
  - Filter chip: Show Sold toggle (SOLD hidden by default)
  - Default sort: newest first (by date_acquired descending)
  - Paginated LazyColumn of product cards (20 per page)
- [x] Create `ProductCard.kt` composable:
  - Product image thumbnail (Coil, diamond icon placeholder when no image)
  - Name, product ID
  - Selling price (WEIGHTED: weight × rate; FIXED: stored value)
  - Status badge (Available, Sold, Layaway, Damaged)
  - Category, metal rate, quantity

### 2.8 Inventory — Status Changes
- [x] Create `MarkAsSoldUseCase` — any role can sell, creates sold_record with quantity (1 to available qty), decreases product quantity, sets SOLD when qty reaches 0
- [x] Create `MarkAsLayawayUseCase`
- [x] Create `MarkAsDamagedUseCase`
- [x] Create `RevertStatusUseCase` — admin/manager can revert Sold/Damaged back to Available (restores qty, soft-deletes record, requires reason)
- [x] Add status change actions to **product detail screen only** (not on inventory list cards):
  - "Mark as Sold" → opens SoldDialog (qty picker + price confirm + optional discount), creates sold_record, decreases available qty, status → SOLD only when all units accounted for
  - "Mark as Layaway" → opens LayawayDialog (customer selection + qty picker + per-unit price), creates layaway_record. Only one active layaway per product.
  - "Mark as Damaged" → opens DamagedDialog (reason input), creates damaged_record, always 1 unit at a time
- [x] Create `SoldDialog.kt` — quantity picker (1 to available qty), confirm per-unit sale price, optional discount (fixed/percentage, admin/manager only, max 20% of profit), snapshot prices at time of sale
- [x] Create `LayawayDialog.kt` — customer auto-suggest + qty picker (1 to available) + per-unit price + optional due date
- [x] Create `RevertDialog.kt` — confirmation with mandatory reason field (admin/manager only)
- [x] Create `DamagedDialog.kt` — reason input
- [x] **Damage History section on Product Detail screen** — list all damaged records for the product showing reason, date recorded, and who recorded it
- [x] **Persist revert reason** — store the reason in the soft-deleted record's `notes` field before deleting, so it is retrievable when the Activity Log is wired up in Phase 5

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
- [x] Create `GetSoldRecordsUseCase` (with date filter, default: today)
- [x] Create `RevertSoldUseCase` — admin/manager can revert sold item back to Available (restores qty +1, soft-deletes sold_record)
- [x] Create `ExportDailySalesPdfUseCase` — generates password-protected PDF of today's sales
  - Branded header: "YKFJ Gold Jewelry — Daily Sales Report — {date}"
  - Table: product name, grams, size, qty, capital, selling price, discount (if any), final price
  - Footer: total items sold, total revenue, total capital, total profit
  - PDF password from `app_settings.daily_export_password`
  - Filename: `ykfj-sales-YYYY-MM-DD.pdf` saved to Downloads
- [x] Create `SoldArchiveViewModel.kt`
- [x] Create `SoldArchiveScreen.kt`:
  - Date picker filter (default: current date)
  - List of sold items with: product info, sale price, capital, profit, customer, date
  - Daily summary: total revenue, total capital, total profit, items count
  - "Export Today's Sales" button → generates password-protected PDF (staff, manager, admin)
  - "Revert to Available" action per item (admin/manager only)

### 3.2 Layaway Management
- [x] Create `GetActiveLayawaysUseCase`, `AddLayawayPaymentUseCase`, `SplitLayawayPaymentUseCase`, `UpdateLayawayUseCase`, `CompleteLayawayUseCase`, `CancelLayawayUseCase`, `DeleteLayawayPaymentUseCase`
- [x] Create `LayawayViewModel.kt`
- [x] Create `LayawayScreen.kt`:
  - Flat list of all active layaways, searchable/filterable by customer name
  - Each row: customer, product, qty, total (unit_price × qty), paid, remaining, due date
  - Overdue layaways flagged/highlighted
  - Tap to view full transaction history with dates
- [x] Create `LayawayDetailScreen.kt` or expandable section:
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
- [x] Create `PaluwaganGroupEntity`, `PaluwaganSlotEntity`, `PaluwaganPaymentEntity` (Room entities with indexes)
- [x] Create `PaluwaganGroupDao`, `PaluwaganSlotDao`, `PaluwaganPaymentDao` (Flow-based queries)
- [x] Create `PaluwaganRepository` implementation
- [x] Create use cases:
  - `CreatePaluwaganGroupUseCase` — name, contribution amount, frequency, total slots, start date
  - `AddPaluwaganSlotUseCase` — assign customer from directory to a position (supports multiple slots per customer)
  - `SwapPaluwaganPositionsUseCase` — admin/manager swaps two slots' positions
  - `RecordPaluwaganPaymentUseCase` — mark slot as PAID/LATE for a round
  - `AdvancePaluwaganRoundUseCase` — move to next round
  - `CompletePaluwaganGroupUseCase` — mark group as COMPLETED when all rounds done
  - `GetActivePaluwaganGroupsUseCase`
- [x] Create `PaluwaganViewModel.kt` with `PaluwaganUiState`
- [x] Create `PaluwaganScreen.kt`:
  - List of active paluwagan groups with: name, contribution, frequency, current round/total, member count
  - "Create Group" button (admin/manager)
- [x] Create `PaluwaganDetailScreen.kt`:
  - Group info header (name, contribution, frequency, status)
  - Member list with position numbers and customer names
  - Current round indicator — who collects this round
  - Payment grid: rows = slots, columns = rounds, cells = PAID/UNPAID/LATE status
  - "Record Payment" action per slot (updates customer credit score: +1 on-time, -2 late)
  - "Swap Positions" action (admin/manager)
  - "Advance Round" button
- [x] Create `CreatePaluwaganDialog.kt` — group setup form (inline in PaluwaganScreen.kt)
- [x] Create `AddPaluwaganMemberDialog.kt` — customer auto-suggest + position assignment (inline in PaluwaganDetailScreen.kt)

### 3.4 Damaged Items
- [x] Create `GetDamagedRecordsUseCase`
- [x] Create `RevertDamagedUseCase` — admin/manager can revert damaged item back to Available (restores qty +1, soft-deletes damaged_record)
- [x] Create `DamagedViewModel.kt`
- [x] Create `DamagedScreen.kt`:
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
- [x] Create `GetDailySalesUseCase`, `GetMonthlySalesUseCase`, `GetInventorySummaryUseCase`
- [x] Create `AnalyticsViewModel.kt`
- [x] Create `AnalyticsScreen.kt`:
  - **Daily Sales Card:** date picker, revenue, capital cost, profit, items sold count
  - **Monthly Sales Card:** month picker, same metrics aggregated
  - **Inventory Summary Card:** total items, total inventory value (capital), total potential revenue
  - **Top Categories:** items sold per category (current month)
  - **Layaway Outstanding:** total outstanding balance across all active layaways
  - **Paluwagan Summary:** active groups count, total contribution collected, current round progress

### 4.2 Export to CSV
- [x] Create `ExportSalesUseCase` — generates CSV from sold_records for selected date range
- [x] CSV format: Date, Product ID, Product Name, Category, Capital, Sold Price, Profit, Customer
- [x] Save CSV to Downloads folder
- [x] Share intent to open in Google Sheets or other apps
- [x] Add export button to Analytics screen

### 4.3 Phase 4 Testing
- [ ] Unit test: Daily/monthly aggregation calculations
- [ ] Unit test: CSV generation format
- [ ] Manual test: sell several items → check analytics numbers match → export CSV → open in Google Sheets
- [ ] Manual test: daily sales PDF — sell items with/without discounts → export → verify PDF layout, totals, and password protection

**Phase 4 Deliverable:** Analytics dashboard showing daily/monthly financials, inventory value, and CSV export for Google Sheets.

---

## Phase 5 — Sync & Remote Access (Week 8-9)

### 5.1 Ktor Embedded Server (Tablet)
- [x] Create `SyncServer.kt` — Ktor embedded server setup
- [x] Create `AuthRoutes.kt` — POST /api/auth/login, GET /api/auth/me
- [x] Create `SyncRoutes.kt` — GET /api/sync/status, GET /api/sync/changes, POST /api/sync/push
- [x] Create `CrudRoutes.kt` — CRUD endpoints for all entities
- [x] Create JWT token generation/validation (local, no cloud)
- [x] Create `ImageRoutes.kt` — GET /api/images/{image_id}, GET /api/images/sync?since=
- [x] Create `SyncServerManager.kt` — start/stop server lifecycle tied to app
- [x] Create `SyncForegroundService.kt` — foreground service to keep Ktor server alive when screen is off
  - Persistent notification: "YKFJ Server Running · Last sync: X min ago"
  - Starts automatically when app opens on tablet
  - Survives screen off, app backgrounded
  - Add FOREGROUND_SERVICE permission to AndroidManifest

### 5.2 Network Service Discovery
- [x] Create `NsdManager.kt` — register tablet as service on network
- [x] Create `NsdDiscovery.kt` — phone discovers tablet service
- [x] Create `ConnectionResolver.kt`:
  - Try NSD (same WiFi) first
  - Fall back to Tailscale IP (stored in settings)
  - Fall back to offline cache

### 5.3 Sync Client (Phone)
- [x] Create `SyncClient.kt` — Ktor HTTP client
- [x] Create `SyncManager.kt`:
  - Pull changes from tablet since last sync timestamp
  - Push local changes (including pending queue) to tablet
  - Update local Room DB with received changes
  - Store last sync timestamp
- [x] Integrate with `PendingSyncManager` (from Phase 1):
  - On reconnect, auto-push all PENDING actions to tablet in order
  - Mark as SYNCED on success, FAILED on error (retry on next sync)
  - Clear SYNCED entries after confirmation
- [x] Auto-sync on app open when connection available
- [x] Manual pull-to-refresh sync
- [x] Image sync: download thumbnails first (fast list view), then full images in background
- [x] Sync status indicator in UI (last synced: X minutes ago, pending changes count)

### 5.4 Device Role Configuration
- [x] Add setting: "This device is: Tablet (Primary) / Phone (Secondary)"
- [x] Tablet mode: starts Ktor server, registers NSD
- [x] Phone mode: runs sync client, discovers via NSD

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
- [x] Create `ManageUsersUseCase` (create, update, resetPassword, deactivate) with sealed `Result` for field errors
- [x] Create `UserManagementViewModel.kt` (the existing `SettingsViewModel` continues to handle device-role/sync UI)
- [x] Create `UserManagementScreen.kt` (admin only, defended in-screen):
  - LazyColumn of `UserCard` with name, @username, role badge, "(You)" tag for current user
  - Edit / Reset password / Deactivate icon buttons per row (deactivate disabled on the current user)
  - FAB → Add User dialog (username, name, password, role segmented buttons)
  - Edit dialog (username/name/role) + separate Reset Password dialog (with note that it doesn't sync)
  - Deactivate confirmation dialog
- [x] Wire up sync: added `enqueueUser` to `SyncEnqueuer`, decode "users" entity_type in `pushPending`, `UserRepositoryImpl` now enqueues create/update/resetPassword/deactivate. Server-side push handler (pre-existing) only updates metadata and skips inserts — so the UI surfaces a note that resets and account creation should be done on the tablet.
- [x] Replace the "Coming in Phase 6.1" placeholder in Settings with a `NavigableSection`. Route `user_management` added to `NavGraph`.

### 6.2 Role Permission Enforcement
- [x] Create `PermissionChecker.kt` utility — pure `forRole(role)` returning a `Permissions` data class with one boolean per privileged action (add/edit/delete products, view profit, apply discount, edit customers, view history, edit layaway, manage paluwagan groups, swap positions, manage rates/categories/suppliers, view analytics, archive/purge, manage users, backup, export). `Permissions.NONE` covers logged-out fallback.
- [x] Audit + tighten existing role gates across screens — most ViewModels already exposed `canEdit`/`isAdmin`/`isAdminOrManager`; this pass added the remaining hides:
  - **Inventory list:** "Add Item" FAB hidden for non-admin; empty-state copy adjusted
  - **Customers:** detail navigation now disabled for Staff (`canViewHistory` flag); edit icon already gated to Admin+Manager
  - **Layaway detail:** "Add Payment" relaxed to all roles (was Admin+Manager) — matches business rule "all roles add layaway payments"; edit/delete still Admin only
  - **Customer Layaway detail:** same payment-add relaxation; complete/cancel/delete-payment stays Admin only
  - **Paluwagan detail:** per-row "Record Payment" relaxed to all roles (was Admin+Manager) — matches "all roles add paluwagan payments"; reorder, swap, archive, advance-round still Admin+Manager
- [x] Pre-existing gates verified solid: `ProductDetailScreen` (edit/delete/profit Admin, revert Admin+Manager), `SoldDialog` (discount Admin+Manager), `DamagedScreen` / `SoldArchiveScreen` (revert Admin+Manager), sidebar already hides MetalRates/Categories/Suppliers/Analytics/Settings from Staff and the Settings screen from Manager.
- [x] All gates implemented as **hide**, not disable — empty FABs, missing icons, null click handlers — per the plan's directive.

### 6.3 Archive Manager
- [ ] Add "Archived" tab/filter to Sold Archive, Layaway, Paluwagan, and Damaged screens (deferred — admin tooling complete; per-screen archived view is a follow-up)
- [x] Create `ArchiveRecordUseCase` — wraps the four repos' `archive()` calls + activity log; takes `ArchivableRecordType` enum (SOLD / LAYAWAY / DAMAGED / PALUWAGAN) plus record id
- [x] Create `ExportArchiveUseCase` — pulls archived rows in a date range, resolves product/customer/user FKs to readable names, builds CSV via `CsvWriter`, writes to public Downloads via `MediaStore`. Returns `Result.Success(uri, rowCount, fileName)` / `NoRecords` / `WriteFailed`.
- [x] Create `PurgeArchivedRecordsUseCase` — hard-deletes archived rows in range. For PALUWAGAN, also hard-deletes child slots and payments. Logs an activity entry on success.
- [x] DAO additions: `getArchivedInRange(start, end)` + `hardDeleteArchivedInRange(start, end)` on `SoldRecordDao`, `LayawayRecordDao`, `DamagedRecordDao`, `PaluwaganGroupDao`. Date filter uses each type's natural transaction date (`sold_date` / `created_at` / `date_recorded` / `start_date`).
- [x] Create `CsvWriter.kt` utility — RFC-4180-compliant field escaping; writes to public Downloads with `MediaStore.Downloads` + `IS_PENDING` two-step commit; UTF-8.
- [x] Create `ArchiveManagerViewModel.kt` — `ArchiveManagerUiState` with type / start / end / previewCount / isAdmin / isWorking / messages. Auto-refreshes the preview count whenever type or range changes.
- [x] Create `ArchiveManagerScreen.kt` (in Settings, admin-only `Export & Delete`):
  - `FilterChip` row for record type: Sold / Layaway / Damaged / Paluwagan
  - Two `OutlinedButton` date fields opening Material3 `DatePickerDialog`s
  - Preview card showing N records that match
  - **Export to CSV** primary button (any non-staff role can export)
  - **Export & Delete** outlined error-color button (Admin only — shows confirmation)
  - CSV filenames: `ykfj-archive-{slug}-{yyyy-MM-dd}-to-{yyyy-MM-dd}.csv` in Downloads
- [x] Wire into Settings — replaced the "Coming in Phase 6.3" placeholder with a `NavigableSection`. Route `archive_manager` added to NavGraph.

### 6.4 Backup & Restore
- [x] Create `BackupManager.kt`:
  - **Manual backup (full):** ZIPs Room DB + entire `images/` tree, writes to public Downloads via `MediaStore` as `ykfj-backup-yyyy-MM-dd-HHmmss.zip` (with `IS_PENDING` two-step commit so partial writes are cleaned up automatically)
  - **Auto backup (DB-only):** ZIP of just the Room DB to `filesDir/backups/auto/ykfj-auto-yyyy-MM-dd-HHmm.zip`, rotates to keep [BackupManager.AUTO_KEEP] = 3 most recent. (Image incremental backup deferred — manual backup covers full snapshots.)
  - **Common pre-write step:** `PRAGMA wal_checkpoint(FULL)` so a plain file copy is internally consistent.
  - **Restore (`restoreFromZip`):** stages everything to cache first, validates `database/ykfj.db` exists & non-zero, calls `database.close()`, deletes WAL/SHM, copies main DB into place, replaces images dir if archive contains one, returns `Success`. Caller must relaunch the process — `BackupRestoreHelper.restartProcess()` does that with `Intent.FLAG_ACTIVITY_CLEAR_TASK` + `Runtime.getRuntime().exit(0)`.
  - **`peekArchive`:** lightweight ZIP scan that confirms `database/ykfj.db` exists before the user is asked to confirm.
- [x] Create `BackupWorker.kt` — `@HiltWorker CoroutineWorker`, `scheduleDaily()` enqueues a unique periodic work request with `KEEP` policy (idempotent), 24h interval, `RequiresBatteryNotLow` constraint. Returns `Result.retry()` on failure.
- [x] Wire WorkManager: `YkfjApp` implements `Configuration.Provider` and supplies a `HiltWorkerFactory`; `AndroidManifest.xml` removes the default `WorkManagerInitializer` startup so Hilt's factory takes over. `BackupWorker.scheduleDaily(this)` is called from `YkfjApp.onCreate()`.
- [x] Create `BackupViewModel.kt` — `BackupUiState` exposes `lastManualAt` / `lastAutoAt` / `autoBackups` / `isAdmin` / `isWorking` / messages / `pendingRestart`. `runManualBackup()`, `restoreFromUri(uri)` (peek → confirm → restore → set pendingRestart so screen shows restart prompt).
- [x] Create `BackupScreen.kt`:
  - Manual backup card with "Last manual backup: …" + "Back up now" button
  - Auto backup card with "Last auto backup: …" + count of locally-stored auto backups
  - Restore card with "Pick backup file…" using `ActivityResultContracts.OpenDocument` for `application/zip`. Admin-only — non-admin sees a disabled state with explanation.
  - List of auto backups on device with name, relative time, formatted size
  - Confirmation dialog before restore (warns the app will close)
  - Mandatory "Restart now" dialog after a successful restore — calls `BackupRestoreHelper.restartProcess`
- [x] Wire into Settings — replaced "Coming in Phase 6.4" placeholder with `NavigableSection`. Route `backup` added to NavGraph.

### 6.5 Activity Log
- [x] `ActivityLogRepository` + `Impl` were already in place from Phase 1 (with `observe(filters)` + `purgeOlderThan`); reused as-is.
- [x] `GetActivityLogsUseCase` — wraps `repository.observe` with the role-aware policy: Staff is locked to their own user_id even if a different filter is requested.
- [x] `ExportActivityLogUseCase` — admin-only; pulls via `ActivityLogDao.getForExport`, resolves `user_id` → display name, writes a CSV (`timestamp, user_id, user_name, action, entity_type, entity_id, description, old_value, new_value`) via `CsvWriter` to public Downloads as `ykfj-activity-log-{from}-to-{to}.csv`.
- [x] `CleanupActivityLogsUseCase` — `repository.purgeOlderThan(now - 90d)`. Wired into `YkfjApp.onCreate()` (fire-and-forget on the IO scope) so it runs once per launch.
- [x] `ActivityLogViewModel.kt` — `ActivityLogFilter` (userId / action / start / end) + `ActivityLogUiState` (logs list, user picker source, lock flag for Staff, `canSelectUser`, `canExport`). Logs Flow is built reactively via `combine(filter, currentUser).flatMapLatest`.
- [x] `ActivityLogScreen.kt` (in Settings):
  - Top app bar with admin-only **export** action icon
  - Horizontal filter chip row: user picker (locked to "Me only" for Staff), action picker, From/To date chips (Material3 `DatePickerDialog`)
  - LazyColumn of expandable cards. Each card shows timestamp + username + action chip with icon. Tapping a card with `old_value`/`new_value` payload reveals before/after blocks (monospace).
- [x] Existing `LogActivityUseCase` integration — every mutating use case from Phases 2–5 already calls it (verified via grep: products, customers, layaway, paluwagan, sold/damaged, supplier, metal rate, category, archive, user-management). No new wiring required.

### 6.6 Session & App Info
- [x] Centralized `app_settings` keys for the Phase 6.6 settings in `data/local/AppSettingKeys.kt` (`session_timeout`, `daily_export_password`).
- [x] Session timeout setting (15 min / 30 min / 1 hour / Never, default 30 min). `SessionManager.idleTimeoutFlow` exposes the live value; `YkfjApp.onCreate` loads the persisted choice on launch via `IdleTimeout.fromName`.
- [x] Default layaway due-date setting — **dropped**. The picker stays "No due date" by default; users set it explicitly per layaway as needed.
- [x] Daily sales export password (admin only). `ExportDailySalesPdfUseCase` already reads `daily_export_password`; the Session & App Info section now exposes a save UI (admin-gated) instead of relying on the `ykfj2024` fallback.
- [x] Idle detection → auto-logout — already wired in `MainActivity.onCreate` (`LaunchedEffect(currentUser)` polls `sessionManager.isSessionExpired()` once per minute) and `MainActivity.onUserInteraction` records activity. Now driven by the persisted timeout.
- [x] Display app version (`BuildConfig.VERSION_NAME` exposed in `SettingsUiState.appVersion`, rendered in the Session & App Info `InfoRow`).
- [x] Display device role (Tablet / Phone) — shown in the new section's `InfoRow` (previously only inside the Sync section's segmented buttons).
- [x] Display sync status and last sync time — `InfoRow` summarises `SyncStatus` (server-running for tablet, last-sync / error for phone).
- [x] Tailscale IP configuration — already exposed in the Sync section (`PhoneSyncDetails.tabletIp` + `TabletSyncDetails.ownTailscaleIp`); no duplicate field needed.
- [x] `SettingsViewModel`: `setIdleTimeout(timeout)` and `setDailyExportPassword(pw)` — each persists via `AppSettingsDao.upsert` and updates UI state.
- [x] `SettingsScreen.SessionAppInfoSection` — collapsible card with `IdleTimeoutChooser` (4-segment row), `DailyExportPasswordField` (admin-only, masked + Save), and the read-only `AppInfoBlock`.

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

## Phase 9 — Gold Purchases Core

> Complete Phases 9–11 before the Phase 7 QA pass and Phase 8 launch.

### 9.1 Database Migration — v5 → v6

- [x] Bump `YkfjDatabase.version` from 5 to 6; add `AutoMigration(from = 5, to = 6)` to the `autoMigrations` array.
- [x] Add `CashMovementType` enum to `data/local/db/enums/Enums.kt`:
  - `CHANGE_FLOAT`, `PURCHASE_FLOAT`, `EXPENSE`, `ADJUSTMENT`
- [x] Add `GOLD_PURCHASED`, `GOLD_PURCHASE_REVERTED` to `ActivityAction` enum in `data/local/db/enums/Enums.kt`.
- [x] Create `data/local/db/entity/GoldPurchaseRecordEntity.kt` — table `gold_purchase_records`:
  - `id: String` (PK UUID), `customer_id: String?`, `total_paid: Double`, `paid_at: Long`, `notes: String?`, `recorded_by: String`, `linked_sold_record_id: String?` (null in Phase 9 — reserved for Phase 10 trade-in), `updated_at: Long`, `is_deleted: Boolean = false`, `sync_version: Long = 0`
  - Indexes: `customer_id`, `paid_at`, `recorded_by`, `linked_sold_record_id`, `updated_at`, `is_deleted`
- [x] Create `data/local/db/entity/GoldPurchaseItemEntity.kt` — table `gold_purchase_items`:
  - `id: String` (PK UUID), `purchase_record_id: String`, `description: String`, `weight_grams: Double`, `metal_rate_id: String?` (optional label reference — no price auto-fill), `buy_rate_per_gram: Double` (always manually entered), `computed_value: Double` (= weight × rate), `override_value: Double?`, `final_value: Double` (= override ?: computed), `photo_filename: String?`, `updated_at: Long`, `is_deleted: Boolean = false`
  - Indexes: `purchase_record_id`, `metal_rate_id`, `updated_at`, `is_deleted`
- [x] Create `data/local/db/entity/CashMovementEntity.kt` — table `cash_movements` (used by Phase 11, created here so the single v5→v6 migration covers all new tables):
  - `id: String` (PK UUID), `type: CashMovementType`, `amount: Double` (positive = in, negative = out), `date: Long` (start-of-day millis), `notes: String?`, `recorded_by: String`, `recorded_at: Long`, `updated_at: Long`, `is_deleted: Boolean = false`, `sync_version: Long = 0`
  - Indexes: `date`, `type`, `recorded_by`, `updated_at`, `is_deleted`
- [x] Add `payment_method: PaymentMethod = PaymentMethod.CASH` to `SoldRecordEntity` — Room autoMigration handles the DEFAULT 'CASH' column addition.
- [x] Add `payment_method: PaymentMethod = PaymentMethod.CASH` to `LayawayTransactionEntity` — same autoMigration.
- [x] Register all three new entities in `YkfjDatabase` entities list; add three new abstract DAO functions.
- [x] Add `default_change_float` to `AppSettingKeys.kt` (constant only — Settings UI is Phase 11).

### 9.2 Domain Layer

- [x] Create `domain/model/GoldPurchaseRecord.kt`:
  - `id`, `customerId: String?`, `totalPaid: Double`, `paidAt: Long`, `notes: String?`, `recordedBy: String`, `linkedSoldRecordId: String?`, `updatedAt: Long`, `isDeleted: Boolean`, `syncVersion: Long`
- [x] Create `domain/model/GoldPurchaseItem.kt`:
  - `id`, `purchaseRecordId: String`, `description: String`, `weightGrams: Double`, `metalRateId: String?`, `buyRatePerGram: Double`, `computedValue: Double`, `overrideValue: Double?`, `finalValue: Double`, `photoFilename: String?`, `updatedAt: Long`, `isDeleted: Boolean`
- [x] Update `domain/model/SoldRecord.kt` — add `paymentMethod: PaymentMethod = PaymentMethod.CASH`
- [x] Update `domain/model/LayawayTransaction.kt` (or equivalent layaway payment model) — add `paymentMethod: PaymentMethod = PaymentMethod.CASH`
- [x] Create `domain/repository/GoldPurchaseRepository.kt`:
  - `fun observeAll(): Flow<List<GoldPurchaseRecord>>`
  - `fun observeById(id: String): Flow<GoldPurchaseRecord?>`
  - `suspend fun getById(id: String): GoldPurchaseRecord?`
  - `fun observeItemsForRecord(recordId: String): Flow<List<GoldPurchaseItem>>`
  - `suspend fun getItemsForRecord(recordId: String): List<GoldPurchaseItem>`
  - `suspend fun insert(record: GoldPurchaseRecord, items: List<GoldPurchaseItem>)`
  - `suspend fun softDelete(recordId: String, updatedAt: Long)`
  - `suspend fun softDeleteItems(recordId: String, updatedAt: Long)`
- [x] Create `domain/usecase/goldpurchase/AddGoldPurchaseUseCase.kt`:
  - `Params(customerId: String?, items: List<ItemDraft>, notes: String?, recordedBy: String)`
  - `ItemDraft(description, weightGrams, metalRateId?, buyRatePerGram, overrideValue?, photoFilename?)`
  - Validates ≥ 1 item; each item must have weight > 0 and buyRatePerGram > 0
  - Computes `computedValue`, `finalValue`, `totalPaid` — no UI math trusted
  - Inserts record + items in a single DB transaction
  - Calls `LogActivityUseCase(GOLD_PURCHASED, entityId = recordId)`
  - `Result`: `Success(recordId)`, `NoItems`, `InvalidItem`
- [x] Create `domain/usecase/goldpurchase/GetGoldPurchasesUseCase.kt`:
  - Returns `Flow<List<GoldPurchaseRecord>>`; caller resolves customer names
- [x] Create `domain/usecase/goldpurchase/GetGoldPurchaseDetailUseCase.kt`:
  - Returns `Flow<Pair<GoldPurchaseRecord?, List<GoldPurchaseItem>>>`
- [x] Create `domain/usecase/goldpurchase/RevertGoldPurchaseUseCase.kt`:
  - `Params(recordId: String, reason: String, actorUserId: String)`
  - Fails with `IsTradeIn` if `linkedSoldRecordId != null` — trade-in revert is atomic and handled in Phase 10
  - Soft-deletes record + all its items
  - Calls `LogActivityUseCase(GOLD_PURCHASE_REVERTED)`
  - `Result`: `Success`, `NotFound`, `IsTradeIn`, `Error`
- [x] Update `domain/usecase/product/MarkAsSoldUseCase.Params` — add `paymentMethod: PaymentMethod = PaymentMethod.CASH`; pass through to entity
- [x] Update `domain/usecase/layaway/AddLayawayPaymentUseCase.Params` — add `paymentMethod: PaymentMethod = PaymentMethod.CASH`
- [x] Update `domain/usecase/layaway/SplitLayawayPaymentUseCase.Params` — add `paymentMethod: PaymentMethod = PaymentMethod.CASH`

### 9.3 Data Layer

- [x] Create `data/local/db/dao/GoldPurchaseRecordDao.kt`:
  - `fun observeAll(): Flow<List<GoldPurchaseRecordEntity>>` — `WHERE is_deleted = 0 ORDER BY paid_at DESC`
  - `fun observeById(id: String): Flow<GoldPurchaseRecordEntity?>`
  - `suspend fun getById(id: String): GoldPurchaseRecordEntity?`
  - `@Insert(REPLACE) suspend fun upsert(record: GoldPurchaseRecordEntity)`
  - `suspend fun softDelete(id: String, updatedAt: Long)`
- [x] Create `data/local/db/dao/GoldPurchaseItemDao.kt`:
  - `fun observeForRecord(recordId: String): Flow<List<GoldPurchaseItemEntity>>`
  - `suspend fun getForRecord(recordId: String): List<GoldPurchaseItemEntity>`
  - `@Insert(REPLACE) suspend fun insertAll(items: List<GoldPurchaseItemEntity>)`
  - `@Insert(REPLACE) suspend fun upsert(item: GoldPurchaseItemEntity)`
  - `suspend fun softDeleteForRecord(recordId: String, updatedAt: Long)`
- [x] Create `data/local/db/dao/CashMovementDao.kt`:
  - `fun observeForDay(startOfDay: Long, endOfDay: Long): Flow<List<CashMovementEntity>>`
  - `suspend fun getForTypeAndDay(type: String, startOfDay: Long, endOfDay: Long): CashMovementEntity?`
  - `@Insert(REPLACE) suspend fun upsert(movement: CashMovementEntity)`
  - `suspend fun softDelete(id: String, updatedAt: Long)`
- [x] Create `data/mapper/GoldPurchaseMapper.kt` — `GoldPurchaseRecordEntity ↔ GoldPurchaseRecord`, `GoldPurchaseItemEntity ↔ GoldPurchaseItem`
- [x] Update `data/mapper/SoldRecordMapper.kt` — map `payment_method` field both directions
- [x] Update layaway transaction mapper — map `payment_method` field both directions
- [x] Create `data/repository/GoldPurchaseRepositoryImpl.kt` — implements `GoldPurchaseRepository`; `insert()` wraps both DAO calls in `withTransaction { }` for atomicity
- [x] Update `di/RepositoryModule.kt` — `@Binds abstract fun bindGoldPurchaseRepository(impl: GoldPurchaseRepositoryImpl): GoldPurchaseRepository`
- [x] Update `di/AppModule.kt` — provide `goldPurchaseRecordDao()`, `goldPurchaseItemDao()`, `cashMovementDao()` from `YkfjDatabase`

### 9.4 Payment Method on Existing Flows

- [x] Create reusable `PaymentMethodPicker` composable in `ui/components/` (or inline in files):
  - `SingleChoiceSegmentedButtonRow` with Cash / GCash / Online Banking / Other
  - Param: `selected: PaymentMethod`, `onSelected: (PaymentMethod) -> Unit`
- [x] `ui/inventory/ProductDetailScreen.kt` — add `PaymentMethodPicker` inside `SoldDialog`
- [x] `ui/inventory/ProductDetailViewModel.kt` — `submitSell(...)` accepts and forwards `paymentMethod`; pass to `MarkAsSoldUseCase.Params`
- [x] `ui/layaway/LayawayDetailScreen.kt` — add `PaymentMethodPicker` to add-payment and split-payment dialogs
- [x] `ui/layaway/LayawayDetailViewModel.kt` — `addPayment(...)` and `splitPayment(...)` accept and forward `paymentMethod`
- [x] `ui/layaway/CustomerLayawayDetailScreen.kt` — same `PaymentMethodPicker` addition
- [x] `ui/layaway/CustomerLayawayDetailViewModel.kt` — same forwarding

### 9.5 Gold Purchases List Screen

- [x] Create `ui/goldpurchase/GoldPurchasesViewModel.kt`:
  - `GoldPurchasesUiState(records: List<GoldPurchaseSummary>, searchQuery: String, isLoading: Boolean)`
  - `GoldPurchaseSummary(id, customerName: String?, totalPaid, itemCount, paidAt, isTradeIn: Boolean)`
  - Loads customer directory as a map for name resolution
  - `searchQuery` debounced via `flatMapLatest`; filters in-memory on `customerName` and date string
- [x] Create `ui/goldpurchase/GoldPurchasesScreen.kt`:
  - Search bar at top
  - `LazyColumn` of cards — each shows: customer name / "Walk-in", date, formatted total paid, item count, "Trade-in" badge if `isTradeIn`
  - Empty state: "No purchases recorded yet"
  - FAB "+" (all roles) → navigate to `add_gold_purchase`
  - Tap card → navigate to `gold_purchase_detail/{id}`

### 9.6 Add Gold Purchase Modal

- [x] Create `ui/goldpurchase/AddGoldPurchaseViewModel.kt`:
  - `AddGoldPurchaseUiState(customer, items: List<GoldPurchaseItemDraft>, notes, totalPaid, isSaving, error, savedId)`
  - `GoldPurchaseItemDraft` — local-only draft: `localId` (UUID), `description: String`, `metalRateId: String?`, `metalRateName: String?`, `weightGrams: String` (raw text field), `buyRatePerGram: String`, `overrideEnabled: Boolean`, `overrideValue: String`, `photoUri: String?`; computed properties: `weightValue: Double?`, `rateValue: Double?`, `computedValue: Double?`, `finalValue: Double?`, `isValid: Boolean`
  - Exposes metal rates list for the optional type picker (display only)
  - `addItem()`, `updateItem(index, draft)`, `removeItem(index)`
  - `submit()` — validates all drafts, calls `AddGoldPurchaseUseCase`, emits `savedId` on success
- [x] Create `ui/goldpurchase/AddGoldPurchaseModal.kt`:
  - `CustomerAutoSuggest` at top — optional; "Walk-in / Anonymous" hint if blank
  - Dynamic item list (each item row):
    - Description text field (required)
    - Optional metal type dropdown (uses metal rates list for label only — no price auto-fill; user sees "18K Saudi" as a label not as a rate source)
    - Weight in grams field + buy rate per gram field (side by side)
    - Helper text below buy rate: *"Current sell rate: ₱X,XXX/g"* — visual reference only, never auto-fills
    - Computed value shown live: *"Value: ₱X,XXX.XX"*
    - "Override price?" toggle — reveals override field; final value updates live
    - *"You pay: ₱X,XXX.XX"* shown prominently per item
    - Camera / gallery icon for optional photo (reuse existing FileProvider + `TakePicture` pattern)
    - "×" remove button (disabled if only 1 item remains)
  - "+ Add item" button below list
  - Grand total shown at bottom: *"Total to pay: ₱XX,XXX.XX"*
  - Notes field (optional)
  - "Confirm Purchase" button — disabled until ≥ 1 valid item

### 9.7 Gold Purchase Detail Screen

- [x] Create `ui/goldpurchase/GoldPurchaseDetailViewModel.kt`:
  - `GoldPurchaseDetailUiState(record, items, customerName, linkedSaleInfo, canRevert, isReverting, revertError, isLoading)`
  - `canRevert = isAdminOrManager && !record.isDeleted && record.linkedSoldRecordId == null`
  - `revert(reason: String)` — calls `RevertGoldPurchaseUseCase`; navigates up on success
- [x] Create `ui/goldpurchase/GoldPurchaseDetailScreen.kt`:
  - `TopAppBar` with back button
  - Header card: customer name / "Walk-in", date, total paid, notes
  - Items list — each row: description, metal type label (if set), weight, buy rate/g, computed value, override indicator, *"Paid: ₱X,XXX.XX"*
  - Photo thumbnails (if present) — tap to view full image
  - "Revert Purchase" button at bottom (Admin/Manager only); hidden from Staff
  - Revert confirmation dialog: reason text field (required), confirm button
  - If `linkedSoldRecordId != null`: informational chip "Part of a trade-in — revert from trade-in screen" (Phase 10 wires this fully)

### 9.8 Navigation & Sidebar

- [x] `ui/navigation/Screen.kt` — add `Screen.GoldPurchases` (route `"gold_purchases"`, label `"Gold Purchases"`, icon `Icons.Default.ShoppingCart` or similar)
- [x] `ui/navigation/NavGraph.kt`:
  - Add `Screen.GoldPurchases` composable destination → `GoldPurchasesScreen`
  - Add `composable("add_gold_purchase")` → `AddGoldPurchaseModal`
  - Add `composable("gold_purchase_detail/{purchaseId}")` with `NavType.StringType` arg → `GoldPurchaseDetailScreen`
- [x] `ui/navigation/Sidebar.kt` / `Screen.sidebarItemsFor()` — add `GoldPurchases` after `Customers` for all roles
- [x] `AndroidManifest.xml` — no new permissions required (reuses existing FileProvider for photos, existing camera permission)

### 9.9 Phase 9 Testing

- [ ] Manual: record a 3-item gold purchase from a walk-in customer — verify total computed correctly, items saved, appears in list
- [ ] Manual: record a purchase with override price on one item — verify final value uses override, total reflects it
- [ ] Manual: add photo to a purchase item — verify photo saved and visible in detail screen
- [ ] Manual: revert a purchase as Admin — verify soft-deleted, disappears from list, activity log entry created
- [ ] Manual: attempt revert as Staff — verify button is hidden
- [ ] Manual: sell an item with GCash payment — verify payment method stored (visible in Phase 11 daily cash)
- [ ] Manual: add a layaway payment with Online Banking — verify payment method stored
- [ ] Manual: search gold purchases by customer name — verify filter works

**Phase 9 Deliverable:** Shop can record buying gold from customers, capturing weight, manually-entered buy rate, optional photo and customer; sales and layaway payments now carry a payment method. Revert is Admin/Manager-only with mandatory reason. No trade-in or daily cash yet.

---

## Phase 10 — Trade-in / Swap

> Depends on Phase 9. Links a gold purchase to a sale in one atomic flow.

### 10.1 Trade-in Flow in Sell Dialog

- [x] `ui/inventory/SoldDialog.kt` — added "Pay with trade-in?" toggle; when on, expands an inline list of `GoldPurchaseItemDraft` rows (description / weight / buy rate / optional override; no photos in fast-checkout flow). Signature now passes `tradeInItems: List<GoldPurchaseItemDraft>` to onConfirm. Caller in `ProductDetailScreen.kt` updated to forward.
- [x] `ui/inventory/ProductDetailViewModel.kt`:
  - `submitSell(...)` extended with optional `List<GoldPurchaseItemDraft>`; empty list → plain `MarkAsSoldUseCase` path (unchanged); non-empty → routes to `SellWithTradeInUseCase`.
  - Atomic create implemented in a new `domain/usecase/goldpurchase/SellWithTradeInUseCase.kt` (rather than inlined in the VM) — wraps `SoldRecord` insert + `GoldPurchaseRecord`+items insert + product qty decrement in one `db.withTransaction { }`. Sets `linkedSoldRecordId` on the purchase record.
  - Net math live in dialog: "Sale − Trade-in = Customer pays X" / "Shop pays customer X" / "Even swap". Net < 0 allowed.

### 10.2 Atomic Revert for Trade-ins

- [x] Created `domain/usecase/goldpurchase/RevertTradeInUseCase.kt`:
  - Takes `goldPurchaseRecordId` + `reason` + `actorUserId`.
  - Looks up `linkedSoldRecordId`; collapses missing-purchase / null-link / missing-sold-record into a single `NotFound` result per plan.
  - One `db.withTransaction { }`: soft-deletes purchase items, soft-deletes purchase record, soft-deletes sold record, restores product quantity by `sold.quantity`.
  - Logs both `GOLD_PURCHASE_REVERTED` and `REVERT` activity actions.
- [x] `ui/goldpurchase/GoldPurchaseDetailScreen.kt` — added a third branch to the record-level actions (`canRevertTradeIn`, Admin/Manager, requires `linkedSoldRecordId != null` and no items sold to supplier). "Revert Trade-in" button opens a confirm dialog with the warning text from the plan, then calls `viewModel.revertTradeIn(reason)`. The old "Part of a trade-in — revert from trade-in screen" chip is replaced with a "Trade-in — linked to a sale" badge. The plain `RevertGoldPurchaseUseCase` still returns `IsTradeIn` for trade-in records as before, surfaced as "This is a trade-in — use Revert Trade-in" if any caller hits that path.

### 10.3 Phase 10 Testing

- [ ] Manual: open a product → sell → toggle trade-in → add 2 scrap items → verify net math displayed correctly (net positive and net negative cases)
- [ ] Manual: confirm trade-in → verify `GoldPurchaseRecord` and `SoldRecord` both created with correct amounts and linked
- [ ] Manual: revert a trade-in from the gold purchase detail screen → verify both records soft-deleted and product qty restored
- [ ] Manual: attempt to revert just the gold purchase side of a trade-in (i.e. a record where `linkedSoldRecordId != null`) without using the trade-in revert flow → verify `IsTradeIn` error shown

**Phase 10 Deliverable:** Customer can hand in scrap as partial or full payment toward new stock. Net math is live in the dialog. Revert unwinds both sides atomically.

---

## Phase 11 — Daily Cash

> Can be implemented independently of Phase 10. Depends only on Phase 9 (for the DB migration and gold purchase totals).

### 11.1 Settings — Change Float Default

- [ ] `ui/settings/SettingsViewModel.kt` — add `defaultChangeFloat: Double`, `setDefaultChangeFloat(amount: Double)` (persists to `app_settings.default_change_float`)
- [ ] `ui/settings/SettingsScreen.kt` `SessionAppInfoSection` — add "Default change float" field (numeric, Admin/Manager only)

### 11.2 Domain & Data

- [ ] Create `domain/model/CashMovement.kt` — mirrors entity; `type: CashMovementType`, `amount: Double`, `date: Long`, `notes: String?`, `recordedBy: String`, `recordedAt: Long`
- [ ] Create `domain/repository/CashMovementRepository.kt`:
  - `fun observeForDay(dayMillis: Long): Flow<List<CashMovement>>`
  - `suspend fun getForTypeAndDay(type: CashMovementType, dayMillis: Long): CashMovement?`
  - `suspend fun upsert(movement: CashMovement)`
  - `suspend fun softDelete(id: String)`
- [ ] Create `data/repository/CashMovementRepositoryImpl.kt`; bind in Hilt
- [ ] Create `data/mapper/CashMovementMapper.kt`

### 11.3 Daily Cash ViewModel & Screen

- [ ] Create `ui/dailycash/DailyCashViewModel.kt`:
  - `DailyCashUiState` — `selectedDay: Long`, `changeFloat: Double`, `purchaseFloat: Double`, `cashSales: Double`, `gcashSales: Double`, `onlineBankingSales: Double`, `otherSales: Double`, `cashLayawayPayments: Double`, `goldPurchasesTotal: Double`, `expenses: List<CashMovement>`, `adjustments: List<CashMovement>`, `cashBalance: Double`, `totalCollected: Double`, `isAdmin: Boolean`, `isAdminOrManager: Boolean`
  - `selectedDay` defaults to start-of-today; `changeDay(dayMillis)` allows browsing past days
  - Combines multiple `Flow`s: `CashMovementRepository.observeForDay`, `SoldRecordDao.observeSumByPaymentMethodForDay`, `LayawayTransactionDao.observeSumByPaymentMethodForDay`, `GoldPurchaseRecordDao.observeSumForDay`
  - Auto-creates `CHANGE_FLOAT` movement from `default_change_float` if none exists for today yet (on first open of the day)
  - `addPurchaseFloat(amount)`, `editChangeFloat(amount)` — upsert movements; Admin/Manager only
  - `addExpense(amount, notes)` — creates `EXPENSE` movement; notes required; Admin/Manager only
  - `addAdjustment(amount, notes)` — Admin only
  - `cashBalance = changeFloat + purchaseFloat + cashSales + cashLayawayPayments − goldPurchasesTotal − expenses.sum() + adjustments.sum()`
  - `totalCollected = cashBalance + gcashSales + onlineBankingSales + otherSales`
- [ ] Create `ui/dailycash/DailyCashScreen.kt`:
  - Date navigation at top (back/forward arrows + date label; defaults to today)
  - **Cash section** (Physical cash in store):
    - Change float row (editable by Admin/Manager — tap to edit)
    - Purchase float row (tap to enter/edit)
    - Cash sales row (expandable → list of individual cash sales)
    - Cash layaway payments row (expandable)
    - Gold purchases row — shown as negative (expandable → individual purchase records)
    - Expenses list — each row shows amount + note; "Add expense" button (Admin/Manager)
    - Adjustments (Admin only); "Add adjustment" button
    - **Cash Balance** — large bold total
  - **GCash section**: GCash sales + GCash layaway payments; **GCash Balance**
  - **Online Banking section**: Online Banking sales + payments; **Online Banking Balance**
  - **Total Collected** (all three combined) at bottom
  - All expandable rows use `AnimatedVisibility`

### 11.4 Navigation & Sidebar

- [ ] `ui/navigation/Screen.kt` — add `Screen.DailyCash` (route `"daily_cash"`, label `"Daily Cash"`, icon `Icons.Default.AccountBalanceWallet` or similar)
- [ ] `ui/navigation/NavGraph.kt` — add `DailyCash` composable destination
- [ ] `ui/navigation/Sidebar.kt` — add `DailyCash` after `Paluwagan` and before `Analytics` for all roles

### 11.5 Phase 11 Testing

- [ ] Manual: open Daily Cash on a fresh day — verify change float auto-populated from default setting
- [ ] Manual: enter a purchase float — verify cash balance updates immediately
- [ ] Manual: record a cash sale, a GCash sale — verify each appears in the correct section
- [ ] Manual: add an expense (electricity bill) — verify cash balance decreases, note displayed
- [ ] Manual: attempt to add expense as Staff — verify button is hidden
- [ ] Manual: navigate to yesterday — verify that day's data is correct and today's floats aren't visible
- [ ] Manual: edit change float for today — verify persisted and balance recalculates

**Phase 11 Deliverable:** Owner can see at a glance how much physical cash is in the store, broken down by source, plus digital payment totals. Expenses reduce the cash balance with a mandatory note. All values update live via Room Flows.

---

## Future Enhancements (Post-Launch)
- [ ] Barcode/QR code scanning for product lookup
- [ ] Receipt printing (Bluetooth thermal printer)
- [ ] WhatsApp integration for layaway payment reminders
- [ ] Multiple store support
- [ ] Dashboard widgets for quick overview
- [ ] Dark mode
