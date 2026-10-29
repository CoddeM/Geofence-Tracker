package com.rahul.assignmentrahul.geofence

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rahul.assignmentrahul.domain.TransitionDeduplicator
import com.rahul.assignmentrahul.domain.TransitionState
import com.rahul.assignmentrahul.domain.TransitionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.geofenceStateDataStore by preferencesDataStore(name = "geofence_state")

/**
 * Persists the last transition per group so duplicate filtering still works after the
 * process is killed (geofence broadcasts often start a fresh process).
 */
@Singleton
class GeofenceStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.geofenceStateDataStore
    private val deduplicator = TransitionDeduplicator()

    /** Saves the transition and returns true if the group should be alerted. */
    suspend fun recordTransition(groupId: String, transition: TransitionType, nowMillis: Long): Boolean {
        var shouldReport = false
        // edit {} is atomic, so two broadcasts arriving together can't both report.
        dataStore.edit { prefs ->
            val decision = deduplicator.decide(prefs.stateFor(groupId), transition, nowMillis)
            shouldReport = decision.shouldReport
            prefs[transitionKey(groupId)] = decision.newState.lastTransition.name
            decision.newState.lastReportedExitAt?.let { prefs[lastExitKey(groupId)] = it }
        }
        return shouldReport
    }

    /**
     * Forget inside/outside state before geofences are re-registered; the INITIAL_TRIGGER_ENTER
     * event re-establishes it. Exit cooldowns are kept.
     */
    suspend fun resetTransitions() {
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(TRANSITION_PREFIX) }
                .forEach { prefs.remove(it) }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.stateFor(groupId: String): TransitionState? {
        val transition = this[transitionKey(groupId)]
            ?.let { name -> TransitionType.entries.firstOrNull { it.name == name } }
            ?: return null
        return TransitionState(transition, this[lastExitKey(groupId)])
    }

    private fun transitionKey(groupId: String) = stringPreferencesKey("$TRANSITION_PREFIX$groupId")
    private fun lastExitKey(groupId: String) = longPreferencesKey("last_exit_$groupId")

    private companion object {
        const val TRANSITION_PREFIX = "transition_"
    }
}
