package com.rahul.assignmentrahul.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user (or null) now and on every sign-in / sign-out. */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(email: String, password: String, displayName: String): FirebaseUser {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Registration did not return a user")
        val profile = UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        user.updateProfile(profile).await()
        return user
    }

    /** Short-lived token that proves our identity to the exit-alert service. */
    suspend fun idToken(): String =
        auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Not signed in")

    fun signOut() = auth.signOut()
}

fun FirebaseUser.displayNameOrEmail(): String =
    displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "Member"
