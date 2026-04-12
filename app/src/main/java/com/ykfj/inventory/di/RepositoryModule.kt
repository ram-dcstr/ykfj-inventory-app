package com.ykfj.inventory.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds repository interfaces (defined in `domain/repository/`) to their
 * implementations (defined in `data/repository/`).
 *
 * Currently empty — populated in Phase 1.4 onward as each repository is
 * introduced. Kept as an abstract class so `@Binds` methods can be added
 * without converting this file later.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule
