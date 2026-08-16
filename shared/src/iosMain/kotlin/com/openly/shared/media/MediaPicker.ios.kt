package com.openly.shared.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * PHPickerViewController-backed counterpart of the Android photo picker.
 *
 * NOTE: never compiled or run — iOS builds require macOS + Xcode. Verify on a Mac. In particular
 * the PhotosUI cinterop surface (item-provider loading in particular) is the part most likely to
 * need adjusting, and Info.plist needs NSPhotoLibraryUsageDescription.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MediaPicker actual constructor() {

    actual suspend fun pickImage(): ByteArray? = suspendCoroutine { continuation ->
        val configuration = PHPickerConfiguration().apply {
            setFilter(PHPickerFilter.imagesFilter())
            setSelectionLimit(1)
        }
        val controller = PHPickerViewController(configuration)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                val result = didFinishPicking.firstOrNull() as? PHPickerResult
                if (result == null) {
                    continuation.resume(null)
                    return
                }
                result.itemProvider.loadObjectOfClass(UIImage) { image, _ ->
                    val jpeg = (image as? UIImage)?.let { UIImageJPEGRepresentation(it, 0.8) }
                    continuation.resume(jpeg?.toByteArray())
                }
            }
        }
        controller.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, true, null)
            ?: continuation.resume(null)
    }
}

/**
 * AVAudioRecorder counterpart of the Android MediaRecorder implementation.
 *
 * NOTE: never compiled or run — verify on a Mac. Info.plist needs NSMicrophoneUsageDescription.
 */
@OptIn(ExperimentalForeignApi::class)
actual class VoiceRecorder actual constructor() {

    private var recorder: AVAudioRecorder? = null
    private var fileUrl: NSURL? = null
    private var recording = false

    actual val isRecording: Boolean get() = recording

    actual fun start() {
        if (recording) return

        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return
        val url = NSURL.fileURLWithPath("$documents/voice-${platform.Foundation.NSDate().timeIntervalSince1970}.m4a")

        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44_100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to 96
        )

        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayAndRecord, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            val newRecorder = AVAudioRecorder(url, settings, null)
            newRecorder.prepareToRecord()
            newRecorder.record()
            recorder = newRecorder
            fileUrl = url
            recording = true
        }
    }

    actual fun stop(): RecordedAudio? {
        if (!recording) return null
        recording = false

        val activeRecorder = recorder ?: return null
        val duration = activeRecorder.currentTime.toInt()
        activeRecorder.stop()
        recorder = null

        val url = fileUrl ?: return null
        fileUrl = null
        val data = NSData.dataWithContentsOfURL(url)
        NSFileManager.defaultManager.removeItemAtURL(url, null)

        val bytes = data?.toByteArray() ?: return null
        return if (bytes.isEmpty()) null else RecordedAudio(bytes, duration)
    }

    actual fun cancel() {
        if (!recording) return
        recording = false
        recorder?.stop()
        recorder = null
        fileUrl?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }
        fileUrl = null
    }
}

/**
 * AVAudioPlayer counterpart of the Android MediaPlayer implementation.
 *
 * NOTE: never compiled or run — verify on a Mac. Unlike Android's MediaPlayer, AVAudioPlayer has
 * no streaming-from-URL mode, so this downloads the whole clip first. Fine for short voice notes.
 */
@OptIn(ExperimentalForeignApi::class)
actual class VoicePlayer actual constructor() {

    private var player: AVAudioPlayer? = null

    actual fun play(url: String, onCompletion: () -> Unit) {
        stop()
        val nsUrl = NSURL.URLWithString(url) ?: run {
            onCompletion()
            return
        }
        val data = NSData.dataWithContentsOfURL(nsUrl) ?: run {
            onCompletion()
            return
        }
        runCatching {
            player = AVAudioPlayer(data, null).apply {
                prepareToPlay()
                play()
            }
        }.onFailure { onCompletion() }
    }

    actual fun stop() {
        player?.stop()
        player = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray =
    ByteArray(length.toInt()).apply {
        if (isNotEmpty()) {
            memScoped { bytes?.readBytes(length.toInt())?.copyInto(this@apply) }
        }
    }
