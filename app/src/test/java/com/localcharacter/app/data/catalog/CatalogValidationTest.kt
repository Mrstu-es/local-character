package com.localcharacter.app.data.catalog

import com.localcharacter.app.domain.character.RemoteAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogValidationTest {
    @Test fun `url policy only accepts https allowlisted host`() {
        assertEquals("api.example.com", RemoteUrlPolicy.validate("https://api.example.com/path", setOf("api.example.com")).host)
        assertThrows(CatalogNetworkException::class.java) {
            RemoteUrlPolicy.validate("http://api.example.com/path", setOf("api.example.com"))
        }
        assertThrows(CatalogNetworkException::class.java) {
            RemoteUrlPolicy.validate("https://evil.example/path", setOf("api.example.com"))
        }
    }

    @Test fun `image validator checks content signature instead of extension`() {
        val webp = "RIFFxxxxWEBPpayload".encodeToByteArray()
        DownloadedAssetValidator.validate(RemoteAsset(webp, "image/webp", "avatar.bin"), AssetKind.IMAGE)
        assertEquals("webp", DownloadedAssetValidator.extension(RemoteAsset(webp, "image/webp", "avatar.bin")))
        assertThrows(CatalogNetworkException::class.java) {
            DownloadedAssetValidator.validate(RemoteAsset("not image".encodeToByteArray(), "image/png", "fake.png"), AssetKind.IMAGE)
        }
    }
}
