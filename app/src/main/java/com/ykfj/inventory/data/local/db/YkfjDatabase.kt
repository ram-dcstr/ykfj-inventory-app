package com.ykfj.inventory.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ykfj.inventory.data.local.db.converters.Converters
import com.ykfj.inventory.data.local.db.dao.AnalyticsDao
import com.ykfj.inventory.data.local.db.dao.ActivityLogDao
import com.ykfj.inventory.data.local.db.dao.AppSettingsDao
import com.ykfj.inventory.data.local.db.dao.CashMovementDao
import com.ykfj.inventory.data.local.db.dao.CategoryDao
import com.ykfj.inventory.data.local.db.dao.CustomerDao
import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseItemDao
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayTransactionDao
import com.ykfj.inventory.data.local.db.dao.MetalRateDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganPaymentDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganSlotDao
import com.ykfj.inventory.data.local.db.dao.PendingSyncDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.local.db.dao.ProductImageDao
import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.local.db.dao.StockAdjustmentDao
import com.ykfj.inventory.data.local.db.dao.SupplierDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.local.db.entity.ActivityLogEntity
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import com.ykfj.inventory.data.local.db.entity.CashMovementEntity
import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import com.ykfj.inventory.data.local.db.entity.CustomerEntity
import com.ykfj.inventory.data.local.db.entity.DamagedRecordEntity
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseItemEntity
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayTransactionEntity
import com.ykfj.inventory.data.local.db.entity.MetalRateEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganGroupEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import com.ykfj.inventory.data.local.db.entity.PendingSyncEntity
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.entity.ProductFts
import com.ykfj.inventory.data.local.db.entity.ProductImageEntity
import com.ykfj.inventory.data.local.db.entity.SoldRecordEntity
import com.ykfj.inventory.data.local.db.entity.StockAdjustmentEntity
import com.ykfj.inventory.data.local.db.entity.SupplierEntity
import com.ykfj.inventory.data.local.db.entity.UserEntity

/**
 * Room database for YKFJ.
 *
 * `autoMigrations` is intentionally empty — populate it with `AutoMigration`
 * entries when bumping the schema version. Schema JSON is exported to
 * `app/schemas/` (wired via the KSP `room.schemaLocation` arg in
 * [app/build.gradle.kts]) so migrations can be tested.
 */
@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        MetalRateEntity::class,
        SupplierEntity::class,
        CustomerEntity::class,
        ProductEntity::class,
        ProductFts::class,
        ProductImageEntity::class,
        SoldRecordEntity::class,
        LayawayRecordEntity::class,
        LayawayTransactionEntity::class,
        DamagedRecordEntity::class,
        PaluwaganGroupEntity::class,
        PaluwaganSlotEntity::class,
        PaluwaganPaymentEntity::class,
        ActivityLogEntity::class,
        AppSettingsEntity::class,
        PendingSyncEntity::class,
        GoldPurchaseRecordEntity::class,
        GoldPurchaseItemEntity::class,
        CashMovementEntity::class,
        StockAdjustmentEntity::class,
    ],
    version = 12,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        // 8→9: adds nullable sold_records.linked_layaway_id (defaults NULL).
        AutoMigration(from = 8, to = 9),
        // 9→10: adds nullable paluwagan_slots.pot_payout_channel (defaults NULL).
        AutoMigration(from = 9, to = 10),
        // 10→11: adds the stock_adjustments table.
        AutoMigration(from = 10, to = 11),
        // 11→12: adds users.must_change_password (defaults 0 / false).
        AutoMigration(from = 11, to = 12),
    ],
)
@TypeConverters(Converters::class)
abstract class YkfjDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun metalRateDao(): MetalRateDao
    abstract fun supplierDao(): SupplierDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun productImageDao(): ProductImageDao
    abstract fun soldRecordDao(): SoldRecordDao
    abstract fun layawayRecordDao(): LayawayRecordDao
    abstract fun layawayTransactionDao(): LayawayTransactionDao
    abstract fun damagedRecordDao(): DamagedRecordDao
    abstract fun paluwaganGroupDao(): PaluwaganGroupDao
    abstract fun paluwaganSlotDao(): PaluwaganSlotDao
    abstract fun paluwaganPaymentDao(): PaluwaganPaymentDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun goldPurchaseRecordDao(): GoldPurchaseRecordDao
    abstract fun goldPurchaseItemDao(): GoldPurchaseItemDao
    abstract fun cashMovementDao(): CashMovementDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    companion object {
        const val DATABASE_NAME = "ykfj.db"

        /**
         * Mirror of the schema version in the @Database annotation above. Used by
         * [com.ykfj.inventory.data.local.backup.BackupManager] to write into the
         * backup manifest and reject restores from a newer schema (which we can't
         * downgrade to). Keep this in sync with the annotation's `version = N`
         * line above when you bump the schema.
         */
        const val SCHEMA_VERSION = 12
    }
}
