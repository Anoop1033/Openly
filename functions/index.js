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
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

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
