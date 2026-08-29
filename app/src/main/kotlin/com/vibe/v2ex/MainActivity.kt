package com.vibe.v2ex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vibe.v2ex.designsystem.V2exTheme
import com.vibe.v2ex.feature.agreement.AgreementScreen
import com.vibe.v2ex.navigation.V2exApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by appViewModel.uiState.collectAsState()
            V2exTheme(darkModePreference = uiState.darkMode) {
                if (uiState.agreementAccepted) {
                    V2exApp()
                } else {
                    AgreementScreen()
                }
            }
        }
    }
}
