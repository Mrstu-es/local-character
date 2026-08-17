package com.localcharacter.app.ui.components

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.localcharacter.app.ui.motion.AppMotion
import okhttp3.OkHttpClient

object CharacterImageLoader {
    private const val MAX_DISK_CACHE_BYTES = 128L * 1024L * 1024L

    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .crossfade(AppMotion.FastMillis)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("character_image_cache"))
                .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                .build()
        }
        .okHttpClient {
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
        .build()
}
