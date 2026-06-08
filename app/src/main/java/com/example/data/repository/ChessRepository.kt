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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class NdjsonUserDetail(val name: String = "Anon")

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
}
