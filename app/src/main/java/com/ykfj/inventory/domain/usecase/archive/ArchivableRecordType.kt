package com.ykfj.inventory.domain.usecase.archive

/** The four archive-eligible record types, with display + sync metadata. */
enum class ArchivableRecordType(val label: String, val entityType: String, val csvSlug: String) {
    SOLD("Sold", "sold_record", "sold"),
    LAYAWAY("Layaway", "layaway_record", "layaway"),
    DAMAGED("Damaged", "damaged_record", "damaged"),
    PALUWAGAN("Paluwagan", "paluwagan_group", "paluwagan"),
}
