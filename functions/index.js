/**
 * Push fan-out for Openly.
 *
 * The in-app notifier (shared/.../MessageNotifier) only fires while the app process is alive and
 * its Firestore listener is attached. This function covers the other case — app backgrounded or
 * killed — by pushing through FCM/APNs whenever a message document is created.
 *
 * Deliberately sent as a DATA-ONLY message: with a `notification` payload the OS posts its own
 * tray entry, which the app can't de-duplicate against the in-process notifier, so a user with the
 * app open on another tab would see the same message twice.
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getStorage } = require("firebase-admin/storage");

initializeApp();

exports.notifyNewMessage = onDocumentCreated(
  "matches/{matchId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const { matchId } = event.params;
    const senderUid = message.senderUid;
    if (!senderUid) return;

    const db = getFirestore();

    const matchSnap = await db.doc(`matches/${matchId}`).get();
    const uids = matchSnap.get("uids") || [];
    const recipientUid = uids.find((uid) => uid !== senderUid);
    if (!recipientUid) return;

    const [recipientSnap, senderSnap] = await Promise.all([
      db.doc(`users/${recipientUid}`).get(),
      db.doc(`users/${senderUid}`).get(),
    ]);

    const token = recipientSnap.get("fcmToken");
    if (!token) return; // Never signed in on a device that can receive push.

    const senderName = senderSnap.get("displayName") || "Someone";
    const senderEmoji = senderSnap.get("avatarEmoji") || "🙂";

    // Attachments have empty text — show what kind of thing arrived instead of a blank push.
    let body = message.text || "";
    if (message.type === "IMAGE") body = "📷 Photo";
    else if (message.type === "VOICE") body = "🎤 Voice message";

    try {
      await getMessaging().send({
        token,
        data: {
          matchId,
          title: `${senderEmoji} ${senderName}`,
          body,
        },
        android: { priority: "high" },
        apns: {
          headers: { "apns-priority": "10" },
          // content-available wakes the iOS app to build the notification itself, mirroring the
          // data-only approach used on Android.
          payload: { aps: { "content-available": 1 } },
        },
      });
    } catch (error) {
      // A stale token (app uninstalled) is the normal failure here — clear it so we stop trying.
      if (
        error.code === "messaging/registration-token-not-registered" ||
        error.code === "messaging/invalid-argument"
      ) {
        await db.doc(`users/${recipientUid}`).update({ fcmToken: "" });
      } else {
        console.error("push failed", error);
      }
    }
  }
);

/**
 * Erases everything belonging to a user who asked to be deleted, completing the cascade that
 * AccountRepository starts on the client.
 *
 * The client has already removed its own profile, its interest records and its auth account by the
 * time this runs. What is left is the *shared* data — matches, their messages and their attachments
 * — which the security rules deliberately refuse to let any client delete: if they allowed it,
 * either participant could wipe the other's history at any moment, not just while leaving. Running
 * here under the admin SDK sidesteps the rules instead of weakening them.
 *
 * Every step is written to be idempotent and independently failure-tolerant. The client may have
 * been killed halfway through its own half of the work, and a retry of this function must be able
 * to finish the job rather than abort on something already gone.
 */
exports.purgeDeletedAccount = onDocumentCreated(
  "deletionRequests/{uid}",
  async (event) => {
    const { uid } = event.params;
    const db = getFirestore();

    const matches = await db
      .collection("matches")
      .where("uids", "array-contains", uid)
      .get();

    for (const match of matches.docs) {
      // Attachments first. The storage prefix is derived from the match id, so once the match
      // document is gone there is nothing left to enumerate them from — they would sit in the
      // bucket forever, unreferenced and still billed for.
      try {
        await getStorage().bucket().deleteFiles({ prefix: `chat/${match.id}/` });
      } catch (error) {
        console.error(`failed clearing attachments for match ${match.id}`, error);
      }

      // recursiveDelete takes the messages subcollection along with the parent document; deleting
      // the document alone would orphan the subcollection, which stays readable in Firestore.
      try {
        await db.recursiveDelete(match.ref);
      } catch (error) {
        console.error(`failed deleting match ${match.id}`, error);
      }
    }

    // The remaining steps repeat what the client should already have done. They are here because
    // "should have" is not "did" — a process killed mid-deletion would otherwise leave a profile
    // that no one can ever remove, since the only account authorised to do so is gone.
    const leftoverInterests = await Promise.all([
      db.collection("interests").where("fromUid", "==", uid).get(),
      db.collection("interests").where("toUid", "==", uid).get(),
    ]);
    for (const snapshot of leftoverInterests) {
      for (const doc of snapshot.docs) {
        await doc.ref.delete().catch((e) => console.error("interest delete failed", e));
      }
    }

    await db.doc(`users/${uid}`).delete().catch((e) => {
      console.error(`failed deleting profile ${uid}`, e);
    });

    try {
      await getAuth().deleteUser(uid);
    } catch (error) {
      // Expected on the happy path: the client deletes its own anonymous account first.
      if (error.code !== "auth/user-not-found") {
        console.error(`failed deleting auth user ${uid}`, error);
      }
    }

    // Last, so that the request document surviving is a reliable signal that a cascade did not
    // finish — which is what you would query for when auditing a deletion complaint.
    await event.data.ref.delete().catch((e) => {
      console.error(`failed clearing deletion request ${uid}`, e);
    });
  }
);
