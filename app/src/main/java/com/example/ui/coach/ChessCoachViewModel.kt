package com.example.ui.coach

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChessGameEntity
import com.example.data.model.LichessProfile
import com.example.data.model.PuzzleEntity
import com.example.data.repository.ChessRepository
import com.example.engine.ChessEngine
import com.example.engine.StockfishJsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChessCoachViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ChessRepository(
        profileDao = db.profileDao(),
        gameDao = db.gameDao(),
        puzzleDao = db.puzzleDao()
    )

    // Reactive states from Room Databases
    val activeProfile: StateFlow<LichessProfile?> = repository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val gamesList: StateFlow<List<ChessGameEntity>> = repository.gamesList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val practicePuzzles: StateFlow<List<PuzzleEntity>> = repository.practicePuzzles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TTS Voice synthesizer
    private var ttsManager: ChessTtsManager? = null

    // --- Interactive Move UI States ---
    var isReactBoardMode by mutableStateOf(true) // Use react-chessboard by default as requested!
    var reactBoardTheme by mutableStateOf("brown") // "brown" or "blue" for Lichess themes
    var originalBoardState by mutableStateOf(ChessEngine.parseFen(ChessEngine.START_FEN))
    var currentBoardState by mutableStateOf(originalBoardState.copyOf())
    var selectedSquare by mutableStateOf(-1)
    var possibleMoves by mutableStateOf<List<Int>>(emptyList())
    var isWhiteTurn by mutableStateOf(true)
    var displayedFen by mutableStateOf(ChessEngine.START_FEN)
    var moveHistoryList by mutableStateOf<List<String>>(emptyList())
    var moveEvaluationsList by mutableStateOf<List<String>>(emptyList())
    var activeMoveIndex by mutableStateOf(-1)

    // --- Analytical Feedback States ---
    val stockfishJsEngine = StockfishJsEngine(application)
    var isLocalEngineMode by mutableStateOf(true) // Default to local Web Worker engine since user requested it!
    var localEngineStatus by mutableStateOf("Démarrage du Thread Stockfish.js...")

    var selectedGameId by mutableStateOf<String?>(null)
    var isAnalyzing by mutableStateOf(false)
    var isGeneratingSummary by mutableStateOf(false)
    var gameSummaryReport by mutableStateOf<String?>(null)
    var stockfishEval by mutableStateOf("0.0")
    var coachAdvice by mutableStateOf("Bonjour ! Je suis votre coach FOCUS+. Sélectionnez une partie pour commencer ou lancez une analyse sur l'échiquier !")
    var userLoginInput by mutableStateOf("")

    // Accessibility & Modern Lichess Toggles
    var isBlindAccessibilityMode by mutableStateOf(false)
    var isComputerAnalysisEnabled by mutableStateOf(true)
    var showTimePerMove by mutableStateOf(false)

    // Daily Training States
    var selectedPuzzle by mutableStateOf<PuzzleEntity?>(null)
    var puzzleProgressMessage by mutableStateOf("Résolvez le problème tactique du jour !")
    var isDailyPuzzleCompleted by mutableStateOf(false)
    var puzzleMovesPlayed by mutableStateOf<List<String>>(emptyList())

    fun fetchTacticsOfTheDay(apiKey: String) {
        viewModelScope.launch {
            puzzleProgressMessage = "Téléchargement d'un défi tactique sur Lichess..."
            val result = repository.syncDailyPuzzle()
            if (result.isSuccess) {
                val puzzle = result.getOrNull()
                if (puzzle != null) {
                    loadPuzzle(puzzle)
                    puzzleProgressMessage = "Défi tactique du jour chargé ! (${puzzle.themes}) - ELO ${puzzle.rating}"
                    speakAdvice("Défi tactique chargé avec succès d'un niveau de " + puzzle.rating + " ELO. À vous de jouer !")
                }
            } else {
                // Try picking a random offline practice puzzle
                val offline = practicePuzzles.value
                if (offline.isNotEmpty()) {
                    val p = offline.random()
                    loadPuzzle(p)
                    puzzleProgressMessage = "Défi résolu localement chargée ! (${p.themes}) - ELO ${p.rating}"
                    speakAdvice("Défi local d'entraînement chargé. " + p.themes)
                } else {
                    puzzleProgressMessage = "Erreur de connexion Lichess. Veuillez réessayer."
                }
            }
        }
    }

    init {
        ttsManager = ChessTtsManager(application)
        
        // Feed offline default puzzles into database if base is empty
        viewModelScope.launch {
            repository.practicePuzzles.collect { current ->
                if (current.isEmpty()) {
                    feedDefaultThematicPuzzles()
                }
            }
        }
        
        // Sync daily puzzle from Lichess on launch
        viewModelScope.launch {
            repository.syncDailyPuzzle()
        }

        // Listen for Local Stockfish.js Web Worker engine statuses and evaluations
        viewModelScope.launch {
            stockfishJsEngine.status.collect { status ->
                localEngineStatus = when(status) {
                    "READY" -> "Moteur Stockfish.js (Worker) prêt"
                    "INITIALIZING" -> "Démarrage du Thread Stockfish.js..."
                    else -> status
                }
            }
        }

        viewModelScope.launch {
            stockfishJsEngine.evaluation.collect { eval ->
                if (isLocalEngineMode) {
                    stockfishEval = eval
                }
            }
        }
    }

    fun speakAdvice(text: String) {
        ttsManager?.speak(text)
    }

    fun stopSpeaking() {
        ttsManager?.stop()
    }

    /**
     * Look up public Lichess user data and load profile and recent games list.
     */
    fun loginWithLichess(username: String, onCallback: (Boolean) -> Unit = {}) {
        if (username.trim().isEmpty()) return
        isAnalyzing = true
        viewModelScope.launch {
            val result = repository.syncLichessUser(username)
            isAnalyzing = false
            if (result.isSuccess) {
                userLoginInput = ""
                onCallback(true)
            } else {
                onCallback(false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearActiveProfile()
            resetBoard()
            coachAdvice = "Bonjour ! Identifiez-vous ci-dessus pour charger votre historique Lichess."
        }
    }

    fun playMoveSound(beforeBoard: CharArray, afterBoard: CharArray, isWhiteTurnValue: Boolean) {
        val checkOpponentKing = ChessEngine.isKingInCheck(afterBoard, isWhiteTurnValue)
        val beforeCount = beforeBoard.count { it != '.' }
        val afterCount = afterBoard.count { it != '.' }
        
        if (checkOpponentKing) {
            ChessSoundManager.playCheck()
        } else if (afterCount < beforeCount) {
            ChessSoundManager.playCapture()
        } else {
            ChessSoundManager.playMove()
        }

        // Blind Player Screen-Reader Voice Announcement
        if (isBlindAccessibilityMode) {
            val diffs = mutableListOf<Int>()
            for (i in 0..63) {
                if (beforeBoard[i] != afterBoard[i]) {
                    diffs.add(i)
                }
            }
            if (diffs.size >= 2) {
                val from = diffs.find { afterBoard[it] == '.' } ?: diffs[0]
                val to = diffs.find { afterBoard[it] != '.' } ?: diffs[1]
                val piece = beforeBoard[from]
                val fromName = ChessEngine.getSquareAlgebraic(from / 8, from % 8)
                val toName = ChessEngine.getSquareAlgebraic(to / 8, to % 8)
                
                val pieceLabel = when(piece.lowercaseChar()) {
                    'p' -> "Pion"
                    'n' -> "Cavalier"
                    'b' -> "Fou"
                    'r' -> "Tour"
                    'q' -> "Dame"
                    'k' -> "Roi"
                    else -> "Pièce"
                }
                
                val isCapture = afterCount < beforeCount
                val actionPhrase = if (isCapture) "prend en" else "vers"
                val checkPhrase = if (checkOpponentKing) "avec échec au Roi !" else ""
                
                speakAdvice("$pieceLabel de $fromName $actionPhrase $toName $checkPhrase")
            }
        }
    }

    /**
     * Start analysing a historical game in the Interactive Board Screen.
     */
    fun loadGameForAnalysis(game: ChessGameEntity) {
        selectedGameId = game.id
        resetBoard()
        // Parse games moves
        val movesList = game.moves.split(" ").filter { it.isNotEmpty() }
        moveHistoryList = movesList
        moveEvaluationsList = List(movesList.size) { "Non analysé" }
        activeMoveIndex = -1
        gameSummaryReport = null
        
        coachAdvice = "Partie chargée : ${game.whiteUser} ELO ${game.whiteElo} contre ${game.blackUser} ELO ${game.blackElo}. Cliquez sur les coups à droite pour naviguer et solliciter l'IA !"
        stockfishEval = if (game.winner == "white") "+Mat" else if (game.winner == "black") "-Mat" else "Égalité"
        
        originalBoardState = ChessEngine.parseFen(game.initialFen)
        currentBoardState = originalBoardState.copyOf()
        displayedFen = game.initialFen
        isWhiteTurn = true
    }

    fun selectHistoryMove(index: Int, apiKey: String) {
        if (index !in -1 until moveHistoryList.size) return
        val boardBefore = currentBoardState.copyOf()
        val indexBefore = activeMoveIndex
        
        activeMoveIndex = index
        
        // Recompute board position up to this index
        var board = originalBoardState.copyOf()
        var whiteTurn = true
        for (i in 0..index) {
            val moveStr = moveHistoryList[i]
            if (moveStr.length >= 4) {
                val fromSq = ChessEngine.algebraicToSquare(moveStr.substring(0, 2))
                val toSq = ChessEngine.algebraicToSquare(moveStr.substring(2, 4))
                if (fromSq != -1 && toSq != -1) {
                    val result = ChessEngine.executeMove(board, fromSq, toSq)
                    board = result.first
                    whiteTurn = !whiteTurn
                }
            }
        }
        currentBoardState = board
        isWhiteTurn = whiteTurn
        displayedFen = ChessEngine.toFen(board, whiteTurn)
        
        if (indexBefore != index) {
            playMoveSound(boardBefore, currentBoardState, whiteTurn)
        }
        
        // Trigger Engine Evaluation + AI coaching for this move!
        analyzePosition(moveHistoryList.getOrNull(index) ?: "Début", apiKey)
    }

    fun handleSquareClick(index: Int, apiKey: String) {
        // If puzzle is active, route square clicks to puzzle solver
        if (selectedPuzzle != null) {
            handlePuzzleSquareClick(index)
            return
        }

        val piece = currentBoardState[index]
        
        if (selectedSquare == -1) {
            // Check if player selected a friendly piece
            if (piece != '.' && ChessEngine.isWhitePiece(piece) == isWhiteTurn) {
                selectedSquare = index
                possibleMoves = ChessEngine.findPossibleMoves(currentBoardState, index)
            }
        } else {
            // Attempt to move piece
            if (index in possibleMoves) {
                val fromIdx = selectedSquare
                val oldBoard = currentBoardState.copyOf()
                val res = ChessEngine.executeMove(currentBoardState, fromIdx, index)
                currentBoardState = res.first
                val nextWhiteTurn = !isWhiteTurn
                isWhiteTurn = nextWhiteTurn
                displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                
                playMoveSound(oldBoard, currentBoardState, nextWhiteTurn)
                
                // Add to history
                val updatedHistory = moveHistoryList.toMutableList()
                updatedHistory.add(res.second)
                moveHistoryList = updatedHistory
                activeMoveIndex = moveHistoryList.size - 1
                
                // Analyze
                analyzePosition(res.second, apiKey)
            }
            selectedSquare = -1
            possibleMoves = emptyList()
        }
    }

    fun handleReactMoveExecuted(moveCode: String, newFen: String, isWhiteTurnValue: Boolean, apiKey: String) {
        val oldBoard = currentBoardState.copyOf()
        currentBoardState = ChessEngine.parseFen(newFen)
        isWhiteTurn = isWhiteTurnValue
        displayedFen = newFen
        
        playMoveSound(oldBoard, currentBoardState, isWhiteTurnValue)
        
        // Add to history list
        val updatedHistory = moveHistoryList.toMutableList()
        updatedHistory.add(moveCode)
        moveHistoryList = updatedHistory
        activeMoveIndex = moveHistoryList.size - 1
        
        // Analyze with both local/cloud engine & Gemini
        analyzePosition(moveCode, apiKey)
    }

    private fun analyzePosition(lastMove: String, apiKey: String) {
        val fenStr = displayedFen
        isAnalyzing = true
        viewModelScope.launch {
            val eval = if (isLocalEngineMode) {
                // Request evaluation from local Web Worker WebView
                stockfishJsEngine.evaluate(fenStr)
                // Wait briefly for the local engine thread to compute
                kotlinx.coroutines.delay(1000)
                stockfishJsEngine.evaluation.value
            } else {
                // Fetch Cloud Stockfish Eval
                val engineResult = repository.fetchStockfishEvaluation(fenStr)
                engineResult.getOrDefault("0.0")
            }
            
            stockfishEval = eval

            // Record evaluation in the evaluations list matching the active move index
            val currentIdx = activeMoveIndex
            if (currentIdx >= 0) {
                val listCopy = moveEvaluationsList.toMutableList()
                while (listCopy.size <= currentIdx) {
                    listCopy.add("Non analysé")
                }
                listCopy[currentIdx] = eval
                moveEvaluationsList = listCopy
            }
            
            // 2. Query Coach Gemini AI with the parsed evaluation and PGN context
            val pgn = getPgnString()
            val evalHistory = getEvaluationsHistoryString()

            val aiResult = repository.getGeminiCoaching(
                fen = fenStr,
                lastMove = lastMove,
                isWhiteTurn = isWhiteTurn,
                evaluation = eval,
                pgn = pgn,
                evalHistory = evalHistory,
                apiKey = apiKey
            )
            isAnalyzing = false
            coachAdvice = aiResult.getOrDefault("Coup joué ! Demandez l'évaluation de l'IA pour approfondir la tactique.")
            // Speak Coach commentary out loud!
            speakAdvice(coachAdvice)
        }
    }

    fun getPgnString(): String {
        val builder = java.lang.StringBuilder()
        val activeGame = gamesList.value.find { it.id == selectedGameId }
        if (activeGame != null) {
            builder.append("[White \"${activeGame.whiteUser} (ELO ${activeGame.whiteElo})\"]\n")
            builder.append("[Black \"${activeGame.blackUser} (ELO ${activeGame.blackElo})\"]\n")
            builder.append("[Result \"${when(activeGame.winner) { "white" -> "1-0"; "black" -> "0-1"; else -> "1/2-1/2" }}\"]\n")
        } else {
            builder.append("[White \"Joueur Blanc\"]\n")
            builder.append("[Black \"Joueur Noir\"]\n")
            builder.append("[Result \"*\"]\n")
        }
        builder.append("\n")
        for (i in moveHistoryList.indices step 2) {
            val moveNum = i / 2 + 1
            val whiteMove = moveHistoryList[i]
            val blackMove = moveHistoryList.getOrNull(i + 1)
            if (blackMove != null) {
                builder.append("$moveNum. $whiteMove $blackMove ")
            } else {
                builder.append("$moveNum. $whiteMove ")
            }
        }
        return builder.toString().trim()
    }

    fun getEvaluationsHistoryString(): String {
        if (moveHistoryList.isEmpty()) return "Aucun coup joué pour l'instant."
        val builder = java.lang.StringBuilder()
        for (i in moveHistoryList.indices) {
            val evalVal = moveEvaluationsList.getOrNull(i) ?: "Non analysé"
            builder.append("Coup ${i + 1} (${moveHistoryList[i]}) : $evalVal\n")
        }
        return builder.toString().trim()
    }

    fun generateGameSummaryReport(apiKey: String) {
        if (moveHistoryList.isEmpty()) {
            coachAdvice = "Veuillez d'abord jouer ou charger une partie pour générer un bilan !"
            return
        }
        isGeneratingSummary = true
        gameSummaryReport = null
        viewModelScope.launch {
            val pgn = getPgnString()
            val evalHistory = getEvaluationsHistoryString()
            val result = repository.getGeminiGameSummary(pgn, evalHistory, apiKey)
            isGeneratingSummary = false
            gameSummaryReport = result.getOrDefault("Impossible de générer le bilan de la partie.")
        }
    }

    fun resetBoard() {
        originalBoardState = ChessEngine.parseFen(ChessEngine.START_FEN)
        currentBoardState = originalBoardState.copyOf()
        selectedSquare = -1
        possibleMoves = emptyList()
        isWhiteTurn = true
        displayedFen = ChessEngine.START_FEN
        moveHistoryList = emptyList()
        moveEvaluationsList = emptyList()
        activeMoveIndex = -1
        stockfishEval = "0.0"
        gameSummaryReport = null
        stopSpeaking()
    }

    // --- Puzzle Solver Engine Logic ---
    fun loadPuzzle(puzzle: PuzzleEntity) {
        selectedPuzzle = puzzle
        isDailyPuzzleCompleted = false
        puzzleProgressMessage = when {
            puzzle.isCompleted -> "Problème déjà réussi ! Réessayez."
            else -> "Tactique : à vous de jouer ! (${puzzle.themes})"
        }
        
        // FEN is state BEFORE puzzle starts
        originalBoardState = ChessEngine.parseFen(puzzle.fen)
        currentBoardState = originalBoardState.copyOf()
        displayedFen = puzzle.fen
        
        // FEN split turn
        val parts = puzzle.fen.split(" ")
        isWhiteTurn = parts.getOrNull(1) != "b"
        
        selectedSquare = -1
        possibleMoves = emptyList()
        puzzleMovesPlayed = emptyList()
    }

    private fun handlePuzzleSquareClick(index: Int) {
        val puzzle = selectedPuzzle ?: return
        if (isDailyPuzzleCompleted) return

        val piece = currentBoardState[index]
        if (selectedSquare == -1) {
            if (piece != '.' && ChessEngine.isWhitePiece(piece) == isWhiteTurn) {
                selectedSquare = index
                possibleMoves = ChessEngine.findPossibleMoves(currentBoardState, index)
            }
        } else {
            if (index in possibleMoves) {
                val fromSq = selectedSquare
                val oldBoard = currentBoardState.copyOf()
                val res = ChessEngine.executeMove(currentBoardState, fromSq, index)
                
                // Move played
                val moveStr = "${ChessEngine.getSquareAlgebraic(fromSq / 8, fromSq % 8)}${ChessEngine.getSquareAlgebraic(index / 8, index % 8)}"
                
                // Check if move matches solution
                val solutionMoves = puzzle.solution.split(" ")
                val currentStep = puzzleMovesPlayed.size
                
                if (currentStep < solutionMoves.size && moveStr.equals(solutionMoves[currentStep], ignoreCase = true)) {
                    // Correct Move!
                    currentBoardState = res.first
                    val updatedPlayed = puzzleMovesPlayed.toMutableList()
                    updatedPlayed.add(moveStr)
                    puzzleMovesPlayed = updatedPlayed
                    
                    val nextWhiteTurn = !isWhiteTurn
                    isWhiteTurn = nextWhiteTurn
                    
                    // Is puzzle fully solved?
                    if (puzzleMovesPlayed.size == solutionMoves.size) {
                        isDailyPuzzleCompleted = true
                        puzzleProgressMessage = "Excellent ! Problème résolu. (+${puzzle.rating} ELO Tactique)"
                        speakAdvice("Félicitations ! Vous avez trouvé la meilleure suite tactique. Excellent coup !")
                        ChessSoundManager.playGameEnd()
                        viewModelScope.launch {
                            repository.solvePuzzle(puzzle.id, true)
                        }
                    } else {
                        playMoveSound(oldBoard, currentBoardState, nextWhiteTurn)
                        
                        // Play opponent reply automatically if there is one!
                        val opponentReplyStep = puzzleMovesPlayed.size
                        if (opponentReplyStep < solutionMoves.size) {
                            val replyMove = solutionMoves[opponentReplyStep]
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(650) // Realistic opponent thinking delay!
                                val rFrom = ChessEngine.algebraicToSquare(replyMove.substring(0, 2))
                                val rTo = ChessEngine.algebraicToSquare(replyMove.substring(2, 4))
                                if (rFrom != -1 && rTo != -1) {
                                    val rOldBoard = currentBoardState.copyOf()
                                    val replyRes = ChessEngine.executeMove(currentBoardState, rFrom, rTo)
                                    currentBoardState = replyRes.first
                                    val finalPlayedList = puzzleMovesPlayed.toMutableList()
                                    finalPlayedList.add(replyMove)
                                    puzzleMovesPlayed = finalPlayedList
                                    val oppNextWhiteTurn = !isWhiteTurn
                                    isWhiteTurn = oppNextWhiteTurn
                                    puzzleProgressMessage = "L'adversaire répond. Quel est votre coup ?"
                                    
                                    playMoveSound(rOldBoard, currentBoardState, oppNextWhiteTurn)
                                }
                            }
                        }
                    }
                } else {
                    // Wrong move!
                    puzzleProgressMessage = "Coup imprécis. Essayez d'analyser une autre suite !"
                    speakAdvice("Ce n'est pas la meilleure ligne de jeu. Cherchez encore !")
                    ChessSoundManager.playCheck() // buzz alert sound
                    selectedSquare = -1
                    possibleMoves = emptyList()
                }
            }
            selectedSquare = -1
            possibleMoves = emptyList()
        }
    }

    fun resetDailyPuzzle() {
        val puzzle = selectedPuzzle
        if (puzzle != null) {
            loadPuzzle(puzzle)
        }
    }

    // --- NEW: Chess Opening Line Practice Capability ---
    var selectedOpeningId by mutableStateOf<String?>(null)
    var currentOpeningMoveIdx by mutableStateOf(0)
    var openingProgressMessage by mutableStateOf("Sélectionnez une ouverture mythique ci-dessous pour commencer !")
    var isOpeningCompleted by mutableStateOf(false)
    var customOpeningDeviationPlayed by mutableStateOf<Boolean>(false)

    fun loadOpeningLine(opening: OpeningLine) {
        selectedOpeningId = opening.id
        currentOpeningMoveIdx = 0
        isOpeningCompleted = false
        customOpeningDeviationPlayed = false
        
        // Reset board to match opening study starts
        originalBoardState = ChessEngine.parseFen(opening.initialFen)
        currentBoardState = originalBoardState.copyOf()
        displayedFen = opening.initialFen
        isWhiteTurn = true
        selectedSquare = -1
        possibleMoves = emptyList()
        
        val firstMove = opening.moves.firstOrNull()
        if (firstMove != null) {
            openingProgressMessage = "Bienvenue dans l'étude : ${opening.name}.\n👉 Coup 1 : ${firstMove.prompt}"
            speakAdvice(firstMove.prompt)
        } else {
            openingProgressMessage = "Étude commencée !"
        }
    }

    fun handleOpeningSquareClick(index: Int) {
        val opening = defaultOpeningLines.find { it.id == selectedOpeningId } ?: return
        val currentMove = opening.moves.getOrNull(currentOpeningMoveIdx) ?: return
        
        val piece = currentBoardState[index]
        
        if (selectedSquare == -1) {
            val expectedColor = currentMove.playerColor
            val isPieceWhite = ChessEngine.isWhitePiece(piece)
            val isCorrectColorSelect = (expectedColor == "White" && isPieceWhite && isWhiteTurn) ||
                                      (expectedColor == "Black" && !isPieceWhite && !isWhiteTurn)
            
            if (piece != '.' && isCorrectColorSelect) {
                selectedSquare = index
                possibleMoves = ChessEngine.findPossibleMoves(currentBoardState, index)
            }
        } else {
            if (index in possibleMoves) {
                val fromIdx = selectedSquare
                val oldBoard = currentBoardState.copyOf()
                val res = ChessEngine.executeMove(currentBoardState, fromIdx, index)
                val testMoveStr = res.second
                
                if (testMoveStr.equals(currentMove.algebraicMove, ignoreCase = true)) {
                    currentBoardState = res.first
                    val nextWhiteTurn = !isWhiteTurn
                    isWhiteTurn = nextWhiteTurn
                    displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                    
                    val moveText = currentMove.san
                    val explanation = currentMove.explanation
                    currentOpeningMoveIdx++
                    
                    val nextMove = opening.moves.getOrNull(currentOpeningMoveIdx)
                    if (nextMove != null) {
                        if (nextMove.playerColor != currentMove.playerColor) {
                            playMoveSound(oldBoard, currentBoardState, nextWhiteTurn)
                            
                            viewModelScope.launch {
                                openingProgressMessage = "Correct ! Vous avez joué $moveText. $explanation\n\nL'ordinateur prépare sa réponse..."
                                speakAdvice("$moveText ! $explanation")
                                kotlinx.coroutines.delay(1250)
                                
                                val oppFrom = ChessEngine.algebraicToSquare(nextMove.algebraicMove.substring(0, 2))
                                val oppTo = ChessEngine.algebraicToSquare(nextMove.algebraicMove.substring(2, 4))
                                if (oppFrom != -1 && oppTo != -1) {
                                    val oOldBoard = currentBoardState.copyOf()
                                    val oppRes = ChessEngine.executeMove(currentBoardState, oppFrom, oppTo)
                                    currentBoardState = oppRes.first
                                    val oppNextWhiteTurn = !isWhiteTurn
                                    isWhiteTurn = oppNextWhiteTurn
                                    displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                                    
                                    currentOpeningMoveIdx++
                                    
                                    playMoveSound(oOldBoard, currentBoardState, oppNextWhiteTurn)

                                    val userNextMove = opening.moves.getOrNull(currentOpeningMoveIdx)
                                    if (userNextMove != null) {
                                        openingProgressMessage = "L'adversaire a joué ${nextMove.san}. ${nextMove.prompt}\n\n👉 Suivant : ${userNextMove.prompt}"
                                        speakAdvice("L'adversaire a joué ${nextMove.san}. ${userNextMove.prompt}")
                                    } else {
                                        isOpeningCompleted = true
                                        openingProgressMessage = "Félicitations 🎉 ! Vous avez complété avec succès l'étude de l'ouverture : ${opening.name}.\n\nVous maîtrisez parfaitement l'ordre des coups d'ouverture théoriques principaux !"
                                        speakAdvice("Félicitations ! Vous avez complété avec succès l'étude de cette ouverture.")
                                        ChessSoundManager.playGameEnd()
                                    }
                                }
                            }
                        } else {
                            playMoveSound(oldBoard, currentBoardState, nextWhiteTurn)
                            
                            openingProgressMessage = "Correct ! Vous avez joué $moveText. $explanation\n\n👉 Prochain coup : ${nextMove.prompt}"
                            speakAdvice("$moveText ! $explanation. ${nextMove.prompt}")
                        }
                    } else {
                        isOpeningCompleted = true
                        openingProgressMessage = "Félicitations 🎉 ! Vous avez complété avec succès l'étude de l'ouverture : ${opening.name}.\n\nVous maîtrisez parfaitement l'ordre des coups d'ouverture théoriques principaux !"
                        speakAdvice("Félicitations ! Vous avez complété avec succès l'étude de cette ouverture.")
                        ChessSoundManager.playGameEnd()
                    }
                } else {
                    currentBoardState = res.first
                    isWhiteTurn = !isWhiteTurn
                    displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                    customOpeningDeviationPlayed = true
                    
                    openingProgressMessage = "Déviation de l'ouverture 💡 (Coup joué : ${res.second}).\nÉvaluation du moteur dynamique..."
                    
                    viewModelScope.launch {
                        stockfishJsEngine.evaluate(displayedFen)
                        kotlinx.coroutines.delay(1000)
                        val eval = stockfishJsEngine.evaluation.value
                        
                        openingProgressMessage = "Déviation théorique 💡\n\n" +
                                "Vous avez joué le coup alternatifs: ${res.second}.\n" +
                                "Stockfish évalue la position à : $eval.\n\n" +
                                "Rappel stratégique : Dans cette position, l'ouverture de référence préconisait ${currentMove.san}. Assurez-vous d'occuper les cases centrales stratégiques, maintenir la sécurité du roi (Roque) et développer rapidement vos cavaliers et vos fous."
                        speakAdvice("Vous avez dévié. L'évaluation est de $eval.")
                    }
                }
            }
            selectedSquare = -1
            possibleMoves = emptyList()
        }
    }

    fun closeOpeningStudy() {
        selectedOpeningId = null
        openingProgressMessage = "Sélectionnez une ouverture mythique ci-dessous pour commencer !"
    }

    private suspend fun feedDefaultThematicPuzzles() {
        // High quality thematic list for practice representing offline capabilities perfectly
        val defaultPuzzles = listOf(
            PuzzleEntity(
                id = "pindex_1",
                fen = "3r2k1/pp3ppp/8/3P4/8/8/PP3PPP/3R2K1 w - - 0 1",
                rating = 1100,
                solution = "d5d6 g8f8",
                themes = "Finales de pions, Promotion",
                isDaily = false
            ),
            PuzzleEntity(
                id = "pindex_2",
                fen = "rn1qkb1r/pp3ppp/2p1pn2/3p4/2PP4/2N2B2/PP2PPPP/R1BQK2R w KQkq - 0 7",
                rating = 1250,
                solution = "c4d5 e6d5",
                themes = "Ouvertures, Contrôle du centre",
                isDaily = false
            ),
            PuzzleEntity(
                id = "pindex_3",
                fen = "r1b1k2r/ppq2ppp/2nbpn2/2ppN3/3P4/2PBPN2/PP3PPP/R1BQK2R w KQkq - 0 8",
                rating = 1420,
                solution = "e5c6 b7c6",
                themes = "Tactique, Échange",
                isDaily = false
            ),
            PuzzleEntity(
                id = "pindex_4",
                fen = "2r3k1/1p3pp1/pq2p2p/3p1n2/1P1P1BP1/P2Q1P1P/2r5/R4KR1 b - - 0 1",
                rating = 1650,
                solution = "f5d4 a1d1",
                themes = "Milieu de jeu, Clouage",
                isDaily = false
            ),
            PuzzleEntity(
                id = "pindex_5",
                fen = "q3r1k1/5ppp/5b2/8/8/2N2Q2/PP1B1PPP/6K1 b - - 0 1",
                rating = 1880,
                solution = "f6c3 f3a8 e8a8",
                themes = "Attaque de la Dame, Échange de matériel",
                isDaily = false
            )
        )
        repository.feedPracticePuzzles(defaultPuzzles)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager?.shutdown()
    }
}

// --- Opening study models and static database ---
data class OpeningMove(
    val moveNumber: Int,
    val playerColor: String, // "White" or "Black"
    val algebraicMove: String, // "e2e4"
    val san: String,           // "e4"
    val prompt: String,        // Direction prompt for user
    val explanation: String    // Explanation of the move's theory
)

data class OpeningLine(
    val id: String,
    val name: String,
    val description: String,
    val initialFen: String = ChessEngine.START_FEN,
    val moves: List<OpeningMove>
)

val defaultOpeningLines = listOf(
    OpeningLine(
        id = "ruy_lopez",
        name = "Partie Espagnole (Ruy Lopez)",
        description = "Mettez la pression sur le centre de manière agressive et préparez le Petit Roque.",
        moves = listOf(
            OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Contrôlez le centre avec votre pion de Roi.", "Excellent ! e4 occupe le centre et libère la Dame et le Fou."),
            OpeningMove(1, "Black", "e7e5", "e5", "L'adversaire répond de manière symétrique en contrôlant d5.", ""),
            OpeningMove(2, "White", "g1f3", "Nf3", "Développez votre Cavalier pour attaquer le pion e5.", "Correct ! Nf3 développe une pièce active et attaque le pion e5."),
            OpeningMove(2, "Black", "b8c6", "Nc6", "Les noirs défendent leur pion avec leur Cavalier.", ""),
            OpeningMove(3, "White", "f1b5", "Bb5", "Menacez le défenseur du pion e5 pour contester le centre.", "Merveilleux ! Bb5 définit la Ruy Lopez classique (Espagnole). Elle met la pression sur le Cavalier c6, préparant le roque et un combat dynamique.")
        )
    ),
    OpeningLine(
        id = "sicilian_defense",
        name = "Défense Sicilienne",
        description = "La réponse noire la plus dynamique et asymétrique face à l'ouverture 1.e4.",
        moves = listOf(
            OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Ouvrez fidèlement avec e4.", "Moderne ! e4 lance la lutte impitoyable du centre."),
            OpeningMove(1, "Black", "c7c5", "c5", "Les noirs répliquent avec c5 pour créer un déséquilibre asymétrique immédiat en d4.", ""),
            OpeningMove(2, "White", "g1f3", "Nf3", "Développez votre Cavalier roi vers une case centrale.", "Superbe ! Nf3 prépare royalement la poussée centrale d4."),
            OpeningMove(2, "Black", "d7d6", "d6", "Les noirs défendent indirectement c5 et limitent la case e5.", ""),
            OpeningMove(3, "White", "d2d4", "d4", "Ouvrez les lignes centrales en poussant d4 !", "Magnifique ! d4 brise le centre et ouvre les colonnes pour votre Dame et Tour."),
            OpeningMove(3, "Black", "c5d4", "cxd4", "Les noirs éliminent votre pion central en l'échange d'un pion d'aile.", "")
        )
    ),
    OpeningLine(
        id = "queens_gambit",
        name = "Gambit Dame",
        description = "Sacrifiez temporairement un pion d'aile pour dominer totalement le centre matériel.",
        moves = listOf(
            OpeningMove(1, "White", "d2d4", "d4", "À vous de jouer : Projetez d4 au centre.", "Parfait ! d4 prend un solide contrôle central."),
            OpeningMove(1, "Black", "d7d5", "d5", "Les noirs répliquent de manière solide avec d5.", ""),
            OpeningMove(2, "White", "c2c4", "c4", "Proposez le sacrifice de pion c4 pour ébranler d5.", "Formidable ! C'est le Gambit Dame (c4). Vous incitez les noirs à dévier de leur ferme soutien central et ouvrez des angles d'attaque !")
        )
    ),
    OpeningLine(
        id = "french_defense",
        name = "Défense Française",
        description = "Une ligne fermée solide pour bloquer les assauts blancs et pilonner leur centre.",
        moves = listOf(
            OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Ouvrez avec e4.", "Excellent ! e4 contrôle le centre."),
            OpeningMove(1, "Black", "e7e6", "e6", "Les noirs se barricadent avec de solides intentions de contre-attaque.", ""),
            OpeningMove(2, "White", "d2d4", "d4", "Établissez votre duo de pions d4 au centre.", "Fantastique ! Ce duo d4/e4 crée un bastion central imprenable."),
            OpeningMove(2, "Black", "d7d5", "d5", "Les noirs lancent immédiatement leur riposte d5.", "")
        )
    )
)

