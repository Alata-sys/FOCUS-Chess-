package com.example.data.repository

import android.util.Log
import com.example.data.db.ChessGameDao
import com.example.data.db.LichessProfileDao
import com.example.data.db.PuzzleDao
import com.example.data.model.ChessGameEntity
import com.example.data.model.LichessProfile
import com.example.data.model.PuzzleEntity
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GeminiClient
import com.example.data.network.LichessClient
import com.example.data.network.Part
import com.example.data.network.GenerationConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class NdjsonUserDetail(val name: String = "Anon")

data class CloudEvalData(
    val score: String,
    val scoreVal: Double,
    val bestMove: String?
)

@JsonClass(generateAdapter = true)
data class NdjsonPlayerDetail(
    val rating: Int? = null,
    val user: NdjsonUserDetail? = null
)

@JsonClass(generateAdapter = true)
data class NdjsonPlayers(
    val white: NdjsonPlayerDetail? = null,
    val black: NdjsonPlayerDetail? = null
)

@JsonClass(generateAdapter = true)
data class NdjsonGame(
    val id: String,
    val speed: String? = "blitz",
    val winner: String? = null,
    val players: NdjsonPlayers? = null,
    val moves: String? = null,
    val createdAt: Long? = null
)

class ChessRepository(
    private val profileDao: LichessProfileDao,
    private val gameDao: ChessGameDao,
    private val puzzleDao: PuzzleDao
) {
    val activeProfile: Flow<LichessProfile?> = profileDao.getProfileFlow()
    val gamesList: Flow<List<ChessGameEntity>> = gameDao.getAllGamesFlow()
    val practicePuzzles: Flow<List<PuzzleEntity>> = puzzleDao.getAllPuzzlesFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val gameAdapter = moshi.adapter(NdjsonGame::class.java)

    /**
     * Look up actual profiles and insert games using raw Lichess usernames publicly.
     */
    suspend fun syncLichessUser(username: String): Result<LichessProfile> = withContext(Dispatchers.IO) {
        try {
            val response = LichessClient.service.getUserProfile(username.trim())
            val profile = LichessProfile(
                id = response.id,
                username = response.username,
                blitzElo = response.perfs?.blitz?.rating ?: 1500,
                bulletElo = response.perfs?.bullet?.rating ?: 1500,
                rapidElo = response.perfs?.rapid?.rating ?: 1500,
                classicalElo = response.perfs?.classical?.rating ?: 1500,
                puzzleElo = response.perfs?.puzzle?.rating ?: 1500,
                winCount = response.count?.win ?: 0,
                lossCount = response.count?.loss ?: 0,
                drawCount = response.count?.draw ?: 0
            )
            profileDao.insertProfile(profile)
            
            // Sync user games next
            syncUserGames(profile.username)
            
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("ChessRepository", "Failed to sync Lichess profile", e)
            Result.failure(e)
        }
    }

    suspend fun exchangeOAuthAndLoadProfile(
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String
    ): Result<LichessProfile> = withContext(Dispatchers.IO) {
        try {
            val tokenResponse = LichessClient.service.exchangeOAuthCode(
                grantType = "authorization_code",
                clientId = clientId,
                code = code,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier
            )
            val accessToken = tokenResponse.accessToken
            val response = LichessClient.service.getAuthenticatedUser("Bearer $accessToken")
            val profile = LichessProfile(
                id = response.id,
                username = response.username,
                blitzElo = response.perfs?.blitz?.rating ?: 1500,
                bulletElo = response.perfs?.bullet?.rating ?: 1500,
                rapidElo = response.perfs?.rapid?.rating ?: 1500,
                classicalElo = response.perfs?.classical?.rating ?: 1500,
                puzzleElo = response.perfs?.puzzle?.rating ?: 1500,
                winCount = response.count?.win ?: 0,
                lossCount = response.count?.loss ?: 0,
                drawCount = response.count?.draw ?: 0,
                accessToken = accessToken
            )
            profileDao.insertProfile(profile)
            
            // Sync user games next
            syncUserGames(profile.username)
            
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("ChessRepository", "exchangeOAuthAndLoadProfile failed", e)
            Result.failure(e)
        }
    }

    suspend fun clearActiveProfile() = withContext(Dispatchers.IO) {
        profileDao.deleteProfile()
        gameDao.clearGames()
    }

    private suspend fun syncUserGames(username: String) {
        try {
            val responseBody = LichessClient.service.getUserGamesNdjson(username, max = 50)
            val gamesList = mutableListOf<ChessGameEntity>()
            
            responseBody.byteStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isEmpty()) continue
                    try {
                        val game = gameAdapter.fromJson(trimmed)
                        if (game != null) {
                            val whiteUser = game.players?.white?.user?.name ?: "Inconnu"
                            val whiteElo = game.players?.white?.rating ?: 1500
                            val blackUser = game.players?.black?.user?.name ?: "Inconnu"
                            val blackElo = game.players?.black?.rating ?: 1500
                            
                            val isWhiteOpp = whiteUser.equals(username, ignoreCase = true)
                            val winner = when (game.winner) {
                                "white" -> "white"
                                "black" -> "black"
                                else -> "draw"
                            }
                            
                            val entity = ChessGameEntity(
                                id = game.id,
                                whiteUser = whiteUser,
                                whiteElo = whiteElo,
                                blackUser = blackUser,
                                blackElo = blackElo,
                                winner = winner,
                                cadence = game.speed ?: "blitz",
                                moves = game.moves ?: "",
                                initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                ratingDiff = if (isWhiteOpp) (game.players?.white?.rating ?: 1500) else (game.players?.black?.rating ?: 1500),
                                dateAdded = game.createdAt ?: System.currentTimeMillis()
                            )
                            gamesList.add(entity)
                        }
                    } catch (ex: Exception) {
                        Log.e("ChessRepository", "Error parsing NdJson game line", ex)
                    }
                }
            }
            if (gamesList.isNotEmpty()) {
                gameDao.clearGames()
                gameDao.insertGames(gamesList)
            }
        } catch (e: Exception) {
            Log.e("ChessRepository", "Failed to download ndjson games", e)
        }
    }

    /**
     * Query Stockfish Cloud Evaluation to get the analysis of the board state.
     */
    suspend fun fetchStockfishEvaluation(fen: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = LichessClient.service.getCloudEvaluation(fen)
            val firstPv = response.pvs.firstOrNull()
            if (firstPv != null) {
                val score = when {
                    firstPv.mate != null -> "Mat en ${firstPv.mate}"
                    firstPv.cp != null -> {
                        val cpValue = firstPv.cp / 100.0
                        val prefix = if (cpValue > 0) "+" else ""
                        "$prefix$cpValue"
                    }
                    else -> "Égalité"
                }
                Result.success(score)
            } else {
                Result.success("Non disponible")
            }
        } catch (e: Exception) {
            // Evaluated fallback
            Result.failure(e)
        }
    }

    /**
     * Query Stockfish Cloud Evaluation to get high-fidelity structured analysis.
     */
    suspend fun fetchLichessCloudEval(fen: String): CloudEvalData? = withContext(Dispatchers.IO) {
        try {
            val response = LichessClient.service.getCloudEvaluation(fen)
            val firstPv = response.pvs.firstOrNull()
            if (firstPv != null) {
                val scoreVal = when {
                    firstPv.mate != null -> if (firstPv.mate > 0) 100.0 else -100.0
                    firstPv.cp != null -> firstPv.cp / 100.0
                    else -> 0.0
                }
                val scoreStr = when {
                    firstPv.mate != null -> if (firstPv.mate > 0) "+Mate en ${firstPv.mate}" else "-Mate en ${Math.abs(firstPv.mate)}"
                    firstPv.cp != null -> {
                        val cpValue = firstPv.cp / 100.0
                        val prefix = if (cpValue > 0) "+" else ""
                        "$prefix$cpValue"
                    }
                    else -> "0.0"
                }
                val bestMove = firstPv.moves.split(" ").firstOrNull()
                CloudEvalData(score = scoreStr, scoreVal = scoreVal, bestMove = bestMove)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ChessRepository", "fetchLichessCloudEval failed", e)
            null
        }
    }

    /**
     * Translates raw Lichess move analytics into structured Coach advice using the configured systemInstruction prompt.
     */
    suspend fun getCoachInteractiveExplanation(
        lichessInput: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(
                "Le Coach FOCUS+ requiert une clé API Gemini. Veuillez configurer votre clé API sécurisée dans le panneau d'AI Studio."
            )
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = lichessInput)))),
            generationConfig = GenerationConfig(temperature = 0.3f), // Lower temperature to avoid hallucinated moves
            systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = "Tu es FOCUS+ Chess Coach, une intelligence artificielle intégrée à une application web d’analyse d’échecs.\n\n" +
                                "CONTEXTE :\n" +
                                "L’application affiche déjà un échiquier interactif (type Lichess), un historique des coups, une évaluation Stockfish et parfois un meilleur coup recommandé.\n" +
                                "Tu ne vois pas l’échiquier directement. Tu reçois uniquement des données textuelles issues de Lichess.\n\n" +
                                "RÔLE :\n" +
                                "Tu es un coach d’échecs pédagogique qui explique les positions comme un entraîneur humain assis à côté du joueur.\n\n" +
                                "DONNÉES QUE TU PEUX RECEVOIR :\n" +
                                "- Coup joué\n" +
                                "- Position FEN\n" +
                                "- Évaluation Stockfish (ex: +1.2, -2.5)\n" +
                                "- Meilleur coup recommandé\n" +
                                "- Type d’erreur (blunder, mistake, inaccuracy, good move)\n" +
                                "- Mode : ANALYSE ou PUZZLE\n\n" +
                                "RÈGLES STRICTES :\n" +
                                "- Ne jamais calculer un coup d’échecs\n" +
                                "- Ne jamais inventer une position ou une variante\n" +
                                "- Ne jamais utiliser des informations non fournies\n" +
                                "- Si une donnée manque, rester général sans supposer\n" +
                                "- Ne jamais mentionner Lichess, API, moteur ou données techniques\n\n" +
                                "STYLE :\n" +
                                "- Direct, humain, pédagogique\n" +
                                "- Court et impactant\n" +
                                "- Pas d’introduction ni de conclusion inutiles\n\n" +
                                "MODES DE SORTIE :\n\n" +
                                "MODE ANALYSE :\n" +
                                "- Explique le coup joué simplement (bon / mauvais / pourquoi)\n" +
                                "- Explique l’impact sur la position (initiative, sécurité du roi, structure, matériel)\n" +
                                "- Explique le meilleur coup donné par l’analyse comme une idée stratégique humaine\n" +
                                "- Donne une leçon ou règle à retenir\n\n" +
                                "MODE PUZZLE :\n" +
                                "- Identifie le thème tactique (fourchette, clouage, attaque double, etc.)\n" +
                                "- Explique pourquoi le coup tenté échoue\n" +
                                "- Donne un indice pour trouver la solution sans donner directement le coup\n\n" +
                                "IMPORTANT :\n" +
                                "Tu es un coach pédagogique, pas un moteur d’échecs. Ton rôle est d’expliquer et de faire comprendre, pas de calculer."
                    )
                )
            )
        )

        try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Analyse indisponible."
            Result.success(reply)
        } catch (e: Exception) {
            Log.e("ChessRepository", "Interactive coaching call failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a conversational Chess Coach brief explaining the tactic behind the position.
     */
    suspend fun getGeminiCoaching(
        fen: String,
        lastMove: String,
        isWhiteTurn: Boolean,
        evaluation: String,
        pgn: String,
        evalHistory: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(
                "Désolé, la clef API Gemini n'est pas configurée dans AI Studio. Pour activer l'analyse IA complète, veuillez l'ajouter dans l'onglet des Secrets."
            )
        }
        
        val prompt = if (pgn.trim().isNotEmpty() && pgn != "*") {
            """
                Explique ce coup critique en français de manière pédagogique et professionnelle, comme un entraîneur d'échecs d'élite (Grand Maître).
                
                Informations globales de la partie:
                - PGN de la partie : $pgn
                - Historique des évaluations Stockfish :
                $evalHistory
                
                Coup ciblé :
                - FEN actuelle : $fen
                - Dernier coup joué : $lastMove
                - Au tour des : ${if (isWhiteTurn) "blancs" else "noirs"}
                - Évaluation Stockfish de ce coup : $evaluation
                
                Donne une analyse claire, bien structurée et convaincante en 2-3 phrases courtes (pour le confort du TTS vocal). Explique l'intention stratégique ou tactique (menace directe, contrôle du centre, clouage, opportunité manquée...) en tenant compte du PGN global et du changement d'évaluation. Sois chaleureux et encourageant !
            """.trimIndent()
        } else {
            """
                Explique ce coup critique en français de manière pédagogique et chaleureuse, comme un Grand Maître en échecs.
                FEN actuelle: $fen
                Dernier coup joué: $lastMove
                Tour des ${if (isWhiteTurn) "blancs" else "noirs"}.
                Évaluation Stockfish actuelle : $evaluation.
                Donne une analyse claire en 2-3 phrases courtes, idéales pour être synthétisées vocalement (TTS). Focus sur l'idée tactique (attaque, défense, contrôle du centre, clouage, fourchette) de façon fluide.
            """.trimIndent()
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.6f),
            systemInstruction = Content(parts = listOf(Part(text = "Tu es FOCUS+, un entraîneur d'échecs d'élite et chaleureux. Tu expliques les choix de manière fluide et simple en français.")))
        )

        try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Coup très intéressant ! Continuez à explorer ce plan."
            Result.success(reply)
        } catch (e: Exception) {
            Log.e("ChessRepository", "Gemini call failure", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a comprehensive full-game coaching commentary based on the entire PGN and Stockfish evaluations list.
     */
    suspend fun getGeminiGameSummary(
        pgn: String,
        evalHistory: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(
                "Le rapport d'analyse complet requiert une clé API Gemini valide configurée dans AI Studio."
            )
        }

        val prompt = """
            Analyse et commente cette partie d'échecs complète en français comme un entraîneur d'élite (Grand Maître).
            Utilise le PGN et l'historique des évaluations Stockfish ci-dessous pour fournir un rapport d'analyse global perspicace et divertissant.
            
            PGN de la partie :
            $pgn
            
            Historique complet des évaluations Stockfish :
            $evalHistory
            
            Rédige un rapport structuré avec les sections suivantes (formate en Markdown propre et attrayant) :
            1. 🏆 **Verdict Global** (Analyse du style de jeu global, qui a dominé et le tournant stratégique de la partie)
            2. 📈 **Points Forts & Faiblesses** (Les moments d'éclat tactique ou les gaffes commises par les joueurs)
            3. 💡 **Le Conseil du Pro** (Un axe de travail ou recommandation concrète pour s'améliorer)
            
            Sois constructif, chaleureux, et pédagogique. Limite l'analyse à environ 150-200 mots au total pour qu'elle reste fluide et agréable à lire.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(parts = listOf(Part(text = "Tu es FOCUS+, un grand entraîneur d'échecs d'élite. Tu coaches avec passion, clarté et gentillesse.")))
        )

        try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Impossible de générer le rapport. Veuillez réessayer."
            Result.success(reply)
        } catch (e: Exception) {
            Log.e("ChessRepository", "Gemini summary call failure", e)
            Result.failure(e)
        }
    }

    /**
     * Download daily puzzle directly from Lichess public API.
     */
    suspend fun syncDailyPuzzle(): Result<PuzzleEntity> = withContext(Dispatchers.IO) {
        try {
            val responseBody = LichessClient.service.getDailyPuzzle()
            val rawJson = responseBody.string()
            val jsonObject = JSONObject(rawJson)
            
            val puzzleObj = jsonObject.getJSONObject("puzzle")
            val gameObj = jsonObject.getJSONObject("game")
            
            val id = puzzleObj.getString("id")
            val fen = puzzleObj.getString("fen")
            val rating = puzzleObj.getInt("rating")
            
            val solutionArray = puzzleObj.getJSONArray("solution")
            val solutionBuilder = StringBuilder()
            for (i in 0 until solutionArray.length()) {
                solutionBuilder.append(solutionArray.getString(i))
                if (i < solutionArray.length() - 1) {
                    solutionBuilder.append(" ")
                }
            }
            
            val themesArray = puzzleObj.getJSONArray("themes")
            val themesBuilder = StringBuilder()
            for (i in 0 until themesArray.length()) {
                themesBuilder.append(themesArray.getString(i))
                if (i < themesArray.length() - 1) {
                    themesBuilder.append(", ")
                }
            }
            
            val puzzle = PuzzleEntity(
                id = id,
                fen = fen,
                rating = rating,
                solution = solutionBuilder.toString(),
                themes = themesBuilder.toString(),
                isDaily = true
            )
            puzzleDao.insertPuzzle(puzzle)
            Result.success(puzzle)
        } catch (e: Exception) {
            Log.e("ChessRepository", "Daily puzzle syncing failed", e)
            Result.failure(e)
        }
    }

    suspend fun solvePuzzle(id: String, state: Boolean) {
        puzzleDao.updatePuzzleState(id, state, if (state) 1 else -1)
    }

    suspend fun feedPracticePuzzles(puzzles: List<PuzzleEntity>) {
        puzzleDao.insertPuzzles(puzzles)
    }

    /**
     * Anticipated Analysis System (Anti-Lag Bullet): Takes all moves of the game,
     * queries Gemini once in background, receives JSON structure mapping each move to an explanation and evaluation.
     * Supports games with up to 300+ moves by chunking them into smaller concurrent requests.
     */
    suspend fun getAnticipatedMoveAnalyses(
        moves: List<String>,
        apiKey: String
    ): Result<Map<Int, com.example.data.model.MoveAnalysis>> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Clé API Gemini absente ou non configurée."))
        }

        val chunkSize = 40
        val mergedMap = mutableMapOf<Int, com.example.data.model.MoveAnalysis>()

        // Split all moves into chunks of 40
        val chunks = moves.chunked(chunkSize)
        
        coroutineScope {
            val deferreds = chunks.mapIndexed { chunkIdx, chunkMoves ->
                val startOffset = chunkIdx * chunkSize
                async {
                    getAnticipatedMoveAnalysesForChunk(chunkMoves, startOffset, apiKey)
                }
            }

            var anySuccess = false
            var lastError: Exception? = null

            for (deferred in deferreds) {
                val res = deferred.await()
                if (res.isSuccess) {
                    anySuccess = true
                    val map = res.getOrNull() ?: emptyMap()
                    mergedMap.putAll(map)
                } else {
                    lastError = res.exceptionOrNull() as? Exception ?: Exception("Analyse de tronçon échouée.")
                }
            }

            if (anySuccess) {
                Result.success(mergedMap)
            } else {
                Result.failure(lastError ?: Exception("Tous les tronçons d'analyse ont échoué."))
            }
        }
    }

    private suspend fun getAnticipatedMoveAnalysesForChunk(
        chunkMoves: List<String>,
        startOffset: Int,
        apiKey: String
    ): Result<Map<Int, com.example.data.model.MoveAnalysis>> = withContext(Dispatchers.IO) {
        val totalCount = chunkMoves.size
        val endOffset = startOffset + totalCount - 1
        
        val movesJoin = chunkMoves.mapIndexed { idx, m -> "Coup demi-coup (ply index) ${startOffset + idx} : $m" }.joinToString("\n")
        val prompt = "Voici un extrait des coups (demi-coups/plies) de la partie d'échecs (du coup index ${startOffset} au coup ${endOffset}) :\n" +
                "$movesJoin\n\n" +
                "Tu es un grand maître d'échecs et coach d'élite. Tu dois analyser chacun des coups de cette liste en une seule fois.\n" +
                "En te basant sur le contexte global, génère une réponse sous forme d'un UNIQUE objet JSON plat contenant obligatoirement pour chaque index de demi-coup (depuis '${startOffset}' jusqu'à '${endOffset}') un commentaire pédagogique court (2 phrases maximum, chaleureux) et la valeur de l'évaluation numérique absolue du point de vue des Blancs (en nombre de pions : par exemple +0.8 ou 1.2 ; valeurs négatives si l'avantage est noir, ex: -1.5 ; 0.0 pour l'égalité). Si c'est un mat forcé pour les blancs, mets +99.0, et pour les noirs -99.0.\n" +
                "Renvoie DIRECTEMENT le JSON sans bloc markdown (pas de ```json), sans introduction, sans conclusion.\n" +
                "Structure JSON attendue :\n" +
                "{\n" +
                "  \"$startOffset\": {\n" +
                "    \"comment\": \"Excellent coup de développement qui prend le contrôle du centre.\",\n" +
                "    \"evaluation\": 0.35\n" +
                "  }\n" +
                "}"

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.4f),
            systemInstruction = Content(parts = listOf(Part(text = "Tu es un assistant d'échecs expert qui s'exprime uniquement par un format JSON d'explications de coups et d'évaluations de position.")))
        )

        try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            var text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("Réponse vide de Gemini pour le tronçon $startOffset"))
            
            // Clean up codeblock if present
            if (text.contains("```json")) {
                text = text.substringAfter("```json").substringBefore("```")
            } else if (text.contains("```")) {
                text = text.substringAfter("```").substringBefore("```")
            }
            text = text.trim()
            
            val jsonObject = org.json.JSONObject(text)
            val resultMap = mutableMapOf<Int, com.example.data.model.MoveAnalysis>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val digits = key.filter { it.isDigit() }
                val idx = digits.toIntOrNull() ?: continue
                
                val innerObj = jsonObject.optJSONObject(key)
                if (innerObj != null) {
                    val comment = innerObj.optString("comment", "Coup analysé.")
                    val eval = innerObj.optDouble("evaluation", 0.0)
                    resultMap[idx] = com.example.data.model.MoveAnalysis(comment, eval)
                } else {
                    // Fallback
                    val comment = jsonObject.optString(key, "Coup analysé.")
                    resultMap[idx] = com.example.data.model.MoveAnalysis(comment, 0.0)
                }
            }
            Result.success(resultMap)
        } catch (e: Exception) {
            Log.e("ChessRepository", "getAnticipatedMoveAnalysesForChunk error at chunk $startOffset", e)
            Result.failure(e)
        }
    }

    suspend fun updateGameAnalysis(gameId: String, jsonAnalysis: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getGameById(gameId)
        if (game != null) {
            val updated = game.copy(analysisExplain = jsonAnalysis)
            gameDao.insertGame(updated)
        }
    }
}
