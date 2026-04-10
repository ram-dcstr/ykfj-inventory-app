# Inventory Rules

## Product ID Format

```
{NAME}-{METALRATE}-{CATEGORY}-{6-digit-sequence}
Example: CUBAN-18KSAUDI-NECKLACE-000001
```

Sequence is scoped per name+rate+category combo. Two different CUBANs with different rates each start at 000001.

## Deletion Rules

- **Metal rates:** Cannot be deleted if any active products reference them. Show an error.
- **Categories:** Cannot be deleted if any active products reference them. Show an error.
- **Products:** Cannot be deleted if any sold_records, layaway_records, or damaged_records reference them. Must revert all linked records first.
- All other deletions are soft deletes (`is_deleted = true`).

## Suppliers

- Supplier directory in sidebar. Admin/Manager can add/edit suppliers.
- Products can optionally link to a supplier via `supplier_id`.
- Add item modal includes a supplier picker dropdown.

## Inventory Display

- **Default sort:** Newest first (by `date_acquired` descending)
- **SOLD products (qty=0):** Hidden from default inventory view. Visible via status filter toggle.
- **Profit margin:** Shown on product detail screen for Admin only (capital vs selling price, margin %).

## Images

Only 1 image per product. Two compressed versions generated at save time (full + thumbnail).

## Quantity & Status

Products support **mixed actions** — a product with qty=5 can have units sold, damaged, and on layaway simultaneously. The product `status` field reflects the overall state:
- **AVAILABLE** — at least 1 unit is still available (not sold/damaged/layaway)
- **SOLD** — all units are accounted for (sold + damaged + layaway = original qty)

Available qty = `quantity - (total sold + total damaged + total on layaway)`

### Restocking
Admin can **increase** product quantity (restock) via the edit form. Quantity cannot be decreased manually — decreases only happen through sell/layaway/damage actions.

### Selling
Any role can sell 1+ units in a single transaction (up to available qty).

```
Product: Gold Ring (qty: 5, status: AVAILABLE)

Sell 2 → available: 3, status: AVAILABLE, sold_record created (quantity: 2)
Damage 1 → available: 2, status: AVAILABLE, damaged_record created
Layaway 1 → available: 1, status: AVAILABLE, layaway_record created (quantity: 1)
Sell 1 → available: 0, status: SOLD, sold_record created (quantity: 1)
```

Sold record stores per-unit prices. Total sale = `sold_price × quantity`.

### Layaway
- Staff can reserve a specific number of units (1 to available qty)
- Only **one active layaway** per product at a time. Must complete or cancel before creating another.
- `layaway_records.unit_price` = per-unit price. Total = `unit_price × quantity`.

### Damaged
- Always 1 unit at a time. Each damaged_record is for 1 unit.

### Reverting (Admin/Manager only)
Reverting a sold or damaged record requires a **confirmation dialog with a mandatory reason**.

```
Revert sold (admin/manager):
  → confirmation dialog with reason field
  → sold_record soft-deleted
  → available qty restored (+sold_record.quantity)
  → status → AVAILABLE (if was SOLD)

Same pattern for damaged (restores 1 unit).
```

### Status Change UI
All status change actions (Sell, Layaway, Damaged, Revert) appear on the **product detail screen only**. No quick actions on inventory list cards.
