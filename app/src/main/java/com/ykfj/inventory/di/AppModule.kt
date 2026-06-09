package com.ykfj.inventory.di

import android.content.Context
import androidx.room.Room
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.migration.MIGRATION_1_2
import com.ykfj.inventory.data.local.db.migration.MIGRATION_3_4
import com.ykfj.inventory.data.local.db.dao.AnalyticsDao
import com.ykfj.inventory.data.local.db.dao.CashMovementDao
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseItemDao
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseRecordDao
import com.ykfj.inventory.data.local.db.dao.ActivityLogDao
import com.ykfj.inventory.data.local.db.dao.AppSettingsDao
import com.ykfj.inventory.data.local.db.dao.CategoryDao
import com.ykfj.inventory.data.local.db.dao.CustomerDao
import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
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
import com.ykfj.inventory.data.local.db.dao.SupplierDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the singleton Room database and every DAO.
 *
 * DAOs are provided (not bound) because they come from the abstract
 * [YkfjDatabase] accessors — Hilt can't bind accessors directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): YkfjDatabase = Room.databaseBuilder(
        context,
        YkfjDatabase::class.java,
        YkfjDatabase.DATABASE_NAME,
    ).addMigrations(MIGRATION_1_2, MIGRATION_3_4).build()

    @Provides
    fun provideUserDao(db: YkfjDatabase): UserDao = db.userDao()

    @Provides
    fun provideCategoryDao(db: YkfjDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideMetalRateDao(db: YkfjDatabase): MetalRateDao = db.metalRateDao()

    @Provides
    fun provideSupplierDao(db: YkfjDatabase): SupplierDao = db.supplierDao()

    @Provides
    fun provideCustomerDao(db: YkfjDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideProductDao(db: YkfjDatabase): ProductDao = db.productDao()

    @Provides
    fun provideProductImageDao(db: YkfjDatabase): ProductImageDao = db.productImageDao()

    @Provides
    fun provideSoldRecordDao(db: YkfjDatabase): SoldRecordDao = db.soldRecordDao()

    @Provides
    fun provideLayawayRecordDao(db: YkfjDatabase): LayawayRecordDao = db.layawayRecordDao()

    @Provides
    fun provideLayawayTransactionDao(db: YkfjDatabase): LayawayTransactionDao =
        db.layawayTransactionDao()

    @Provides
    fun provideDamagedRecordDao(db: YkfjDatabase): DamagedRecordDao = db.damagedRecordDao()

    @Provides
    fun providePaluwaganGroupDao(db: YkfjDatabase): PaluwaganGroupDao = db.paluwaganGroupDao()

    @Provides
    fun providePaluwaganSlotDao(db: YkfjDatabase): PaluwaganSlotDao = db.paluwaganSlotDao()

    @Provides
    fun providePaluwaganPaymentDao(db: YkfjDatabase): PaluwaganPaymentDao =
        db.paluwaganPaymentDao()

    @Provides
    fun provideActivityLogDao(db: YkfjDatabase): ActivityLogDao = db.activityLogDao()

    @Provides
    fun provideAppSettingsDao(db: YkfjDatabase): AppSettingsDao = db.appSettingsDao()

    @Provides
    fun providePendingSyncDao(db: YkfjDatabase): PendingSyncDao = db.pendingSyncDao()

    @Provides
    fun provideAnalyticsDao(db: YkfjDatabase): AnalyticsDao = db.analyticsDao()

    @Provides
    fun provideGoldPurchaseRecordDao(db: YkfjDatabase): GoldPurchaseRecordDao =
        db.goldPurchaseRecordDao()

    @Provides
    fun provideGoldPurchaseItemDao(db: YkfjDatabase): GoldPurchaseItemDao =
        db.goldPurchaseItemDao()

    @Provides
    fun provideCashMovementDao(db: YkfjDatabase): CashMovementDao = db.cashMovementDao()
}
