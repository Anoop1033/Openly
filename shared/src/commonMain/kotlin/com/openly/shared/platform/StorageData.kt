package com.openly.shared.platform

import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorageMetadata

/**
 * GitLive's `Data` is an expect class wrapping each platform's native byte buffer (a `ByteArray`
 * on Android, `NSData` on iOS), so converting from a plain `ByteArray` has to cross the same
 * boundary.
 */
expect fun ByteArray.toStorageData(): Data

/** Shared metadata builder so both platforms tag uploads with the same content type. */
fun storageMetadata(contentType: String): FirebaseStorageMetadata =
    FirebaseStorageMetadata(contentType = contentType)
