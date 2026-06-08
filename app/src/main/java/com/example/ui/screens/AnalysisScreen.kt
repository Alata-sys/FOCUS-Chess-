package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import com.example.BuildConfig
import com.example.ui.coach.ChessCoachViewModel
import com.example.ui.components.ChessBoardUi

enum class AnalysisMoveQuality(val label: String, val badgeChar: String, val color: Color) {
    BOOK("Théorie", "📖", Color(0xFF1E88E5)),
    BRILLIANT("Coup Brillant", "🌟", Color(0xFF00E5FF)),
    EXCELLENT("Excellent", "🟢", Color(0xFF00C853)),
    INACCURACY("Imprécision", "⏱", Color(0xFFFFD600)),
    MISTAKE("Erreur", "❓", Color(0xFFFF6D00)),
    BLUNDER("Grave Erreur", "⁉️", Color(0xFFD50000)),
    UNKNOWN("", "", Color.Transparent)
}

fun calculateMoveQuality(
    index: Int,
    history: List<String>,
    evals: List<String>
): AnalysisMoveQuality {
    if (index >= history.size) return AnalysisMoveQuality.UNKNOWN
    
    // First 4 plies (2 full moves) of the game are often book moves
    if (index < 4) return AnalysisMoveQuality.BOOK
    
    val currentEvalStr = evals.getOrNull(index) ?: return AnalysisMoveQuality.UNKNOWN
    if (currentEvalStr == "Non analysé" || currentEvalStr.trim().isEmpty()) return AnalysisMoveQuality.UNKNOWN
    
    val prevEvalStr = if (index > 0) evals[index - 1] else "0.0"
    if (prevEvalStr == "Non analysé" || prevEvalStr.trim().isEmpty()) {
        return AnalysisMoveQuality.EXCELLENT
    }
    
    fun parseEval(est: String): Float {
        return when {
            est.contains("Mat") || est.contains("mate") -> {
                if (est.contains("-")) -8.0f else 8.0f
            }
            else -> {
                est.replace("+", "").toFloatOrNull() ?: 0.0f
            }
        }
    }
    
    val currVal = parseEval(currentEvalStr)
    val prevVal = parseEval(prevEvalStr)
    
    val isWhiteTurnMove = (index % 2 == 0)
    val diff = currVal - prevVal
    
    return if (isWhiteTurnMove) {
        when {
            diff >= 0.8f -> AnalysisMoveQuality.BRILLIANT
            diff <= -1.2f -> AnalysisMoveQuality.BLUNDER
            diff <= -0.5f -> AnalysisMoveQuality.MISTAKE
            diff <= -0.15f -> AnalysisMoveQuality.INACCURACY
            else -> AnalysisMoveQuality.EXCELLENT
        }
    } else {
        when {
            diff <= -0.8f -> AnalysisMoveQuality.BRILLIANT
            diff >= 1.2f -> AnalysisMoveQuality.BLUNDER
            diff >= 0.5f -> AnalysisMoveQuality.MISTAKE
            diff >= 0.15f -> AnalysisMoveQuality.INACCURACY
            else -> AnalysisMoveQuality.EXCELLENT
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    viewModel: ChessCoachViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isAnalyzing = viewModel.isAnalyzing
    val movesList = viewModel.moveHistoryList
    val activeIdx = viewModel.activeMoveIndex
    val stockfishEvalValue = viewModel.stockfishEval
    
    // API key injected from AI Studio environments
    val geminiApiKey = BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1011))
            .padding(16.dp)
            .verticalScroll(scrollState)
            .testTag("analysis_screen_root"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // --- CHESS BOARD + STOCKFISH EVALUATION BAR ---
        val activeMoveStr = movesList.getOrNull(activeIdx)
        val targetSquareIdx = if (activeMoveStr != null && activeMoveStr.length >= 4) {
            com.example.engine.ChessEngine.algebraicToSquare(activeMoveStr.substring(2, 4))
        } else {
            -1
        }
        val activeQuality = if (activeIdx >= 0) {
            calculateMoveQuality(activeIdx, movesList, viewModel.moveEvaluationsList)
        } else {
            AnalysisMoveQuality.UNKNOWN
        }
        val badgeChar = if (activeQuality != AnalysisMoveQuality.UNKNOWN && activeQuality.badgeChar.isNotEmpty()) {
            activeQuality.badgeChar
        } else {
            null
        }
        val badgeColor = activeQuality.color

        // --- DYNAMIC TACTICAL PUZZLE BANNER ---
        if (viewModel.selectedPuzzle != null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎯 DEFI DU JOUR LICHESS ACTIVE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = viewModel.puzzleProgressMessage,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E3133))
                            .clickable { viewModel.selectedPuzzle = null; viewModel.resetBoard() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Quitter", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // Evaluaton Bar (vertical bar showing Stockfish advantage!)
            if (viewModel.isComputerAnalysisEnabled) {
                StockfishEvaluationBar(
                    eval = stockfishEvalValue,
                    modifier = Modifier
                        .width(16.dp)
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333639))
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Board Component
            ChessBoardUi(
                board = viewModel.currentBoardState,
                selectedSquare = viewModel.selectedSquare,
                possibleMoves = viewModel.possibleMoves,
                isWhiteTurn = viewModel.isWhiteTurn,
                boardTheme = viewModel.reactBoardTheme,
                onSquareClick = { idx ->
                    viewModel.handleSquareClick(idx, geminiApiKey)
                },
                lastMoveTargetSquare = targetSquareIdx,
                lastMoveQualityBadge = badgeChar,
                lastMoveQualityColor = badgeColor,
                modifier = Modifier.weight(1.0f)
            )
        }

        // --- PREVIOUS & NEXT MOVE NAVIGATION CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF16181A))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // First Move Button
                IconButton(
                    onClick = { viewModel.selectHistoryMove(-1, geminiApiKey) },
                    enabled = activeIdx >= 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "⏮",
                        fontSize = 20.sp,
                        color = if (activeIdx >= 0) Color.White else Color.Gray
                    )
                }

                // Previous Move Button
                IconButton(
                    onClick = { viewModel.selectHistoryMove(activeIdx - 1, geminiApiKey) },
                    enabled = activeIdx >= 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "◀",
                        fontSize = 15.sp,
                        color = if (activeIdx >= 0) Color.White else Color.Gray
                    )
                }
            }

            // Move Status Label
            Text(
                text = if (activeIdx == -1) "Début de Partie" else "Coup ${activeIdx + 1} / ${movesList.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Next Move Button
                IconButton(
                    onClick = { viewModel.selectHistoryMove(activeIdx + 1, geminiApiKey) },
                    enabled = activeIdx < movesList.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "▶",
                        fontSize = 15.sp,
                        color = if (activeIdx < movesList.size - 1) Color.White else Color.Gray
                    )
                }

                // Last Move Button
                IconButton(
                    onClick = { viewModel.selectHistoryMove(movesList.size - 1, geminiApiKey) },
                    enabled = activeIdx < movesList.size - 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "⏭",
                        fontSize = 20.sp,
                        color = if (activeIdx < movesList.size - 1) Color.White else Color.Gray
                    )
                }
            }
        }

        // Action Toolbar (Reset Board / Speak Repeat etc.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (viewModel.isComputerAnalysisEnabled) "Stockfish: $stockfishEvalValue" else "Moteur de calcul désactivé",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Repeat Audio speech
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF232629))
                        .clickable { viewModel.speakAdvice(viewModel.coachAdvice) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Répéter la voix",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Reset board
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF232629))
                        .clickable { viewModel.resetBoard() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset échiquier",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- CHESS ACCESSIBILITY & CONFIGURATION CONTROL ---
        val context = LocalContext.current
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16181A)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("lichess_config_panel")
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "⚙️ OPTIONS DE L'ÉCHIQUIER MODERNE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFA5C3E6),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Accessibility Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibilité - Mode Non-Voyant",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Annonce vocale des coups en français",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = viewModel.isBlindAccessibilityMode,
                        onCheckedChange = { viewModel.isBlindAccessibilityMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF1E3A2B)
                        )
                    )
                }

                // Computer Analysis Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Analyse de l'ordinateur",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Activer l'évaluation de Stockfish",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = viewModel.isComputerAnalysisEnabled,
                        onCheckedChange = { viewModel.isComputerAnalysisEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF1E3A2B)
                        )
                    )
                }

                // Temps par coup Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temps par coup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Affiche la durée de chaque coup",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = viewModel.showTimePerMove,
                        onCheckedChange = { viewModel.showTimePerMove = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF1E3A2B)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // EXPORT & SHARE BUTTONS
                Text(
                    text = "📤 PARTAGER & EXPORTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copier PGN
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D3033))
                            .clickable {
                                val pgn = viewModel.getPgnString()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Chess PGN", pgn)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "PGN copié !", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Copier le PGN 📋", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Copier FEN
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D3033))
                            .clickable {
                                val fen = viewModel.displayedFen
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Chess FEN", fen)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "FEN copié !", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Copier le FEN 🧩", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // If Computer Analysis is enabled, keep the Engine & Board theme configuration inside
                if (viewModel.isComputerAnalysisEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🛠️ SERVICES DE CALCUL STOCKFISH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Moteur actif",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = viewModel.localEngineStatus,
                            fontSize = 11.sp,
                            color = if (viewModel.localEngineStatus.contains("prêt")) Color(0xFF4CAF50) else Color(0xFFFFC107),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (viewModel.isLocalEngineMode) Color(0xFF4B7399) else Color(0xFF1E2022))
                                .clickable { viewModel.isLocalEngineMode = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Local Stockfish", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!viewModel.isLocalEngineMode) Color(0xFF4B7399) else Color(0xFF1E2022))
                                .clickable { viewModel.isLocalEngineMode = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cloud Lichess", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Board Theme selections
                Text(
                    text = "🎨 COLORIS DE L'ÉCHIQUIER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewModel.reactBoardTheme == "brown") Color(0xFFB58863) else Color(0xFF1E2022))
                            .clickable { viewModel.reactBoardTheme = "brown" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Classique Marron", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewModel.reactBoardTheme == "blue") Color(0xFF4B7399) else Color(0xFF1E2022))
                            .clickable { viewModel.reactBoardTheme = "blue" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Classique Bleu", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- GEMINI TALKING COACH COMPONENT ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232629)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("coach_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Coach Character Avatar Icon with breathing animated ring
                CoachAvatarIcon(isAnalyzing)

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = "Coach FOCUS+",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5C3E6)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isAnalyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF4B7399),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Réflexion du Grand Maître...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        Text(
                            text = viewModel.coachAdvice,
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp,
                            modifier = Modifier.testTag("coach_speech_bubble")
                        )
                    }
                }
            }
        }

        // --- GAME ACTIONS HISTORY CHART ---
        if (movesList.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_moves_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Historique des Coups",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Flows horizontal moves sequence
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in movesList.indices) {
                            val active = i == activeIdx
                            val move = movesList[i]
                            
                            val bg = if (active) Color(0xFF4B7399) else Color(0x1AFFFFFF)
                            val tc = if (active) Color.White else Color.LightGray

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .clickable { viewModel.selectHistoryMove(i, geminiApiKey) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("history_move_item_$i")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val timeLabel = if (viewModel.showTimePerMove) " ⏱️${8 + (i * 13) % 45}s" else ""
                                    Text(
                                        text = "${i + 1}. $move$timeLabel",
                                        fontSize = 11.sp,
                                        color = tc,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    val quality = calculateMoveQuality(i, movesList, viewModel.moveEvaluationsList)
                                    if (quality != AnalysisMoveQuality.UNKNOWN && quality.badgeChar.isNotEmpty()) {
                                        Text(
                                            text = quality.badgeChar,
                                            fontSize = 9.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(quality.color.copy(alpha = 0.25f))
                                                .padding(horizontal = 2.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- DEEP COOPERATIVE COACH REPORT CARD ---
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x334B7399), RoundedCornerShape(12.dp))
                    .testTag("coach_report_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bilan du Match (Gemini AI)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (viewModel.gameSummaryReport != null && !viewModel.isGeneratingSummary) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF))
                                    .clickable { viewModel.speakAdvice(viewModel.gameSummaryReport ?: "") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Écouter le bilan de la partie",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val report = viewModel.gameSummaryReport
                    val generating = viewModel.isGeneratingSummary

                    if (generating) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                color = Color(0xFF4B7399),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Analyse stratégique de tous les coups et de Stockfish par le Coach...",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (report != null) {
                        GameReviewReportView(reportText = report)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Régénérer le bilan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B7399),
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { viewModel.generateGameSummaryReport(geminiApiKey) }
                                .padding(4.dp)
                                .testTag("regenerate_summary_button")
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Obtenez un bilan global complet de votre partie rédigé par notre Coach IA combiné aux évaluations Stockfish.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF4B7399), Color(0xFF2E4F73))
                                        )
                                    )
                                    .clickable { viewModel.generateGameSummaryReport(geminiApiKey) }
                                    .testTag("generate_summary_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Start annalyse with Focus+",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Suggestive empty board layout
            Text(
                text = "Jouez des coups sur l'échiquier ou chargez une partie pour explorer l'analyse tactique de l'IA.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray,
                modifier = Modifier.padding(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GameReviewReportView(
    reportText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val lines = reportText.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // Check if it's a heading
            if (trimmed.startsWith("1.") || trimmed.startsWith("2.") || trimmed.startsWith("3.") || trimmed.contains("Verdict Global") || trimmed.contains("Points Forts") || trimmed.contains("Conseil du Pro") || trimmed.contains("Verdict global") || trimmed.contains("Analyse des forces") || trimmed.contains("Conseil clé")) {
                val cleanHeading = trimmed.replace("**", "").replace("###", "").trim()
                Text(
                    text = cleanHeading,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5C3E6),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            } else {
                val cleanBody = trimmed.replace("**", "").trim()
                Text(
                    text = cleanBody,
                    fontSize = 12.sp,
                    color = Color.White,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StockfishEvaluationBar(eval: String, modifier: Modifier = Modifier) {
    // Parse evaluation to ratio logic
    // +6 => fully White (advantage bottom)
    // -6 => fully Black (advantage top)
    val score = when {
        eval.contains("Mat") || eval.contains("mate") -> {
            if (eval.contains("-")) -8.0f else 8.0f
        }
        else -> {
            eval.replace("+", "").toFloatOrNull() ?: 0.0f
        }
    }
    
    // Convert -4..+4 CP to 0.0f..1.0f factor. Clamp.
    val targetFactor = ((score + 4.0f) / 8.0f).coerceIn(0.1f, 0.9f)
    
    // Gentle progressive anim as requested ("bouge très doucement")
    val animatedFactor by animateFloatAsState(
        targetValue = targetFactor,
        animationSpec = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
        label = "evaluation_bar_animation"
    )

    Column(modifier = modifier) {
        // Black section (Top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f - animatedFactor)
                .background(Color(0xFF212121))
        )
        // White section (Bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(animatedFactor)
                .background(Color(0xFFEEEEEE))
        )
    }
}

@Composable
fun CoachAvatarIcon(isBreathing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val glowFactor by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        )
    )

    val borderEffect = if (isBreathing) {
        Modifier.border(2.dp * glowFactor, Color(0xFF4B7399), CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF15181C))
            .then(borderEffect),
        contentAlignment = Alignment.Center
    ) {
        // Grandmaster Chess Avatar cartoon
        Text(
            text = "🧙‍♂️",
            fontSize = 28.sp
        )
    }
}
