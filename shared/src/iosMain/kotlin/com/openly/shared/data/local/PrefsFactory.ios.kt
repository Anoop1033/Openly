package com.openly.shared.data.local

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * App-private Documents directory.
 *
 * NOTE: never compiled — iOS builds require macOS + Xcode. Verify on a Mac.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal actual fun prefsDirectoryPath(): String {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(documentDirectory?.path) { "Could not resolve iOS Documents directory" }
}
