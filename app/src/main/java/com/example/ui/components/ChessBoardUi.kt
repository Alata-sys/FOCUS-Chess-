package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImagePainter
import com.example.engine.ChessEngine

@Composable
fun ChessBoardUi(
    board: CharArray,
    selectedSquare: Int,
    possibleMoves: List<Int>,
    isWhiteTurn: Boolean,
    boardTheme: String, // "brown" or "blue" to match lichess
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lastMoveTargetSquare: Int = -1,
    lastMoveQualityBadge: String? = null,
    lastMoveQualityColor: Color = Color.Transparent
) {
    // --- Lichess Color Theme Palettes ---
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    val lightSquareColor = if (boardTheme == "blue") Color(0xFFDEE3E6) else Color(0xFFF0D9B5)
    val darkSquareColor = if (boardTheme == "blue") Color(0xFF8CA2AD) else Color(0xFFB58863)
    val labelLightColor = if (boardTheme == "blue") Color(0xFF8CA2AD) else Color(0xFFB58863)
    val labelDarkColor = if (boardTheme == "blue") Color(0xFFDEE3E6) else Color(0xFFF0D9B5)

    // Lichess Translucent Yellow Highlight Color for Selected and Active Pieces
    val selectionColor = Color(0x90BAC141)

    val whiteKingInCheck = remember(board) { ChessEngine.isKingInCheck(board, true) }
    val blackKingInCheck = remember(board) { ChessEngine.isKingInCheck(board, false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = darkSquareColor),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(6.dp)
            .testTag("chess_board_card")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0..7) {
                Row(modifier = Modifier.weight(1f)) {
                    for (c in 0..7) {
                        val squareIdx = r * 8 + c
                        val piece = board[squareIdx]
                        val isDarkSquare = (r + c) % 2 == 1
                        
                        val squareBaseColor = if (isDarkSquare) darkSquareColor else lightSquareColor
                        val isSelected = selectedSquare == squareIdx
                        val isPossible = squareIdx in possibleMoves
                        val isKingInCheckSquare = (piece == 'K' && whiteKingInCheck) || (piece == 'k' && blackKingInCheck)
                        
                        // Apply selection highlight layer, or check highlight, on top of base square color
                        val displayColor = when {
                            isKingInCheckSquare -> Color(0xFFD32F2F) // Lichess red glow for check alerts!
                            isSelected -> selectionColor
                            else -> squareBaseColor
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(displayColor)
                                .clickable { onSquareClick(squareIdx) }
                                .semantics {
                                    val pieceName = if (piece != '.') {
                                        val color = if (ChessEngine.isWhitePiece(piece)) "Blanc" else "Noir"
                                        "${ChessEngine.getPieceName(piece)} $color"
                                    } else "Case vide"
                                    contentDescription = "$pieceName en ${ChessEngine.getSquareAlgebraic(r, c)}"
                                }
                                .testTag("square_${r}_${c}")
                        ) {
                            // --- File Numbers (8 to 1) on Left Column (c == 0) ---
                            if (c == 0) {
                                Text(
                                    text = (8 - r).toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkSquare) labelDarkColor else labelLightColor,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 3.dp, top = 2.dp)
                                )
                            }

                            // --- Rank Letters (a to h) on Bottom Row (r == 7) ---
                            if (r == 7) {
                                Text(
                                    text = ('a' + c).toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkSquare) labelDarkColor else labelLightColor,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 4.dp, bottom = 2.dp)
                                )
                            }

                            // --- Render Lichess Cburnett Style Pieces ---
                            if (piece != '.') {
                                val pieceName = getPieceImageName(piece)
                                val pieceUrl = "https://lichess1.org/assets/piece/cburnett/$pieceName.svg"

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.85f)
                                        .align(Alignment.Center)
                                ) {
                                    val painter = rememberAsyncImagePainter(
                                        model = pieceUrl,
                                        imageLoader = imageLoader
                                    )
                                    val painterState = painter.state

                                    if (painterState is AsyncImagePainter.State.Error) {
                                        // Backup Unicode text renderer if network fails or offline
                                        val isWhitePiece = ChessEngine.isWhitePiece(piece)
                                        val unicodeGlyph = when (piece.lowercaseChar()) {
                                            'p' -> "♟"
                                            'n' -> "♞"
                                            'b' -> "♝"
                                            'r' -> "♜"
                                            'q' -> "♛"
                                            'k' -> "♚"
                                            else -> ""
                                        }
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = unicodeGlyph,
                                                fontSize = 32.sp,
                                                color = if (isWhitePiece) Color.White else Color(0xFF1F1F1F),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        Image(
                                            painter = painter,
                                            contentDescription = ChessEngine.getPieceName(piece).toString(),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            // --- Elegant Legal Move Indicator Dot/Ring ---
                            if (isPossible) {
                                if (piece == '.') {
                                    // Empty square target dot
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.Center)
                                            .clip(CircleShape)
                                            .background(Color(0x40000000)) // Soft matching Lichess translucent black dot
                                    )
                                } else {
                                    // Target capture ring enclosing the piece
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .align(Alignment.Center)
                                            .clip(CircleShape)
                                            .background(Color(0x30165500)) // Translucent light dark forest ring
                                    )
                                }
                            }

                            // --- Elegant Move Quality Badge ---
                            if (squareIdx == lastMoveTargetSquare && lastMoveQualityBadge != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(lastMoveQualityColor)
                                        .border(1.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lastMoveQualityBadge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
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

/**
 * Returns the exact filename mapping for Cburnett theme on Lichess
 */
private fun getPieceImageName(piece: Char): String {
    return when (piece) {
        'P' -> "wP"
        'N' -> "wN"
        'B' -> "wB"
        'R' -> "wR"
        'Q' -> "wQ"
        'K' -> "wK"
        'p' -> "bP"
        'n' -> "bN"
        'b' -> "bB"
        'r' -> "bR"
        'q' -> "bQ"
        'k' -> "bK"
        else -> ""
    }
}
