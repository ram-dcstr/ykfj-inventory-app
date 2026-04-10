# Paluwagan (Rotating Savings)

Cash-only rotating pot. Admin creates group with flexible frequency (daily/weekly/bi-weekly/monthly). Members come from the customer directory.

```
Admin creates group: "January Group", ₱1,000/slot, Weekly, 10 slots

Members:
  Position 1: Maria (1 slot)
  Position 2: Juan (2 slots — positions 2 and 5)
  Position 3: Pedro (1 slot)
  ...
  Position 10: Ana (1 slot)

Each round (weekly):
  All 10 slots pay ₱1,000 = ₱10,000 pot
  Round 1: Position 1 (Maria) collects ₱10,000
  Round 2: Position 2 (Juan) collects ₱10,000
  ...
  Round 5: Position 5 (Juan) collects ₱10,000 (his 2nd slot)
  ...
  Round 10: Position 10 (Ana) collects ₱10,000

Juan has 2 slots:
  - Pays ₱2,000 per round (₱1,000 × 2 slots)
  - Collects ₱10,000 twice (rounds 2 and 5)
```

- One customer can hold multiple slots (pay & collect per slot)
- Admin/manager can swap slot positions mid-cycle
- Payments tracked per slot per round: PAID / UNPAID / LATE
- Group completes when all rounds are done (`current_round > total_slots`)
