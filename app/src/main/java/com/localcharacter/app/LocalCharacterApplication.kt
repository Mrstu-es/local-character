package com.localcharacter.app

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.localcharacter.app.ui.components.CharacterImageLoader
import com.localcharacter.app.data.repository.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalCharacterApplication : Application(), ImageLoaderFactory {
    val container by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installDebugStrictMode()
        applicationScope.launch {
            if (container.characters.isEmpty()) SeedData.characters.forEach { container.characters.save(it) }
            container.userPersonas.ensureDefault()
        }
    }

    override fun newImageLoader(): ImageLoader = CharacterImageLoader.create(this)

    private fun installDebugStrictMode() {
        if (!AppBuildInfo.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
    }
}
