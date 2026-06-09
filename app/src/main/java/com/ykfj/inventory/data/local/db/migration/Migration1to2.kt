package com.ykfj.inventory.data.local.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrates `paluwagan_groups.frequency` (TEXT enum) to `frequency_days` (INTEGER).
 *
 * Old values are converted: DAILY→1, WEEKLY→7, BI_WEEKLY→14, MONTHLY→30.
 * SQLite does not support ALTER COLUMN, so the table is recreated.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE paluwagan_groups_new (
                group_id        TEXT    NOT NULL PRIMARY KEY,
                name            TEXT    NOT NULL,
                contribution_amount REAL NOT NULL,
                frequency_days  INTEGER NOT NULL,
                total_slots     INTEGER NOT NULL,
                current_round   INTEGER NOT NULL DEFAULT 0,
                status          TEXT    NOT NULL DEFAULT 'ACTIVE',
                start_date      INTEGER NOT NULL,
                notes           TEXT,
                is_archived     INTEGER NOT NULL DEFAULT 0,
                created_at      INTEGER NOT NULL,
                updated_at      INTEGER NOT NULL,
                is_deleted      INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO paluwagan_groups_new
            SELECT
                group_id, name, contribution_amount,
                CASE frequency
                    WHEN 'DAILY'    THEN 1
                    WHEN 'WEEKLY'   THEN 7
                    WHEN 'BI_WEEKLY' THEN 14
                    WHEN 'MONTHLY'  THEN 30
                    ELSE 7
                END,
                total_slots, current_round, status, start_date, notes,
                is_archived, created_at, updated_at, is_deleted
            FROM paluwagan_groups
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE paluwagan_groups")
        db.execSQL("ALTER TABLE paluwagan_groups_new RENAME TO paluwagan_groups")
        db.execSQL("CREATE INDEX index_paluwagan_groups_name       ON paluwagan_groups (name)")
        db.execSQL("CREATE INDEX index_paluwagan_groups_status     ON paluwagan_groups (status)")
        db.execSQL("CREATE INDEX index_paluwagan_groups_updated_at ON paluwagan_groups (updated_at)")
        db.execSQL("CREATE INDEX index_paluwagan_groups_is_archived ON paluwagan_groups (is_archived)")
        db.execSQL("CREATE INDEX index_paluwagan_groups_is_deleted  ON paluwagan_groups (is_deleted)")
    }
}
