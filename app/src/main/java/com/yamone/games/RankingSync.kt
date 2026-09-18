package com.yamone.games

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yamone.games.arcadecore.ArcadeRecordStorage
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Network-constrained durable job, not a screen-lifetime coroutine. */
internal object RankingSyncScheduler {
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<RankingSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("yamone-ranking-outbox")
            .build()
        // Replacement always rescans durable personal bests and preserves in-flight newer revisions.
        WorkManager.getInstance(context).enqueueUniqueWork("yamone-ranking-sync-v305", ExistingWorkPolicy.REPLACE, request)
    }
}

class RankingSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val repository = OnlineRankingRepository(applicationContext)
            repository.syncRankingState(AppPreferences(applicationContext).nickname())
            if (repository.hasPending()) Result.retry() else Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}

class YamoneGamesApplication : Application() {
    // Strong references: Android stores preference listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("records_") || key.startsWith("best_") || key == "nickname")) {
            RankingSyncScheduler.schedule(this)
        }
    }
    private val connectivity = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) RankingSyncScheduler.schedule(this@YamoneGamesApplication)
        }
    }
    override fun onCreate() {
        super.onCreate()
        // Deterministic one-time record/unit migration completes before screens or upload jobs read it.
        ArcadeRecordStorage(this)
        listOf("yamone_arcade_records", "yamone_sudoku_game", "yamone_games_settings").forEach {
            getSharedPreferences(it, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        }
        runCatching { getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(connectivity) }
        RankingSyncScheduler.schedule(this)
    }
}
