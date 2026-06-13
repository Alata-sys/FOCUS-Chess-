package com.example.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AnalysisEngineType {
    LICHESS,
    STOCKFISH_LOCAL
}

data class ChessEnginePreset(
    val name: String,
    val version: String,
    val size: String,
    val isInstalled: Boolean,
    val isDefault: Boolean = false
)

class EngineManager private constructor(context: Context) {

    private val sharedPrefs = context.getSharedPreferences("engine_manager_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: EngineManager? = null

        fun getInstance(context: Context): EngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EngineManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // --- ENGINE SELECTION ---
    private val _activeEngineType = MutableStateFlow(
        AnalysisEngineType.valueOf(
            sharedPrefs.getString("active_engine_type", AnalysisEngineType.LICHESS.name) ?: AnalysisEngineType.LICHESS.name
        )
    )
    val activeEngineType: StateFlow<AnalysisEngineType> = _activeEngineType

    fun setActiveEngine(type: AnalysisEngineType) {
        _activeEngineType.value = type
        sharedPrefs.edit().putString("active_engine_type", type.name).apply()
        Log.d("EngineManager", "Active engine configured to: ${type.name}")
    }

    // --- ANALYSIS CONFIGS ---
    private val _analysisDepth = MutableStateFlow(sharedPrefs.getInt("analysis_depth", 15))
    val analysisDepth: StateFlow<Int> = _analysisDepth

    fun setAnalysisDepth(depth: Int) {
        _analysisDepth.value = depth.coerceIn(1, 40)
        sharedPrefs.edit().putInt("analysis_depth", _analysisDepth.value).apply()
    }

    private val _multiPv = MutableStateFlow(sharedPrefs.getInt("multi_pv", 1))
    val multiPv: StateFlow<Int> = _multiPv

    fun setMultiPv(pv: Int) {
        _multiPv.value = pv.coerceIn(1, 5)
        sharedPrefs.edit().putInt("multi_pv", _multiPv.value).apply()
    }

    private val _cpuLimit = MutableStateFlow(sharedPrefs.getInt("cpu_limit", 4))
    val cpuLimit: StateFlow<Int> = _cpuLimit

    fun setCpuLimit(limit: Int) {
        _cpuLimit.value = limit.coerceIn(1, 8)
        sharedPrefs.edit().putInt("cpu_limit", _cpuLimit.value).apply()
    }

    private val _isDeepAnalysis = MutableStateFlow(sharedPrefs.getBoolean("is_deep_analysis", false))
    val isDeepAnalysis: StateFlow<Boolean> = _isDeepAnalysis

    fun setDeepAnalysis(isDeep: Boolean) {
        _isDeepAnalysis.value = isDeep
        sharedPrefs.edit().putBoolean("is_deep_analysis", isDeep).apply()
        if (isDeep) {
            // Apply deep analysis settings
            setAnalysisDepth(22)
            setMultiPv(3)
        } else {
            // Back to standard quick analysis
            setAnalysisDepth(15)
            setMultiPv(1)
        }
    }

    // --- PRESETS & ENGINE LISTS ---
    private val defaultInstalledPresetsSet = setOf("Stockfish Lite")
    private val _installedEnginesSet = MutableStateFlow(
        sharedPrefs.getStringSet("installed_engines", defaultInstalledPresetsSet) ?: defaultInstalledPresetsSet
    )
    val installedEnginesSet: StateFlow<Set<String>> = _installedEnginesSet

    private val _activePresetName = MutableStateFlow(
        sharedPrefs.getString("active_preset_name", "Stockfish Lite") ?: "Stockfish Lite"
    )
    val activePresetName: StateFlow<String> = _activePresetName

    fun setActivePreset(presetName: String) {
        _activePresetName.value = presetName
        sharedPrefs.edit().putString("active_preset_name", presetName).apply()
        Log.d("EngineManager", "Active local engine preset configured to: $presetName")
    }

    // Real static presets inside the application code
    val presets = listOf(
        ChessEnginePreset("Stockfish Lite", "10.0.2 (WASM)", "4 MB", true, true),
        ChessEnginePreset("Stockfish 16", "16.0 (WASM)", "12 MB", false),
        ChessEnginePreset("Stockfish 17", "17.0 (WASM)", "16 MB", false),
        ChessEnginePreset("Stockfish NNUE", "17.0-NNUE (WASM)", "32 MB", false)
    )

    // User extra custom engines (e.g. from the modal + button)
    private val _customEngines = MutableStateFlow(loadCustomEngines())
    val customEngines: StateFlow<List<ChessEnginePreset>> = _customEngines

    fun installEngine(name: String) {
        val current = _installedEnginesSet.value.toMutableSet()
        current.add(name)
        _installedEnginesSet.value = current
        sharedPrefs.edit().putStringSet("installed_engines", current).apply()
    }

    fun uninstallEngine(name: String) {
        val current = _installedEnginesSet.value.toMutableSet()
        current.remove(name)
        _installedEnginesSet.value = current
        sharedPrefs.edit().putStringSet("installed_engines", current).apply()
        if (activePresetName.value == name) {
            setActivePreset("Stockfish Lite")
        }
    }

    fun addCustomEngine(name: String, version: String, size: String) {
        val preset = ChessEnginePreset(name, version, size, isInstalled = true)
        val list = _customEngines.value.toMutableList()
        list.add(preset)
        _customEngines.value = list
        saveCustomEngines(list)
        installEngine(name)
    }

    fun removeCustomEngine(name: String) {
        val list = _customEngines.value.toMutableList()
        list.removeAll { it.name == name }
        _customEngines.value = list
        saveCustomEngines(list)
        uninstallEngine(name)
    }

    private fun loadCustomEngines(): List<ChessEnginePreset> {
        val serialized = sharedPrefs.getStringSet("custom_engines_serialized", emptySet()) ?: emptySet()
        return serialized.mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size >= 3) {
                ChessEnginePreset(parts[0], parts[1], parts[2], isInstalled = true)
            } else null
        }
    }

    private fun saveCustomEngines(list: List<ChessEnginePreset>) {
        val serialized = list.map { "${it.name}|${it.version}|${it.size}" }.toSet()
        sharedPrefs.edit().putStringSet("custom_engines_serialized", serialized).apply()
    }
}
