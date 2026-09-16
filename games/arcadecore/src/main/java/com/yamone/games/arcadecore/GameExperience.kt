package com.yamone.games.arcadecore

import android.content.*
import android.media.*
import android.os.*
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** App-local settings: deliberately independent from record/identity/ad storage for safe rollback. */
data class ExperienceSettings(
    val effects: Boolean = true,
    val music: Boolean = true,
    val vibration: Boolean = true,
    val effectsVolume: Float = .55f,
    val musicVolume: Float = .30f,
    val hapticStrength: HapticStrength = HapticStrength.LIGHT,
    val musicStyle: MusicStyle = MusicStyle.AUTO,
    val respectSilentMode: Boolean = true
)

val LocalGameExperience = staticCompositionLocalOf<GameExperienceController?> { null }

class GameExperienceController(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("yamone_experience_v3", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    private val soundIds = mutableMapOf<GameSound, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val readyTracks = mutableMapOf<MusicScene, File>()
    private var view = WeakReference<View>(null)
    private var player: MediaPlayer? = null
    private var playerScene: MusicScene? = null
    private var prepared = false
    private var closed = false
    private var desiredScene = MusicScene.HOME
    private var desiredPlaying = true
    private var blocked = false
    private var focused = false
    private var lastFeedbackAt = 0L
    private var lastHapticAt = 0L
    var foreground by mutableStateOf(false); private set
    var interrupted by mutableStateOf(false); private set
    var pauseGeneration by mutableIntStateOf(0); private set
    var settings by mutableStateOf(readSettings()); private set

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                focused = true
                reconcile()
            } else {
                focused = false
                interrupted = true
                pauseGeneration++
                pauseAudio()
            }
        }, handler).build()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                interrupted = true
                pauseGeneration++
            }
            reconcile()
        }
    }

    init {
        soundPool.setOnLoadCompleteListener { _, id, status -> handler.post {
            if (!closed && status == 0) loadedIds += id
        } }
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") app.registerReceiver(receiver, filter)
        worker.execute {
            val directory = File(app.cacheDir, "yamone-audio-v3").apply { mkdirs() }
            GameSound.entries.forEach { sound ->
                val file = File(directory, "fx-${sound.name}.wav")
                runCatching { if (!file.exists()) file.writeBytes(SoundSynthesis.effect(sound)) }.onSuccess {
                    handler.post { if (!closed) soundIds[sound] = soundPool.load(file.absolutePath, 1) }
                }
            }
            MusicScene.entries.forEach { scene ->
                val file = File(directory, "bg-${scene.name}.wav")
                runCatching { if (!file.exists()) file.writeBytes(SoundSynthesis.music(scene)) }.onSuccess {
                    handler.post { if (!closed) { readyTracks[scene] = file; reconcile() } }
                }
            }
        }
    }

    fun attachView(value: View) { view = WeakReference(value) }
    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (!active) { pauseGeneration++; pauseAudio(); abandonFocus() }
        reconcile()
    }
    fun setBlocked(value: Boolean) {
        if (blocked == value) return
        blocked = value
        if (value) { pauseAudio(); abandonFocus() }
        reconcile()
    }
    fun setScene(scene: MusicScene, playing: Boolean) {
        if (desiredScene == scene && desiredPlaying == playing) return
        desiredScene = scene; desiredPlaying = playing
        reconcile()
    }
    fun resumeByUser() { interrupted = false; reconcile() }

    fun update(transform: (ExperienceSettings) -> ExperienceSettings) {
        val next = transform(settings)
        settings = next.copy(effectsVolume = safeVolume(next.effectsVolume, .55f), musicVolume = safeVolume(next.musicVolume, .30f))
        prefs.edit().putBoolean("effects", settings.effects).putBoolean("music", settings.music)
            .putBoolean("vibration", settings.vibration).putBoolean("respectSilent", settings.respectSilentMode)
            .putFloat("effectsVolume", settings.effectsVolume).putFloat("musicVolume", settings.musicVolume)
            .putString("hapticStrength", settings.hapticStrength.name).putString("musicStyle", settings.musicStyle.name).apply()
        if (!settings.effects) soundPool.autoPause()
        reconcile()
    }

    fun play(sound: GameSound, haptic: Boolean = true, feedbackView: View? = null) {
        if (closed || !foreground || blocked || interrupted) return
        val now = SystemClock.elapsedRealtime()
        if (haptic && settings.vibration && now - lastHapticAt >= 120L) {
            val code = when {
                settings.hapticStrength == HapticStrength.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
                sound == GameSound.ERROR || sound == GameSound.RECORD -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            (feedbackView ?: view.get())?.takeIf { it.hasWindowFocus() }?.performHapticFeedback(code)
            lastHapticAt = now
        }
        if (!settings.effects || !maySound() || now - lastFeedbackAt < 45L) return
        val id = soundIds[sound]?.takeIf { it in loadedIds } ?: return
        // Do not steal another player's audio focus for a short tap when BGM is disabled.
        if (!focused && manager.isMusicActive) return
        soundPool.play(id, settings.effectsVolume, settings.effectsVolume, 1, 0, 1f)
        lastFeedbackAt = now
    }

    fun previewMusic() {
        resumeByUser()
        desiredPlaying = true
        reconcile()
    }

    private fun maySound() = !settings.respectSilentMode || manager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    private fun reconcile() {
        if (closed) return
        if (!foreground || blocked || interrupted || !desiredPlaying || !settings.music || settings.musicVolume <= 0f || !maySound()) {
            pauseAudio(stopEffects = !foreground || blocked || interrupted || !maySound())
            if (!foreground || blocked || !settings.music || !maySound()) abandonFocus()
            return
        }
        val scene = when (settings.musicStyle) {
            MusicStyle.AUTO -> desiredScene
            MusicStyle.CALM -> MusicScene.SUDOKU
            MusicStyle.CHEERFUL -> MusicScene.HOME
        }
        val file = readyTracks[scene] ?: return
        if (!focused) {
            focused = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!focused) return
        }
        if (playerScene != scene || player == null) {
            player?.release(); prepared = false; playerScene = scene
            val next = MediaPlayer()
            player = next
            runCatching {
                next.setAudioAttributes(attributes)
                next.setDataSource(file.absolutePath)
                next.isLooping = true
                next.setOnPreparedListener {
                    if (!closed && player === it) { prepared = true; reconcile() }
                }
                next.setOnErrorListener { mp, _, _ ->
                    if (player === mp) { prepared = false; player = null; playerScene = null }
                    mp.release(); true
                }
                next.prepareAsync()
            }.onFailure { next.release(); player = null; playerScene = null }
        } else if (prepared) {
            runCatching { player?.setVolume(settings.musicVolume, settings.musicVolume); if (player?.isPlaying == false) player?.start() }
        }
    }
    private fun pauseAudio(stopEffects: Boolean = true) {
        runCatching { if (prepared && player?.isPlaying == true) player?.pause() }
        if (stopEffects) soundPool.autoPause()
    }
    private fun abandonFocus() {
        if (focused) { focused = false; manager.abandonAudioFocusRequest(focusRequest) }
    }
    private fun safeVolume(value: Float, fallback: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else fallback
    private fun readSettings() = ExperienceSettings(
        effects = prefs.getBoolean("effects", true), music = prefs.getBoolean("music", true),
        vibration = prefs.getBoolean("vibration", true), effectsVolume = safeVolume(prefs.getFloat("effectsVolume", .55f), .55f),
        musicVolume = safeVolume(prefs.getFloat("musicVolume", .30f), .30f),
        hapticStrength = runCatching { HapticStrength.valueOf(prefs.getString("hapticStrength", "LIGHT")!!) }.getOrDefault(HapticStrength.LIGHT),
        musicStyle = runCatching { MusicStyle.valueOf(prefs.getString("musicStyle", "AUTO")!!) }.getOrDefault(MusicStyle.AUTO),
        respectSilentMode = prefs.getBoolean("respectSilent", true)
    )
    override fun close() {
        if (closed) return
        closed = true; pauseAudio(); abandonFocus()
        runCatching { app.unregisterReceiver(receiver) }
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null)
        player?.release(); player = null; soundPool.release(); view.clear()
    }
}
