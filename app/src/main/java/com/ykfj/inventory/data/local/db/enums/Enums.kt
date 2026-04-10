package com.ykfj.inventory.data.local.db.enums

/** Persisted enums. Stored as String (enum name) via Room type converters. */

enum class UserRole { ADMIN, MANAGER, STAFF }

enum class PricingType { WEIGHTED, FIXED }

enum class ProductStatus { AVAILABLE, SOLD, LAYAWAY, DAMAGED }

enum class DiscountType { NONE, FIXED, PERCENTAGE }

enum class LayawayStatus { ACTIVE, COMPLETED, CANCELLED }

enum class PaluwaganFrequency { DAILY, WEEKLY, BI_WEEKLY, MONTHLY }

enum class PaluwaganGroupStatus { ACTIVE, COMPLETED }

enum class PaluwaganPaymentStatus { PAID, UNPAID, LATE }

enum class ActivityAction {
    LOGIN,
    LOGOUT,
    CREATE,
    UPDATE,
    DELETE,
    SELL,
    LAYAWAY,
    DAMAGE,
    REVERT,
    PAYMENT,
    ARCHIVE,
    EXPORT,
    BACKUP,
    RESTORE,
    SETTINGS_CHANGE,
}
