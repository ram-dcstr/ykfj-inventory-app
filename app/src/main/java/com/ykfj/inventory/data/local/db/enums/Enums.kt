package com.ykfj.inventory.data.local.db.enums

/** Persisted enums. Stored as String (enum name) via Room type converters. */

enum class UserRole { ADMIN, MANAGER, STAFF }

enum class PricingType { WEIGHTED, FIXED }

enum class ProductStatus { AVAILABLE, SOLD, LAYAWAY, DAMAGED }

enum class DiscountType { NONE, FIXED, PERCENTAGE }

enum class LayawayStatus { ACTIVE, COMPLETED, CANCELLED }

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    GCASH("GCash"),
    ONLINE_BANKING("Online Banking"),
    OTHER("Other"),
}

/** Reason a unit left stock outside of a sale / layaway / damage. */
enum class StockAdjustmentReason(val label: String) {
    LOST("Lost"),
    STOLEN("Stolen"),
    MISCOUNT("Miscount correction"),
    SUPPLIER_RETURN("Returned to supplier"),
    OTHER("Other"),
}

enum class PaluwaganGroupStatus { ACTIVE, COMPLETED }

enum class PaluwaganPaymentStatus { PAID, UNPAID, LATE, PREPAID }

enum class CashMovementType {
    CHANGE_FLOAT,
    PURCHASE_FLOAT,
    EXPENSE,
    ADJUSTMENT,
}

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
    GOLD_PURCHASED,
    GOLD_PURCHASE_REVERTED,
    GOLD_SOLD_TO_SUPPLIER,
    GOLD_SOLD_TO_SUPPLIER_REVERTED,
}
