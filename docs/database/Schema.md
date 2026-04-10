# YKFJ Database Schema (Room)

All tables include:
- `created_at: Long` (epoch millis)
- `updated_at: Long` (epoch millis)
- `is_deleted: Boolean` (soft delete for sync)

Archivable tables also include:
- `is_archived: Boolean` (default false) — on: sold_records, layaway_records, paluwagan_groups, damaged_records

## users

| Column | Type | Notes |
|---|---|---|
| user_id | String (UUID) | PK |
| username | String | Unique |
| password_hash | String | bcrypt |
| name | String | Display name |
| role | Enum | ADMIN, MANAGER, STAFF |
| is_active | Boolean | |

## products

| Column | Type | Notes |
|---|---|---|
| product_id | String | PK, auto-generated: NAME-RATE-CAT-000001 |
| name | String | |
| category_id | String | FK → categories |
| metal_rate_id | String | FK → metal_rates (nullable for FIXED) |
| supplier_id | String | FK → suppliers, nullable |
| date_acquired | Long | Epoch millis — when the product arrived (required) |
| pricing_type | Enum | WEIGHTED, FIXED |
| capital_price | Double | Cost price |
| selling_price | Double | Nullable — FIXED items only. WEIGHTED: always NULL, calculated as weight_grams × metal_rate.price_per_gram |
| weight_grams | Double | Nullable — for WEIGHTED items |
| size | String | Nullable |
| quantity | Int | |
| notes | String | Nullable |
| status | Enum | AVAILABLE, SOLD, LAYAWAY, DAMAGED |

## product_images

Only 1 image per product.

| Column | Type | Notes |
|---|---|---|
| image_id | String (UUID) | PK |
| product_id | String | FK → products, unique |
| file_name | String | Local file name in app storage |
| file_size_bytes | Long | Compressed file size |

## metal_rates

| Column | Type | Notes |
|---|---|---|
| rate_id | String (UUID) | PK |
| name | String | e.g., "18K Saudi Gold" |
| price_per_gram | Double | Selling price per gram |

## categories

| Column | Type | Notes |
|---|---|---|
| category_id | String (UUID) | PK |
| name | String | e.g., "Necklace", "Ring" |

## suppliers

| Column | Type | Notes |
|---|---|---|
| supplier_id | String (UUID) | PK |
| name | String | Supplier/business name |
| representative_name | String | Nullable — contact person |
| mobile | String | Nullable |
| address | String | Nullable |
| notes | String | Nullable |

## customers

| Column | Type | Notes |
|---|---|---|
| customer_id | String (UUID) | PK |
| name | String | |
| mobile | String | Nullable |
| phone | String | Nullable |
| birthday | Long | Nullable, epoch millis |
| address | String | Nullable |
| credit_score | Int | Default 100. -2 paluwagan late, -3 layaway late, +1 on-time |
| notes | String | Nullable |

## sold_records

| Column | Type | Notes |
|---|---|---|
| sold_id | String (UUID) | PK |
| product_id | String | FK → products |
| customer_id | String | FK → customers, nullable |
| sold_by | String | FK → users — who made the sale |
| quantity | Int | Number of units sold (1 to product.quantity) |
| sold_price | Double | Per-unit sale price at time of sale |
| capital_price | Double | Per-unit capital at time of sale |
| discount_amount | Double | Discount applied per unit (0 if none) |
| discount_type | Enum | NONE, FIXED, PERCENTAGE |
| sold_date | Long | Epoch millis |
| notes | String | Nullable |

## layaway_records

| Column | Type | Notes |
|---|---|---|
| layaway_id | String (UUID) | PK |
| product_id | String | FK → products (one layaway per product) |
| customer_id | String | FK → customers (can have multiple layaways) |
| created_by | String | FK → users — who created the layaway |
| quantity | Int | Number of units reserved |
| unit_price | Double | Per-unit agreed price. Total = unit_price × quantity |
| total_paid | Double | Running total of payments across all transactions |
| due_date | Long | Nullable, epoch millis — deadline for full payment |
| status | Enum | ACTIVE, COMPLETED, CANCELLED |
| completion_date | Long | Nullable, epoch millis — set when COMPLETED or CANCELLED |
| forfeited_amount | Double | Nullable — set to total_paid when CANCELLED (no refunds, becomes profit) |

A customer can have multiple active layaway_records (one per product). Split payments create separate layaway_transaction entries per layaway with allocated amounts.

## layaway_transactions

| Column | Type | Notes |
|---|---|---|
| transaction_id | String (UUID) | PK |
| layaway_id | String | FK → layaway_records |
| amount_paid | Double | |
| payment_date | Long | Epoch millis |
| notes | String | Nullable |

## damaged_records

| Column | Type | Notes |
|---|---|---|
| damaged_id | String (UUID) | PK |
| product_id | String | FK → products |
| recorded_by | String | FK → users — who recorded the damage |
| reason | String | |
| date_recorded | Long | Epoch millis |
| notes | String | Nullable |

## paluwagan_groups

| Column | Type | Notes |
|---|---|---|
| group_id | String (UUID) | PK |
| name | String | e.g., "January 2026 Group" |
| contribution_amount | Double | Amount per slot per round |
| frequency | Enum | DAILY, WEEKLY, BI_WEEKLY, MONTHLY |
| total_slots | Int | Total number of slots in the group |
| current_round | Int | Current round (1-based), 0 = not started |
| status | Enum | ACTIVE, COMPLETED |
| start_date | Long | Epoch millis |
| notes | String | Nullable |

## paluwagan_slots

| Column | Type | Notes |
|---|---|---|
| slot_id | String (UUID) | PK |
| group_id | String | FK → paluwagan_groups |
| customer_id | String | FK → customers |
| position | Int | Collection order (1-based). Admin can swap positions. |

A customer with 2 slots has 2 entries with different positions. In round N, the slot at position N collects the pot.

## paluwagan_payments

| Column | Type | Notes |
|---|---|---|
| payment_id | String (UUID) | PK |
| group_id | String | FK → paluwagan_groups |
| slot_id | String | FK → paluwagan_slots |
| round_number | Int | Which round this payment is for |
| amount_paid | Double | |
| payment_date | Long | Epoch millis, nullable if unpaid |
| status | Enum | PAID, UNPAID, LATE |
| notes | String | Nullable |

## app_settings

| Column | Type | Notes |
|---|---|---|
| key | String | PK — setting name |
| value | String | Setting value |

Keys: `session_timeout`, `default_layaway_due_days`, `daily_export_password`, `device_role`, `tailscale_ip`

## activity_logs

| Column | Type | Notes |
|---|---|---|
| log_id | String (UUID) | PK |
| user_id | String | FK → users — who performed the action |
| action | Enum | LOGIN, LOGOUT, CREATE, UPDATE, DELETE, SELL, LAYAWAY, DAMAGE, REVERT, PAYMENT, ARCHIVE, EXPORT, BACKUP, RESTORE, SETTINGS_CHANGE |
| entity_type | String | Nullable — "product", "customer", "metal_rate", "layaway", "sold_record", etc. |
| entity_id | String | Nullable — ID of the affected entity |
| description | String | Human-readable summary, e.g., "Sold 2x Gold Ring to Maria" |
| old_value | String | Nullable — JSON of previous values (for updates) |
| new_value | String | Nullable — JSON of new values (for updates) |
| timestamp | Long | Epoch millis |

Auto-cleanup: logs older than 90 days are deleted automatically. Not soft-deleted — hard delete.

## products_fts (FTS4 virtual table)

| Column | Type | Notes |
|---|---|---|
| name | String | Mirrored from products |
| product_id | String | Mirrored from products |
| notes | String | Mirrored from products |

FTS4 content table linked to `products`. Auto-updated when products change.

## pending_sync_queue (phone only)

| Column | Type | Notes |
|---|---|---|
| id | String (UUID) | PK |
| entity_type | String | "product", "customer", etc. |
| entity_id | String | ID of the changed entity |
| action | String | INSERT, UPDATE, DELETE |
| payload | String | JSON of the change |
| status | String | PENDING, SYNCED, FAILED |
