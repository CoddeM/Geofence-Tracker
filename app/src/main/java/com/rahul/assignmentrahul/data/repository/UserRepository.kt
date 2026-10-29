package com.rahul.assignmentrahul.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{uid}: profile + this user's FCM token.
 * The Cloud Function reads the token to push alerts; only the user can read/write their own doc.
 */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) {
    private fun userDoc(uid: String) = firestore.collection(USERS).document(uid)

    // merge = true everywhere: profile and token can be written in any order without overwriting each other.
    suspend fun saveProfile(uid: String, displayName: String, email: String) {
        userDoc(uid).set(
            mapOf("displayName" to displayName, "email" to email),
            SetOptions.merge(),
        ).await()
    }

    suspend fun refreshPushToken(uid: String) {
        saveFcmToken(uid, messaging.token.await())
    }

    suspend fun saveFcmToken(uid: String, token: String) {
        userDoc(uid).set(
            mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** Called on sign-out so this device stops receiving the previous user's alerts. */
    suspend fun removePushToken(uid: String) {
        userDoc(uid).update("fcmToken", FieldValue.delete()).await()
        messaging.deleteToken().await()
    }

    private companion object {
        const val USERS = "users"
    }
}
