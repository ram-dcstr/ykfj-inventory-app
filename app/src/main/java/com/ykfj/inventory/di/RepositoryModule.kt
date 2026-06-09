package com.ykfj.inventory.di

import com.ykfj.inventory.data.repository.AppSettingsRepositoryImpl
import com.ykfj.inventory.data.repository.CashMovementRepositoryImpl
import com.ykfj.inventory.data.repository.GoldPurchaseRepositoryImpl
import com.ykfj.inventory.data.repository.PaluwaganRepositoryImpl
import com.ykfj.inventory.data.repository.PendingSyncManagerImpl
import com.ykfj.inventory.data.repository.ActivityLogRepositoryImpl
import com.ykfj.inventory.data.repository.CategoryRepositoryImpl
import com.ykfj.inventory.data.repository.CustomerRepositoryImpl
import com.ykfj.inventory.data.repository.DamagedRecordRepositoryImpl
import com.ykfj.inventory.data.repository.LayawayRepositoryImpl
import com.ykfj.inventory.data.repository.MetalRateRepositoryImpl
import com.ykfj.inventory.data.repository.ProductImageRepositoryImpl
import com.ykfj.inventory.data.repository.ProductRepositoryImpl
import com.ykfj.inventory.data.repository.SoldRecordRepositoryImpl
import com.ykfj.inventory.data.repository.SupplierRepositoryImpl
import com.ykfj.inventory.data.repository.UserRepositoryImpl
import com.ykfj.inventory.domain.repository.AppSettingsRepository
import com.ykfj.inventory.domain.repository.CashMovementRepository
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.sync.PendingSyncManager
import com.ykfj.inventory.domain.repository.ActivityLogRepository
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.MetalRateRepository
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.SupplierRepository
import com.ykfj.inventory.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds repository interfaces (defined in `domain/repository/`) to their
 * implementations (defined in `data/repository/`).
 *
 * Additional bindings are added as repository implementations are built
 * in later phases.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindActivityLogRepository(impl: ActivityLogRepositoryImpl): ActivityLogRepository

    @Binds
    abstract fun bindMetalRateRepository(impl: MetalRateRepositoryImpl): MetalRateRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindSupplierRepository(impl: SupplierRepositoryImpl): SupplierRepository

    @Binds
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository

    @Binds
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    abstract fun bindProductImageRepository(impl: ProductImageRepositoryImpl): ProductImageRepository

    @Binds
    abstract fun bindSoldRecordRepository(impl: SoldRecordRepositoryImpl): SoldRecordRepository

    @Binds
    abstract fun bindDamagedRecordRepository(impl: DamagedRecordRepositoryImpl): DamagedRecordRepository

    @Binds
    abstract fun bindLayawayRepository(impl: LayawayRepositoryImpl): LayawayRepository

    @Binds
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository

    @Binds
    abstract fun bindPaluwaganRepository(impl: PaluwaganRepositoryImpl): PaluwaganRepository

    @Binds
    abstract fun bindPendingSyncManager(impl: PendingSyncManagerImpl): PendingSyncManager

    @Binds
    abstract fun bindGoldPurchaseRepository(impl: GoldPurchaseRepositoryImpl): GoldPurchaseRepository

    @Binds
    abstract fun bindCashMovementRepository(impl: CashMovementRepositoryImpl): CashMovementRepository
}
