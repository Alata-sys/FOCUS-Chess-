package com.example

import android.content.Intent
import android.util.Log
import android.os.Bundle
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.coach.ChessCoachViewModel
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.TrainingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screens.GlobalSplashScreen
import kotlinx.coroutines.delay

enum class Screen {
    DASHBOARD,
    ANALYSIS,
    TRAINING
}

class MainActivity : ComponentActivity() {
    
    // Modern Constructor viewModels delegation
    private val viewModel: ChessCoachViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && "focusplus" == uri.scheme && "oauth" == uri.host) {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                Log.d("MainActivity", "Inbound Lichess OAuth request received with code")
                viewModel.handleOAuthCallback(code) { success ->
                    if (success) {
                        Log.d("MainActivity", "Successfully logged in via Lichess OAuth!")
                    } else {
                        Log.e("MainActivity", "Failed to login via Lichess OAuth!")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure WebView directories exist to prevent chromium cache enumeration errors
        try {
            val jsCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsCacheDir.exists()) {
                jsCacheDir.mkdirs()
            }
            if (!wasmCacheDir.exists()) {
                wasmCacheDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to pre-create WebView cache directories: ${e.message}")
        }

        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MyApplicationTheme {
                var showGlobalSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(3000)
                    showGlobalSplash = false
                }

                AnimatedContent(
                    targetState = showGlobalSplash,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(600))
                    },
                    label = "GlobalSplashTransition"
                ) { isSplash ->
                    if (isSplash) {
                        GlobalSplashScreen(modifier = Modifier.fillMaxSize())
                    } else {
                        val profile by viewModel.activeProfile.collectAsState()
                        
                        // Keep tracks of active screen navigation
                        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

                        if (profile == null) {
                            // Force login if not authenticated
                            LoginScreen(
                                viewModel = viewModel,
                                onLoginSuccess = {
                                    currentScreen = Screen.DASHBOARD
                                },
                                skipIntro = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Authenticated Application Shell
                            Scaffold(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0F1011))
                                    .windowInsetsPadding(WindowInsets.statusBars),
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = Color(0xFF16181A),
                                        tonalElevation = 8.dp,
                                        modifier = Modifier
                                            .windowInsetsPadding(WindowInsets.navigationBars)
                                            .testTag("app_navigation_bar")
                                    ) {
                                        NavigationBarItem(
                                            selected = currentScreen == Screen.DASHBOARD,
                                            onClick = { currentScreen = Screen.DASHBOARD },
                                            label = { Text("Stats", fontSize = 11.sp) },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Leaderboard,
                                                    contentDescription = "Stats"
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = Color.White,
                                                unselectedIconColor = Color.Gray,
                                                unselectedTextColor = Color.Gray,
                                                indicatorColor = Color(0xFF4B7399)
                                            ),
                                            modifier = Modifier.testTag("nav_item_dashboard")
                                        )

                                        NavigationBarItem(
                                            selected = currentScreen == Screen.ANALYSIS,
                                            onClick = { currentScreen = Screen.ANALYSIS },
                                            label = { Text("Coach IA", fontSize = 11.sp) },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = "Analyse"
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = Color.White,
                                                unselectedIconColor = Color.Gray,
                                                unselectedTextColor = Color.Gray,
                                                indicatorColor = Color(0xFF4B7399)
                                            ),
                                            modifier = Modifier.testTag("nav_item_analysis")
                                        )

                                        NavigationBarItem(
                                            selected = currentScreen == Screen.TRAINING,
                                            onClick = { currentScreen = Screen.TRAINING },
                                            label = { Text("Puzzles", fontSize = 11.sp) },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Extension,
                                                    contentDescription = "Entraînement"
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Color.White,
                                                selectedTextColor = Color.White,
                                                unselectedIconColor = Color.Gray,
                                                unselectedTextColor = Color.Gray,
                                                indicatorColor = Color(0xFF4B7399)
                                            ),
                                            modifier = Modifier.testTag("nav_item_training")
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                // Smooth slide / fade Content screen swaps
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .background(Color(0xFF0F1011))
                                ) { targetScreen ->
                                    when (targetScreen) {
                                        Screen.DASHBOARD -> DashboardScreen(
                                            viewModel = viewModel,
                                            onNavigateToAnalysis = {
                                                currentScreen = Screen.ANALYSIS
                                            }
                                        )
                                        Screen.ANALYSIS -> AnalysisScreen(
                                            viewModel = viewModel
                                        )
                                        Screen.TRAINING -> TrainingScreen(
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
