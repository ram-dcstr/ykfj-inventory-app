# Archiving and Export

## Archive System

### Archivable Records
- `sold_records` — completed sales
- `layaway_records` — COMPLETED or CANCELLED layaways
- `paluwagan_groups` — COMPLETED groups
- `damaged_records` — all damaged records

### How It Works
1. Each archivable table has `is_archived: Boolean` (default false)
2. Active views filter `WHERE is_archived = false`
3. Each section has an "Archived" tab to view archived records
4. Admin/manager can archive completed records

### Yearly Cleanup Workflow
```
Settings → Archive Manager (Admin only)
  → Select record type (Sold, Layaway, Paluwagan, Damaged)
  → Select date range
  → Preview records to export
  → "Export & Delete":
      1. Generates CSV file(s) to Downloads folder
      2. After successful export → hard deletes archived records from DB
  → Filenames: ykfj-archive-sold-2025.csv, etc.
```

## Daily Sales Export (PDF)

Staff, Manager, and Admin can export today's sales as a **password-protected PDF**.

**Layout:**
```
┌──────────────────────────────────────────────┐
│            YKFJ Gold Jewelry                 │
│          Daily Sales Report                  │
│           April 6, 2026                      │
├──────────────────────────────────────────────┤
│ #  Product        Grams  Size  Qty           │
│    Capital   Selling   Discount   Final      │
│──────────────────────────────────────────────│
│ 1  Cuban Chain    18.5g  22"   ×2            │
│    ₱2,800    ₱3,500    -₱200     ₱3,300     │
│                                              │
│ 2  Gold Ring      5.2g   7     ×1            │
│    ₱1,200    ₱1,800    —         ₱1,800     │
│──────────────────────────────────────────────│
│                                              │
│  Total Items Sold:  3                        │
│  Total Revenue:     ₱8,400                   │
│  Total Capital:     ₱6,800                   │
│  Total Profit:      ₱1,600                   │
└──────────────────────────────────────────────┘
```

- **Password:** Set by admin in Settings. Required to open the PDF.
- **Trigger:** Manual "Export Today's Sales" button on Sold Archive screen. Today only — no date picker.
- **Filename:** `ykfj-sales-YYYY-MM-DD.pdf`
- **Access:** Staff, Manager, Admin

## CSV Export Formats

**Sold:** Date, Product ID, Product Name, Category, Qty, Capital (per unit), Sold Price (per unit), Total, Profit, Customer, Notes

**Layaway:** Layaway ID, Product, Customer, Total Price, Total Paid, Status, Start Date, Completion Date, Payment Count

**Paluwagan:** Group Name, Contribution Amount, Frequency, Total Slots, Start Date, Completion Date, Total Rounds

**Paluwagan Payments (companion):** Group Name, Member Name, Slot Position, Round, Amount, Status, Payment Date

**Damaged:** Date, Product ID, Product Name, Category, Reason, Notes
