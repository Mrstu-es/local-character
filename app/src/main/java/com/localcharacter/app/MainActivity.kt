package com.localcharacter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localcharacter.app.ui.AppViewModel
import com.localcharacter.app.ui.LocalCharacterApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val appViewModel: AppViewModel = viewModel()
            LocalCharacterApp(appViewModel)
        }
    }

}
