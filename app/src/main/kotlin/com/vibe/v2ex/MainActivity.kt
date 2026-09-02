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
import androidx.lifecycle.lifecycleScope
import com.vibe.v2ex.data.datastore.SettingsDataStore
import com.vibe.v2ex.designsystem.V2exTheme
import com.vibe.v2ex.feature.agreement.AgreementScreen
import com.vibe.v2ex.feature.settings.AppIcon
import com.vibe.v2ex.navigation.V2exApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private val incomingDeepLink = MutableStateFlow<Uri?>(null)

    @Inject lateinit var settingsDataStore: SettingsDataStore

    /** 用户在设置里选的桌面图标；切 alias 会结束当前任务，所以攒到 [onStop] 再做。 */
    private var chosenAppIcon: AppIcon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        incomingDeepLink.value = intent?.data
        lifecycleScope.launch {
            settingsDataStore.appIcon.collect { chosenAppIcon = AppIcon.fromName(it) }
        }
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

    override fun onStop() {
        super.onStop()
        chosenAppIcon?.let { AppIcon.sync(this, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink.value = intent.data
    }
}
