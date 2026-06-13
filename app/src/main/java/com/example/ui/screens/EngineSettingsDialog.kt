package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.AnalysisEngineType
import com.example.engine.ChessEnginePreset
import com.example.ui.coach.ChessCoachViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EngineSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ChessCoachViewModel
) {
    val engineManager = viewModel.engineManager
    
    val activeEngineType by engineManager.activeEngineType.collectAsState()
    val depth by engineManager.analysisDepth.collectAsState()
    val multiPv by engineManager.multiPv.collectAsState()
    val cpuLimit by engineManager.cpuLimit.collectAsState()
    val isDeepAnalysis by engineManager.isDeepAnalysis.collectAsState()
    val activePresetName by engineManager.activePresetName.collectAsState()
    val installedEngines by engineManager.installedEnginesSet.collectAsState()
    val customEngines by engineManager.customEngines.collectAsState()

    var showAddEngineModal by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2D3035), RoundedCornerShape(16.dp))
                .testTag("engine_settings_dialog_root"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141618))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = Color(0xFF81B64C), // Chess.com green
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Paramètres du Moteur",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.Gray
                        )
                    }
                }

                Divider(color = Color(0xFF2D3035), modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable configs list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    
                    // --- MOTEUR D'ANALYSE SECTOR ---
                    Text(
                        text = "Moteur d'analyse",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81B64C),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Lichess Cloud Engine Option
                    EngineSelectionRow(
                        title = "Analyse Lichess (En ligne, défaut)",
                        description = "Requêtes cloud ultra-rapides et économes en batterie.",
                        isSelected = activeEngineType == AnalysisEngineType.LICHESS,
                        onClick = { engineManager.setActiveEngine(AnalysisEngineType.LICHESS) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stockfish Local Option
                    EngineSelectionRow(
                        title = "Analyse Stockfish Local (Hors-ligne)",
                        description = "Exécution locale au sein de l'appareil par un Web Worker d'élite.",
                        isSelected = activeEngineType == AnalysisEngineType.STOCKFISH_LOCAL,
                        onClick = { engineManager.setActiveEngine(AnalysisEngineType.STOCKFISH_LOCAL) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Button: Add Moteur
                    Button(
                        onClick = { showAddEngineModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x2281B64C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF81B64C), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("➕ Ajouter ou Gérer les versions de moteurs", color = Color(0xFF81B64C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- STOCKFISH OFFLINE SETTINGS (ONLY SHOWN IF STOCKFISH LOCAL SELECTED) ---
                    AnimatedVisibility(
                        visible = activeEngineType == AnalysisEngineType.STOCKFISH_LOCAL,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Divider(color = Color(0xFF2D3035), modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                text = "Réglages de l'Analyse Hors-ligne",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B7399),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Quick vs Deep presets selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B1D20))
                                    .border(1.dp, Color(0xFF2D3035), RoundedCornerShape(8.dp)),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { engineManager.setDeepAnalysis(false) }
                                        .background(if (!isDeepAnalysis) Color(0xFF2D3035) else Color.Transparent)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Analyse rapide (15/1)",
                                        color = if (!isDeepAnalysis) Color.White else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { engineManager.setDeepAnalysis(true) }
                                        .background(if (isDeepAnalysis) Color(0xFF4B7399) else Color.Transparent)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Analyse approfondie (22/3)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Active LocalPreset version row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B1D20))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Version Stockfish Active", color = Color.White, fontSize = 12.sp)
                                Text(activePresetName, color = Color(0xFF81B64C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Slider Analysis Depth
                            SliderConfigRow(
                                label = "Profondeur de l'analyse",
                                value = depth,
                                range = 1..40,
                                onValueChange = { engineManager.setAnalysisDepth(it) }
                            )

                            // Slider MultiPV Variants
                            SliderConfigRow(
                                label = "Nombre de variantes (MultiPV)",
                                value = multiPv,
                                range = 1..5,
                                onValueChange = { engineManager.setMultiPv(it) }
                            )

                            // Slider CPU Thread limit
                            SliderConfigRow(
                                label = "Limite CPU (Threads)",
                                value = cpuLimit,
                                range = 1..8,
                                onValueChange = { engineManager.setCpuLimit(it) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Divider(color = Color(0xFF2D3035), modifier = Modifier.padding(vertical = 12.dp))

                // Footer Actions
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81B64C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- SUBDIALOG FOR ADDING AND MANAGING ENGINES ---
    if (showAddEngineModal) {
        ManageEnginesModal(
            onDismiss = { showAddEngineModal = false },
            presets = engineManager.presets,
            customEngines = customEngines,
            installedEnginesSet = installedEngines,
            activePresetName = activePresetName,
            onInstall = { engineManager.installEngine(it) },
            onUninstall = { engineManager.uninstallEngine(it) },
            onSelectActive = { engineManager.setActivePreset(it) },
            onAddCustom = { name, ver, sz -> engineManager.addCustomEngine(name, ver, sz) },
            onRemoveCustom = { engineManager.removeCustomEngine(it) }
        )
    }
}

@Composable
fun EngineSelectionRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF1B1D20) else Color(0xFF16181A))
            .border(1.dp, if (isSelected) Color(0xFF81B64C) else Color(0xFF2D3035), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF81B64C),
                unselectedColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = Color.Gray, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun SliderConfigRow(
    label: String,
    value: Int,
    range: ClosedRange<Int>,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value.toString(), color = Color(0xFF4B7399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4B7399),
                activeTrackColor = Color(0xFF4B7399),
                inactiveTrackColor = Color(0xFF2D3035)
            )
        )
    }
}

@Composable
fun ManageEnginesModal(
    onDismiss: () -> Unit,
    presets: List<ChessEnginePreset>,
    customEngines: List<ChessEnginePreset>,
    installedEnginesSet: Set<String>,
    activePresetName: String,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onSelectActive: (String) -> Unit,
    onAddCustom: (String, String, String) -> Unit,
    onRemoveCustom: (String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customVersion by remember { mutableStateOf("") }
    var customSize by remember { mutableStateOf("10 MB") }
    var showForm by remember { mutableStateOf(false) }

    // Simulating download states per engine name
    val installingMap = remember { mutableStateMapOf<String, Float>() }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2E3238), RoundedCornerShape(14.dp))
                .testTag("manage_engines_modal_root"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101113))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Gérer les Version Stockfish",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.LightGray)
                    }
                }

                Divider(color = Color(0xFF2D3035), modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Combine lists
                    val allEngines = (presets + customEngines).distinctBy { it.name }

                    allEngines.forEach { engine ->
                        val isInstalled = installedEnginesSet.contains(engine.name)
                        val isActive = activePresetName == engine.name
                        val isInstallingVal = installingMap[engine.name]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) Color(0xFF1D221A) else Color(0xFF16181A))
                                .border(1.dp, if (isActive) Color(0xFF81B64C) else Color(0xFF2D3035), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = engine.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isActive) Color(0xFF81B64C) else Color.White
                                    )
                                    if (engine.isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "[Defaut]",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Thin,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Text("Version: ${engine.version} • Taille: ${engine.size}", color = Color.Gray, fontSize = 11.sp)
                                
                                // Progress bar if installing
                                if (isInstallingVal != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = isInstallingVal,
                                        color = Color(0xFF81B64C),
                                        trackColor = Color(0x3381B64C),
                                        modifier = Modifier.fillMaxWidth(0.8f).height(4.dp)
                                    )
                                }
                            }

                            // Dynamic Statut & actions buttons
                            Column(horizontalAlignment = Alignment.End) {
                                if (isInstallingVal != null) {
                                    Text(
                                        text = "Installation: ${(isInstallingVal * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFCC00)
                                    )
                                } else if (!isInstalled) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                // Simulating smooth aesthetic download
                                                for (prog in 0..10) {
                                                    installingMap[engine.name] = prog / 10f
                                                    delay(150)
                                                }
                                                installingMap.remove(engine.name)
                                                onInstall(engine.name)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81B64C)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Installer", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                } else {
                                    // Installed and Selectable
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = Color(0xFF81B64C), modifier = Modifier.size(16.dp))
                                        } else {
                                            Text(
                                                text = "Activer",
                                                color = Color(0xFF2196F3),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { onSelectActive(engine.name) }
                                                    .padding(6.dp)
                                                    .testTag("action_select_engine_${engine.name}")
                                            )
                                        }
                                        
                                        // Delete option (never allow deleting Stockfish Lite default)
                                        if (engine.name != "Stockfish Lite") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Supprimer",
                                                tint = Color(0xFFE53935),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable {
                                                        if (customEngines.any { it.name == engine.name }) {
                                                            onRemoveCustom(engine.name)
                                                        } else {
                                                            onUninstall(engine.name)
                                                        }
                                                    }
                                                    .testTag("action_delete_engine_${engine.name}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!showForm) {
                        OutlinedButton(
                            onClick = { showForm = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Text("➕ Ajouter un Moteur Customisé uci", color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        // Custom Engine Registration Form
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1D20)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF2D3035), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enregistrer un moteur Custom UCI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it },
                                    label = { Text("Nom du Moteur", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_custom_engine_name"),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF81B64C))
                                )

                                OutlinedTextField(
                                    value = customVersion,
                                    onValueChange = { customVersion = it },
                                    label = { Text("Version", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF81B64C))
                                )

                                OutlinedTextField(
                                    value = customSize,
                                    onValueChange = { customSize = it },
                                    label = { Text("Taille (ex: 24 MB)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF81B64C))
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            if (customName.isNotBlank() && customVersion.isNotBlank()) {
                                                onAddCustom(customName, customVersion, customSize)
                                                customName = ""
                                                customVersion = ""
                                                showForm = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81B64C)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Installer", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { showForm = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Annuler", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF2D3035), modifier = Modifier.padding(vertical = 12.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D20)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Text("Fermer", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
