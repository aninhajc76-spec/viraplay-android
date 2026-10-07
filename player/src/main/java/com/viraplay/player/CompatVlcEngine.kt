package com.viraplay.player

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.max

data class CompatAudioTrack(
    val id: Int,
    val name: String
)

class CompatVlcEngine(context: Context) {
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf(
            "--network-caching=1800",
            "--audio-time-stretch",
            "--no-video-title-show"
        )
    )

    private val player = MediaPlayer(libVlc).apply {
        runCatching { setAudioOutput("android_audiotrack") }
        runCatching { setAudioOutputDevice("stereo") }
    }
    private var attachedView: VLCVideoLayout? = null

    fun attach(view: VLCVideoLayout) {
        if (attachedView === view) return
        runCatching {
            if (attachedView != null) player.detachViews()
        }
        player.attachViews(view, null, false, true)
        attachedView = view
    }

    fun detach() {
        runCatching { player.detachViews() }
        attachedView = null
    }

    fun open(url: String, startPositionMs: Long = 0L) {
        if (url.isBlank()) return
        runCatching { player.stop() }

        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=1800")
        media.addOption(":http-reconnect=true")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")

        player.setMedia(media)
        media.release()
        player.play()

        if (startPositionMs > 0L) {
            Thread {
                try {
                    Thread.sleep(850)
                    player.setTime(startPositionMs)
                } catch (_: Throwable) {
                }
            }.start()
        }
    }

    fun play() {
        runCatching { player.play() }
    }

    fun pause() {
        runCatching { player.pause() }
    }

    fun stop() {
        runCatching { player.stop() }
    }

    fun isPlaying(): Boolean = runCatching { player.isPlaying }.getOrDefault(false)

    fun currentPosition(): Long =
        runCatching { max(0L, player.time) }.getOrDefault(0L)

    fun duration(): Long =
        runCatching { max(0L, player.length) }.getOrDefault(0L)

    fun seekTo(positionMs: Long) {
        runCatching { player.setTime(positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val target = (currentPosition() + deltaMs)
            .coerceAtLeast(0L)
            .let { value ->
                val total = duration()
                if (total > 0L) value.coerceAtMost(total) else value
            }
        seekTo(target)
    }

    fun audioTracks(): List<CompatAudioTrack> =
        runCatching {
            player.audioTracks
                ?.filter { it.id != -1 }
                ?.map { CompatAudioTrack(it.id, it.name?.takeIf(String::isNotBlank) ?: "Áudio") }
                .orEmpty()
        }.getOrDefault(emptyList())

    fun currentAudioTrackId(): Int =
        runCatching { player.audioTrack }.getOrDefault(-1)

    fun setAudioTrack(id: Int): Boolean =
        runCatching { player.setAudioTrack(id) }.getOrDefault(false)

    fun selectPortugueseAudio(): String? {
        val tracks = audioTracks()
        val preferred = tracks.firstOrNull { track ->
            val value = track.name.lowercase()
            value.contains("portugu") ||
                value.contains("pt-br") ||
                value.contains("pt_br") ||
                value.contains("ptbr") ||
                value == "pt" ||
                value.contains("brasil") ||
                value.contains("brazil") ||
                value.contains("dublado") ||
                value.contains("dub") ||
                value.contains(" por")
        } ?: return null

        return if (setAudioTrack(preferred.id)) preferred.name else null
    }

    fun cycleAudio(): String {
        val tracks = audioTracks()
        if (tracks.isEmpty()) return "Compatibilidade"

        val current = currentAudioTrackId()
        val index = tracks.indexOfFirst { it.id == current }
        val next = tracks[if (index < 0 || index >= tracks.lastIndex) 0 else index + 1]
        setAudioTrack(next.id)
        return next.name
    }

    fun release() {
        runCatching { stop() }
        detach()
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }
}
