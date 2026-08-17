package com.localcharacter.app.data.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterAvatarStoreTest {
    @Test fun detectsCommonGalleryImageHeaders() {
        assertEquals(AvatarImageFormat.JPEG, AvatarImageHeader.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(AvatarImageFormat.PNG, AvatarImageHeader.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(AvatarImageFormat.WEBP, AvatarImageHeader.detect("RIFF0000WEBP".encodeToByteArray()))
        assertEquals(AvatarImageFormat.GIF, AvatarImageHeader.detect("GIF89a".encodeToByteArray()))
        assertEquals(AvatarImageFormat.HEIF, AvatarImageHeader.detect(bmff("heic")))
        assertEquals(AvatarImageFormat.AVIF, AvatarImageHeader.detect(bmff("avif")))
    }

    @Test fun rejectsFilesThatOnlyClaimToBeImages() {
        assertNull(AvatarImageHeader.detect("not an image".encodeToByteArray()))
        assertNull(AvatarImageHeader.detect(byteArrayOf()))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun bmff(brand: String) = byteArrayOf(0, 0, 0, 24) + "ftyp".encodeToByteArray() + brand.encodeToByteArray()
}
