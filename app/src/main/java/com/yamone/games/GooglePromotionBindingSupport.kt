package com.yamone.games

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Adapter point for Google identity. The current build does not yet add Google Credential Manager.
 * Later, the real implementation only needs to return a fresh Google ID token for the configured
 * server/Web OAuth client ID. Email/name/profile scopes are not required by this contract.
 */
internal interface GooglePromotionIdentityProvider {
    suspend fun freshIdToken(): String?
}

internal sealed interface PromotionRestoreResult {
    data class Success(val entitlement: PromotionEntitlement) : PromotionRestoreResult
    data object NoEntitlement : PromotionRestoreResult
    data object SignInRequired : PromotionRestoreResult
    data object Offline : PromotionRestoreResult
    data object ServerUnavailable : PromotionRestoreResult
}

internal sealed interface PromotionUnlinkResult {
    data object Success : PromotionUnlinkResult
    data object SignInRequired : PromotionUnlinkResult
    data object Offline : PromotionUnlinkResult
    data object ServerUnavailable : PromotionUnlinkResult
}

/**
 * Future Android production provider:
 * - one Yamone-issued free code is bound to the first verified Google subject
 * - the Worker stores only an HMAC of the Google subject
 * - reinstall/device change restores the entitlement after the same Google identity is verified
 */
internal class GoogleAccountBoundPromotionRepository(
    context: Context,
    private val identityProvider: GooglePromotionIdentityProvider
) {
    private val appContext = context.applicationContext
    private val store = PromotionStore(appContext)
    private val client = GoogleBoundPromotionClient()

    fun current(): PromotionEntitlement = store.current()

    suspend fun claim(rawCode: String): PromotionRedeemResult {
        val code = normalizePromotionCode(rawCode)
        if (code.length !in 4..40) return PromotionRedeemResult.InvalidCode
        if (!hasUsableNetwork(appContext)) return PromotionRedeemResult.Offline
        val idToken = identityProvider.freshIdToken() ?: return PromotionRedeemResult.ServerUnavailable

        return when (val response = client.claim(code, idToken)) {
            is GoogleBoundPromotionApiResult.Success -> {
                val entitlement = PromotionEntitlement(
                    active = true,
                    validUntilMillis = response.validUntilMillis,
                    label = response.label
                )
                store.apply(entitlement)
                PromotionRedeemResult.Success(store.current())
            }
            GoogleBoundPromotionApiResult.InvalidCode -> PromotionRedeemResult.InvalidCode
            GoogleBoundPromotionApiResult.NotStarted -> PromotionRedeemResult.NotStarted
            GoogleBoundPromotionApiResult.Expired -> PromotionRedeemResult.Expired
            GoogleBoundPromotionApiResult.AlreadyClaimed,
            GoogleBoundPromotionApiResult.NoEntitlement,
            GoogleBoundPromotionApiResult.Unlinked,
            GoogleBoundPromotionApiResult.InvalidIdentity,
            GoogleBoundPromotionApiResult.ServerUnavailable -> PromotionRedeemResult.ServerUnavailable
        }
    }

    suspend fun restore(): PromotionRestoreResult {
        if (!hasUsableNetwork(appContext)) return PromotionRestoreResult.Offline
        val idToken = identityProvider.freshIdToken() ?: return PromotionRestoreResult.SignInRequired

        return when (val response = client.restore(idToken)) {
            is GoogleBoundPromotionApiResult.Success -> {
                val entitlement = PromotionEntitlement(
                    active = true,
                    validUntilMillis = response.validUntilMillis,
                    label = response.label
                )
                store.apply(entitlement)
                PromotionRestoreResult.Success(store.current())
            }
            GoogleBoundPromotionApiResult.NoEntitlement -> PromotionRestoreResult.NoEntitlement
            GoogleBoundPromotionApiResult.InvalidIdentity -> PromotionRestoreResult.SignInRequired
            else -> PromotionRestoreResult.ServerUnavailable
        }
    }

    suspend fun unlink(): PromotionUnlinkResult {
        if (!hasUsableNetwork(appContext)) return PromotionUnlinkResult.Offline
        val idToken = identityProvider.freshIdToken() ?: return PromotionUnlinkResult.SignInRequired
        return when (client.unlink(idToken)) {
            GoogleBoundPromotionApiResult.Unlinked -> {
                store.clear()
                PromotionUnlinkResult.Success
            }
            GoogleBoundPromotionApiResult.InvalidIdentity -> PromotionUnlinkResult.SignInRequired
            else -> PromotionUnlinkResult.ServerUnavailable
        }
    }
}

private sealed interface GoogleBoundPromotionApiResult {
    data class Success(val validUntilMillis: Long?, val label: String) : GoogleBoundPromotionApiResult
    data object InvalidCode : GoogleBoundPromotionApiResult
    data object NotStarted : GoogleBoundPromotionApiResult
    data object Expired : GoogleBoundPromotionApiResult
    data object AlreadyClaimed : GoogleBoundPromotionApiResult
    data object NoEntitlement : GoogleBoundPromotionApiResult
    data object InvalidIdentity : GoogleBoundPromotionApiResult
    data object Unlinked : GoogleBoundPromotionApiResult
    data object ServerUnavailable : GoogleBoundPromotionApiResult
}

private class GoogleBoundPromotionClient {
    suspend fun claim(code: String, idToken: String): GoogleBoundPromotionApiResult = request(
        method = "POST",
        path = "/v1/promotion/google/claim",
        body = JSONObject().put("code", code).put("idToken", idToken)
    )

    suspend fun restore(idToken: String): GoogleBoundPromotionApiResult = request(
        method = "POST",
        path = "/v1/promotion/google/restore",
        body = JSONObject().put("idToken", idToken)
    )

    suspend fun unlink(idToken: String): GoogleBoundPromotionApiResult = request(
        method = "DELETE",
        path = "/v1/promotion/google/link",
        body = JSONObject().put("idToken", idToken)
    )

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject
    ): GoogleBoundPromotionApiResult = withContext(Dispatchers.IO) {
        val connection = (URL("$API_BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()

            if (status in 200..299 && json?.optBoolean("ok") == true) {
                if (json.optBoolean("unlinked", false)) {
                    return@withContext GoogleBoundPromotionApiResult.Unlinked
                }
                if (!json.optBoolean("active", true)) {
                    return@withContext GoogleBoundPromotionApiResult.NoEntitlement
                }
                val validUntil = if (json.isNull("validUntil")) {
                    null
                } else {
                    json.optLong("validUntil").takeIf { it > 0L }
                }
                return@withContext GoogleBoundPromotionApiResult.Success(
                    validUntilMillis = validUntil,
                    label = json.optString("label", "프로모션").ifBlank { "프로모션" }
                )
            }

            return@withContext when (json?.optString("error")) {
                "INVALID_PROMOTION_CODE", "PROMOTION_NOT_FOUND" -> GoogleBoundPromotionApiResult.InvalidCode
                "PROMOTION_NOT_STARTED" -> GoogleBoundPromotionApiResult.NotStarted
                "PROMOTION_EXPIRED" -> GoogleBoundPromotionApiResult.Expired
                "PROMOTION_ALREADY_CLAIMED" -> GoogleBoundPromotionApiResult.AlreadyClaimed
                "INVALID_GOOGLE_ID_TOKEN" -> GoogleBoundPromotionApiResult.InvalidIdentity
                else -> GoogleBoundPromotionApiResult.ServerUnavailable
            }
        } catch (_: IOException) {
            GoogleBoundPromotionApiResult.ServerUnavailable
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_BASE = "https://yamone-games-ranking-api.yamone0479.workers.dev"
    }
}
