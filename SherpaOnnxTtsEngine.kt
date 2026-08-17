package com.localcharacter.app.tts

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.localcharacter.app.data.voice.VoiceFilesCodec
import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.domain.model.VoiceModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SherpaOnnxTtsEngine : TtsEngine {
    private var tts: OfflineTts? = null
    private var settings = TtsSynthesisSettings()
    private val stopRequested = AtomicBoolean(false)

    override suspend fun loadVoice(voice: VoiceModel) = withContext(Dispatchers.IO) {
        require(voice.engine in setOf(VoiceEngineType.KOKORO, VoiceEngineType.PIPER, VoiceEngineType.VITS)) {
            "Motor de voz no compatible con sherpa-onnx: ${voice.engine}."
        }
        unloadVoice()
        val files = VoiceFilesCodec.decode(voice.filesJson)
        require(files.isNotEmpty()) { "La voz no contiene un manifiesto local de archivos." }
        files.forEach { require(File(it.localPath).isFile) { "Falta ${it.relativePath} en la voz instalada." } }
        fun required(role: String) = files.firstOrNull { it.role == role }?.localPath
            ?: error("La voz no contiene el archivo requerido: $role.")
        val dataDir = files.firstOrNull { it.role == "data" }?.let { data ->
            val parts = data.relativePath.split('/')
            var installRoot = File(data.localPath)
            repeat(parts.size) { installRoot = requireNotNull(installRoot.parentFile) }
            File(installRoot, parts.first()).absolutePath
        }.orEmpty()
        val lexicon = files.filter { it.role == "lexicon" }.joinToString(",") { it.localPath }
        val modelConfig = when (voice.engine) {
            VoiceEngineType.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = required("model"),
                    voices = required("voices"),
                    tokens = required("tokens"),
                    dataDir = dataDir,
                    lexicon = lexicon,
                    lang = voice.language,
                    lengthScale = 1f,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            VoiceEngineType.PIPER, VoiceEngineType.VITS -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = required("model"),
                    lexicon = lexicon,
                    tokens = required("tokens"),
                    dataDir = dataDir,
                    lengthScale = 1f,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            else -> error("Motor de voz no compatible.")
        }
        stopRequested.set(false)
        tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
    }

    override suspend fun unloadVoice() = withContext(Dispatchers.IO) {
        stopRequested.set(true)
        tts?.release()
        tts = null
    }

    override suspend fun configure(settings: TtsSynthesisSettings) {
        this.settings = settings
    }

    override suspend fun synthesize(text: String): AudioResult = withContext(Dispatchers.Default) {
        val active = tts ?: error("Carga una voz local antes de reproducirla.")
        stopRequested.set(false)
        val audio = active.generateWithConfigAndCallback(
            text = text,
            config = GenerationConfig(
                speed = settings.speed.coerceIn(.5f, 2f),
                sid = settings.speakerId.coerceAtLeast(0),
            ),
        ) {
            if (stopRequested.get()) 0 else 1
        }
        ensureActive()
        val pcm = ShortArray(audio.samples.size) { index ->
            (audio.samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        AudioResult(pcm, audio.sampleRate)
    }

    override suspend fun stop() {
        stopRequested.set(true)
    }
}
