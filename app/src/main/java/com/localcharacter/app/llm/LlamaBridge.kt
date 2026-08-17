package com.localcharacter.app.llm

internal fun interface NativeTokenCallback {
    fun onToken(token: String)
}

internal object LlamaBridge {
    init {
        System.loadLibrary("local_character")
    }

    external fun getVersion(): String
    external fun loadModel(
        path: String,
        nativeLibraryDir: String,
        contextSize: Int,
        threads: Int,
        batchSize: Int,
    ): String
    external fun unloadModel()
    external fun setChatTemplate(mode: String, customTemplate: String?)
    external fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        seed: Int,
        callback: NativeTokenCallback,
    )
    external fun stopGeneration()
}
