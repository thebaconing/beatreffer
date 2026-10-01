package de.simon.beatreffer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import de.simon.beatreffer.ui.CalibrationScreen
import de.simon.beatreffer.ui.HomeScreen
import de.simon.beatreffer.ui.MainViewModel
import de.simon.beatreffer.ui.PracticeScreen
import de.simon.beatreffer.ui.ResultScreen
import de.simon.beatreffer.ui.Screen
import de.simon.beatreffer.ui.SettingsScreen
import de.simon.beatreffer.ui.SplashScreen
import de.simon.beatreffer.ui.StatsScreen
import de.simon.beatreffer.ui.TaktTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaktTheme {
                // Splash nur beim echten Start, nicht nach Wiederherstellung
                var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(Unit) {
                    delay(1200)
                    showSplash = false
                }
                Box(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
                        when (vm.screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.PRACTICE -> PracticeScreen(vm)
                            Screen.RESULT -> ResultScreen(vm)
                            Screen.STATS -> StatsScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                            Screen.CALIBRATION -> CalibrationScreen(vm)
                        }
                    }
                    AnimatedVisibility(visible = showSplash, exit = fadeOut()) { SplashScreen() }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Im Hintergrund nicht weiterklicken: laufende Uebung beenden und auswerten
        if (!isChangingConfigurations) {
            if (vm.screen == Screen.PRACTICE) vm.stopPractice()
            if (vm.screen == Screen.CALIBRATION) vm.open(Screen.SETTINGS)
        }
    }
}
