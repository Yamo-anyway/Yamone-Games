package com.yamone.games

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UMP privacy gate for Yamone Games.
 *
 * The current app is treated as child-directed. UMP still refreshes consent information on every
 * process launch, but setTagForUnderAgeOfConsent(true) prevents asking a child to provide consent.
 * GMA Next-Gen receives its own child-directed signal separately in YamoneAdMob.
 */
internal object YamonePrivacy {
    private const val TAG = "YamonePrivacy"

    private val started = AtomicBoolean(false)
    private val sessionCanRequestAds = CompletableDeferred<Boolean>()
    private val _privacyOptionsRequired = MutableStateFlow(false)

    @Volatile
    private var consentInformation: ConsentInformation? = null

    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun start(activity: Activity, onComplete: (Boolean) -> Unit = {}) {
        val info = consentInformation
            ?: UserMessagingPlatform.getConsentInformation(activity.applicationContext).also {
                consentInformation = it
            }

        if (!started.compareAndSet(false, true)) {
            if (sessionCanRequestAds.isCompleted) onComplete(info.canRequestAds())
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(true)
            .build()

        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                updatePrivacyOptions(info)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let {
                        Log.w(TAG, "Consent form error ${it.errorCode}: ${it.message}")
                    }
                    updatePrivacyOptions(info)
                    finish(info.canRequestAds(), onComplete)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update error ${requestError.errorCode}: ${requestError.message}")
                updatePrivacyOptions(info)
                // UMP can retain a valid decision from a previous session even when refresh fails.
                finish(info.canRequestAds(), onComplete)
            }
        )
    }

    suspend fun awaitCanRequestAds(activity: Activity): Boolean {
        start(activity)
        return sessionCanRequestAds.await()
    }

    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        val info = consentInformation
            ?: UserMessagingPlatform.getConsentInformation(activity.applicationContext).also {
                consentInformation = it
            }

        if (info.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            updatePrivacyOptions(info)
            onDismissed()
            return
        }

        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let {
                Log.w(TAG, "Privacy options error ${it.errorCode}: ${it.message}")
            }
            updatePrivacyOptions(info)
            onDismissed()
        }
    }

    private fun finish(canRequestAds: Boolean, onComplete: (Boolean) -> Unit) {
        if (!sessionCanRequestAds.isCompleted) {
            sessionCanRequestAds.complete(canRequestAds)
        }
        onComplete(canRequestAds)
    }

    private fun updatePrivacyOptions(info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
