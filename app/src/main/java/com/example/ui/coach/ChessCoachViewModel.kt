package com.example.ui.coach

import android.app.Application
import android.util.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import kotlin.random.Random
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val engineManager = com.example.engine.EngineManager.getInstance(application)
    val stockfishJsEngine = StockfishJsEngine(application)
    var isLocalEngineMode by mutableStateOf(engineManager.activeEngineType.value == com.example.engine.AnalysisEngineType.STOCKFISH_LOCAL)
    fun toggleEngineMode() {
        val nextType = if (isLocalEngineMode) com.example.engine.AnalysisEngineType.LICHESS else com.example.engine.AnalysisEngineType.STOCKFISH_LOCAL
        engineManager.setActiveEngine(nextType)
    }
    var localEngineStatus by mutableStateOf("Démarrage du Thread Stockfish.js...")

    var selectedGameId by mutableStateOf<String?>(null)
    var isAnalyzing by mutableStateOf(false)
    var isGeneratingSummary by mutableStateOf(false)
    var gameSummaryReport by mutableStateOf<String?>(null)
    
    var isGeneratingStructuredCoaching by mutableStateOf(false)
    var parsedGameCoaching by mutableStateOf<com.example.data.model.ParsedCoachingFeedback?>(null)
    var structuredCoachingError by mutableStateOf<String?>(null)
    
    // Dashboard Insights
    var isFetchingInsights by mutableStateOf(false)
    var dashboardInsights by mutableStateOf<String?>(null)
    
    var isVoiceCoachingEnabled by mutableStateOf(true)

    fun toggleVoiceCoaching() {
        isVoiceCoachingEnabled = !isVoiceCoachingEnabled
        if (!isVoiceCoachingEnabled) {
            stopSpeaking()
        }
    }

    fun fetchDashboardInsights(apiKey: String) {
        isFetchingInsights = true
        viewModelScope.launch {
            val games = gamesList.value
            if (games.isEmpty()) {
                dashboardInsights = "Aucune partie récente pour analyser les blunders."
                isFetchingInsights = false
                return@launch
            }
            
            // Analyze the last 3 games for tactics
            val pgnSummary = games.take(3).joinToString("\n") { it.moves }
            val result = repository.getGeminiGameSummary(
                pgn = pgnSummary,
                evalHistory = "Performance Analysis",
                apiKey = apiKey
            )
            dashboardInsights = result.getOrDefault("Impossible d'analyser les tactiques via Gemini.")
            isFetchingInsights = false
        }
    }
    
    var stockfishEval by mutableStateOf("0.0")
    var coachAdvice by mutableStateOf("Bonjour ! Je suis votre coach FOCUS+. Sélectionnez une partie pour commencer ou lancez une analyse sur l'échiquier !")
    var userLoginInput by mutableStateOf("")
    var customClientId by mutableStateOf("")
    var cachedAnalyses by mutableStateOf<Map<Int, com.example.data.model.MoveAnalysis>>(emptyMap())

    // Spaced Repetition & LotusChess Openings Progression
    var masteredOpeningsPositions by mutableStateOf(45)
    var isLotusSpacedRepetitionMode by mutableStateOf(false)
    var currentSpacedRepetitionOpeningName by mutableStateOf("")
    var currentSpacedRepetitionMove by mutableStateOf<OpeningMove?>(null)

    var isPreAnalyzingBackground by mutableStateOf(false)
    var preAnalysisProgress by mutableStateOf("")

    // Accessibility & Modern Lichess Toggles
    var isBoardFlipped by mutableStateOf(false)
    fun toggleBoardFlip() {
        isBoardFlipped = !isBoardFlipped
    }
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
        ChessSoundManager.initialize(application)
        ttsManager = ChessTtsManager(application)

        // Load mastered openings positions
        val prefs = application.getSharedPreferences("lotuschess_opening_prefs", Context.MODE_PRIVATE)
        masteredOpeningsPositions = prefs.getInt("mastered_positions", 45)
        
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

        // Proactive sync for active profile games
        viewModelScope.launch {
            activeProfile.collect { profile ->
                if (profile != null) {
                    preAnalyzeAllUnanalyzedGames()
                }
            }
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
            engineManager.activeEngineType.collect { type ->
                isLocalEngineMode = (type == com.example.engine.AnalysisEngineType.STOCKFISH_LOCAL)
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
        if (isVoiceCoachingEnabled) {
            ttsManager?.speak(text)
        }
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
                preAnalyzeAllUnanalyzedGames()
                onCallback(true)
            } else {
                onCallback(false)
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..60)
            .map { allowedChars[Random.nextInt(allowedChars.length)] }
            .joinToString("")
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(bytes)
        return Base64.encodeToString(hashedBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun startLichessOAuth(context: Context, clientId: String) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        
        // Save code_verifier to SharedPreferences to retrieve it after redirection
        val prefs = context.getSharedPreferences("lichess_oauth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("code_verifier", verifier)
            .putString("client_id_used", clientId)
            .apply()
            
        val authorizationUrl = Uri.parse("https://lichess.org/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", "focusplus://oauth")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
            
        Log.d("ChessCoachViewModel", "Launching Lichess OAuth authorize URL: $authorizationUrl")
        val intent = Intent(Intent.ACTION_VIEW, authorizationUrl).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun handleOAuthCallback(code: String, onCallback: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("lichess_oauth_prefs", Context.MODE_PRIVATE)
        val verifier = prefs.getString("code_verifier", null)
        val clientId = prefs.getString("client_id_used", "focus-plus-chess-coach") ?: "focus-plus-chess-coach"
        
        if (verifier == null) {
            Log.e("ChessCoachViewModel", "Error: Saved code_verifier not found in SharedPreferences!")
            onCallback(false)
            return
        }
        
        isAnalyzing = true
        coachAdvice = "Connexion via Lichess OAuth en cours..."
        viewModelScope.launch {
            val result = repository.exchangeOAuthAndLoadProfile(
                code = code,
                codeVerifier = verifier,
                clientId = clientId.trim(),
                redirectUri = "focusplus://oauth"
            )
            isAnalyzing = false
            if (result.isSuccess) {
                coachAdvice = "Connexion OAuth réussie ! Votre profil et vos parties ont été importés."
                preAnalyzeAllUnanalyzedGames()
                onCallback(true)
            } else {
                coachAdvice = "Erreur de connexion OAuth. Veuillez réessayer."
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
        parsedGameCoaching = null
        structuredCoachingError = null
        
        stockfishEval = if (game.winner == "white") "+Mat" else if (game.winner == "black") "-Mat" else "Égalité"
        
        originalBoardState = ChessEngine.parseFen(game.initialFen)
        currentBoardState = originalBoardState.copyOf()
        displayedFen = game.initialFen
        isWhiteTurn = true

        // Clean cache
        cachedAnalyses = emptyMap()

        val apiKey = com.example.BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"

        if (!game.analysisExplain.isNullOrEmpty()) {
            try {
                val jsonObject = org.json.JSONObject(game.analysisExplain)
                val resultMap = mutableMapOf<Int, com.example.data.model.MoveAnalysis>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val idx = key.toIntOrNull() ?: continue
                    val innerObj = jsonObject.optJSONObject(key)
                    if (innerObj != null) {
                        val comment = innerObj.optString("comment", "Coup analysé.")
                        val eval = innerObj.optDouble("evaluation", 0.0)
                        resultMap[idx] = com.example.data.model.MoveAnalysis(comment, eval)
                    }
                }
                cachedAnalyses = resultMap
                coachAdvice = "Analyse chargée de la mémoire locale ! Fluidité de navigation instantanée garantie à 100%."
                Log.d("ChessCoachViewModel", "Loaded fully cached analysis for game ${game.id}")
            } catch (e: Exception) {
                Log.e("ChessCoachViewModel", "Failed to load cached analysis", e)
                coachAdvice = "Erreur de lecture du cache local. Analyse en arrière-plan..."
                triggerAnticipatoryAnalysis(movesList, apiKey)
            }
        } else {
            coachAdvice = "Partie chargée : ${game.whiteUser} contre ${game.blackUser}. Analyse anticipée en arrière-plan..."
            triggerAnticipatoryAnalysis(movesList, apiKey)
        }

        // Immediately jump to the end of the game
        if (movesList.isNotEmpty()) {
            selectHistoryMove(movesList.size - 1, apiKey)
        }
    }

    fun triggerAnticipatoryAnalysis(moves: List<String>, apiKey: String) {
        if (moves.isEmpty()) return
        isAnalyzing = true
        viewModelScope.launch {
            try {
                // Compute all FENs for the game's moves sequentially
                val fens = mutableListOf<String>()
                var board = originalBoardState.copyOf()
                var whiteTurn = true
                for (moveStr in moves) {
                    val result = ChessEngine.parseAndExecuteAnyMove(board, moveStr, whiteTurn)
                    if (result != null) {
                        board = result.first
                    }
                    whiteTurn = !whiteTurn
                    fens.add(ChessEngine.toFen(board, whiteTurn))
                }

                // Parallel query Lichess Stockfish engine evaluations for each position
                val realStockfishEvals = mutableMapOf<Int, String>()
                coroutineScope {
                    val deferredEvals = fens.mapIndexed { index, fen ->
                        async(Dispatchers.IO) {
                            try {
                                val evalData = repository.fetchLichessCloudEval(fen)
                                index to (evalData?.score ?: "0.0")
                            } catch (e: Exception) {
                                index to "0.0"
                            }
                        }
                    }
                    deferredEvals.forEach { deferred ->
                        val (idx, evalStr) = deferred.await()
                        realStockfishEvals[idx] = evalStr
                    }
                }

                val result = repository.getAnticipatedMoveAnalyses(moves, apiKey)
                if (result.isSuccess) {
                    val rawMap = result.getOrNull() ?: emptyMap()
                    
                    val merged = rawMap.mapValues { (index, analysis) ->
                        val trueEval = realStockfishEvals[index] ?: "0.0"
                        var scoreVal = 0.0
                        try {
                            if (trueEval.contains("Mat") || trueEval.contains("Mate")) {
                                scoreVal = if (trueEval.contains("-")) -9.9 else 9.9
                            } else {
                                scoreVal = trueEval.replace("+", "").replace(" ", "").toDoubleOrNull() ?: 0.0
                            }
                        } catch (e: Exception) {}
                        
                        com.example.data.model.MoveAnalysis(
                            comment = analysis.comment,
                            evaluation = scoreVal
                        )
                    }
                    
                    cachedAnalyses = merged
                    coachAdvice = "Analyse anticipée Stockfish & IA terminée avec succès ! La navigation est 100% fluide."
                } else {
                    Log.e("ChessCoachViewModel", "Anticipatory pre-analysis failed: ${result.exceptionOrNull()?.message}")
                    coachAdvice = "Analyse anticipée indisponible. L'analyse en direct est activée pour votre navigation."
                }
            } catch (e: Exception) {
                Log.e("ChessCoachViewModel", "Failed to run background pre-analysis", e)
            } finally {
                isAnalyzing = false
                val currIdx = activeMoveIndex
                if (currIdx in 0 until moveHistoryList.size) {
                    selectHistoryMove(currIdx, apiKey)
                }
            }
        }
    }

    private fun parseKeyToMoveIndex(key: String): Int {
        val digits = key.filter { it.isDigit() }
        val num = digits.toIntOrNull() ?: return -1
        return num - 1
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
            val result = ChessEngine.parseAndExecuteAnyMove(board, moveStr, whiteTurn)
            if (result != null) {
                board = result.first
            }
            whiteTurn = !whiteTurn
        }
        currentBoardState = board
        isWhiteTurn = whiteTurn
        displayedFen = ChessEngine.toFen(board, whiteTurn)
        
        if (indexBefore != index) {
            if (index >= 0) {
                val moveStr = moveHistoryList.getOrNull(index) ?: ""
                when {
                    moveStr.endsWith("#") -> ChessSoundManager.playGameEnd()
                    moveStr.endsWith("+") -> ChessSoundManager.playCheck()
                    moveStr.contains("x") -> ChessSoundManager.playCapture()
                    else -> ChessSoundManager.playMove()
                }
            } else {
                ChessSoundManager.playMove()
            }
        }
        
        if (index == -1) {
            coachAdvice = "Prêt à démarrer ! Choisissez un coup ci-dessus."
            stockfishEval = "0.0"
            return
        }

        // CHECK CACHED ANALYSES FIRST
        val analysis = cachedAnalyses[index]
        if (analysis != null) {
            coachAdvice = analysis.comment
            val evalVal = analysis.evaluation
            stockfishEval = if (evalVal >= 0) "+${String.format(java.util.Locale.US, "%.1f", evalVal)}" else String.format(java.util.Locale.US, "%.1f", evalVal)
            speakAdvice(analysis.comment)
        } else {
            // Fallback: Trigger live Engine Evaluation + AI coaching for this move
            analyzePosition(moveHistoryList.getOrNull(index) ?: "Début", apiKey)
        }
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

    private fun getFrenchMoveNotation(boardBefore: CharArray, from: Int, to: Int): String {
        if (from !in 0..63 || to !in 0..63) return ""
        val piece = boardBefore[from]
        val pieceLetter = when (piece.lowercaseChar()) {
            'k' -> "R"
            'q' -> "D"
            'r' -> "T"
            'b' -> "F"
            'n' -> "C"
            else -> "" // Pawn
        }
        val colFrom = from % 8
        val colTo = to % 8
        if (piece.lowercaseChar() == 'k' && Math.abs(colFrom - colTo) == 2) {
            return if (colTo == 6) "O-O" else "O-O-O"
        }
        val toSquareAlgebraic = ChessEngine.getSquareAlgebraic(to / 8, to % 8)
        return "$pieceLetter$toSquareAlgebraic"
    }

    private fun classifyMove(
        scoreBefore: Double,
        scoreAfter: Double,
        playedMove: String,
        bestMoveUci: String?,
        isWhiteBefore: Boolean
    ): String {
        if (bestMoveUci != null && playedMove.equals(bestMoveUci, ignoreCase = true)) {
            return "Excellent"
        }
        
        val diff = if (isWhiteBefore) {
            scoreAfter - scoreBefore
        } else {
            scoreBefore - scoreAfter
        }
        
        return when {
            diff <= -2.0 -> "Blunder"
            diff <= -1.0 -> "Mistake"
            diff <= -0.5 -> "Inaccuracy"
            diff >= 0.0 -> "Excellent"
            else -> "Good Move"
        }
    }

    private suspend fun getEvaluationForFen(fen: String, useLocalFallback: Boolean = true): com.example.data.repository.CloudEvalData {
        if (isLocalEngineMode) {
            stockfishJsEngine.evaluate(fen)
            kotlinx.coroutines.delay(1200)
            val evalStr = stockfishJsEngine.evaluation.value
            val bestMove = stockfishJsEngine.bestMove.value.ifEmpty { null }
            
            var scoreVal = 0.0
            try {
                if (evalStr.contains("Mat") || evalStr.contains("Mate")) {
                    scoreVal = if (evalStr.contains("-")) -100.0 else 100.0
                } else {
                    scoreVal = evalStr.replace("+", "").replace(" ", "").toDoubleOrNull() ?: 0.0
                }
            } catch (e: Exception) {}
            
            return com.example.data.repository.CloudEvalData(score = evalStr, scoreVal = scoreVal, bestMove = bestMove)
        }

        val cloudData = try {
            repository.fetchLichessCloudEval(fen)
        } catch (e: Exception) {
            null
        }
        if (cloudData != null) {
            return cloudData
        }
        if (useLocalFallback) {
            stockfishJsEngine.evaluate(fen)
            kotlinx.coroutines.delay(1000)
            val evalStr = stockfishJsEngine.evaluation.value
            val bestMove = stockfishJsEngine.bestMove.value.ifEmpty { null }
            
            var scoreVal = 0.0
            try {
                if (evalStr.contains("Mat") || evalStr.contains("Mate")) {
                    scoreVal = if (evalStr.contains("-")) -100.0 else 100.0
                } else {
                    scoreVal = evalStr.replace("+", "").toDoubleOrNull() ?: 0.0
                }
            } catch (e: Exception) {}
            
            return com.example.data.repository.CloudEvalData(score = evalStr, scoreVal = scoreVal, bestMove = bestMove)
        }
        return com.example.data.repository.CloudEvalData(score = "0.0", scoreVal = 0.0, bestMove = null)
    }

    private fun analyzePosition(lastMove: String, apiKey: String) {
        isAnalyzing = true
        viewModelScope.launch {
            try {
                val currentIndex = activeMoveIndex
                val history = moveHistoryList
                
                if (currentIndex < 0) {
                    coachAdvice = "Bonjour ! Je suis votre coach FOCUS+. Jouez un coup ou sélectionnez une partie pour commencer !"
                    isAnalyzing = false
                    return@launch
                }
                
                // 1. Reconstruct board BEFORE the move
                var boardBefore = originalBoardState.copyOf()
                var whiteBefore = true
                for (i in 0 until currentIndex) {
                    val moveStr = history.getOrNull(i) ?: break
                    val parseRes = ChessEngine.parseAndExecuteAnyMove(boardBefore, moveStr, whiteBefore)
                    if (parseRes != null) {
                        boardBefore = parseRes.first
                    }
                    whiteBefore = !whiteBefore
                }
                val fenBefore = ChessEngine.toFen(boardBefore, whiteBefore)
                
                // 2. Reconstruct board AFTER the move
                var boardAfter = boardBefore.copyOf()
                var whiteAfter = whiteBefore
                val latestMoveStr = history.getOrNull(currentIndex)
                var lastFrom = -1
                var lastTo = -1
                if (latestMoveStr != null) {
                    val parseRes = ChessEngine.parseAndExecuteAnyMove(boardAfter, latestMoveStr, whiteAfter)
                    if (parseRes != null) {
                        boardAfter = parseRes.first
                    }
                    whiteAfter = !whiteAfter
                    if (latestMoveStr.length >= 4 && latestMoveStr[0] in 'a'..'h') {
                        lastFrom = ChessEngine.algebraicToSquare(latestMoveStr.substring(0, 2))
                        lastTo = ChessEngine.algebraicToSquare(latestMoveStr.substring(2, 4))
                    }
                }
                val fenAfter = ChessEngine.toFen(boardAfter, whiteAfter)
                
                // 3. Query Lichess evaluations
                val scoreBefore = getEvaluationForFen(fenBefore)
                val scoreAfter = getEvaluationForFen(fenAfter)
                
                // Determine Played French move & Best French move
                val playedFrench = if (lastFrom != -1 && lastTo != -1) {
                    getFrenchMoveNotation(boardBefore, lastFrom, lastTo)
                } else {
                    latestMoveStr ?: lastMove
                }
                
                val bestMoveUci = scoreBefore.bestMove
                val bestFrench = if (bestMoveUci != null && bestMoveUci.length >= 4) {
                    try {
                        val bFrom = ChessEngine.algebraicToSquare(bestMoveUci.substring(0, 2))
                        val bTo = ChessEngine.algebraicToSquare(bestMoveUci.substring(2, 4))
                        if (bFrom != -1 && bTo != -1) {
                            getFrenchMoveNotation(boardBefore, bFrom, bTo)
                        } else {
                            bestMoveUci
                        }
                    } catch (e: Exception) {
                        bestMoveUci
                    }
                } else {
                    "Aucun"
                }
                
                // Classify Move Type
                val moveType = classifyMove(
                    scoreBefore = scoreBefore.scoreVal,
                    scoreAfter = scoreAfter.scoreVal,
                    playedMove = latestMoveStr ?: "",
                    bestMoveUci = bestMoveUci,
                    isWhiteBefore = whiteBefore
                )
                
                // Format raw string matching "[MODE: ANALYSE] Coup : Ff4 | Type : Blunder | Éval : +1.2 -> -2.5 | Meilleur coup : O-O | FEN : ..."
                val formattedLichessData = "[MODE: ANALYSE] Coup : $playedFrench | Type : $moveType | Éval : ${scoreBefore.score} -> ${scoreAfter.score} | Meilleur coup : $bestFrench | FEN : $fenAfter"
                
                // Update UI states
                stockfishEval = scoreAfter.score
                
                val currentIdx = activeMoveIndex
                if (currentIdx >= 0) {
                    val listCopy = moveEvaluationsList.toMutableList()
                    while (listCopy.size <= currentIdx) {
                        listCopy.add("Non analysé")
                    }
                    listCopy[currentIdx] = scoreAfter.score
                    moveEvaluationsList = listCopy
                }
                
                // 4. Send Lichess crude analytics to Gemini Coach API with the strict prompt
                val aiResult = repository.getCoachInteractiveExplanation(
                    lichessInput = formattedLichessData,
                    apiKey = apiKey
                )
                
                isAnalyzing = false
                coachAdvice = aiResult.getOrDefault("Coup joué ! Demandez l'évaluation de l'IA pour approfondir la tactique.")
                
                // Speak Coach commentary out loud!
                speakAdvice(coachAdvice)
                
            } catch (e: Exception) {
                Log.e("ChessCoachViewModel", "Interactive analysis failure", e)
                isAnalyzing = false
                coachAdvice = "Erreur d'analyse. Veuillez réessayer."
            }
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

    fun generateStructuredGameCoaching(apiKey: String) {
        if (moveHistoryList.isEmpty()) {
            coachAdvice = "Veuillez d'abord jouer ou charger une partie pour générer un bilan !"
            return
        }
        isGeneratingStructuredCoaching = true
        structuredCoachingError = null
        parsedGameCoaching = null
        viewModelScope.launch {
            val pgn = getPgnString()
            val evalHistory = getEvaluationsHistoryString()
            val result = repository.getGeminiStructuredCoaching(pgn, evalHistory, apiKey)
            isGeneratingStructuredCoaching = false
            if (result.isSuccess) {
                parsedGameCoaching = result.getOrNull()
            } else {
                structuredCoachingError = result.exceptionOrNull()?.message ?: "Impossible de générer le bilan structuré de la partie."
            }
        }
    }

    fun resetBoard() {
        selectedPuzzle = null
        isDailyPuzzleCompleted = false
        puzzleMovesPlayed = emptyList()
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
        parsedGameCoaching = null
        structuredCoachingError = null
        stopSpeaking()
    }

    // --- Puzzle Solver Engine Logic ---
    fun triggerPuzzleCoachAnalysis(status: String, playedMove: String, whyItFails: String, apiKey: String) {
        val puzzle = selectedPuzzle ?: return
        isAnalyzing = true
        coachAdvice = "Analyse en cours..."
        viewModelScope.launch {
            try {
                val solutionMoves = puzzle.solution.split(" ")
                val solutionStr = solutionMoves.firstOrNull() ?: "Aucune"
                
                // Format: [MODE: PUZZLE] Statut : Échec | Thème : Fork | Coup tenté : Dxf7 | Pourquoi ça rate : La Tour g7 protège f7 | Solution Stockfish : Cg6+
                val formattedStr = "[MODE: PUZZLE] Statut : $status | Thème : ${puzzle.themes} | Coup tenté : $playedMove | Pourquoi ça rate : $whyItFails | Solution Stockfish : $solutionStr"
                
                val aiResult = repository.getCoachInteractiveExplanation(
                    lichessInput = formattedStr,
                    apiKey = apiKey
                )
                isAnalyzing = false
                coachAdvice = aiResult.getOrDefault("Conseils d'analyse non disponibles.")
                speakAdvice(coachAdvice)
            } catch (e: Exception) {
                isAnalyzing = false
                Log.e("ChessCoachViewModel", "triggerPuzzleCoachAnalysis failed", e)
            }
        }
    }

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

        // Trigger initial coach feedback with Nouveau status!
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"
        triggerPuzzleCoachAnalysis(
            status = "Nouveau",
            playedMove = "Aucun",
            whyItFails = "Aucune erreur pour l'instant. L'utilisateur commence le puzzle.",
            apiKey = apiKey
        )
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
                    val playedFrench = try {
                        getFrenchMoveNotation(currentBoardState, fromSq, index)
                    } catch (e: Exception) {
                        moveStr
                    }
                    
                    puzzleProgressMessage = "Coup imprécis. Essayez d'analyser une autre suite !"
                    ChessSoundManager.playCheck() // buzz alert sound
                    
                    // Trigger Puzzle Coach analysis for Error / Failure!
                    val apiKey = com.example.BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"
                    triggerPuzzleCoachAnalysis(
                        status = "Échec",
                        playedMove = playedFrench,
                        whyItFails = "Ce n'est pas le coup attendu de la solution tactique.",
                        apiKey = apiKey
                    )
                    
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

    fun incrementMasteredPositions(count: Int) {
        val application = getApplication<Application>()
        val prefs = application.getSharedPreferences("lotuschess_opening_prefs", Context.MODE_PRIVATE)
        masteredOpeningsPositions = (masteredOpeningsPositions + count).coerceAtMost(1000)
        prefs.edit().putInt("mastered_positions", masteredOpeningsPositions).apply()
    }

    fun preAnalyzeAllUnanalyzedGames() {
        if (isPreAnalyzingBackground) return
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY ?: "MY_GEMINI_API_KEY"
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return
        
        viewModelScope.launch(Dispatchers.Default) {
            isPreAnalyzingBackground = true
            
            // Allow the UI to complete its initial load and become fully interactive immediately
            kotlinx.coroutines.delay(2000)
            
            try {
                val games = gamesList.value
                val unanalyzed = games.filter { it.analysisExplain.isNullOrEmpty() }
                
                Log.d("ChessCoachViewModel", "Starting background pre-analysis for ${unanalyzed.size} games")
                
                for ((idx, game) in unanalyzed.withIndex()) {
                    // Let the thread breathe and process user clicks/events between games
                    kotlinx.coroutines.delay(300)
                    
                    withContext(Dispatchers.Main) {
                        preAnalysisProgress = "Analyse en arrière-plan ${idx + 1}/${unanalyzed.size} (${game.whiteUser}...) "
                    }
                    
                    // Mechanism to protect against infinite loops or extremely slow responses (5-second safety timeout)
                    val success = kotlinx.coroutines.withTimeoutOrNull(5000) {
                        try {
                            val movesList = game.moves.split(" ").filter { it.isNotEmpty() }
                            if (movesList.isEmpty()) return@withTimeoutOrNull true
                            
                            // 1. Generate FENs for all moves in this game (CPU bound!)
                            val fens = mutableListOf<String>()
                            var board = ChessEngine.parseFen(game.initialFen)
                            var whiteTurn = true
                            for (moveStr in movesList) {
                                // Yield during intensive chess simulations to process other tasks
                                kotlinx.coroutines.yield()
                                val result = ChessEngine.parseAndExecuteAnyMove(board, moveStr, whiteTurn)
                                if (result != null) {
                                    board = result.first
                                }
                                whiteTurn = !whiteTurn
                                fens.add(ChessEngine.toFen(board, whiteTurn))
                            }
                            
                            // 2. Fetch Stockfish evaluations with an ultra-short safety timeout
                            val realStockfishEvals = mutableMapOf<Int, String>()
                            fens.forEachIndexed { fIdx, fen ->
                                kotlinx.coroutines.yield()
                                try {
                                    val evalData = kotlinx.coroutines.withTimeoutOrNull(1500) {
                                        repository.fetchLichessCloudEval(fen)
                                    }
                                    realStockfishEvals[fIdx] = evalData?.score ?: "0.0"
                                } catch (e: Exception) {
                                    realStockfishEvals[fIdx] = "0.0"
                                }
                            }
                            
                            // 3. Request Gemini analyses for the moves with a short safety timeout
                            val result = kotlinx.coroutines.withTimeoutOrNull(3000) {
                                repository.getAnticipatedMoveAnalyses(movesList, apiKey)
                            }
                            if (result != null && result.isSuccess) {
                                val rawMap = result.getOrNull() ?: emptyMap()
                                
                                // Merge Stockfish + Gemini
                                val merged = rawMap.mapValues { (index, analysis) ->
                                    val trueEval = realStockfishEvals[index] ?: "0.0"
                                    var scoreVal = 0.0
                                    try {
                                        if (trueEval.contains("Mat") || trueEval.contains("Mate")) {
                                            scoreVal = if (trueEval.contains("-")) -9.9 else 9.9
                                        } else {
                                            scoreVal = trueEval.replace("+", "").replace(" ", "").toDoubleOrNull() ?: 0.0
                                        }
                                    } catch (e: Exception) {}
                                    
                                    com.example.data.model.MoveAnalysis(
                                        comment = analysis.comment,
                                        evaluation = scoreVal
                                    )
                                }
                                
                                // Serialize to JSON and save to database
                                val jsonObject = org.json.JSONObject()
                                merged.forEach { (index, analysis) ->
                                    val inner = org.json.JSONObject().apply {
                                        put("comment", analysis.comment)
                                        put("evaluation", analysis.evaluation)
                                    }
                                    jsonObject.put(index.toString(), inner)
                                }
                                
                                repository.updateGameAnalysis(game.id, jsonObject.toString())
                                Log.d("ChessCoachViewModel", "Successfully preanalyzed and cached game ${game.id}")
                            }
                        } catch (e: Exception) {
                            Log.e("ChessCoachViewModel", "Failed to background pre-analyze game ${game.id}", e)
                        }
                        true
                    }
                    
                    if (success == null) {
                        Log.e("ChessCoachViewModel", "Cancelled / Timed out background pre-analysis for game ${game.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChessCoachViewModel", "Failed in global pre-analysis scope", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isPreAnalyzingBackground = false
                    preAnalysisProgress = ""
                }
            }
        }
    }

    fun startLotusSpacedRepetition() {
        isLotusSpacedRepetitionMode = true
        loadNextSpacedRepetitionPosition()
    }

    fun loadNextSpacedRepetitionPosition() {
        val extraLines = listOf(
            OpeningLine(
                id = "caro_kann",
                name = "Défense Caro-Kann",
                description = "Une ouverture hyper-solide pour contester le pion e4 central.",
                moves = listOf(
                    OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Projetez e4 au centre.", "Correct ! e4 lance le débat central."),
                    OpeningMove(1, "Black", "c7c6", "c6", "Les noirs préparent d5 avec c6.", ""),
                    OpeningMove(2, "White", "d2d4", "d4", "Prenez tout le centre en plaçant d4.", "Parfait ! Le duo e4/d4 domine l'espace central."),
                    OpeningMove(2, "Black", "d7d5", "d5", "Les noirs attaquent votre pion e4.", "")
                )
            ),
            OpeningLine(
                id = "scandinavian",
                name = "Défense Scandinave",
                description = "L'une des contre-attaques les plus directes contre 1.e4.",
                moves = listOf(
                    OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Ouvrez fidèlement avec e4.", "Excellent ! e4 est actif."),
                    OpeningMove(1, "Black", "d7d5", "d5", "Les noirs répliquent immédiatement avec d5.", ""),
                    OpeningMove(2, "White", "e4d5", "exd5", "Capturez le pion noir d5.", "Génial ! exd5 ouvre de solides lignes de combat.")
                )
            ),
            OpeningLine(
                id = "italian_game",
                name = "Partie Italienne (Jiuoco Piano)",
                description = "Une ouverture historique ouverte privilégiant un développement rapide et sain.",
                moves = listOf(
                    OpeningMove(1, "White", "e2e4", "e4", "À vous de jouer : Proposez e4 au premier coup.", "Parfait ! e4 ouvre le jeu."),
                    OpeningMove(1, "Black", "e7e5", "e5", "Les noirs jouent de manière symétrique.", ""),
                    OpeningMove(2, "White", "g1f3", "Nf3", "Développez votre cavalier en f3 pour presser e5.", "Correct ! Nf3 est naturel."),
                    OpeningMove(2, "Black", "b8c6", "Nc6", "Le cavalier noir défend e5.", ""),
                    OpeningMove(3, "White", "f1c4", "Bc4", "Développez votre fou en c4 pour attaquer la case faible f7.", "Actif ! Bc4 définit l'Italienne mythique, visant f7 !")
                )
            )
        )
        
        val pool = defaultOpeningLines + extraLines
        val randomLine = pool.random()
        val moves = randomLine.moves
        if (moves.isEmpty()) return
        
        val randomMoveRandomIdx = Random.nextInt(moves.size)
        val targetMove = moves[randomMoveRandomIdx]
        
        selectedOpeningId = randomLine.id
        currentOpeningMoveIdx = randomMoveRandomIdx
        isOpeningCompleted = false
        customOpeningDeviationPlayed = false
        currentSpacedRepetitionOpeningName = randomLine.name
        currentSpacedRepetitionMove = targetMove
        
        // Reconstruct position up to targetMove
        originalBoardState = ChessEngine.parseFen(randomLine.initialFen)
        var board = originalBoardState.copyOf()
        var whiteTurn = true
        for (i in 0 until randomMoveRandomIdx) {
            val mv = moves[i]
            val result = ChessEngine.parseAndExecuteAnyMove(board, mv.algebraicMove, whiteTurn)
            if (result != null) {
                board = result.first
            }
            whiteTurn = !whiteTurn
        }
        currentBoardState = board
        isWhiteTurn = whiteTurn
        displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
        selectedSquare = -1
        possibleMoves = emptyList()
        
        openingProgressMessage = "LotusChess Répétition Espacée 🌸\n\nPosition issue de : ${randomLine.name}\n👉 ${targetMove.prompt}"
        speakAdvice("${targetMove.prompt}")
    }

    fun handleSpacedRepetitionSquareClick(index: Int) {
        val targetMove = currentSpacedRepetitionMove ?: return
        val piece = currentBoardState[index]
        
        if (selectedSquare == -1) {
            val expectedColor = targetMove.playerColor
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
                
                if (testMoveStr.equals(targetMove.algebraicMove, ignoreCase = true)) {
                    currentBoardState = res.first
                    val nextWhiteTurn = !isWhiteTurn
                    isWhiteTurn = nextWhiteTurn
                    displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                    
                    playMoveSound(oldBoard, currentBoardState, nextWhiteTurn)
                    
                    // Award points! Mastered +5 positions!
                    incrementMasteredPositions(5)
                    
                    isOpeningCompleted = true
                    openingProgressMessage = "Félicitations 🌸 !\n\nVous avez trouvé le coup d'ouverture exact : ${targetMove.san} !\n${targetMove.explanation}\n\n+5 positions ajoutées à votre mémorisation LotusChess (${masteredOpeningsPositions}/1000) !"
                    speakAdvice("Excellent ! Vous avez maîtrisé cinq nouvelles positions théoriques.")
                    ChessSoundManager.playGameEnd()
                } else {
                    currentBoardState = res.first
                    isWhiteTurn = !isWhiteTurn
                    displayedFen = ChessEngine.toFen(currentBoardState, isWhiteTurn)
                    customOpeningDeviationPlayed = true
                    
                    openingProgressMessage = "Déviation de l'ouverture théorique 💡 (Coup joué : ${res.second}).\n\nCe n'est pas le coup principal préconisé par le dictionnaire LotusChess de répétition espacée dans cette position. Essayez à nouveau."
                    speakAdvice("Ce coup dévie de la théorie de référence de Lotus Chess. Essayez à nouveau.")
                }
            }
            selectedSquare = -1
            possibleMoves = emptyList()
        }
    }

    fun quitSpacedRepetition() {
        isLotusSpacedRepetitionMode = false
        closeOpeningStudy()
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

