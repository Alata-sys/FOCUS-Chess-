package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.coach.ChessCoachViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

enum class SplashPhase {
    INTRO,        // Phase 1: Pure black, logo fades in and scale zoom
    FORM,         // Phase 2: Simple text input form for Pseudo
    FADE_OUT      // Phase 3: Transition to main game app
}

@Composable
fun LoginScreen(
    viewModel: ChessCoachViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val isAnalyzing = viewModel.isAnalyzing
    val scrollState = rememberScrollState()

    // Screen phases & flow
    var currentPhase by remember { mutableStateOf(SplashPhase.INTRO) }

    // Fade logo & scale anims during Phase 1
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }

    // Start introductory animation trigger
    LaunchedEffect(Unit) {
        val scaleJob = this.async {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
        val alphaJob = this.async {
            logoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(1000)
            )
        }
        scaleJob.await()
        alphaJob.await()
        delay(1200) // Delay to let the branding settle beautifully
        currentPhase = SplashPhase.FORM
    }

    // Offset logo vertically upwards to clear space for form
    val targetOffset = if (currentPhase == SplashPhase.INTRO) 0.dp else (-40).dp
    val logoOffset by animateDpAsState(targetValue = targetOffset, animationSpec = tween(800, easing = FastOutSlowInEasing))

    val targetAlpha = if (currentPhase == SplashPhase.FADE_OUT) 0f else 1f
    val fadeOutAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(600))

    // Handle transition to main screen
    LaunchedEffect(currentPhase) {
        if (currentPhase == SplashPhase.FADE_OUT) {
            delay(700)
            onLoginSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0D0E),
                        Color(0xFF050607)
                    )
                )
            )
            .graphicsLayer(alpha = fadeOutAlpha)
            .testTag("login_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- HEADER: LOGO AREA ---
            Box(
                modifier = Modifier
                    .offset(y = logoOffset)
                    .scale(logoScale.value)
                    .animateContentSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF4B7399),
                                        Color(0xFF233649)
                                    )
                                )
                            )
                            .shadow(12.dp, CircleShape)
                            .border(2.dp, Color(0xFF5E8CBA), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 62.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.scale(if (currentPhase == SplashPhase.INTRO) logoAlpha.value else 1.0f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "FOCUS+",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 38.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp,
                        modifier = Modifier.testTag("app_logo_title")
                    )

                    Text(
                        text = "ANALYSE DE JEU & COACHING IA",
                        fontSize = 12.sp,
                        color = Color(0xFFA5C3E6),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- PHASE FORM ---
            AnimatedVisibility(
                visible = currentPhase == SplashPhase.FORM,
                enter = fadeIn(animationSpec = tween(600)) + expandVertically(),
                exit = fadeOut(animationSpec = tween(400)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181A)),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF4B7399), Color(0x334B7399))
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "Bienvenue sur FOCUS+",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive Tab Switcher
                            var isOAuthTab by remember { mutableStateOf(true) }
                            val context = androidx.compose.ui.platform.LocalContext.current

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F1011))
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isOAuthTab) Color(0xFF4B7399) else Color.Transparent)
                                        .clickable { isOAuthTab = true }
                                        .padding(vertical = 10.dp)
                                        .testTag("tab_oauth"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isOAuthTab) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "OAuth2 Sécurisé",
                                            color = if (isOAuthTab) Color.White else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isOAuthTab) Color(0xFF4B7399) else Color.Transparent)
                                        .clickable { isOAuthTab = false }
                                        .padding(vertical = 10.dp)
                                        .testTag("tab_username"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (!isOAuthTab) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pseudo Public",
                                            color = if (!isOAuthTab) Color.White else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isOAuthTab) {
                                // --- OAUTH FLOW PANEL ---
                                Text(
                                    text = "Connectez-vous en toute sécurité via Lichess. Cette méthode protège vos données et garantit un accès instantané à votre historique de parties complet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                var showAdvancedOAuth by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdvancedOAuth = !showAdvancedOAuth }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reglages Client ID (Optionnel/UCI)",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (showAdvancedOAuth) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = viewModel.customClientId,
                                        onValueChange = { viewModel.customClientId = it },
                                        label = { Text("Lichess Client ID", color = Color.Gray) },
                                        placeholder = { Text("focus-plus-chess-coach", color = Color.DarkGray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF4B7399),
                                            unfocusedBorderColor = Color.DarkGray
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("oauth_client_id_field")
                                    )
                                    Text(
                                        text = "Par défaut, l'application utilise 'focus-plus-chess-coach'. Pour enregistrer votre propre ID applicatif UCI Lichess, configurez en redirection : focusplus://oauth",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                if (isAnalyzing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF4B7399))
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val configuredClientId = viewModel.customClientId.ifBlank { "focus-plus-chess-coach" }
                                            viewModel.startLichessOAuth(context, configuredClientId)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4B7399)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("oauth_login_button")
                                    ) {
                                        Text(
                                            text = "Se connecter avec Lichess ♟️",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            } else {
                                // --- LEGACY PUBLIC USERNAME FLOW PANEL ---
                                Text(
                                    text = "Entrez votre Pseudo de joueur pour charger publiquement vos parties sans connexion par mot de passe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = viewModel.userLoginInput,
                                    onValueChange = { viewModel.userLoginInput = it },
                                    label = { Text("Pseudo du joueur", color = Color.Gray) },
                                    placeholder = { Text("Exemple: LanceurTactique", color = Color.DarkGray) },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "User Icon",
                                            tint = Color(0xFF4B7399)
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF4B7399),
                                        unfocusedBorderColor = Color.DarkGray
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("username_field")
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                if (isAnalyzing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF4B7399)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val input = viewModel.userLoginInput.trim()
                                            if (input.isNotEmpty()) {
                                                viewModel.loginWithLichess(input) { succeed ->
                                                    if (succeed) {
                                                        currentPhase = SplashPhase.FADE_OUT
                                                    } else {
                                                        errorMsg = "Joueur introuvable ou erreur réseau. Essayer un autre pseudo."
                                                    }
                                                }
                                            } else {
                                                errorMsg = "Veuillez entrer un pseudo avant de continuer."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4B7399)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("import_button")
                                    ) {
                                        Text(
                                            text = "C'est parti ! 🚀",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = errorMsg != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = errorMsg ?: "",
                                    color = Color(0xFFEF9A9A),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

