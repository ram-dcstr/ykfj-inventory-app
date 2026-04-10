package com.ykfj.inventory.data.local.db.converters

import androidx.room.TypeConverter
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganFrequency
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.db.enums.UserRole

/**
 * Room type converters. Every enum is stored as its [Enum.name] so the DB is
 * human-readable and resilient to ordinal reordering.
 *
 * Long (epoch millis) and primitive types are handled natively by Room.
 */
class Converters {

    @TypeConverter
    fun userRoleToString(value: UserRole): String = value.name

    @TypeConverter
    fun stringToUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun pricingTypeToString(value: PricingType): String = value.name

    @TypeConverter
    fun stringToPricingType(value: String): PricingType = PricingType.valueOf(value)

    @TypeConverter
    fun productStatusToString(value: ProductStatus): String = value.name

    @TypeConverter
    fun stringToProductStatus(value: String): ProductStatus = ProductStatus.valueOf(value)

    @TypeConverter
    fun discountTypeToString(value: DiscountType): String = value.name

    @TypeConverter
    fun stringToDiscountType(value: String): DiscountType = DiscountType.valueOf(value)

    @TypeConverter
    fun layawayStatusToString(value: LayawayStatus): String = value.name

    @TypeConverter
    fun stringToLayawayStatus(value: String): LayawayStatus = LayawayStatus.valueOf(value)

    @TypeConverter
    fun paluwaganFrequencyToString(value: PaluwaganFrequency): String = value.name

    @TypeConverter
    fun stringToPaluwaganFrequency(value: String): PaluwaganFrequency =
        PaluwaganFrequency.valueOf(value)

    @TypeConverter
    fun paluwaganGroupStatusToString(value: PaluwaganGroupStatus): String = value.name

    @TypeConverter
    fun stringToPaluwaganGroupStatus(value: String): PaluwaganGroupStatus =
        PaluwaganGroupStatus.valueOf(value)

    @TypeConverter
    fun paluwaganPaymentStatusToString(value: PaluwaganPaymentStatus): String = value.name

    @TypeConverter
    fun stringToPaluwaganPaymentStatus(value: String): PaluwaganPaymentStatus =
        PaluwaganPaymentStatus.valueOf(value)

    @TypeConverter
    fun activityActionToString(value: ActivityAction): String = value.name

    @TypeConverter
    fun stringToActivityAction(value: String): ActivityAction = ActivityAction.valueOf(value)
}
