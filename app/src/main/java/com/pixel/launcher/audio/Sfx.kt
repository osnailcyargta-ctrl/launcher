package com.pixel.launcher.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import com.pixel.launcher.R
import com.pixel.launcher.core.Prefs
import com.pixel.launcher.core.SfxEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Chiptune sound effects.
 *
 * Everything runs through a single [SoundPool]: the clips are a few kilobytes
 * each, they stay decoded in memory, and playback never allocates or touches the
 * disk. That is the difference between a launcher that feels instant and one
 * that stutters on every tap.
 *
 * A user-supplied sound is copied into app storage the moment it is picked, so
 * playback never depends on another app's content provider still being alive.
 */
class Sfx(context: Context, private val prefs: Prefs) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val soundIds = ConcurrentHashMap<SfxEvent, Int>()
    private val readyIds = ConcurrentHashMap<Int, Boolean>()

    @Volatile
    private var pool: SoundPool? = null

    @Volatile
    private var warmedUp = false

    /**
     * A tap that arrived before its clip finished decoding. Replaying it on the
     * load callback means the very first tap after a cold start still makes a
     * sound instead of silently doing nothing.
     */
    @Volatile
    private var pendingEvent: SfxEvent? = null

    private fun ensurePool(): SoundPool {
        pool?.let { return it }
        return synchronized(this) {
            pool ?: SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_GAME routes to the media stream. The sonification
                        // usage lands on the system stream instead, which is
                        // muted or near-silent on a lot of phones - that is why
                        // the effects could not be heard.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
                .also { created ->
                    created.setOnLoadCompleteListener { _, sampleId, status ->
                        if (status != 0) return@setOnLoadCompleteListener
                        readyIds[sampleId] = true
                        val queued = pendingEvent
                        if (queued != null && soundIds[queued] == sampleId) {
                            pendingEvent = null
                            playNow(created, sampleId)
                        }
                    }
                    pool = created
                }
        }
    }

    /** Decodes every clip once, off the main thread. Safe to call repeatedly. */
    fun warmUp() {
        if (warmedUp) return
        warmedUp = true
        scope.launch {
            val target = ensurePool()
            for (event in SfxEvent.entries) {
                loadInto(target, event)
            }
        }
    }

    private fun loadInto(target: SoundPool, event: SfxEvent) {
        val custom = customFile(event)
        val id = if (custom.exists() && custom.length() > 0) {
            runCatching { target.load(custom.absolutePath, 1) }.getOrNull()
                ?.takeIf { it != 0 }
                ?: loadDefault(target, event)
        } else {
            loadDefault(target, event)
        }
        if (id != null && id != 0) soundIds[event] = id
    }

    private fun loadDefault(target: SoundPool, event: SfxEvent): Int? =
        runCatching { target.load(appContext, defaultRes(event), 1) }.getOrNull()

    fun play(event: SfxEvent) {
        val settings = prefs.settings.value
        if (!settings.soundEnabled || settings.volume <= 0) return
        if (settings.respectSilentMode && isPhoneSilent()) return

        if (!warmedUp) warmUp()
        val target = pool ?: run {
            // The pool is still being built; remember the tap so it is not lost.
            pendingEvent = event
            return
        }
        val id = soundIds[event] ?: run {
            pendingEvent = event
            return
        }
        if (readyIds[id] != true) {
            pendingEvent = event
            return
        }
        playNow(target, id)
    }

    private fun playNow(target: SoundPool, id: Int) {
        val volume = (prefs.settings.value.volume / 100f).coerceIn(0f, 1f)
        runCatching { target.play(id, volume, volume, 1, 0, 1f) }
            .onFailure { Log.w(TAG, "play failed for sample $id", it) }
    }

    private fun isPhoneSilent(): Boolean {
        val mode = audioManager?.ringerMode ?: return false
        return mode == AudioManager.RINGER_MODE_SILENT ||
            mode == AudioManager.RINGER_MODE_VIBRATE
    }

    /** True when the media stream itself is turned all the way down. */
    fun isSystemVolumeZero(): Boolean {
        val manager = audioManager ?: return false
        return runCatching {
            manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }.getOrDefault(false)
    }

    // --------------------------------------------------------- custom clips

    fun customFile(event: SfxEvent): File =
        File(File(appContext.filesDir, "sfx").apply { mkdirs() }, "${event.name}.snd")

    /**
     * Copies the picked audio into app storage and swaps it in. Returns false if
     * the file could not be read or is implausibly large for a sound effect.
     */
    suspend fun installCustom(event: SfxEvent, uri: Uri, displayName: String?): Boolean =
        withContext(Dispatchers.IO) {
            val target = customFile(event)
            val copied = runCatching { copyClip(uri, target) }.getOrDefault(-1L)

            if (copied <= 0L) {
                target.delete()
                return@withContext false
            }

            prefs.setCustomSound(event, displayName?.takeIf { it.isNotBlank() } ?: "custom")
            reload(event)
            true
        }

    /** Returns the number of bytes written, or -1 if the clip was unusable. */
    private fun copyClip(uri: Uri, target: File): Long {
        val input = appContext.contentResolver.openInputStream(uri) ?: return -1L
        input.use { source ->
            target.outputStream().use { output ->
                var total = 0L
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_CLIP_BYTES) return -1L
                    output.write(buffer, 0, read)
                }
                return total
            }
        }
    }

    fun clearCustom(event: SfxEvent) {
        customFile(event).delete()
        prefs.setCustomSound(event, null)
        reload(event)
    }

    fun clearAllCustom() {
        SfxEvent.entries.forEach { customFile(it).delete() }
        prefs.clearCustomSounds()
        SfxEvent.entries.forEach { reload(it) }
    }

    private fun reload(event: SfxEvent) {
        scope.launch {
            val target = pool ?: ensurePool()
            soundIds.remove(event)?.let { old ->
                readyIds.remove(old)
                runCatching { target.unload(old) }
            }
            loadInto(target, event)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        warmedUp = false
        pendingEvent = null
        soundIds.clear()
        readyIds.clear()
    }

    private fun defaultRes(event: SfxEvent): Int = when (event) {
        SfxEvent.CLICK -> R.raw.sfx_click
        SfxEvent.OPEN -> R.raw.sfx_open
        SfxEvent.BACK -> R.raw.sfx_back
        SfxEvent.TOGGLE -> R.raw.sfx_toggle
        SfxEvent.PAGE -> R.raw.sfx_page
        SfxEvent.MENU -> R.raw.sfx_menu
        SfxEvent.PIN -> R.raw.sfx_pin
        SfxEvent.ERROR -> R.raw.sfx_error
    }

    private companion object {
        const val TAG = "Sfx"
        const val MAX_CLIP_BYTES = 4L * 1024 * 1024
    }
}
