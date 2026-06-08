package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PuzzleEntity
import com.example.ui.coach.ChessCoachViewModel
import com.example.ui.coach.defaultOpeningLines
import com.example.ui.coach.OpeningLine
import com.example.ui.components.ChessBoardUi

@Composable
fun TrainingScreen(
    viewModel: ChessCoachViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val puzzles by viewModel.practicePuzzles.collectAsState()
    val activePuzzle = viewModel.selectedPuzzle
    val isCompleted = viewModel.isDailyPuzzleCompleted
    
    var activeTrainingTab by remember { mutableStateOf("puzzles") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1011))
            .padding(16.dp)
            .verticalScroll(scrollState)
            .testTag("training_screen_root"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // --- Segmented Switcher for Training Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF16181A))
                .padding(4.dp)
                .testTag("training_type_switcher"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("puzzles" to "🎯 Puzzles Tactiques", "openings" to "📖 Étude d'Ouvertures")
            for ((tabId, tabName) in tabs) {
                val isSelected = activeTrainingTab == tabId
                val tabBg = if (isSelected) Color(0xFF4B7399) else Color.Transparent
                val tabTextColor = if (isSelected) Color.White else Color.Gray
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tabBg)
                        .clickable { 
                            activeTrainingTab = tabId
                            if (tabId == "puzzles") {
                                // Clear opening study when switching to puzzles
                                viewModel.closeOpeningStudy()
                            } else {
                                // Clear active puzzle when switching to opening
                                viewModel.selectedPuzzle = null
                            }
                        }
                        .padding(vertical = 10.dp)
                        .testTag("tab_item_$tabId"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = tabTextColor
                    )
                }
            }
        }

        if (activeTrainingTab == "puzzles") {
            // ==================== TACTICAL PUZZLES VIEW ====================
            
            // --- WEEKLY PLAN SUMMARY CARD ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("training_plan_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Entraînement Quotidien",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Objectif : 30 minutes de tactique",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Color(0xFF4B7399)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Indicator
                    val progress = if (puzzles.any { it.isCompleted }) 0.45f else 0.1f
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color(0xFF2E7D32),
                        trackColor = Color(0xFF333639),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "15m / 30m complétés",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Progrès : ${(progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- SOLVE ACTIVE PUZZLE SECTION ---
            if (activePuzzle != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF232629)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("active_puzzle_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activePuzzle.isDaily) "🔥 Défi Tactique du Jour" else "🎯 Exercice d'Entraînement",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 15.sp
                            )

                            // Difficulty Tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2E7D32))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activePuzzle.rating} ELO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Active Puzzle Board UI
                        ChessBoardUi(
                            board = viewModel.currentBoardState,
                            selectedSquare = viewModel.selectedSquare,
                            possibleMoves = viewModel.possibleMoves,
                            isWhiteTurn = viewModel.isWhiteTurn,
                            boardTheme = viewModel.reactBoardTheme,
                            onSquareClick = { idx ->
                                viewModel.handleSquareClick(idx, "KEY")
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .shadow(4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Coach/Solving directions bubble
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1A4B7399))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.puzzleProgressMessage,
                                textAlign = TextAlign.Center,
                                color = if (isCompleted) Color(0xFF4CA288) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("puzzle_prompt_status")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Reset buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.resetDailyPuzzle() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3F41)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recommencer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { viewModel.selectedPuzzle = null },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B7399)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("Retour à la liste", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // --- THEMATIC PRACTICAL EXERCISE CARDS LIST ---
            Text(
                text = "Exercices personnalisés (Lichess DB)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (puzzle in puzzles) {
                    PuzzleThematicCard(
                        puzzle = puzzle,
                        isActive = puzzle.id == activePuzzle?.id,
                        onSelect = { viewModel.loadPuzzle(puzzle) }
                    )
                }
            }

        } else {
            // ==================== OPENINGS PRACTICE VIEW ====================
            val activeOpeningId = viewModel.selectedOpeningId
            
            if (activeOpeningId != null) {
                // ACTIVE STUDY SCREEN
                val currentOpening = defaultOpeningLines.find { it.id == activeOpeningId }
                
                if (currentOpening != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF232629)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .testTag("active_opening_study_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Étude : ${currentOpening.name}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            
                            Text(
                                text = "Progrès : Coup ${viewModel.currentOpeningMoveIdx + 1} / ${currentOpening.moves.size}",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Opening Board Component
                            ChessBoardUi(
                                board = viewModel.currentBoardState,
                                selectedSquare = viewModel.selectedSquare,
                                possibleMoves = viewModel.possibleMoves,
                                isWhiteTurn = viewModel.isWhiteTurn,
                                boardTheme = viewModel.reactBoardTheme,
                                onSquareClick = { idx ->
                                    viewModel.handleOpeningSquareClick(idx)
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .shadow(4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Feedback box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E2124))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(text = "🧙‍♂️", fontSize = 24.sp)
                                    Column {
                                        Text(
                                            text = "Coach d'Ouvertures",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFA5C3E6)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = viewModel.openingProgressMessage,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Reset and Back buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.loadOpeningLine(currentOpening) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3F41)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Recommencer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { viewModel.closeOpeningStudy() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B7399)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Text("Quitter l'étude", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                // OPENINGS LIST SCREEN
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📖", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pratique Interactive des Ouvertures",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Entraînez-vous sur les premiers coups théoriques clés. L'assistant IA et le moteur Stockfish.js évaluent instantanément vos coups alternatifs si vous déviez de la théorie !",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    text = "Lignes d'Ouvertures Disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (opening in defaultOpeningLines) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.loadOpeningLine(opening) }
                                .testTag("opening_item_${opening.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF15181C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "♟", fontSize = 20.sp, color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(
                                        text = opening.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = opening.description,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun PuzzleThematicCard(
    puzzle: PuzzleEntity,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF25292E) else Color(0xFF1E2124)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("puzzle_item_${puzzle.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Puzzle status marker icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (puzzle.isCompleted) Color(0xFF2E7D32) else Color(0xFF15181C)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (puzzle.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Complété",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Tactique",
                        tint = Color(0xFF4B7399),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1.0f)) {
                // Puzzle Name Theme
                Text(
                    text = puzzle.themes.replaceFirstChar { it.uppercase() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Difficulté : ${puzzle.rating} ELO",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.DarkGray
            )
        }
    }
}
