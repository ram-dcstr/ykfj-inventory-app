package com.ykfj.inventory.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds [pot_collected_at] (nullable INTEGER) to [paluwagan_slots].
 * Records the actual date a slot's collector physically received the pot money.
 * NULL = not yet collected / not yet recorded.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE paluwagan_slots ADD COLUMN pot_collected_at INTEGER",
        )
    }
}
