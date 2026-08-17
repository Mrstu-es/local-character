package com.localcharacter.app.data.catalog

import android.util.Log
import com.localcharacter.app.AppBuildInfo

internal object CatalogDebugLog {
    fun event(provider: String, message: String) {
        if (AppBuildInfo.DEBUG) Log.d("CharacterCatalog", "Provider: $provider · $message")
    }
}
