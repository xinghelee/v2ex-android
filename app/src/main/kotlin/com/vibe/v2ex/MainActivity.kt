package com.vibe.v2ex

import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private val incomingDeepLink = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        incomingDeepLink.value = intent?.data
        enableEdgeToEdge()
        setContent {
            val uiState by appViewModel.uiState.collectAsState()
            val deepLink by incomingDeepLink.collectAsState()
            V2exTheme(
                darkModePreference = uiState.darkMode,
                appTheme = uiState.theme,
                readingFontSize = uiState.readingFontSize,
                readingLineSpacing = uiState.readingLineSpacing,
                readingMonoFont = uiState.readingMonoFont,
            ) {
                // 「使用须知」闸门是 App Store Guideline 1.2 的产物，Android 版隐藏不展示
                // （AgreementScreen 代码保留，未接线）。
                V2exApp(
                    deepLinkUri = deepLink,
                    onDeepLinkHandled = { incomingDeepLink.value = null },
                    liquidGlassEnabled = uiState.liquidGlassEnabled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink.value = intent.data
    }
}
