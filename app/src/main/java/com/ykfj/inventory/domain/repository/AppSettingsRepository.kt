package com.ykfj.inventory.domain.repository

interface AppSettingsRepository {
    suspend fun getValue(key: String): String?
}
