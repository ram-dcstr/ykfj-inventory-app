package com.ykfj.inventory.di

import com.ykfj.inventory.data.repository.ActivityLogRepositoryImpl
import com.ykfj.inventory.data.repository.CategoryRepositoryImpl
import com.ykfj.inventory.data.repository.MetalRateRepositoryImpl
import com.ykfj.inventory.data.repository.UserRepositoryImpl
import com.ykfj.inventory.domain.repository.ActivityLogRepository
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.repository.MetalRateRepository
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
}
