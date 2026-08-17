package com.localcharacter.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.localcharacter.app.domain.model.VoiceModel
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Platform fallback. Android performs playback; downloaded sherpa voices remain fully offline. */
class AndroidSystemTtsEngine(context: Context) : TtsEngine {
    private val applicationContext = context.applicationContext
    private var platform: TextToSpeech? = null
    private var voice: VoiceModel? = null
    private var settings = TtsSynthesisSettings()
    override val performsPlayback: Boolean = true

    override suspend fun loadVoice(voice: VoiceModel) {
        val engine = ensureInitialized()
        this.voice = voice
        val locale = Locale.forLanguageTag(voice.language.ifBlank { Locale.getDefault().toLanguageTag() })
        val result = withContext(Dispatchers.Main.immediate) { engine.setLanguage(locale) }
        require(result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            "El TTS del sistema no tiene una voz para ${voice.language}."
        }
    }

    override suspend fun unloadVoice() {
        withContext(Dispatchers.Main.immediate) {
            platform?.stop()
            platform?.shutdown()
            platform = null
            voice = null
        }
    }

    override suspend fun configure(settings: TtsSynthesisSettings) {
        this.settings = settings
    }

    override suspend fun synthesize(text: String): AudioResult {
        val engine = ensureInitialized()
        return suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(AudioResult())
                }
                @Deprecated("Deprecated by Android")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("El TTS del sistema no pudo reproducir el texto."))
                    }
                }
                override fun onError(id: String?, errorCode: Int) = onError(id)
            })
            engine.setSpeechRate(settings.speed.coerceIn(.5f, 2f))
            engine.setPitch(settings.pitch.coerceIn(.5f, 2f))
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume.coerceIn(0f, 1f)) }
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("Android rechazó la síntesis de voz."))
            }
            continuation.invokeOnCancellation { engine.stop() }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) { platform?.stop() }
    }

    private suspend fun ensureInitialized(): TextToSpeech {
        platform?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                lateinit var instance: TextToSpeech
                instance = TextToSpeech(applicationContext) { status ->
                    if (!continuation.isActive) return@TextToSpeech
                    if (status == TextToSpeech.SUCCESS) {
                        platform = instance
                        voice?.let { instance.language = Locale.forLanguageTag(it.language) }
                        continuation.resume(instance)
                    } else continuation.resumeWithException(IllegalStateException("El TTS de Android no está disponible."))
                }
            }
        }
    }
}
