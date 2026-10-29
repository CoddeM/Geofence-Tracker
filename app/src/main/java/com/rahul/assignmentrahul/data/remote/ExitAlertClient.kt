package com.rahul.assignmentrahul.data.remote

import com.rahul.assignmentrahul.BuildConfig
import com.rahul.assignmentrahul.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class ExitAlertException(message: String, val isRetryable: Boolean) : IOException(message)

/**
 * Asks the exit-alert service (a Cloudflare Worker, see /notifier) to push a notification
 * to the other group members. The service holds the FCM credentials, so the app never does.
 *
 * Safe to call more than once for the same event: the service marks the event as notified
 * and ignores repeats.
 */
@Singleton
class ExitAlertClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val authRepository: AuthRepository,
) {
    suspend fun notifyGroup(groupId: String, eventId: String) {
        val baseUrl = BuildConfig.EXIT_ALERT_URL.trimEnd('/')
        if (baseUrl.isEmpty()) {
            throw ExitAlertException("EXIT_ALERT_URL is not set in local.properties", isRetryable = false)
        }
        // A Firebase ID token proves to the service which user is calling.
        val idToken = authRepository.idToken()
        val body = JSONObject()
            .put("groupId", groupId)
            .put("eventId", eventId)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/notify-exit")
            .header("Authorization", "Bearer $idToken")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    // 4xx means the request itself is wrong (e.g. not a member): retrying won't help.
                    val retryable = code >= 500 || code == 408 || code == 429
                    throw ExitAlertException("Exit alert service returned HTTP $code", retryable)
                }
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
