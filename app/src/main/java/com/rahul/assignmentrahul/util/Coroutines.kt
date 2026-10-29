package com.rahul.assignmentrahul.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] so a cancelled coroutine
 * (e.g. a cleared ViewModel) stops instead of being treated as a normal failure.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
