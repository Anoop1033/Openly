package com.openly.shared.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Android's photo picker is an Activity-result contract, which only a ComponentActivity can
 * register — same constraint as the location permission launcher, so it uses the same
 * attach-from-MainActivity pattern.
 */
actual class MediaPicker actual constructor() {

    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var context: Context? = null
    private var pending: ((Uri?) -> Unit)? = null

    fun attach(context: Context, launcher: ActivityResultLauncher<PickVisualMediaRequest>) {
        this.context = context.applicationContext
        this.launcher = launcher
    }

    /** Called by MainActivity's launcher callback. */
    fun onResult(uri: Uri?) {
        pending?.invoke(uri)
        pending = null
    }

    actual suspend fun pickImage(): ByteArray? {
        val launcher = launcher ?: return null
        val uri = suspendCoroutine<Uri?> { continuation ->
            pending = { continuation.resume(it) }
            launcher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        } ?: return null

        return runCatching { decodeAndCompress(uri) }.getOrNull()
    }

    /**
     * Downscales to at most [MAX_DIMENSION] on the long edge before re-encoding. Phone cameras
     * produce multi-megabyte images and a chat bubble never needs that; uploading the original
     * would be slow and burn Storage quota for no visible gain.
     */
    private fun decodeAndCompress(uri: Uri): ByteArray? {
        val ctx = context ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)

        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { longEdge / it <= MAX_DIMENSION }
        }
        val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 80
    }
}

actual class VoiceRecorder actual constructor() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis = 0L
    private var context: Context? = null
    private var recording = false

    actual val isRecording: Boolean get() = recording

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    actual fun start() {
        val ctx = context ?: return
        if (recording) return

        val file = File.createTempFile("voice-", ".m4a", ctx.cacheDir)
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.onFailure {
            newRecorder.release()
            file.delete()
            return
        }

        recorder = newRecorder
        outputFile = file
        startedAtMillis = System.currentTimeMillis()
        recording = true
    }

    actual fun stop(): RecordedAudio? {
        if (!recording) return null
        recording = false

        val file = outputFile
        // stop() throws if the recording was too short for the encoder to produce a valid file —
        // treat that as "no recording" rather than letting it crash the send path.
        val stopped = runCatching {
            recorder?.stop()
        }.isSuccess
        recorder?.release()
        recorder = null
        outputFile = null

        if (!stopped || file == null || !file.exists()) {
            file?.delete()
            return null
        }

        val bytes = runCatching { file.readBytes() }.getOrNull()
        val duration = durationSecondsOf(file)
        file.delete()

        if (bytes == null || bytes.isEmpty()) return null
        return RecordedAudio(bytes, duration)
    }

    actual fun cancel() {
        if (!recording) return
        recording = false
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    /** Prefer the container's own duration; fall back to wall-clock if it can't be read. */
    private fun durationSecondsOf(file: File): Int {
        val fromMetadata = runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.let { (it / 1000).toInt() }
            }
        }.getOrNull()
        return fromMetadata
            ?: ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt()
    }
}

actual class VoicePlayer actual constructor() {

    private var player: MediaPlayer? = null

    actual fun play(url: String, onCompletion: () -> Unit) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(url)
                setOnCompletionListener {
                    onCompletion()
                    stop()
                }
                prepareAsync()
                setOnPreparedListener { it.start() }
            }
        }.onFailure {
            onCompletion()
        }
    }

    actual fun stop() {
        runCatching { player?.release() }
        player = null
    }
}
