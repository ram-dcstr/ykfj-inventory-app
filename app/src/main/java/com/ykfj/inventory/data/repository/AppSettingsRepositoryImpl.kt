package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.AppSettingsDao
import com.ykfj.inventory.domain.repository.AppSettingsRepository
import javax.inject.Inject

class AppSettingsRepositoryImpl @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
) : AppSettingsRepository {
    override suspend fun getValue(key: String): String? = appSettingsDao.getValue(key)
}
