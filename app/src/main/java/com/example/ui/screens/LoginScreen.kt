package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.coach.ChessCoachViewModel

@Composable
fun LoginScreen(
    viewModel: ChessCoachViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val isAnalyzing = viewModel.isAnalyzing
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1C1E),
                        Color(0xFF0F1011)
                    )
                )
            )
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
            // Chess Theme Header
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4B7399))
                    .shadow(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Visual beautiful Knight
                Text(
                    text = "♞",
                    fontSize = 60.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FOCUS+",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("app_logo_title")
            )

            Text(
                text = "Analyse de jeu & Coaching IA",
                fontSize = 14.sp,
                color = Color(0xFFA5C3E6),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232629)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connexion Chess Club",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Entrez un pseudo public Lichess pour importer vos statistiques de jeu, ou connectez-vous par OAuth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Username Input Fields
                    OutlinedTextField(
                        value = viewModel.userLoginInput,
                        onValueChange = { viewModel.userLoginInput = it },
                        label = { Text("Pseudo Public Lichess", color = Color.Gray) },
                        placeholder = { Text("Ex: DrNykterstein o El_Kiki", color = Color.DarkGray) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Pseudo",
                                tint = Color(0xFF4B7399)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4B7399),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_field")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = Color(0xFF4B7399),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        // Quick import button
                        Button(
                            onClick = {
                                if (viewModel.userLoginInput.trim().isNotEmpty()) {
                                    viewModel.loginWithLichess(viewModel.userLoginInput) { succeed ->
                                        if (succeed) {
                                            onLoginSuccess()
                                        } else {
                                            errorMsg = "Compte introuvable ou erreur de chargement. Veuillez vérifier le pseudo."
                                        }
                                    }
                                } else {
                                    errorMsg = "Veuillez entrer un pseudo Lichess valide."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B7399)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_button")
                        ) {
                            Text(
                                "Importer Profil & Parties",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Lichess OAuth Simulated button
                        OutlinedButton(
                            onClick = {
                                isAnalyzing == false
                                // Simulate OAuth token retrieval with custom delay
                                viewModel.userLoginInput = "DrNykterstein" // Magnus Carlsen placeholder
                                viewModel.loginWithLichess("DrNykterstein") { succeed ->
                                    if (succeed) onLoginSuccess()
                                }
                            },
                            border = ButtonDefaults.outlinedButtonBorder(true),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("oauth_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "OAuth",
                                tint = Color(0xFFA4C2E6),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Se connecter via Lichess OAuth",
                                color = Color(0xFFA4C2E6),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(visible = errorMsg != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMsg ?: "",
                            color = Color.Red,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Footer features details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤖", fontSize = 24.sp)
                    Text("Coach IA (Gemini)", fontSize = 11.sp, color = Color.LightGray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗣️", fontSize = 24.sp)
                    Text("Voix Humaine (TTS)", fontSize = 11.sp, color = Color.LightGray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📈", fontSize = 24.sp)
                    Text("Analyse Stockfish", fontSize = 11.sp, color = Color.LightGray)
                }
            }
        }
    }
}
