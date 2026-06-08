package com.example.engine

/**
 * Representation of a Chess Game state validator and FEN utility.
 * Handles parsing, executing basic moves, tracking scores, and highlighting.
 */
object ChessEngine {

    val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parseFen(fen: String): CharArray {
        val board = CharArray(64) { '.' }
        val parts = fen.trim().split(" ")
        if (parts.isEmpty()) return board
        val rows = parts[0].split("/")
        for (r in 0..7) {
            if (r >= rows.size) break
            val rowStr = rows[r]
            var col = 0
            for (char in rowStr) {
                if (char.isDigit()) {
                    val emptySquares = char.toString().toInt()
                    col += emptySquares
                } else {
                    if (col < 8) {
                        board[r * 8 + col] = char
                        col++
                    }
                }
            }
        }
        return board
    }

    fun toFen(board: CharArray, isWhiteTurn: Boolean): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var emptyCount = 0
            for (c in 0..7) {
                val piece = board[r * 8 + c]
                if (piece == '.') {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece)
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (r < 7) {
                sb.append("/")
            }
        }
        sb.append(if (isWhiteTurn) " w " else " b ")
        // Default suffixes for simplicity
        sb.append("KQkq - 0 1")
        return sb.toString()
    }

    fun isWhitePiece(piece: Char): Boolean = piece.isUpperCase()
    fun isBlackPiece(piece: Char): Boolean = piece.isLowerCase()

    fun getPieceName(piece: Char): String {
        return when (piece.lowercaseChar()) {
            'p' -> "Pion"
            'r' -> "Tour"
            'n' -> "Cavalier"
            'b' -> "Fou"
            'q' -> "Dame"
            'k' -> "Roi"
            else -> ""
        }
    }

    fun getSquareAlgebraic(row: Int, col: Int): String {
        if (row !in 0..7 || col !in 0..7) return ""
        val file = ('a' + col).toString()
        val rank = (8 - row).toString()
        return "$file$rank"
    }

    fun algebraicToSquare(notation: String): Int {
        if (notation.length < 2) return -1
        val fileChar = notation[0].lowercaseChar()
        val rankChar = notation[1]
        val col = fileChar - 'a'
        val row = '8' - rankChar
        if (row !in 0..7 || col !in 0..7) return -1
        return row * 8 + col
    }

    /**
     * Finds legal moves from a given square.
     * Keeps calculations lightweight but logically robust for UI interactive feedback.
     */
    fun findPossibleMoves(board: CharArray, squareIndex: Int): List<Int> {
        if (squareIndex !in 0..63) return emptyList()
        val piece = board[squareIndex]
        if (piece == '.') return emptyList()

        val possible = mutableListOf<Int>()
        val row = squareIndex / 8
        val col = squareIndex % 8
        val isWhite = isWhitePiece(piece)

        fun addIfValid(r: Int, c: Int): Boolean {
            if (r !in 0..7 || c !in 0..7) return false
            val destIndex = r * 8 + c
            val destPiece = board[destIndex]
            if (destPiece == '.') {
                possible.add(destIndex)
                return true
            }
            val destIsWhite = isWhitePiece(destPiece)
            if (isWhite != destIsWhite) {
                possible.add(destIndex)
            }
            return false // Blocked by any piece
        }

        when (piece.lowercaseChar()) {
            'p' -> {
                val dir = if (isWhite) -1 else 1
                // Single step
                val nextRow = row + dir
                if (nextRow in 0..7) {
                    val idx = nextRow * 8 + col
                    if (board[idx] == '.') {
                        possible.add(idx)
                        // Double step from starting rank
                        val startRank = if (isWhite) 6 else 1
                        if (row == startRank) {
                            val doubleIdx = (row + 2 * dir) * 8 + col
                            if (board[doubleIdx] == '.') {
                                possible.add(doubleIdx)
                            }
                        }
                    }
                }
                // Diagonals captures
                for (dc in listOf(-1, 1)) {
                    val targetC = col + dc
                    if (targetC in 0..7 && nextRow in 0..7) {
                        val idx = nextRow * 8 + targetC
                        val targetPiece = board[idx]
                        if (targetPiece != '.' && isWhitePiece(targetPiece) != isWhite) {
                            possible.add(idx)
                        }
                    }
                }
            }
            'n' -> {
                val moves = listOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((dr, dc) in moves) {
                    val r = row + dr
                    val c = col + dc
                    if (r in 0..7 && c in 0..7) {
                        val idx = r * 8 + c
                        val destPiece = board[idx]
                        if (destPiece == '.' || isWhitePiece(destPiece) != isWhite) {
                            possible.add(idx)
                        }
                    }
                }
            }
            'b' -> {
                val dirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                for ((dr, dc) in dirs) {
                    var r = row + dr
                    var c = col + dc
                    while (r in 0..7 && c in 0..7) {
                        val idx = r * 8 + c
                        val destPiece = board[idx]
                        if (destPiece == '.') {
                            possible.add(idx)
                        } else {
                            if (isWhitePiece(destPiece) != isWhite) {
                                possible.add(idx)
                            }
                            break // Blocked
                        }
                        r += dr
                        c += dc
                    }
                }
            }
            'r' -> {
                val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                for ((dr, dc) in dirs) {
                    var r = row + dr
                    var c = col + dc
                    while (r in 0..7 && c in 0..7) {
                        val idx = r * 8 + c
                        val destPiece = board[idx]
                        if (destPiece == '.') {
                            possible.add(idx)
                        } else {
                            if (isWhitePiece(destPiece) != isWhite) {
                                possible.add(idx)
                            }
                            break // Blocked
                        }
                        r += dr
                        c += dc
                    }
                }
            }
            'q' -> {
                val dirs = listOf(
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, -1 to 1, 1 to -1, 1 to 1
                )
                for ((dr, dc) in dirs) {
                    var r = row + dr
                    var c = col + dc
                    while (r in 0..7 && c in 0..7) {
                        val idx = r * 8 + c
                        val destPiece = board[idx]
                        if (destPiece == '.') {
                            possible.add(idx)
                        } else {
                            if (isWhitePiece(destPiece) != isWhite) {
                                possible.add(idx)
                            }
                            break // Blocked
                        }
                        r += dr
                        c += dc
                    }
                }
            }
            'k' -> {
                val dirs = listOf(
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, -1 to 1, 1 to -1, 1 to 1
                )
                for ((dr, dc) in dirs) {
                    val r = row + dr
                    val c = col + dc
                    if (r in 0..7 && c in 0..7) {
                        val idx = r * 8 + c
                        val destPiece = board[idx]
                        if (destPiece == '.' || isWhitePiece(destPiece) != isWhite) {
                            possible.add(idx)
                        }
                    }
                }
            }
        }
        return possible
    }

    /**
     * Executes a move on the board and yields the resulting Algebraic Move notation (e.g. "e2e4" or "Nf3").
     */
    fun executeMove(board: CharArray, from: Int, to: Int): Pair<CharArray, String> {
        val nextBoard = board.copyOf()
        val piece = nextBoard[from]
        val destPiece = nextBoard[to]

        nextBoard[to] = piece
        nextBoard[from] = '.'

        val moveNotation = "${getSquareAlgebraic(from / 8, from % 8)}${getSquareAlgebraic(to / 8, to % 8)}"
        return nextBoard to moveNotation
    }

    /**
     * Check if a king (white if checkWhiteKing is true, otherwise black) is in check.
     */
    fun isKingInCheck(board: CharArray, checkWhiteKing: Boolean): Boolean {
        val kingChar = if (checkWhiteKing) 'K' else 'k'
        val kingIdx = board.indexOf(kingChar)
        if (kingIdx == -1) return false

        for (i in 0..63) {
            val piece = board[i]
            if (piece != '.') {
                val isPieceWhite = isWhitePiece(piece)
                if (isPieceWhite != checkWhiteKing) {
                    val moves = findPossibleMoves(board, i)
                    if (kingIdx in moves) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
