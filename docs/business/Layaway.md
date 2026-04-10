# Layaway Rules

- One layaway record per product. A customer can have **multiple active layaways** (one per product).
- Has an optional `due_date`. Overdue layaways are flagged/highlighted in the UI.
- Default due date for online/reserved items is configurable in Settings (default: 3 days).
- In-store and online layaways are treated the same — no type distinction.
- Late layaway payments deduct -3 from customer credit score.
- Only one active layaway per product at a time.
- **Downpayment is optional** — a layaway can be created with ₱0 paid.

## Payments
- Each layaway has its own payment history via `layaway_transactions`.
- **Split payments:** A customer can make one payment and allocate it across multiple of their active layaways. The app creates separate `layaway_transaction` entries for each layaway with the allocated amount.
- Layaway auto-completes when `total_paid >= unit_price × quantity`.
- Admin can also manually mark a layaway as COMPLETED.

## Editing Layaway (Admin Only)
- Admin can edit: customer, quantity, unit_price, due_date, notes — everything **except the linked product**.
- Admin can **edit or delete individual payment records** (`layaway_transactions`). Deleting a payment reduces `total_paid` accordingly.

## Layaway Screen
- Flat list of all active layaways. Staff can search/filter by customer name.
- Each row shows: customer, product, qty, total, paid, remaining, due date.

## Cancellation
- Reserved units become available again
- `layaway_records.status` → CANCELLED, `completion_date` set
- **No refunds.** Payments already made are forfeited and become profit for the shop. The `forfeited_amount` field records the total amount kept.
- Forfeited amounts are tracked separately in analytics as "cancelled layaway profit" — no sold_record is created.
