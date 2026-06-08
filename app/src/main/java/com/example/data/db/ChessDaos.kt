package com.example.data.db

import androidx.room.*
import com.example.data.model.ChessGameEntity
import com.example.data.model.LichessProfile
import com.example.data.model.PuzzleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LichessProfileDao {
    @Query("SELECT * FROM lichess_profile LIMIT 1")
    fun getProfileFlow(): Flow<LichessProfile?>

    @Query("SELECT * FROM lichess_profile LIMIT 1")
    suspend fun getProfileSync(): LichessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: LichessProfile)

    @Query("DELETE FROM lichess_profile")
    suspend fun deleteProfile()
}

@Dao
interface ChessGameDao {
    @Query("SELECT * FROM chess_games ORDER BY dateAdded DESC")
    fun getAllGamesFlow(): Flow<List<ChessGameEntity>>

    @Query("SELECT * FROM chess_games WHERE id = :gameId LIMIT 1")
    suspend fun getGameById(gameId: String): ChessGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<ChessGameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: ChessGameEntity)

    @Query("DELETE FROM chess_games")
    suspend fun clearGames()
}

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM targeted_puzzles ORDER BY rating ASC")
    fun getAllPuzzlesFlow(): Flow<List<PuzzleEntity>>

    @Query("SELECT * FROM targeted_puzzles WHERE isDaily = 1 LIMIT 1")
    fun getDailyPuzzleFlow(): Flow<PuzzleEntity?>

    @Query("SELECT * FROM targeted_puzzles WHERE isDaily = 1")
    suspend fun getDailyPuzzleSync(): PuzzleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPuzzles(puzzles: List<PuzzleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPuzzle(puzzle: PuzzleEntity)

    @Query("UPDATE targeted_puzzles SET isCompleted = :completed, userSolves = :solveState WHERE id = :id")
    suspend fun updatePuzzleState(id: String, completed: Boolean, solveState: Int)

    @Query("DELETE FROM targeted_puzzles WHERE isDaily = 0")
    suspend fun clearPracticePuzzles()
}
