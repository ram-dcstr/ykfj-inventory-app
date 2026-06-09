package com.ykfj.inventory.domain.sync

/** Whether this device acts as the primary tablet server or a secondary phone client. */
enum class DeviceRole {
    /** Tablet: runs the Ktor embedded server and registers via NSD. */
    TABLET,

    /** Phone: runs the sync client, discovers the tablet via NSD or Tailscale. */
    PHONE,
}
