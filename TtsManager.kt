package com.localcharacter.app.tts

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.domain.model.VoiceModel
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TtsPlaybackState {
    data object Idle : TtsPlaybackState
    data class Loading(val voiceId: String) : TtsPlaybackState
    data class Synthesizing(val messageId: String) : TtsPlaybackState
    data class Playing(val messageId: String) : TtsPlaybackState
    data class Failed(val messageId: String?, val message: String) : TtsPlaybackState
}

/** One process-wide TTS lifecycle: one heavy local voice and one reusable AudioTrack. */
class TtsManager(context: Context) : ComponentCallbacks2 {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sherpaEngine = SherpaOnnxTtsEngine()
    private val systemEngine = AndroidSystemTtsEngine(applicationContext)
    private val player = ReusablePcmPlayer()
    private val mutableState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    val state: StateFlow<TtsPlaybackState> = mutableState.asStateFlow()
    private var activeEngine: TtsEngine? = null
    private var loadedVoiceId: String? = null
    private var activeJob: Job? = null

    init {
        applicationContext.registerComponentCallbacks(this)
    }

    fun speak(
        messageId: String,
        text: String,
        voice: VoiceModel,
        settings: TtsSynthesisSettings,
        unloadAfter: Boolean = false,
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            runCatching {
                stopEnginesAndPlayer()
                val engine = if (voice.engine == VoiceEngineType.ANDROID_SYSTEM) systemEngine else sherpaEngine
                if (activeEngine !== engine || loadedVoiceId != voice.id) {
                    mutableState.value = TtsPlaybackState.Loading(voice.id)
                    activeEngine?.unloadVoice()
                    engine.configure(settings)
                    engine.loadVoice(voice)
                    activeEngine = engine
                    loadedVoiceId = voice.id
                } else engine.configure(settings)
                mutableState.value = TtsPlaybackState.Synthesizing(messageId)
                val audio = engine.synthesize(text)
                if (!engine.performsPlayback) {
                    require(audio.samples.isNotEmpty() && audio.sampleRate > 0) { "La voz no generó audio." }
                    mutableState.value = TtsPlaybackState.Playing(messageId)
                    player.play(audio, settings.volume)
                }
                if (unloadAfter) {
                    engine.unloadVoice()
                    if (activeEngine === engine) {
                        activeEngine = null
                        loadedVoiceId = null
                    }
                }
                mutableState.value = TtsPlaybackState.Idle
            }.onFailure { error ->
                if (error !is CancellationException) {
                    mutableState.value = TtsPlaybackState.Failed(messageId, error.message ?: "No se pudo reproducir la voz.")
                }
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = scope.launch {
            stopEnginesAndPlayer()
            mutableState.value = TtsPlaybackState.Idle
        }
    }

    fun unload() {
        activeJob?.cancel()
        activeJob = scope.launch {
            stopEnginesAndPlayer()
            activeEngine?.unloadVoice()
            activeEngine = null
            loadedVoiceId = null
            player.release()
            mutableState.value = TtsPlaybackState.Idle
        }
    }

    private suspend fun stopEnginesAndPlayer() {
        activeEngine?.stop()
        player.stop()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) unload()
    }

    override fun onLowMemory() = unload()
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    companion object {
        fun systemVoice(language: String = Locale.getDefault().toLanguageTag()) = VoiceModel(
            id = "android-system:$language",
            name = "Voz del sistema",
            engine = VoiceEngineType.ANDROID_SYSTEM,
            language = language,
            license = "Android system voice",
            author = "Android TTS provider",
            source = "android://text-to-speech",
            contentHash = "android-system:$language",
        )
    }
}

private class ReusablePcmPlayer {
    private val lock = Any()
    private var track: AudioTrack? = null
    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    suspend fun play(audio: AudioResult, volume: Float) = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val current = ensureTrack(audio.sampleRate, audio.channelCount)
        synchronized(lock) {
            current.pause()
            current.flush()
            current.setVolume(volume.coerceIn(0f, 1f))
            current.play()
        }
        var offset = 0
        while (offset < audio.samples.size) {
            val written = current.write(audio.samples, offset, audio.samples.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) error("Android no pudo escribir el audio generado.")
            offset += written
        }
        val expectedMillis = audio.samples.size * 1_000L / audio.sampleRate.coerceAtLeast(1) / audio.channelCount.coerceAtLeast(1)
        val remaining = expectedMillis - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
        synchronized(lock) { if (track === current) current.stop() }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching { track?.pause() }
            runCatching { track?.flush() }
        }
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            runCatching { track?.release() }
            track = null
            sampleRate = 0
            channelCount = 0
        }
    }

    private fun ensureTrack(rate: Int, channels: Int): AudioTrack = synchronized(lock) {
        if (track != null && sampleRate == rate && channelCount == channels) return@synchronized track!!
        track?.release()
        val channelMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minimum = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(rate / 2)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(channelMask).build(),
            )
            .setBufferSizeInBytes(minimum * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also {
                track = it
                sampleRate = rate
                channelCount = channels
            }
    }
}
