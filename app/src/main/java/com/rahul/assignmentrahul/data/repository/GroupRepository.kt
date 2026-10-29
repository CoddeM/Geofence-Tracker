package com.rahul.assignmentrahul.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.rahul.assignmentrahul.data.model.ExitEvent
import com.rahul.assignmentrahul.data.model.GeofenceArea
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.domain.InviteCodeGenerator
import com.rahul.assignmentrahul.util.runSuspendCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class InviteCodeNotFoundException : Exception("No group found for this invite code")

/**
 * Firestore layout:
 *  groups/{groupId}                  name, ownerId, memberIds, memberNames, inviteCode, geofence
 *  groups/{groupId}/events/{eventId} userId, userName, type, clientTimeMillis, createdAt
 *  inviteCodes/{code}                groupId, ownerId: lets non-members find a group by code
 */
@Singleton
class GroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val groups get() = firestore.collection(GROUPS)
    private val inviteCodes get() = firestore.collection(INVITE_CODES)
    private fun events(groupId: String) = groups.document(groupId).collection(EVENTS)

    fun observeMyGroups(userId: String): Flow<List<Group>> =
        myGroupsQuery(userId).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toGroup() }.sortedBy { it.name.lowercase() }
        }

    suspend fun getMyGroups(userId: String): List<Group> =
        myGroupsQuery(userId).get().await().documents.mapNotNull { it.toGroup() }

    fun observeGroup(groupId: String): Flow<Group?> =
        groups.document(groupId).snapshots().map { it.toGroup() }

    suspend fun getGroup(groupId: String): Group? =
        groups.document(groupId).get().await().toGroup()

    fun observeExitEvents(groupId: String, limit: Long = 20): Flow<List<ExitEvent>> =
        events(groupId)
            .orderBy(FIELD_CLIENT_TIME, Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toExitEvent() } }

    /**
     * Writes the group and its invite-code lookup doc in one batch (both or neither).
     * If the random code is already taken, the rules reject the batch and we try a new code.
     */
    suspend fun createGroup(name: String, ownerId: String, ownerName: String): String {
        var lastError: Throwable? = null
        repeat(MAX_INVITE_CODE_ATTEMPTS) {
            val groupRef = groups.document()
            val code = InviteCodeGenerator.generate()
            val result = runSuspendCatching {
                firestore.batch()
                    .set(
                        groupRef,
                        mapOf(
                            "name" to name,
                            "ownerId" to ownerId,
                            FIELD_MEMBER_IDS to listOf(ownerId),
                            FIELD_MEMBER_NAMES to mapOf(ownerId to ownerName),
                            "inviteCode" to code,
                            "geofence" to null,
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .set(inviteCodes.document(code), mapOf("groupId" to groupRef.id, "ownerId" to ownerId))
                    .commit()
                    .await()
            }
            val error = result.exceptionOrNull() ?: return groupRef.id
            if (!error.isPermissionDenied()) throw error
            lastError = error
        }
        throw checkNotNull(lastError)
    }

    /**
     * Looks up the code, then adds the current user to that group. The security rules only
     * allow a non-member to change memberIds/memberNames by adding exactly themselves.
     */
    suspend fun joinGroup(inviteCode: String, userId: String, userName: String): String {
        val codeDoc = inviteCodes.document(inviteCode).get(Source.SERVER).await()
        val groupId = codeDoc.getString("groupId") ?: throw InviteCodeNotFoundException()
        val groupRef = groups.document(groupId)

        val joined = runSuspendCatching {
            groupRef.update(
                mapOf(
                    FIELD_MEMBER_IDS to FieldValue.arrayUnion(userId),
                    "$FIELD_MEMBER_NAMES.$userId" to userName,
                )
            ).await()
        }
        val error = joined.exceptionOrNull() ?: return groupId
        // The rules also reject a "join" that adds nobody, i.e. when we're already a member.
        // A member can read the group, so a successful read means joining isn't needed.
        if (error.isPermissionDenied() && runSuspendCatching { getGroup(groupId) }.getOrNull() != null) {
            return groupId
        }
        throw error
    }

    suspend fun updateGeofence(groupId: String, area: GeofenceArea) {
        groups.document(groupId).update(
            "geofence",
            mapOf(
                "latitude" to area.latitude,
                "longitude" to area.longitude,
                "radiusMeters" to area.radiusMeters,
            ),
        ).await()
    }

    /**
     * [eventId] is chosen by the caller and stays the same across retries, so a retry writes
     * the same document. merge() keeps the server's `notifiedAt` mark if it is already there.
     */
    suspend fun addExitEvent(
        groupId: String,
        eventId: String,
        userId: String,
        userName: String,
        timestampMillis: Long,
    ) {
        events(groupId).document(eventId).set(
            mapOf(
                "userId" to userId,
                "userName" to userName,
                "type" to TYPE_EXIT,
                FIELD_CLIENT_TIME to timestampMillis,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun myGroupsQuery(userId: String) = groups.whereArrayContains(FIELD_MEMBER_IDS, userId)

    private fun Throwable.isPermissionDenied() =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private fun DocumentSnapshot.toGroup(): Group? {
        if (!exists()) return null
        return Group(
            id = id,
            name = getString("name").orEmpty(),
            ownerId = getString("ownerId").orEmpty(),
            memberIds = (get(FIELD_MEMBER_IDS) as? List<*>)?.filterIsInstance<String>().orEmpty(),
            memberNames = (get(FIELD_MEMBER_NAMES) as? Map<*, *>)
                ?.mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }
                ?.toMap()
                .orEmpty(),
            inviteCode = getString("inviteCode").orEmpty(),
            geofence = (get("geofence") as? Map<*, *>)?.toGeofenceArea(),
        )
    }

    private fun Map<*, *>.toGeofenceArea(): GeofenceArea? {
        val latitude = (this["latitude"] as? Number)?.toDouble() ?: return null
        val longitude = (this["longitude"] as? Number)?.toDouble() ?: return null
        val radius = (this["radiusMeters"] as? Number)?.toDouble() ?: return null
        return GeofenceArea(latitude, longitude, radius)
    }

    private fun DocumentSnapshot.toExitEvent(): ExitEvent? {
        if (getString("type") != TYPE_EXIT) return null
        return ExitEvent(
            id = id,
            userId = getString("userId").orEmpty(),
            userName = getString("userName").orEmpty(),
            timestampMillis = getLong(FIELD_CLIENT_TIME) ?: 0L,
        )
    }

    private companion object {
        const val GROUPS = "groups"
        const val EVENTS = "events"
        const val INVITE_CODES = "inviteCodes"
        const val FIELD_MEMBER_IDS = "memberIds"
        const val FIELD_MEMBER_NAMES = "memberNames"
        const val FIELD_CLIENT_TIME = "clientTimeMillis"
        const val TYPE_EXIT = "EXIT"
        const val MAX_INVITE_CODE_ATTEMPTS = 3
    }
}
