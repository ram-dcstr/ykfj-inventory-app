# Roles and Permissions

## Sidebar Badges

The sidebar shows alert badges on navigation items for payment urgency:
- **Layaway:** Red badge with count of overdue layaways (past due_date, still ACTIVE)
- **Paluwagan:** Red badge with count of paluwagan payments due today (UNPAID for current round)

No badge on Inventory or other nav items.

## Role Permissions

| Feature | Admin | Manager | Staff |
|---|:---:|:---:|:---:|
| View inventory | Y | Y | Y |
| View profit margin (detail screen) | Y | N | N |
| Add / edit products | Y | N | N |
| Delete products | Y | N | N |
| Change item status (sell/layaway/damage) | Y | Y | Y |
| Apply discount when selling | Y | Y | N |
| View sold archive | Y | Y | Y |
| Revert status (Sold/Damaged → Available) | Y | Y | N |
| Edit layaway (all fields + payments) | Y | N | N |
| Add layaway payment | Y | Y | Y |
| Manage paluwagan groups | Y | Y | N |
| Add paluwagan payment | Y | Y | Y |
| Swap paluwagan positions | Y | Y | N |
| Manage metal rates | Y | Y | N |
| Manage categories | Y | Y | N |
| Manage suppliers | Y | Y | N |
| Add customers | Y | Y | Y |
| Edit customers | Y | Y | N |
| View customer history | Y | Y | N |
| View analytics | Y | Y | N |
| Export data | Y | N | N |
| Manage users | Y | N | N |
| Archive records | Y | Y | N |
| Export & purge archives | Y | N | N |
| Backup / Restore | Y | N | N |
| View activity log (own) | Y | Y | Y |
| View activity log (all) | Y | Y | N |
| Export activity log CSV | Y | N | N |

## Activity Log

Tracks all user actions in the app for auditing and accountability.

### Logged Actions
- **Auth:** login, logout, session timeout
- **Inventory:** create/update/delete product, status changes (sold, layaway, damaged, revert with reason)
- **Payments:** layaway payments (single + split), paluwagan payments
- **Reference data:** metal rate, category, supplier, customer create/update/delete
- **Financial:** discounts applied, price changes (old → new values captured)
- **Admin:** user management, settings changes, backup, restore, archive, export, purge

### Change Tracking
Edits capture `old_value` and `new_value` as JSON. Example:
```
Action: UPDATE, Entity: metal_rate/abc-123
Description: "Updated 18K Saudi Gold price"
old_value: { "price_per_gram": 3200 }
new_value: { "price_per_gram": 3500 }
```

### Access
- **Staff:** sees only their own actions
- **Manager:** sees all actions
- **Admin:** sees all actions + can export to CSV

### Retention
- Auto-cleanup: logs older than **90 days** are hard-deleted
- Admin can export logs to CSV before cleanup (date range filter)

### UI
- Located in Settings screen as "Activity Log" section
- Filterable by: user, action type, date range
- Exportable to CSV (admin only)
