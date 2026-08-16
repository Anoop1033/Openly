package com.openly.shared.media

/**
 * Picks an image from the device gallery and returns it as JPEG bytes (already downscaled — see
 * each actual for the limit). Returns null when the user cancels.
 *
 * Bytes rather than a platform file handle: it's the one representation that means the same thing
 * on both platforms and is what Cloud Storage's putData wants anyway.
 */
expect class MediaPicker() {
    suspend fun pickImage(): ByteArray?
}

/** A finished recording: the encoded audio plus how long it ran, for the bubble's duration label. */
data class RecordedAudio(val bytes: ByteArray, val durationSeconds: Int) {
    // ByteArray uses identity equals, which would make two identical recordings compare unequal
    // and quietly break any state comparison this ends up inside.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordedAudio) return false
        return durationSeconds == other.durationSeconds && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + durationSeconds
}

/** Records a voice note to AAC/m4a — the format both platforms encode and play natively. */
expect class VoiceRecorder() {
    val isRecording: Boolean
    fun start()
    /** Stops and returns the recording, or null if it failed or was too short to be useful. */
    fun stop(): RecordedAudio?
    /** Aborts without producing a file, e.g. when the user backs out of the screen. */
    fun cancel()
}

/** Plays a voice note straight from its Cloud Storage download URL. */
expect class VoicePlayer() {
    fun play(url: String, onCompletion: () -> Unit)
    fun stop()
}
