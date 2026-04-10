# YKFJ LAN Sync API (Ktor on Tablet)

## Authentication

```
POST /api/auth/login
  Body: { "username": "...", "password": "..." }
  Response: { "token": "jwt...", "user": {...} }

GET /api/auth/me
  Header: Authorization: Bearer <token>
  Response: { "user": {...} }
```

## Sync Endpoints

```
GET /api/sync/status
  Response: { "server_time": 1234567890, "device_id": "tablet-xxx" }

GET /api/sync/changes?since={timestamp}
  Response: {
    "products": [...changed since timestamp...],
    "customers": [...],
    "sold_records": [...],
    "layaway_records": [...],
    "layaway_transactions": [...],
    "damaged_records": [...],
    "metal_rates": [...],
    "categories": [...],
    "suppliers": [...],
    "users": [...],
    "product_images": [...],
    "paluwagan_groups": [...],
    "paluwagan_slots": [...],
    "paluwagan_payments": [...],
    "activity_logs": [...],
    "server_time": 1234567890
  }

POST /api/sync/push
  Body: { same structure as above, with phone's changes }
  Response: { "accepted": true, "server_time": 1234567890 }
```

## Image Endpoints

```
GET /api/images/{image_id}
  Query: ?type=thumb|full (default: full)
  Response: image file (JPEG bytes)
  Phone caches locally after first download

GET /api/images/sync?since={timestamp}
  Response: list of image_ids that changed since timestamp
  Phone downloads only new/updated images
```

## CRUD Endpoints (used by phone when online)

```
GET    /api/products?page={n}&size={20}&search={query}&status={filter}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}    ← soft delete

(Same pattern for: customers, suppliers, metal-rates, categories,
 sold-records, layaway-records, layaway-transactions, damaged-records,
 paluwagan-groups, paluwagan-slots, paluwagan-payments)
```
