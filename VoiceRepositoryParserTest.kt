package com.localcharacter.app.data.voice

import com.localcharacter.app.domain.model.VoiceEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRepositoryParserTest {
    @Test fun `parses version one manifest and secure piper voice`() {
        val manifest = VoiceRepositoryParser.parseManifest(
            """{"schema":"localcharacter.voice.repository","version":1,"name":"Voces ES","voicesIndex":"voices.json"}""".encodeToByteArray(),
            "https://voices.example/voice-repository.json",
            "repo",
        )
        val modelHash = "a".repeat(64)
        val tokensHash = "b".repeat(64)
        val index = """
            {"version":1,"voices":[{
              "id":"es-ana","name":"Ana","language":"es","engine":"piper","sizeBytes":15,
              "license":"CC-BY-4.0","author":"Autora","creator":"Autora","source":"https://voices.example/source","version":"1.0",
              "files":[
                {"role":"model","url":"ana/model.onnx","relativePath":"model.onnx","sizeBytes":10,"sha256":"$modelHash"},
                {"role":"tokens","url":"ana/tokens.txt","relativePath":"tokens.txt","sizeBytes":5,"sha256":"$tokensHash"}
              ]
            }]}
        """.trimIndent().encodeToByteArray()
        val voices = VoiceRepositoryParser.parseIndex(index, "repo", manifest.indexUrl, manifest.allowedHosts)
        assertEquals(1, voices.size)
        assertEquals(VoiceEngineType.PIPER, voices.single().engine)
        assertEquals("https://voices.example/ana/model.onnx", voices.single().files.first().url)
    }

    @Test fun `rejects traversal executable files and unconsented real person voice`() {
        assertTrue(!VoiceRepositoryParser.isSafeRelativePath("../model.onnx"))
        val invalid = """
            {"version":1,"voices":[{
              "id":"clone","name":"Clone","language":"es","engine":"vits","sizeBytes":2,
              "license":"x","author":"x","source":"x","version":"1",
              "consent":{"realPersonVoice":true,"confirmed":false},
              "files":[
                {"role":"model","url":"model.apk","relativePath":"model.apk","sizeBytes":1,"sha256":"${"a".repeat(64)}"},
                {"role":"tokens","url":"tokens.txt","relativePath":"tokens.txt","sizeBytes":1,"sha256":"${"b".repeat(64)}"}
              ]
            }]}
        """.trimIndent().encodeToByteArray()
        val error = runCatching {
            VoiceRepositoryParser.parseIndex(invalid, "repo", "https://voices.example/voices.json", setOf("voices.example"))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
