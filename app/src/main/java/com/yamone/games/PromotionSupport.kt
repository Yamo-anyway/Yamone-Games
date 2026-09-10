package com.yamone.games

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Purchase structure is reserved, but no purchase UI is exposed in this release.
internal const val PURCHASE_UI_ENABLED = false

internal data class PromotionEntitlement(
    val active: Boolean,
    val validUntilMillis: Long?,
    val label: String
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        active && (validUntilMillis == null || validUntilMillis > nowMillis)
}

internal sealed interface PromotionRedeemResult {
    data class Success(val entitlement: PromotionEntitlement) : PromotionRedeemResult
    data object InvalidCode : PromotionRedeemResult
    data object NotStarted : PromotionRedeemResult
    data object Expired : PromotionRedeemResult
    data object Offline : PromotionRedeemResult
    data object ServerUnavailable : PromotionRedeemResult
}

/**
 * Stores only the promotion entitlement on this install.
 * No account, advertising id, device id, nickname, or game record is sent to the promotion API.
 */
internal class PromotionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(nowMillis: Long = System.currentTimeMillis()): PromotionEntitlement {
        val enabled = prefs.getBoolean(KEY_ACTIVE, false)
        val rawUntil = prefs.getLong(KEY_VALID_UNTIL, VALUE_INDEFINITE)
        val validUntil = rawUntil.takeUnless { it == VALUE_INDEFINITE }
        val entitlement = PromotionEntitlement(
            active = enabled,
            validUntilMillis = validUntil,
            label = prefs.getString(KEY_LABEL, DEFAULT_LABEL).orEmpty().ifBlank { DEFAULT_LABEL }
        )
        if (enabled && !entitlement.isActive(nowMillis)) {
            clear()
            return PromotionEntitlement(false, null, DEFAULT_LABEL)
        }
        return entitlement
    }

    fun isFullscreenAdFree(nowMillis: Long = System.currentTimeMillis()): Boolean =
        current(nowMillis).isActive(nowMillis)

    fun apply(grant: PromotionEntitlement) {
        if (!grant.active) return
        val existing = current()
        val mergedUntil = when {
            existing.active && existing.validUntilMillis == null -> null
            grant.validUntilMillis == null -> null
            existing.active && existing.validUntilMillis != null -> maxOf(existing.validUntilMillis, grant.validUntilMillis)
            else -> grant.validUntilMillis
        }
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_VALID_UNTIL, mergedUntil ?: VALUE_INDEFINITE)
            .putString(KEY_LABEL, grant.label.ifBlank { DEFAULT_LABEL })
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_VALID_UNTIL)
            .remove(KEY_LABEL)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "yamone_promotion"
        const val KEY_ACTIVE = "fullscreen_promotion_active"
        const val KEY_VALID_UNTIL = "fullscreen_promotion_until"
        const val KEY_LABEL = "fullscreen_promotion_label"
        const val VALUE_INDEFINITE = -1L
        const val DEFAULT_LABEL = "프로모션"
    }
}

internal class PromotionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = PromotionStore(appContext)
    private val client = PromotionClient()

    fun current(): PromotionEntitlement = store.current()

    suspend fun redeem(rawCode: String): PromotionRedeemResult {
        val code = normalizePromotionCode(rawCode)
        if (code.length !in 4..40) return PromotionRedeemResult.InvalidCode
        if (!hasUsableNetwork(appContext)) return PromotionRedeemResult.Offline

        return when (val response = client.redeem(code)) {
            is PromotionApiResult.Success -> {
                val grant = PromotionEntitlement(
                    active = true,
                    validUntilMillis = response.validUntilMillis,
                    label = response.label
                )
                store.apply(grant)
                PromotionRedeemResult.Success(store.current())
            }
            PromotionApiResult.InvalidCode -> PromotionRedeemResult.InvalidCode
            PromotionApiResult.NotStarted -> PromotionRedeemResult.NotStarted
            PromotionApiResult.Expired -> PromotionRedeemResult.Expired
            PromotionApiResult.ServerUnavailable -> PromotionRedeemResult.ServerUnavailable
        }
    }
}

private sealed interface PromotionApiResult {
    data class Success(val validUntilMillis: Long?, val label: String) : PromotionApiResult
    data object InvalidCode : PromotionApiResult
    data object NotStarted : PromotionApiResult
    data object Expired : PromotionApiResult
    data object ServerUnavailable : PromotionApiResult
}

private class PromotionClient {
    suspend fun redeem(code: String): PromotionApiResult = withContext(Dispatchers.IO) {
        val connection = (URL("$API_BASE/v1/promotion/redeem").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }

        try {
            val body = JSONObject().put("code", code).toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()

            if (status in 200..299 && json?.optBoolean("ok") == true) {
                val validUntil = if (json.isNull("validUntil")) null else json.optLong("validUntil").takeIf { it > 0L }
                return@withContext PromotionApiResult.Success(
                    validUntilMillis = validUntil,
                    label = json.optString("label", "프로모션").ifBlank { "프로모션" }
                )
            }

            return@withContext when (json?.optString("error")) {
                "INVALID_PROMOTION_CODE", "PROMOTION_NOT_FOUND" -> PromotionApiResult.InvalidCode
                "PROMOTION_NOT_STARTED" -> PromotionApiResult.NotStarted
                "PROMOTION_EXPIRED" -> PromotionApiResult.Expired
                else -> PromotionApiResult.ServerUnavailable
            }
        } catch (_: IOException) {
            PromotionApiResult.ServerUnavailable
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_BASE = "https://yamone-games-ranking-api.yamone0479.workers.dev"
    }
}

internal fun normalizePromotionCode(value: String): String = value
    .trim()
    .uppercase()
    .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
