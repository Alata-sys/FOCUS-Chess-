package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.coach.ChessCoachViewModel
import com.example.ui.components.ChessBoardUi

@Composable
fun AnalysisScreen(
    viewModel: ChessCoachViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val geminiApiKey = BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"

    val movesList = viewModel.moveHistoryList
    val currentIndex = viewModel.activeMoveIndex
    val isAnalyzing = viewModel.isAnalyzing
    val coachAdvice = viewModel.coachAdvice
    val stockfishEval = viewModel.stockfishEval
    val isLocalEngine = viewModel.isLocalEngineMode

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            val targetIndex = currentIndex + 1
            if (targetIndex < movesList.size + 1) {
                lazyListState.animateScrollToItem(targetIndex)
            }
        } else {
            lazyListState.animateScrollToItem(0)
        }
    }

    val evalScoreStr = stockfishEval
    val evalDouble = remember(evalScoreStr) {
        try {
            if (evalScoreStr.contains("Mat") || evalScoreStr.contains("Mate")) {
                if (evalScoreStr.contains("-")) -9.9 else 9.9
            } else {
                evalScoreStr.replace("+", "").replace(" ", "").toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    val (whiteHeight, blackHeight) = remember(evalDouble) {
        val w: Double
        val b: Double
        if (evalDouble > 0) {
            val wh = 50.0 + evalDouble * 10.0
            w = wh.coerceIn(5.0, 95.0)
            b = 100.0 - w
        } else {
            val bh = 50.0 + Math.abs(evalDouble) * 10.0
            b = bh.coerceIn(5.0, 95.0)
            w = 100.0 - b
        }
        w.toFloat() to b.toFloat()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1011))
            .testTag("analysis_screen_root")
    ) {
        // --- CHESS BOARD & VERTICAL ADVANTAGE BAR ROW (1.05 Aspect Ratio) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .aspectRatio(1.1f)
                .background(Color.Black),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- VERTICAL ADVANTAGE BAR (GAUCHE) ---
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // The actual bar
                Column(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF313338))
                        .border(1.2.dp, Color(0xFF1E1F22), RoundedCornerShape(6.dp))
                ) {
                    // Black advantage segment (Top)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(blackHeight)
                            .background(Color(0xFF313338))
                    )
                    // White advantage segment (Bottom)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(whiteHeight)
                            .background(Color.White)
                    )
                }

                // Format values for display: Top is White (evalDouble), Bottom is Black (-evalDouble)
                val topLabel = if (evalDouble >= 0) "+${String.format(java.util.Locale.US, "%.1f", evalDouble)}" else String.format(java.util.Locale.US, "%.1f", evalDouble)
                val bottomLabel = if (-evalDouble >= 0) "+${String.format(java.util.Locale.US, "%.1f", -evalDouble)}" else String.format(java.util.Locale.US, "%.1f", -evalDouble)

                // Top Label (Côté Blancs - Valeur brute)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = topLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // Bottom Label (Côté Noirs - Opposé mathématique)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = bottomLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        maxLines = 1
                    )
                }
            }

            // --- CHESS BOARD (1:1 Aspect Ratio) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.0f),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardUi(
                    board = viewModel.currentBoardState,
                    selectedSquare = viewModel.selectedSquare,
                    possibleMoves = viewModel.possibleMoves,
                    isWhiteTurn = viewModel.isWhiteTurn,
                    boardTheme = viewModel.reactBoardTheme,
                    onSquareClick = { idx ->
                        viewModel.handleSquareClick(idx, geminiApiKey)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- MAIN SCROLL CONTAINER ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- MOVES TIMELINE CAROUSEL ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📜 Déroulement de la Partie",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "Étape ${currentIndex + 1} / ${movesList.size}",
                        fontSize = 11.sp,
                        color = Color(0xFF4B7399),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF16181A))
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                        .testTag("historical_moves_carousel"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start of game item
                    item {
                        MoveChip(
                            label = "Début",
                            isSelected = currentIndex == -1,
                            onClick = { viewModel.selectHistoryMove(-1, geminiApiKey) }
                        )
                    }

                    itemsIndexed(movesList) { index, move ->
                        val moveNumberStr = if (index % 2 == 0) "${(index / 2) + 1}." else ""
                        MoveChip(
                            label = "$moveNumberStr $move",
                            isSelected = currentIndex == index,
                            onClick = { viewModel.selectHistoryMove(index, geminiApiKey) }
                        )
                    }
                }
            }

            // --- NAVIGATION CONTROLS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (currentIndex >= 0) {
                            viewModel.selectHistoryMove(currentIndex - 1, geminiApiKey)
                        }
                    },
                    enabled = currentIndex >= 0,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E2124),
                        disabledContainerColor = Color(0xFF121415)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Precedent", fontSize = 12.sp, color = Color.White)
                }

                Button(
                    onClick = {
                        if (currentIndex < movesList.size - 1) {
                            viewModel.selectHistoryMove(currentIndex + 1, geminiApiKey)
                        }
                    },
                    enabled = currentIndex < movesList.size - 1,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E2124),
                        disabledContainerColor = Color(0xFF121415)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Suivant", fontSize = 12.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Suivant", tint = Color.White)
                }
            }

            // --- COACH ADVICE CARD ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coach_advice_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Coach Label and Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x224B7399)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🧙‍♂️", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Coach Assistant FOCUS+",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Données Cloud Lichess + Gemini",
                                    color = Color(0xFF4CA288),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Action Panel (TTS Speak, Re-evaluate)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledIconButton(
                                onClick = { viewModel.speakAdvice(coachAdvice) },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x1BFFFFFF)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "Écouter l'analyse",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    val activeMoveCode = movesList.getOrNull(currentIndex) ?: "début"
                                    viewModel.selectHistoryMove(currentIndex, geminiApiKey)
                                },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x154B7399)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Loop,
                                    contentDescription = "Réévaluer",
                                    tint = Color(0xFFA5C3E6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isAnalyzing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF4B7399),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Calcul de l'évaluation Lichess & Rédaction du rapport IA...",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Display Score badge
                        val isPositive = stockfishEval.startsWith("+")
                        val badgeBg = if (isPositive) Color(0x1E4CA288) else Color(0x1EC62828)
                        val badgeColor = if (isPositive) Color(0xFF4CA288) else Color(0xFFEF9A9A)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Évaluation : $stockfishEval",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Styled Markdown Advice representation
                        Text(
                            text = coachAdvice,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.testTag("coach_advice_text")
                        )
                    }
                }
            }

            // --- SECTOR SETTINGS & UTILS PANEL ---
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141618)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = isLocalEngine.let { if (it) "Moteur Local (Web Worker) actif" else "Moteur Lichess Cloud actif" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }

                    Switch(
                        checked = isLocalEngine,
                        onCheckedChange = { viewModel.toggleEngineMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4B7399),
                            checkedTrackColor = Color(0x334B7399)
                        ),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun MoveChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) Color(0xFF4B7399) else Color(0xFF2C2F33)
    val contentColor = if (isSelected) Color.White else Color.LightGray

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
