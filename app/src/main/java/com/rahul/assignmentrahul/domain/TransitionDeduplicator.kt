package com.rahul.assignmentrahul.domain

enum class TransitionType { ENTER, EXIT }

/** What this device last knew about one group's geofence. */
data class TransitionState(
    val lastTransition: TransitionType,
    val lastReportedExitAt: Long?,
)

data class TransitionDecision(
    val shouldReport: Boolean,
    val newState: TransitionState,
)

/**
 * Decides whether a geofence transition is a real change worth telling the group about.
 *
 * Play services can deliver the same transition more than once (re-registration, process
 * restarts), and GPS drift near the boundary can make a user flap between inside and outside.
 * Rules:
 *  - The same transition twice in a row is a duplicate.
 *  - ENTER is never reported; it only marks the user as inside again.
 *  - EXIT is reported, unless another EXIT was reported less than [exitCooldownMillis] ago.
 */
class TransitionDeduplicator(
    private val exitCooldownMillis: Long = DEFAULT_EXIT_COOLDOWN_MILLIS,
) {

    fun decide(
        previous: TransitionState?,
        transition: TransitionType,
        nowMillis: Long,
    ): TransitionDecision {
        if (previous?.lastTransition == transition) {
            return TransitionDecision(shouldReport = false, newState = previous)
        }

        val lastExitAt = previous?.lastReportedExitAt
        if (transition == TransitionType.ENTER) {
            return TransitionDecision(
                shouldReport = false,
                newState = TransitionState(TransitionType.ENTER, lastExitAt),
            )
        }

        val inCooldown = lastExitAt != null && nowMillis - lastExitAt < exitCooldownMillis
        return if (inCooldown) {
            TransitionDecision(false, TransitionState(TransitionType.EXIT, lastExitAt))
        } else {
            TransitionDecision(true, TransitionState(TransitionType.EXIT, nowMillis))
        }
    }

    companion object {
        const val DEFAULT_EXIT_COOLDOWN_MILLIS = 60_000L
    }
}
