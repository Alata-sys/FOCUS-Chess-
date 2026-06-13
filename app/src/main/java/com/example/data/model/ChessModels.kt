package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lichess_profile")
data class LichessProfile(
    @PrimaryKey val id: String,
    val username: String,
    val blitzElo: Int,
    val bulletElo: Int,
    val rapidElo: Int,
    val classicalElo: Int,
    val puzzleElo: Int = 1500,
    val winCount: Int,
    val lossCount: Int,
    val drawCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val accessToken: String? = null
)

@Entity(tableName = "chess_games")
data class ChessGameEntity(
    @PrimaryKey val id: String,
    val whiteUser: String,
    val whiteElo: Int,
    val blackUser: String,
    val blackElo: Int,
    val winner: String, // "white", "black", "draw"
    val cadence: String, // "bullet", "blitz", "rapid", "classical"
    val moves: String, // space-separated moves
    val initialFen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    val analysisExplain: String? = null,
    val ratingDiff: Int = 0,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "targeted_puzzles")
data class PuzzleEntity(
    @PrimaryKey val id: String,
    val fen: String,
    val rating: Int,
    val solution: String, // "e2e4 e7e5" (space-separated)
    val themes: String, // "fourchette, clouage"
    val isDaily: Boolean = false,
    val isCompleted: Boolean = false,
    val userSolves: Int = 0, // 0 = not tried, 1 = correct, -1 = incorrect
    val addedDate: Long = System.currentTimeMillis()
)

data class MoveAnalysis(
    val comment: String,
    val evaluation: Double // absolute from white's perspective
)

