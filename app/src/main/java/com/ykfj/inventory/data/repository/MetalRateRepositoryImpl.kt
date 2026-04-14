package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.MetalRateDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.repository.MetalRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MetalRateRepositoryImpl @Inject constructor(
    private val metalRateDao: MetalRateDao,
    private val productDao: ProductDao,
) : MetalRateRepository {

    override fun observeAll(): Flow<List<MetalRate>> =
        metalRateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): MetalRate? =
        metalRateDao.getById(id)?.toDomain()

    override suspend fun upsert(metalRate: MetalRate) {
        val existing = metalRateDao.getById(metalRate.id)
        if (existing == null) {
            metalRateDao.insert(metalRate.toEntity())
        } else {
            metalRateDao.update(metalRate.toEntity())
        }
    }

    override suspend fun delete(id: String) {
        metalRateDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun countActiveProducts(rateId: String): Int =
        productDao.countByMetalRate(rateId)
}
