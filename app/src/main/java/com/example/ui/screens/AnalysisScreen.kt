package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
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

    // Clean and robust rating parser
    class EvalState(
        val isMate: Boolean,
        val mateMoves: Int?, // positive if white has mate, negative if black has mate
        val cpValue: Double // pawns evaluation scale. e.g. 1.2
    )

    val evalScoreStr = stockfishEval
    val parsedEval = remember(evalScoreStr) {
        try {
            val clean = evalScoreStr.trim()
            if (clean.contains("Mat", ignoreCase = true) || 
                clean.contains("Mate", ignoreCase = true) || 
                clean.contains("#", ignoreCase = true) || 
                clean.contains("M", ignoreCase = true)
            ) {
                val numberOnly = clean.filter { it.isDigit() }.toIntOrNull() ?: 3
                val isBlackMate = clean.contains("-") || clean.contains("noir", ignoreCase = true)
                val moves = if (isBlackMate) -numberOnly else numberOnly
                EvalState(isMate = true, mateMoves = moves, cpValue = if (isBlackMate) -9.9 else 9.9)
            } else {
                val doubleVal = clean.replace("+", "").replace(" ", "").toDoubleOrNull() ?: 0.0
                EvalState(isMate = false, mateMoves = null, cpValue = doubleVal)
            }
        } catch (e: Exception) {
            EvalState(isMate = false, mateMoves = null, cpValue = 0.0)
        }
    }

    val (whiteHeight, blackHeight) = remember(parsedEval) {
        val w: Double
        val b: Double
        val cp = parsedEval.cpValue
        if (cp > 0) {
            val wh = 50.0 + cp * 10.0
            w = wh.coerceIn(5.0, 95.0)
            b = 100.0 - w
        } else {
            val bh = 50.0 + Math.abs(cp) * 10.0
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
        // Get active game and profile for badge data
        val games by viewModel.gamesList.collectAsState()
        val profile by viewModel.activeProfile.collectAsState()
        val activeGame = remember(games, viewModel.selectedGameId) { games.find { it.id == viewModel.selectedGameId } }
        val userName = profile?.username ?: ""
        
        val isWhite = activeGame?.whiteUser.equals(userName, ignoreCase = true)
        val opponentName = if (isWhite) activeGame?.blackUser ?: "" else activeGame?.whiteUser ?: ""
        val opponentElo = if (isWhite) activeGame?.blackElo ?: 0 else activeGame?.whiteElo ?: 0
        val userElo = if (isWhite) activeGame?.whiteElo ?: 0 else activeGame?.blackElo ?: 0
        
        // Extract real white and black rating changes
        val whiteRatingChange = activeGame?.whiteRatingDiff
        val blackRatingChange = activeGame?.blackRatingDiff

        val userRatingChange = if (isWhite) whiteRatingChange else blackRatingChange
        val opponentRatingChange = if (isWhite) blackRatingChange else whiteRatingChange

        val oppTop = !viewModel.isBoardFlipped
        
        // --- TOP BADGE ---
        Box(
            modifier = Modifier
                .padding(start = 64.dp, end = 12.dp)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlayerBadge(
                name = if (oppTop) opponentName else userName,
                elo = if (oppTop) opponentElo else userElo,
                ratingDiff = if (oppTop) opponentRatingChange else userRatingChange,
                isUser = !oppTop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- CHESS BOARD & VERTICAL ADVANTAGE BAR ROW (1.05 Aspect Ratio) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
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

                val topLabel = remember(parsedEval) {
                    if (parsedEval.isMate) {
                        val mMoves = parsedEval.mateMoves ?: 3
                        if (mMoves > 0) {
                            "-M$mMoves"
                        } else {
                            "M${Math.abs(mMoves)}"
                        }
                    } else {
                        val negated = -parsedEval.cpValue
                        val capped = negated.coerceIn(-5.0, 5.0)
                        String.format(java.util.Locale.US, "%+.1f", capped)
                    }
                }

                val bottomLabel = remember(parsedEval) {
                    if (parsedEval.isMate) {
                        val mMoves = parsedEval.mateMoves ?: 3
                        if (mMoves > 0) {
                            "M$mMoves"
                        } else {
                            "-M${Math.abs(mMoves)}"
                        }
                    } else {
                        val capped = parsedEval.cpValue.coerceIn(-5.0, 5.0)
                        String.format(java.util.Locale.US, "%+.1f", capped)
                    }
                }

                // Top Label (Côté Noirs - Opposé mathématique)
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
                        color = Color.LightGray,
                        maxLines = 1
                    )
                }

                // Bottom Label (Côté Blancs - Valeur brute)
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
                        color = Color.White,
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
                    modifier = Modifier.fillMaxSize(),
                    isFlipped = viewModel.isBoardFlipped
                )
            }
        }
        
        // --- BOTTOM BADGE ---
        Box(
            modifier = Modifier
                .padding(start = 64.dp, end = 12.dp)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlayerBadge(
                name = if (oppTop) userName else opponentName,
                elo = if (oppTop) userElo else opponentElo,
                ratingDiff = if (oppTop) userRatingChange else opponentRatingChange,
                isUser = oppTop,
                modifier = Modifier.fillMaxWidth()
            )
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

            // --- NAVIGATION CONTROLS WITH LONG-PRESS AUTO-REPEAT ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AutoRepeatButton(
                        enabled = currentIndex >= 0,
                        onClick = {
                            if (currentIndex >= 0) {
                                viewModel.selectHistoryMove(currentIndex - 1, geminiApiKey)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Précédent", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Précédent", fontSize = 12.sp, color = Color.White)
                    }

                    AutoRepeatButton(
                        enabled = currentIndex < movesList.size - 1,
                        onClick = {
                            if (currentIndex < movesList.size - 1) {
                                viewModel.selectHistoryMove(currentIndex + 1, geminiApiKey)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suivant", fontSize = 12.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Suivant", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleBoardFlip() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF1E2124),
                        contentColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("lichess_board_flip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Loop,
                        contentDescription = "Inverser la perspective de l'échiquier",
                        tint = Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- DEEP DISCREET VOICE COACH MODE (NO HOGGING CARDS) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141618))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("voice_coach_indicator"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(650, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x154CA288))
                        .clickable { viewModel.speakAdvice(coachAdvice) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Répéter l'analyse vocale",
                        tint = Color(0xFF4CA288),
                        modifier = Modifier
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- LIGNES CANDIDATES ---
            val candidateLines by viewModel.stockfishJsEngine.candidateLines.collectAsState()
            if (isLocalEngine && candidateLines.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141618)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF4CA288),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lignes Candidates (MultiPV)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        candidateLines.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.LightGray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
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
fun PlayerBadge(
    name: String,
    elo: Int,
    ratingDiff: Int?,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isUser) Color(0xFF4B7399) else Color(0xFF40444B)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($elo)",
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        if (ratingDiff != null && ratingDiff != 0) {
            Spacer(modifier = Modifier.width(6.dp))
            val color = if (ratingDiff > 0) Color(0xFF4CA288) else Color(0xFFE53935)
            Text(
                text = "${if (ratingDiff > 0) "+" else ""}$ratingDiff",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
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

@Composable
fun AutoRepeatButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            while (true) {
                onClick()
                delay(200) // 5 plies per second for fluid auto-forward and backward scrolling
            }
        }
    }

    val containerColor = if (enabled) {
        if (isPressed) Color(0xFF32363C) else Color(0xFF1E2124)
    } else {
        Color(0xFF121415)
    }

    val contentColor = if (enabled) Color.White else Color.Gray

    Box(
        modifier = modifier
            .height(48.dp) // Accessibility compliant touch targets >= 48.dp
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

