package com.localcharacter.app.tts

import com.localcharacter.app.domain.model.VoiceModel

data class AudioResult(
    val samples: ShortArray = ShortArray(0),
    val sampleRate: Int = 0,
    val channelCount: Int = 1,
)

data class TtsSynthesisSettings(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val speakerId: Int = 0,
)

interface TtsEngine {
    /** True only for platform engines that synthesize and play through Android itself. */
    val performsPlayback: Boolean get() = false
    suspend fun loadVoice(voice: VoiceModel)
    suspend fun unloadVoice()
    suspend fun synthesize(text: String): AudioResult
    suspend fun stop()
    suspend fun configure(settings: TtsSynthesisSettings) = Unit
}
