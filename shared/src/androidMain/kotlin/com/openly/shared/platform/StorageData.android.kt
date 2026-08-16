package com.openly.shared.platform

import dev.gitlive.firebase.storage.Data

/** Android's `Data` already wraps a ByteArray, so this is a straight pass-through. */
actual fun ByteArray.toStorageData(): Data = Data(this)
