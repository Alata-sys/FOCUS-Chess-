package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChessGameEntity
import com.example.ui.coach.ChessCoachViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.BuildConfig

@Composable
fun DashboardScreen(
    viewModel: ChessCoachViewModel,
    onNavigateToAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.activeProfile.collectAsState()
    val games by viewModel.gamesList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchDashboardInsights(BuildConfig.GEMINI_API_KEY)
    }

    val safeProfile = profile ?: return

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1011))
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen_root")
    ) {
        // --- PROFILE HEADER CARD ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag("profile_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4B7399)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = safeProfile.username.take(1).uppercase(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = safeProfile.username,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.testTag("username_header")
                        )
                        Text(
                            text = "Abonné FOCUS+",
                            fontSize = 12.sp,
                            color = Color(0xFF4CA288),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Logout Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF))
                            .clickable { viewModel.logout() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Déconnexion",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // --- SUBSTATS SUMMARY ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EloCounterCard(label = "Blitz", elo = safeProfile.blitzElo, color = Color(0xFFE53935), modifier = Modifier.weight(1f))
                EloCounterCard(label = "Rapide", elo = safeProfile.rapidElo, color = Color(0xFF43A047), modifier = Modifier.weight(1f))
                EloCounterCard(label = "Bullet", elo = safeProfile.bulletElo, color = Color(0xFF1E88E5), modifier = Modifier.weight(1f))
            }
        }

        // --- WIN/LOSS RATIO PIE CHART & TIMELINE CHART ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("stats_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ratios de Victoires & Statistiques",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ModernWinLossStats(
                        wins = safeProfile.winCount,
                        losses = safeProfile.lossCount,
                        draws = safeProfile.drawCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- CUSTOM BEZIER GRAPHIC (ELO PROGRESSION OVER TIME) ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .testTag("elo_growth_card")
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
                        Text(
                            text = "Courbe d'Évolution ELO",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = Color(0xFF4B7399)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Extract last ratings for progression curve points
                    val points = games.take(8).map { it.ratingDiff.toFloat() }.reversed()
                    EloProgressionLineChart(
                        points = points,
                        baseElo = safeProfile.blitzElo.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        }

        // --- TACTICAL INSIGHTS CARD ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "💡 Analyse Tactique (Gemini)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2B65C)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (viewModel.isFetchingInsights) {
                        Text("Analyse en cours...", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        Text(
                            text = viewModel.dashboardInsights ?: "Aucune information.",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- RECENT GAMES ROW TABLE HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parties Récentes (Lichess)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "${games.size} chargées",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // --- 30 LES RECENT GAMES CHEVRONS ---
        items(games.take(30)) { game ->
            GameItemRow(
                game = game,
                username = safeProfile.username,
                onAnalyze = {
                    viewModel.loadGameForAnalysis(game)
                    onNavigateToAnalysis()
                },
                onOpenLichess = {
                    val url = "https://lichess.org/${game.id}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EloCounterCard(label: String, elo: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
        modifier = modifier.testTag("elo_card_$label")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
                    .align(Alignment.End)
            )
            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("$elo ELO", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun ModernWinLossStats(wins: Int, losses: Int, draws: Int, modifier: Modifier = Modifier) {
    val total = (wins + losses + draws).toFloat()
    val winPct = if (total > 0) (wins / total * 100) else 0f
    val lossPct = if (total > 0) (losses / total * 100) else 0f
    val drawPct = if (total > 0) (draws / total * 100) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Horizontal segmented progress bar (highly modern, like Lichess profile stats)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Distribution des Résultats",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )

            // Segmented Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2F33))
            ) {
                if (total == 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aucune donnée", fontSize = 11.sp, color = Color.White)
                    }
                } else {
                    if (wins > 0) {
                        Box(
                            modifier = Modifier
                                .weight(wins.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFF4CA288)), // Emerald green
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${winPct.toInt()}% V",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    if (draws > 0) {
                        Box(
                            modifier = Modifier
                                .weight(draws.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFF8A949C)), // Lichess Grey
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${drawPct.toInt()}% N",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    if (losses > 0) {
                        Box(
                            modifier = Modifier
                                .weight(losses.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFFE53935)), // Web Red
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${lossPct.toInt()}% D",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Circular Donut/Ring Chart with centered statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (total == 0f) {
                        drawCircle(color = Color.Gray, radius = size.minDimension / 2, style = Stroke(width = 12f))
                        return@Canvas
                    }

                    val winAngle = (wins / total) * 360f
                    val lossAngle = (losses / total) * 360f
                    val drawAngle = (draws / total) * 360f

                    val strokeWidth = 14f
                    val pad = strokeWidth / 2f
                    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                    // Draw Wins Ring Segment
                    drawArc(
                        color = Color(0xFF4CA288),
                        startAngle = -90f,
                        sweepAngle = winAngle,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = rectSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Draws Ring Segment
                    drawArc(
                        color = Color(0xFF8A949C),
                        startAngle = -90f + winAngle,
                        sweepAngle = drawAngle,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = rectSize,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Losses Ring Segment
                    drawArc(
                        color = Color(0xFFE53935),
                        startAngle = -90f + winAngle + drawAngle,
                        sweepAngle = lossAngle,
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = rectSize,
                        style = Stroke(width = strokeWidth)
                    )
                }

                // Centered statistics labels inside the ring!
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WINRATE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "${String.format("%.1f", winPct)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "${total.toInt()} parties",
                        fontSize = 9.sp,
                        color = Color.LightGray
                    )
                }
            }

            // Legend indicators
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                LegendItem(label = "Victoires", count = wins, color = Color(0xFF4CA288))
                LegendItem(label = "Nulles (Draws)", count = draws, color = Color(0xFF8A949C))
                LegendItem(label = "Défaites", count = losses, color = Color(0xFFE53935))
            }
        }
    }
}

@Composable
fun LegendItem(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("$label : ", fontSize = 12.sp, color = Color.Gray)
        Text(count.toString(), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EloProgressionLineChart(points: List<Float>, baseElo: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) {
            // Placeholder line
            drawLine(
                color = Color(0xFF4B7399),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3f
            )
            return@Canvas
        }

        val minVal = points.minOrNull() ?: baseElo
        val maxVal = points.maxOrNull() ?: baseElo
        val range = (maxVal - minVal).coerceAtLeast(50f)

        val widthStep = size.width / (points.size - 1)
        
        val drawPath = Path()
        
        for (i in points.indices) {
            val rating = points[i]
            val x = i * widthStep
            val y = size.height - ((rating - minVal) / range) * (size.height - 20f) - 10f
            
            if (i == 0) {
                drawPath.moveTo(x, y)
            } else {
                val prevX = (i - 1) * widthStep
                val prevRating = points[i - 1]
                val prevY = size.height - ((prevRating - minVal) / range) * (size.height - 20f) - 10f
                // Smooth bezier cubic curve points
                drawPath.cubicTo(
                    (prevX + x) / 2f, prevY,
                    (prevX + x) / 2f, y,
                    x, y
                )
            }
            
            // Highlight node spots
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(x, y)
            )
        }

        // Render Bezier Line with glowing ambient stroke
        drawPath(
            path = drawPath,
            color = Color(0xFF4B7399),
            style = Stroke(width = 4f)
        )
    }
}

@Composable
fun GameItemRow(
    game: ChessGameEntity,
    username: String,
    onAnalyze: () -> Unit,
    onOpenLichess: () -> Unit
) {
    val isWhite = game.whiteUser.equals(username, ignoreCase = true)
    val myResult = if (game.winner == "draw") {
        "D"
    } else if ((game.winner == "white" && isWhite) || (game.winner == "black" && !isWhite)) {
        "V"
    } else {
        "N"
    }

    val statusColor = when (myResult) {
        "V" -> Color(0xFF2E7D32) // Win (Green)
        "D" -> Color(0xFFEF6C00) // Draw (Orange)
        else -> Color(0xFFC62828) // Loss (Red)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2124)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_item_${game.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Outcome circular circle tag
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = myResult,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Opponent Name
                val opponent = if (isWhite) game.blackUser else game.whiteUser
                val oppElo = if (isWhite) game.blackElo else game.whiteElo
                Text(
                    text = "$opponent ($oppElo)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.cadence.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ID: ${game.id}",
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }
            }

            // Quick Actions: Analyse (Coach IA) and Lichess link
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Lichess",
                    color = Color(0xFFA5C3E6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onOpenLichess() }
                        .padding(8.dp)
                )

                Button(
                    onClick = { onAnalyze() },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B7399)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Analyser", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
