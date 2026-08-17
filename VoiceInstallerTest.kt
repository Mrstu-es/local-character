package com.localcharacter.app.data.voice

import com.localcharacter.app.domain.model.VoiceEngineType
import com.localcharacter.app.domain.model.VoiceModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `installs verified files atomically and records offline paths`() = runBlocking {
        val model = "onnx-model".encodeToByteArray()
        val tokens = "a b c".encodeToByteArray()
        val remote = remoteVoice(model, tokens)
        val store = FakeVoiceStore()
        val installer = VoiceInstaller(
            temporary.newFolder("voices"), store,
            downloads = FakeDownloads(mapOf("model.onnx" to model, "tokens.txt" to tokens)),
            now = { 123L },
        )
        val result = installer.install(remote, setOf("voices.example"))
        assertEquals(VoiceInstallOutcome.INSTALLED, result.outcome)
        val saved = requireNotNull(store.saved)
        assertTrue(File(requireNotNull(saved.localModelPath)).isFile)
        assertEquals(model.size.toLong() + tokens.size, saved.sizeBytes)
        assertFalse(temporary.root.walkTopDown().any { it.name.startsWith(".install-") })
    }

    @Test fun `failed download leaves no partial installation`() = runBlocking {
        val root = temporary.newFolder("failed")
        val remote = remoteVoice("model".encodeToByteArray(), "tokens".encodeToByteArray())
        val installer = VoiceInstaller(root, FakeVoiceStore(), downloads = object : VoiceFileDownloadClient {
            override suspend fun download(file: RemoteVoiceFile, allowedHosts: Set<String>, destination: File) {
                destination.parentFile?.mkdirs()
                destination.writeText("partial")
                error("network failed")
            }
        })
        runCatching { installer.install(remote, setOf("voices.example")) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    private fun remoteVoice(model: ByteArray, tokens: ByteArray) = RemoteVoice(
        repositoryId = "repo", remoteId = "ana", name = "Ana", language = "es",
        engine = VoiceEngineType.PIPER,
        files = listOf(
            RemoteVoiceFile("model", "https://voices.example/model.onnx", "model.onnx", model.size.toLong(), model.sha256()),
            RemoteVoiceFile("tokens", "https://voices.example/tokens.txt", "tokens.txt", tokens.size.toLong(), tokens.sha256()),
        ),
        sampleUrl = null, sizeBytes = model.size.toLong() + tokens.size, license = "CC0", author = "A",
        creator = "A", source = "https://voices.example", version = "1", consent = null,
    )

    private class FakeDownloads(private val content: Map<String, ByteArray>) : VoiceFileDownloadClient {
        override suspend fun download(file: RemoteVoiceFile, allowedHosts: Set<String>, destination: File) {
            destination.parentFile?.mkdirs()
            destination.writeBytes(requireNotNull(content[file.relativePath]))
        }
    }

    private class FakeVoiceStore : VoiceInstallStore {
        var saved: VoiceModel? = null
        override suspend fun find(repositoryId: String, remoteId: String): VoiceModel? = saved
        override suspend fun install(voice: VoiceModel, finalizeFiles: () -> Unit) {
            finalizeFiles()
            saved = voice
        }
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
