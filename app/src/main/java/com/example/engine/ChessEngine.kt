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
     * Universal move parser and executor. Supporting standard coordinate format (e.g., "e2e4")
     * and Standard Algebraic Notation/SAN (e.g., "e4", "Nf3", "exd5", "Bxf7+", "O-O", "e8=Q")
     */
    fun parseAndExecuteAnyMove(board: CharArray, moveStr: String, isWhite: Boolean): Pair<CharArray, String>? {
        val nextBoard = board.copyOf()
        val clean = moveStr.replace("+", "").replace("#", "").replace("!", "").replace("?", "").trim()

        if (clean.isEmpty()) return null

        // 1. Check Kingside Castling
        if (clean.equals("O-O", ignoreCase = true) || clean.equals("0-0", ignoreCase = true)) {
            if (isWhite) {
                if (nextBoard[60] == 'K') nextBoard[60] = '.'
                nextBoard[62] = 'K'
                if (nextBoard[63] == 'R') nextBoard[63] = '.'
                nextBoard[61] = 'R'
            } else {
                if (nextBoard[4] == 'k') nextBoard[4] = '.'
                nextBoard[6] = 'k'
                if (nextBoard[7] == 'r') nextBoard[7] = '.'
                nextBoard[5] = 'r'
            }
            return nextBoard to moveStr
        }

        // 2. Check Queenside Castling
        if (clean.equals("O-O-O", ignoreCase = true) || clean.equals("0-0-0", ignoreCase = true)) {
            if (isWhite) {
                if (nextBoard[60] == 'K') nextBoard[60] = '.'
                nextBoard[58] = 'K'
                if (nextBoard[56] == 'R') nextBoard[56] = '.'
                nextBoard[59] = 'R'
            } else {
                if (nextBoard[4] == 'k') nextBoard[4] = '.'
                nextBoard[2] = 'k'
                if (nextBoard[0] == 'r') nextBoard[0] = '.'
                nextBoard[3] = 'r'
            }
            return nextBoard to moveStr
        }

        // 3. Check Coordinate Moves (e.g., "e2e4")
        if (clean.length == 4 && 
            clean[0] in 'a'..'h' && clean[1] in '1'..'8' && 
            clean[2] in 'a'..'h' && clean[3] in '1'..'8') {
            val fromSq = algebraicToSquare(clean.substring(0, 2))
            val toSq = algebraicToSquare(clean.substring(2, 4))
            if (fromSq != -1 && toSq != -1) {
                val piece = nextBoard[fromSq]
                nextBoard[toSq] = piece
                nextBoard[fromSq] = '.'

                // Handle secondary rook movement for castling represented in coords
                if (piece == 'K' && fromSq == 60 && toSq == 62) {
                    nextBoard[61] = 'R'; nextBoard[63] = '.'
                } else if (piece == 'K' && fromSq == 60 && toSq == 58) {
                    nextBoard[59] = 'R'; nextBoard[56] = '.'
                } else if (piece == 'k' && fromSq == 4 && toSq == 6) {
                    nextBoard[5] = 'r'; nextBoard[7] = '.'
                } else if (piece == 'k' && fromSq == 4 && toSq == 2) {
                    nextBoard[3] = 'r'; nextBoard[0] = '.'
                }

                // Handle promotion represented in coords
                if (piece == 'P' && toSq / 8 == 0) {
                    nextBoard[toSq] = 'Q'
                } else if (piece == 'p' && toSq / 8 == 7) {
                    nextBoard[toSq] = 'q'
                }

                return nextBoard to moveStr
            }
        }

        // 4. Check Standard Algebraic Notation (SAN)
        var sanClean = clean.replace("x", "") // remove capture tag
        
        // Extract promotion piece if present (e.g., "e8=Q" or "e8Q")
        var promoteChar: Char? = null
        if (sanClean.contains("=")) {
            val idx = sanClean.indexOf("=")
            if (idx + 1 < sanClean.length) {
                promoteChar = sanClean[idx + 1]
            }
            sanClean = sanClean.substring(0, idx)
        } else if (sanClean.length >= 3 && sanClean.last().isUpperCase() && (sanClean.last() == 'Q' || sanClean.last() == 'R' || sanClean.last() == 'B' || sanClean.last() == 'N')) {
            promoteChar = sanClean.last()
            sanClean = sanClean.dropLast(1)
        }

        if (sanClean.length < 2) return null
        val last2 = sanClean.takeLast(2)
        val toSq = algebraicToSquare(last2)
        if (toSq == -1) return null

        val firstChar = sanClean[0]
        val isPieceMove = firstChar.isUpperCase()
        val pieceType = if (isPieceMove) firstChar.lowercaseChar() else 'p'
        val targetPieceChar = if (isWhite) pieceType.uppercaseChar() else pieceType.lowercaseChar()

        // Extract any extra file (col) or rank (row) disambiguation markers
        var fileClue: Char? = null
        var rankClue: Char? = null

        if (!isPieceMove) {
            // Pauw capture like "exd5" (cleaned to "ed5") starts with the starting file
            if (sanClean.length > 2) {
                val first = sanClean[0]
                if (first in 'a'..'h') {
                    fileClue = first
                }
            }
        } else {
            // Piece movement: "Nbd2" -> clue is "b"
            val clueStr = sanClean.drop(1).dropLast(2)
            for (char in clueStr) {
                if (char in 'a'..'h') fileClue = char
                if (char in '1'..'8') rankClue = char
            }
        }

        // Search candidates
        val candidates = mutableListOf<Int>()
        for (idx in 0..63) {
            if (board[idx] == targetPieceChar) {
                val possible = findPossibleMoves(board, idx)
                if (toSq in possible) {
                    candidates.add(idx)
                }
            }
        }

        val filtered = candidates.filter { idx ->
            val col = idx % 8
            val row = idx / 8
            val colChar = 'a' + col
            val rowChar = '8' - row
            var ok = true
            if (fileClue != null && colChar != fileClue) ok = false
            if (rankClue != null && rowChar != rankClue) ok = false
            ok
        }

        if (filtered.isEmpty()) return null
        val fromSq = filtered[0]

        // Execute move
        nextBoard[toSq] = targetPieceChar
        nextBoard[fromSq] = '.'

        // En passant capture cleanup
        if (pieceType == 'p' && fileClue != null && board[toSq] == '.') {
            val capturedPawnSquare = (fromSq / 8) * 8 + (toSq % 8)
            nextBoard[capturedPawnSquare] = '.'
        }

        // Apply promotion
        if (promoteChar != null) {
            nextBoard[toSq] = if (isWhite) promoteChar.uppercaseChar() else promoteChar.lowercaseChar()
        }

        return nextBoard to moveStr
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
