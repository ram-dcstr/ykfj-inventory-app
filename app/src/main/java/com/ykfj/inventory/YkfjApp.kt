package com.ykfj.inventory

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the [ApplicationComponent] from this class.
 * Kept minimal — one-time initialization (DB seeding, server start) will be wired
 * in later phases via dedicated initializers, not here.
 */
@HiltAndroidApp
class YkfjApp : Application()
