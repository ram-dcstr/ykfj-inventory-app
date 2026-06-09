package com.ykfj.inventory.data.local.db

import at.favre.lib.crypto.bcrypt.BCrypt
import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import com.ykfj.inventory.data.local.db.entity.CustomerEntity
import com.ykfj.inventory.data.local.db.entity.DamagedRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayTransactionEntity
import com.ykfj.inventory.data.local.db.entity.MetalRateEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganGroupEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.entity.SoldRecordEntity
import com.ykfj.inventory.data.local.db.entity.SupplierEntity
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.db.enums.UserRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: YkfjDatabase,
) {
    suspend fun seedIfEmpty() {
        seedAdminIfNeeded()
        seedTestDataIfNeeded()
    }

    // ── Admin user ────────────────────────────────────────────────────────────

    private suspend fun seedAdminIfNeeded() {
        if (database.userDao().count() > 0) return
        val now = System.currentTimeMillis()
        val hash = bcrypt("admin123")
        database.userDao().insert(
            UserEntity(
                user_id = ADMIN_ID,
                username = "admin",
                password_hash = hash,
                name = "Administrator",
                role = UserRole.ADMIN,
                created_at = now,
                updated_at = now,
            ),
        )
    }

    // ── Test data ─────────────────────────────────────────────────────────────

    private suspend fun seedTestDataIfNeeded() {
        if (database.metalRateDao().count() > 0) return
        val now = System.currentTimeMillis()
        val hash = bcrypt("admin123")
        seedUsers(hash, now)
        seedMetalRates(now)
        seedCategories(now)
        seedSuppliers(now)
        seedCustomers(now)
        seedProducts(now)
        seedSoldRecords(now)
        seedLayaways(now)
        seedDamagedRecords(now)
        seedPaluwagan(now)
    }

    private suspend fun seedUsers(hash: String, now: Long) {
        database.userDao().insert(
            UserEntity(
                user_id = MANAGER_ID,
                username = "maria",
                password_hash = hash,
                name = "Maria Cruz",
                role = UserRole.MANAGER,
                created_at = now,
                updated_at = now,
            ),
        )
        database.userDao().insert(
            UserEntity(
                user_id = STAFF_ID,
                username = "carlo",
                password_hash = hash,
                name = "Carlo Santos",
                role = UserRole.STAFF,
                created_at = now,
                updated_at = now,
            ),
        )
    }

    private suspend fun seedMetalRates(now: Long) {
        listOf(
            MetalRateEntity(RATE_18KSAUDI, "18K Saudi", 3_200.0, now, now),
            MetalRateEntity(RATE_18KITALIAN, "18K Italian", 3_100.0, now, now),
            MetalRateEntity(RATE_14K, "14K", 2_400.0, now, now),
        ).forEach { database.metalRateDao().insert(it) }
    }

    private suspend fun seedCategories(now: Long) {
        listOf(
            CategoryEntity(CAT_NECKLACE, "Necklace", now, now),
            CategoryEntity(CAT_BRACELET, "Bracelet", now, now),
            CategoryEntity(CAT_RING, "Ring", now, now),
            CategoryEntity(CAT_EARRINGS, "Earrings", now, now),
        ).forEach { database.categoryDao().insert(it) }
    }

    private suspend fun seedSuppliers(now: Long) {
        listOf(
            SupplierEntity(SUP_001, "Gold Empire Manila", "Ramon Dela Cruz", "09171111111", "Binondo, Manila", null, now, now),
            SupplierEntity(SUP_002, "Brilliant Gems Cebu", "Luz Fernandez", "09282222222", "Colon St., Cebu City", null, now, now),
        ).forEach { database.supplierDao().insert(it) }
    }

    private suspend fun seedCustomers(now: Long) {
        listOf(
            CustomerEntity(CUST_001, "Maria Santos", "09171234567", null, null, "Quezon City", 100, null, now, now),
            CustomerEntity(CUST_002, "Juan dela Cruz", "09281234567", null, null, "Makati City", 95, null, now, now),
            CustomerEntity(CUST_003, "Ana Reyes", "09391234567", "028765432", null, "Pasig City", 80, "Prefers layaway", now, now),
            CustomerEntity(CUST_004, "Pedro Gomez", "09451234567", null, null, "Caloocan City", 65, null, now, now),
            CustomerEntity(CUST_005, "Rose Villanueva", "09561234567", null, null, "Mandaluyong", 100, null, now, now),
        ).forEach { database.customerDao().insert(it) }
    }

    private suspend fun seedProducts(now: Long) {
        val dao = database.productDao()
        // 1 — Cuban Chain: 3 made, 1 sold → qty 2
        dao.insert(ProductEntity("CUBAN-18KSAUDI-NECKLACE-000001", "Cuban Chain", CAT_NECKLACE,
            RATE_18KSAUDI, SUP_001, now - 30 * DAY, PricingType.WEIGHTED, 45_000.0,
            null, 15.0, "18 inches", 2, null, ProductStatus.AVAILABLE, now - 30 * DAY, now))
        // 2 — Bangkol Chain
        dao.insert(ProductEntity("BANGKOL-18KITALIAN-NECKLACE-000001", "Bangkol Chain", CAT_NECKLACE,
            RATE_18KITALIAN, SUP_001, now - 25 * DAY, PricingType.WEIGHTED, 22_000.0,
            null, 8.0, "16 inches", 2, null, ProductStatus.AVAILABLE, now - 25 * DAY, now))
        // 3 — Rope Bracelet: SOLD (qty 0)
        dao.insert(ProductEntity("ROPE-14K-BRACELET-000001", "Rope Bracelet", CAT_BRACELET,
            RATE_14K, SUP_002, now - 20 * DAY, PricingType.WEIGHTED, 10_000.0,
            null, 5.0, "7 inches", 0, null, ProductStatus.SOLD, now - 20 * DAY, now - 7 * DAY))
        // 4 — Figaro Bracelet
        dao.insert(ProductEntity("FIGARO-18KSAUDI-BRACELET-000001", "Figaro Bracelet", CAT_BRACELET,
            RATE_18KSAUDI, SUP_001, now - 18 * DAY, PricingType.WEIGHTED, 18_000.0,
            null, 6.0, "7.5 inches", 2, null, ProductStatus.AVAILABLE, now - 18 * DAY, now))
        // 5 — Solitaire Ring: LAYAWAY (qty 1, 1 on layaway → available 0)
        dao.insert(ProductEntity("SOLITAIRE-18KSAUDI-RING-000001", "Solitaire Ring", CAT_RING,
            RATE_18KSAUDI, null, now - 15 * DAY, PricingType.FIXED, 20_000.0,
            28_000.0, null, "Size 7", 1, "Diamond solitaire, 0.5 ct", ProductStatus.LAYAWAY, now - 15 * DAY, now))
        // 6 — Diamond Stud Earrings: fixed price, qty 4
        dao.insert(ProductEntity("STUD-14K-EARRINGS-000001", "Diamond Stud Earrings", CAT_EARRINGS,
            null, SUP_002, now - 12 * DAY, PricingType.FIXED, 3_200.0,
            4_500.0, null, null, 4, "0.25 ct each", ProductStatus.AVAILABLE, now - 12 * DAY, now))
        // 7 — Snake Chain: qty 2, 1 damaged → available 1
        dao.insert(ProductEntity("SNAKE-18KITALIAN-NECKLACE-000002", "Snake Chain", CAT_NECKLACE,
            RATE_18KITALIAN, SUP_001, now - 10 * DAY, PricingType.WEIGHTED, 17_000.0,
            null, 6.0, "20 inches", 1, null, ProductStatus.AVAILABLE, now - 10 * DAY, now))
        // 8 — Pavé Ring: LAYAWAY
        dao.insert(ProductEntity("PAVE-14K-RING-000001", "Pavé Ring", CAT_RING,
            RATE_14K, null, now - 8 * DAY, PricingType.FIXED, 5_800.0,
            8_500.0, null, "Size 6", 1, null, ProductStatus.LAYAWAY, now - 8 * DAY, now))
        // 9 — Herringbone Chain
        dao.insert(ProductEntity("HERRINGBONE-18KSAUDI-NECKLACE-000002", "Herringbone Chain", CAT_NECKLACE,
            RATE_18KSAUDI, SUP_001, now - 5 * DAY, PricingType.WEIGHTED, 36_000.0,
            null, 12.0, "22 inches", 3, null, ProductStatus.AVAILABLE, now - 5 * DAY, now))
    }

    private suspend fun seedSoldRecords(now: Long) {
        val dao = database.soldRecordDao()
        dao.insert(SoldRecordEntity(
            sold_id = "sold-001", product_id = "ROPE-14K-BRACELET-000001",
            customer_id = CUST_001, sold_by = ADMIN_ID, quantity = 1,
            sold_price = 12_000.0, capital_price = 10_000.0,
            discount_amount = 0.0, discount_type = DiscountType.NONE,
            sold_date = now - 7 * DAY, notes = null,
            created_at = now - 7 * DAY, updated_at = now - 7 * DAY,
        ))
        dao.insert(SoldRecordEntity(
            sold_id = "sold-002", product_id = "CUBAN-18KSAUDI-NECKLACE-000001",
            customer_id = CUST_002, sold_by = MANAGER_ID, quantity = 1,
            sold_price = 49_000.0, capital_price = 45_000.0,
            discount_amount = 0.0, discount_type = DiscountType.NONE,
            sold_date = now - 3 * DAY, notes = null,
            created_at = now - 3 * DAY, updated_at = now - 3 * DAY,
        ))
    }

    private suspend fun seedLayaways(now: Long) {
        val lDao = database.layawayRecordDao()
        val tDao = database.layawayTransactionDao()
        // Layaway 1 — Solitaire Ring, due in 14 days, ₱10k paid of ₱28k
        lDao.insert(LayawayRecordEntity(
            layaway_id = "lay-001", product_id = "SOLITAIRE-18KSAUDI-RING-000001",
            customer_id = CUST_003, created_by = ADMIN_ID, quantity = 1,
            unit_price = 28_000.0, total_paid = 10_000.0,
            due_date = now + 14 * DAY, status = LayawayStatus.ACTIVE,
            created_at = now - 14 * DAY, updated_at = now,
        ))
        tDao.insert(LayawayTransactionEntity(transaction_id = "tx-001", layaway_id = "lay-001", amount_paid = 5_000.0, payment_date = now - 14 * DAY, notes = "Downpayment", created_at = now - 14 * DAY, updated_at = now - 14 * DAY))
        tDao.insert(LayawayTransactionEntity(transaction_id = "tx-002", layaway_id = "lay-001", amount_paid = 5_000.0, payment_date = now - 7 * DAY, notes = null, created_at = now - 7 * DAY, updated_at = now - 7 * DAY))
        // Layaway 2 — Pavé Ring, OVERDUE (due 2 days ago), ₱2k paid of ₱8.5k
        lDao.insert(LayawayRecordEntity(
            layaway_id = "lay-002", product_id = "PAVE-14K-RING-000001",
            customer_id = CUST_004, created_by = ADMIN_ID, quantity = 1,
            unit_price = 8_500.0, total_paid = 2_000.0,
            due_date = now - 2 * DAY, status = LayawayStatus.ACTIVE,
            created_at = now - 10 * DAY, updated_at = now,
        ))
        tDao.insert(LayawayTransactionEntity(transaction_id = "tx-003", layaway_id = "lay-002", amount_paid = 2_000.0, payment_date = now - 10 * DAY, notes = "Downpayment", created_at = now - 10 * DAY, updated_at = now - 10 * DAY))
    }

    private suspend fun seedDamagedRecords(now: Long) {
        database.damagedRecordDao().insert(
            DamagedRecordEntity(
                damaged_id = "dmg-001",
                product_id = "SNAKE-18KITALIAN-NECKLACE-000002",
                recorded_by = STAFF_ID,
                reason = "Bent clasp, chain broken during display",
                date_recorded = now - 5 * DAY,
                created_at = now - 5 * DAY,
                updated_at = now - 5 * DAY,
            ),
        )
    }

    private suspend fun seedPaluwagan(now: Long) {
        val gDao = database.paluwaganGroupDao()
        val sDao = database.paluwaganSlotDao()
        val pDao = database.paluwaganPaymentDao()

        gDao.insert(PaluwaganGroupEntity(
            group_id = "palu-001", name = "Linggo-linggo Group",
            contribution_amount = 500.0, frequency_days = 7,
            total_slots = 5, current_round = 2,
            status = PaluwaganGroupStatus.ACTIVE,
            start_date = now - 14 * DAY, notes = "Every Monday collection",
            is_archived = false, created_at = now - 14 * DAY, updated_at = now,
        ))

        val slots = listOf(CUST_001, CUST_002, CUST_003, CUST_004, CUST_005)
            .mapIndexed { i, customerId ->
                PaluwaganSlotEntity(
                    slot_id = "palu-slot-00${i + 1}", group_id = "palu-001",
                    customer_id = customerId, position = i + 1,
                    pot_collected_at = if (i == 0) now - 7 * DAY else null,
                    created_at = now - 14 * DAY, updated_at = now,
                )
            }
        slots.forEach { sDao.insert(it) }

        // Round 1 payments (all resolved)
        val round1Statuses = listOf(PAID, PAID, LATE, PAID, PAID)
        slots.forEachIndexed { i, slot ->
            pDao.insert(PaluwaganPaymentEntity(
                payment_id = "pp-${i + 1}-1", group_id = "palu-001",
                slot_id = slot.slot_id, round_number = 1,
                amount_paid = 500.0, payment_date = now - 7 * DAY,
                status = round1Statuses[i], notes = null,
                created_at = now - 7 * DAY, updated_at = now - 7 * DAY,
            ))
        }
        // Round 2 payments (current — mix of paid/unpaid/late)
        val round2Statuses = listOf(PAID, UNPAID, UNPAID, LATE, UNPAID)
        slots.forEachIndexed { i, slot ->
            val isPaid = round2Statuses[i] != UNPAID
            pDao.insert(PaluwaganPaymentEntity(
                payment_id = "pp-${i + 1}-2", group_id = "palu-001",
                slot_id = slot.slot_id, round_number = 2,
                amount_paid = 500.0,
                payment_date = if (isPaid) now - DAY else null,
                status = round2Statuses[i], notes = null,
                created_at = now - DAY, updated_at = now - DAY,
            ))
        }
    }

    private fun bcrypt(password: String): String =
        BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray())

    private companion object {
        const val BCRYPT_COST = 12
        const val DAY = 86_400_000L

        const val ADMIN_ID = "user-admin"
        const val MANAGER_ID = "user-manager"
        const val STAFF_ID = "user-staff"

        const val RATE_18KSAUDI = "rate-18ksaudi"
        const val RATE_18KITALIAN = "rate-18kitalian"
        const val RATE_14K = "rate-14k"

        const val CAT_NECKLACE = "cat-necklace"
        const val CAT_BRACELET = "cat-bracelet"
        const val CAT_RING = "cat-ring"
        const val CAT_EARRINGS = "cat-earrings"

        const val SUP_001 = "sup-001"
        const val SUP_002 = "sup-002"

        const val CUST_001 = "cust-001"
        const val CUST_002 = "cust-002"
        const val CUST_003 = "cust-003"
        const val CUST_004 = "cust-004"
        const val CUST_005 = "cust-005"

        val PAID = PaluwaganPaymentStatus.PAID
        val UNPAID = PaluwaganPaymentStatus.UNPAID
        val LATE = PaluwaganPaymentStatus.LATE
    }
}
