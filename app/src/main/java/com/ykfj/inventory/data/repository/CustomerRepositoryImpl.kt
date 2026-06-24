package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.CustomerDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val customerDao: CustomerDao,
    private val syncEnqueuer: SyncEnqueuer,
) : CustomerRepository {

    override fun observeAll(): Flow<List<Customer>> =
        customerDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun search(query: String): Flow<List<Customer>> =
        customerDao.search(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Customer? =
        customerDao.getById(id)?.toDomain()

    override suspend fun getByIds(ids: List<String>): List<Customer> =
        customerDao.getByIds(ids).map { it.toDomain() }

    override suspend fun upsert(customer: Customer) {
        val existing = customerDao.getById(customer.id)
        val entity = customer.toEntity()
        if (existing == null) {
            customerDao.insert(entity)
            syncEnqueuer.enqueueCustomer(entity, SyncAction.INSERT)
        } else {
            customerDao.update(entity)
            syncEnqueuer.enqueueCustomer(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun adjustCreditScore(id: String, delta: Int) {
        val existing = customerDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        customerDao.adjustCreditScore(id, delta, now)
        val newScore = (existing.credit_score + delta).coerceIn(0, 100)
        syncEnqueuer.enqueueCustomer(existing.copy(credit_score = newScore, updated_at = now))
    }

    override suspend fun delete(id: String) {
        val existing = customerDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        customerDao.softDelete(id, now)
        syncEnqueuer.enqueueCustomer(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }
}
