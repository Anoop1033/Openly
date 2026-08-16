package com.openly.shared.platform

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * iOS's `Data` wraps `NSData`, so the ByteArray has to be pinned and copied across.
 *
 * NOTE: never compiled — iOS builds require macOS + Xcode. Verify on a Mac; in particular confirm
 * GitLive's iOS `Data` really does take an NSData in the version being built against.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.toStorageData(): Data {
    val nsData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
    return Data(nsData)
}
