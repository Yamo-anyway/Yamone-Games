package com.yamone.games.arcadecore

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import java.lang.ref.WeakReference

/** Local preferences only. Sound is never streamed or downloaded. */
data class FeedbackOptions(
    val sound: Boolean = true,
    val music: Boolean = true,
    val effects: Boolean = true,
    val buttons: Boolean = true,
    val vibration: Boolean = true,
    val strongVibration: Boolean = false,
    val followSilent: Boolean = true,
    val reduceMotion: Boolean = false,
    val masterVolume: Float = .75f,
    val musicVolume: Float = .35f,
    val effectVolume: Float = .65f
)

object GameFeedback {
    private var context: Context? = null
    private var view = WeakReference<View>(null)
    private var manager: AudioManager? = null
    private var pool: SoundPool? = null
    private var player: MediaPlayer? = null
    private val sounds = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null
    private var hasFocus = false
    private var focusLost = false
    private var interacted = false
    private var scene = "home"
    private var playingScene = ""
    private var paused = false
    private var blocked = false
    private var foreground = false
    private var windowFocused = false
    private var lastEffectAt = 0L
    private var lastHapticAt = 0L
    var options = FeedbackOptions(); private set
    val canAdvance: Boolean get() = foreground && windowFocused && !blocked && !paused

    fun attach(host: Context, root: View) {
        view = WeakReference(root)
        if (context != null) return
        context = host.applicationContext
        manager = host.getSystemService(AudioManager::class.java)
        val p = host.getSharedPreferences("yamone_feedback_v03", Context.MODE_PRIVATE)
        options = FeedbackOptions(
            sound = p.getBoolean("sound", true), music = p.getBoolean("music", true),
            effects = p.getBoolean("effects", true), buttons = p.getBoolean("buttons", true),
            vibration = p.getBoolean("vibration", true), strongVibration = p.getBoolean("strong", false),
            followSilent = p.getBoolean("followSilent", true), reduceMotion = p.getBoolean("reduceMotion", false),
            masterVolume = p.getFloat("master", .75f).coerceIn(0f, 1f),
            musicVolume = p.getFloat("musicVolume", .35f).coerceIn(0f, 1f),
            effectVolume = p.getFloat("effectVolume", .65f).coerceIn(0f, 1f)
        )
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
                if (change == AudioManager.AUDIOFOCUS_LOSS) focusLost = true
                if (hasFocus) syncMusic() else {
                    runCatching { player?.pause() }
                    pool?.autoPause()
                }
            }, handler).build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build().also { soundPool ->
            soundPool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
            listOf("tap", "collect", "error", "success", "jump", "start", "finish", "record").forEach { name ->
                val id = host.resources.getIdentifier("ym_$name", "raw", host.packageName)
                if (id != 0) sounds[name] = soundPool.load(host, id, 1)
            }
        }
    }

    fun update(value: FeedbackOptions) {
        options = value.copy(masterVolume = value.masterVolume.coerceIn(0f, 1f),
            musicVolume = value.musicVolume.coerceIn(0f, 1f), effectVolume = value.effectVolume.coerceIn(0f, 1f))
        context?.getSharedPreferences("yamone_feedback_v03", Context.MODE_PRIVATE)?.edit()?.apply {
            putBoolean("sound", options.sound); putBoolean("music", options.music)
            putBoolean("effects", options.effects); putBoolean("buttons", options.buttons)
            putBoolean("vibration", options.vibration); putBoolean("strong", options.strongVibration)
            putBoolean("followSilent", options.followSilent); putBoolean("reduceMotion", options.reduceMotion)
            putFloat("master", options.masterVolume); putFloat("musicVolume", options.musicVolume)
            putFloat("effectVolume", options.effectVolume)
        }?.apply()
        if (!options.sound || !options.effects) pool?.autoPause()
        syncMusic()
    }

    fun foreground(value: Boolean) {
        foreground = value
        if (!value) { pauseAudio(); abandonFocus() } else syncMusic()
    }
    fun windowFocus(value: Boolean) {
        windowFocused = value
        if (!value) { pauseAudio(); abandonFocus() } else syncMusic()
    }
    fun setScene(value: String) {
        scene = value.takeIf { it in listOf("home", "sudoku", "ice", "fish", "snow") } ?: "home"
        syncMusic()
    }
    fun setPaused(value: Boolean) { paused = value; syncMusic() }
    fun setBlocked(value: Boolean) { blocked = value; if (value) pauseAudio(); syncMusic() }
    fun tap() { play("tap") }

    fun play(name: String) {
        if (!foreground || !windowFocused || blocked) return
        interacted = true
        focusLost = false
        syncMusic()
        val now = SystemClock.elapsedRealtime()
        if ((name == "collect" || name == "jump") && now - lastEffectAt < 120L) return
        if (name != "tap") lastEffectAt = now
        if (options.vibration && now - lastHapticAt > 250L && name != "jump") {
            lastHapticAt = now
            val haptic = when {
                !options.strongVibration -> HapticFeedbackConstants.CLOCK_TICK
                name == "error" || name == "finish" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                name == "success" || name == "record" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            // No IGNORE_GLOBAL_SETTING: honor the device's haptic preference.
            view.get()?.performHapticFeedback(haptic)
        }
        if (!audible() || !options.effects || (name == "tap" && !options.buttons)) return
        if (!acquireFocus()) return
        val id = sounds[name] ?: return
        if (id !in loaded) return
        val volume = options.masterVolume * options.effectVolume * if (name == "tap") .45f else 1f
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    private fun audible() = options.sound && (!options.followSilent || manager?.ringerMode == AudioManager.RINGER_MODE_NORMAL)
    private fun acquireFocus(): Boolean {
        if (hasFocus) return true
        if (focusLost || !foreground || !windowFocused || blocked) return false
        hasFocus = request?.let { manager?.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED } == true
        return hasFocus
    }
    private fun syncMusic() {
        if (!foreground || !windowFocused || blocked || paused || !interacted || !audible() || !options.music) {
            runCatching { player?.pause() }
            return
        }
        if (!acquireFocus()) return
        val c = context ?: return
        if (scene != playingScene || player == null) {
            runCatching { player?.release() }
            player = null
            val id = c.resources.getIdentifier("ym_bgm_$scene", "raw", c.packageName)
            if (id != 0) player = runCatching {
                MediaPlayer.create(c, id)?.apply { isLooping = true }
            }.getOrNull()
            playingScene = scene
        }
        val volume = options.masterVolume * options.musicVolume
        runCatching { player?.setVolume(volume, volume); player?.start() }
    }
    private fun pauseAudio() { runCatching { player?.pause() }; pool?.autoPause() }
    private fun abandonFocus() { request?.let { runCatching { manager?.abandonAudioFocusRequest(it) } }; hasFocus = false }
    fun release() {
        pauseAudio(); abandonFocus()
        runCatching { player?.release() }; player = null
        pool?.release(); pool = null; sounds.clear(); loaded.clear()
        context = null; view.clear(); request = null; manager = null
        paused = false; blocked = false; foreground = false; windowFocused = false; interacted = false
        focusLost = false; playingScene = ""; scene = "home"; lastEffectAt = 0L; lastHapticAt = 0L
    }
}
