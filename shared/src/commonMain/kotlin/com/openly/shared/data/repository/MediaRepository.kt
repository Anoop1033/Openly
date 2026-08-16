package com.openly.shared.data.repository

import com.openly.shared.platform.nowMillis
import com.openly.shared.platform.storageMetadata
import com.openly.shared.platform.toStorageData
import dev.gitlive.firebase.storage.FirebaseStorage

/**
 * Uploads chat attachments to Cloud Storage and hands back a download URL to embed in the message.
 *
 * Files are namespaced per match (`chat/{matchId}/…`) so the storage rules can reuse the same
 * "are you a participant" check the Firestore rules already apply to the thread.
 */
class MediaRepository(private val storage: FirebaseStorage) {

    suspend fun uploadImage(matchId: String, senderUid: String, bytes: ByteArray): String =
        upload(matchId, "$senderUid-${nowMillis()}.jpg", bytes, "image/jpeg")

    suspend fun uploadVoiceNote(matchId: String, senderUid: String, bytes: ByteArray): String =
        upload(matchId, "$senderUid-${nowMillis()}.m4a", bytes, "audio/mp4")

    private suspend fun upload(
        matchId: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String
    ): String {
        val ref = storage.reference("chat/$matchId/$fileName")
        // putData rather than putFile: the pickers and the recorder both hand back bytes already,
        // and a byte array is the one shape that means the same thing on Android and iOS.
        // The metadata is not optional here — without an explicit content type the upload lands as
        // application/octet-stream and storage.rules rejects it.
        ref.putData(bytes.toStorageData(), storageMetadata(contentType))
        return ref.getDownloadUrl()
    }
}
