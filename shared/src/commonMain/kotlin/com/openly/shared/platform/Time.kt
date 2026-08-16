package com.openly.shared.platform

import kotlinx.datetime.Clock

/**
 * Wall-clock milliseconds since the epoch.
 *
 * `System.currentTimeMillis()` is JVM-only, so every timestamp in the shared layer goes through
 * here to stay usable from both Android and iOS.
 */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
