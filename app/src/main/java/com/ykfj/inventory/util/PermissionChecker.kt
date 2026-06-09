package com.ykfj.inventory.util

import com.ykfj.inventory.data.local.db.enums.UserRole

/**
 * Single source of truth for "can role X do action Y?".
 *
 * Permissions are derived from the role enum at composition time. Screens read
 * the relevant flag from their UiState (computed in the ViewModel) and
 * **hide** unauthorized actions — never just disable. Use cases should also
 * defend in depth, but the UI gate is what the user sees.
 *
 * Source-of-truth rules: see docs/business/Roles-and-Permissions.md.
 */
data class Permissions(
    // Products
    val canAddProducts: Boolean,
    val canEditProducts: Boolean,
    val canDeleteProducts: Boolean,
    val canViewProfit: Boolean,
    val canApplyDiscount: Boolean,
    val canRevertStatus: Boolean,

    // Customers
    val canEditCustomers: Boolean,
    val canViewCustomerHistory: Boolean,

    // Layaway
    val canEditLayaway: Boolean,

    // Paluwagan
    val canManagePaluwaganGroups: Boolean,
    val canSwapPaluwaganPositions: Boolean,

    // Reference data
    val canManageMetalRates: Boolean,
    val canManageCategories: Boolean,
    val canManageSuppliers: Boolean,

    // Admin surfaces
    val canViewAnalytics: Boolean,
    val canArchiveRecords: Boolean,
    val canPurgeArchives: Boolean,
    val canManageUsers: Boolean,
    val canBackupRestore: Boolean,
    val canExportData: Boolean,
) {
    companion object {
        val NONE = Permissions(
            canAddProducts = false,
            canEditProducts = false,
            canDeleteProducts = false,
            canViewProfit = false,
            canApplyDiscount = false,
            canRevertStatus = false,
            canEditCustomers = false,
            canViewCustomerHistory = false,
            canEditLayaway = false,
            canManagePaluwaganGroups = false,
            canSwapPaluwaganPositions = false,
            canManageMetalRates = false,
            canManageCategories = false,
            canManageSuppliers = false,
            canViewAnalytics = false,
            canArchiveRecords = false,
            canPurgeArchives = false,
            canManageUsers = false,
            canBackupRestore = false,
            canExportData = false,
        )
    }
}

object PermissionChecker {
    fun forRole(role: UserRole?): Permissions {
        if (role == null) return Permissions.NONE
        val isAdmin = role == UserRole.ADMIN
        val isManager = role == UserRole.MANAGER
        val adminOrManager = isAdmin || isManager
        return Permissions(
            canAddProducts = isAdmin,
            canEditProducts = isAdmin,
            canDeleteProducts = isAdmin,
            canViewProfit = isAdmin,
            canApplyDiscount = adminOrManager,
            canRevertStatus = adminOrManager,
            canEditCustomers = adminOrManager,
            canViewCustomerHistory = adminOrManager,
            canEditLayaway = isAdmin,
            canManagePaluwaganGroups = adminOrManager,
            canSwapPaluwaganPositions = adminOrManager,
            canManageMetalRates = adminOrManager,
            canManageCategories = adminOrManager,
            canManageSuppliers = adminOrManager,
            canViewAnalytics = adminOrManager,
            canArchiveRecords = adminOrManager,
            canPurgeArchives = isAdmin,
            canManageUsers = isAdmin,
            canBackupRestore = isAdmin,
            canExportData = isAdmin,
        )
    }
}
