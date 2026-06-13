package com.example.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StockfishJsEngine(private val context: Context) {

    private var webView: WebView? = null
    
    private val _evaluation = MutableStateFlow("0.0")
    val evaluation: StateFlow<String> = _evaluation

    private val _status = MutableStateFlow("INITIALIZING")
    val status: StateFlow<String> = _status

    private val _bestMove = MutableStateFlow("")
    val bestMove: StateFlow<String> = _bestMove

    private val _candidateLines = MutableStateFlow<List<String>>(emptyList())
    val candidateLines: StateFlow<List<String>> = _candidateLines

    private val handler = Handler(Looper.getMainLooper())

    init {
        handler.post {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    addJavascriptInterface(StockfishInterface(), "AndroidInterface")
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("StockfishJsEngine", "Stockfish page loaded.")
                        }
                    }
                    
                    loadDataWithBaseURL("https://local.stockfish", getHtmlContent(), "text/html", "UTF-8", null)
                }
                Log.d("StockfishJsEngine", "WebView initialized successfully.")
            } catch (e: Throwable) {
                Log.e("StockfishJsEngine", "WebView creation failed", e)
                _status.value = "ERROR: ${e.message}"
            }
        }
    }

    fun evaluate(fen: String) {
        val manager = EngineManager.getInstance(context)
        evaluate(fen, manager.analysisDepth.value, manager.multiPv.value, manager.cpuLimit.value)
    }

    fun evaluate(fen: String, depth: Int, multiPv: Int, threads: Int) {
        handler.post {
            webView?.evaluateJavascript("javascript:evaluatePosition('${fen.replace("'", "\\'")}', $depth, $multiPv, $threads);", null)
        }
    }

    private fun getHtmlContent(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Stockfish Web Worker</title>
            </head>
            <body>
                <script>
                    var stockfish = null;
                    var currentFen = "";

                    function initStockfish() {
                        try {
                            const workerCode = "importScripts('https://cdnjs.cloudflare.com/ajax/libs/stockfish.js/10.0.2/stockfish.js');";
                            const blob = new Blob([workerCode], { type: 'application/javascript' });
                            const workerUrl = URL.createObjectURL(blob);
                            stockfish = new Worker(workerUrl);

                            stockfish.onmessage = function(event) {
                                var line = event.data;
                                console.log("SF: " + line);
                                
                                if (line.includes("score cp ")) {
                                    var parts = line.split(" ");
                                    var cpIdx = parts.indexOf("cp");
                                    if (cpIdx !== -1 && cpIdx + 1 < parts.length) {
                                        var cpVal = parseInt(parts[cpIdx + 1]);
                                        var scoreVal = cpVal / 100.0;
                                        var fenParts = currentFen.trim().split(" ");
                                        var isBlackMove = fenParts.length > 1 && fenParts[1] === "b";
                                        if (isBlackMove) {
                                            scoreVal = -scoreVal;
                                        }
                                        if (window.AndroidInterface) {
                                            var sign = scoreVal > 0 ? "+" : "";
                                            window.AndroidInterface.onEvaluation(sign + scoreVal.toFixed(2));
                                        }
                                    }
                                } else if (line.includes("score mate ")) {
                                    var parts = line.split(" ");
                                    var mateIdx = parts.indexOf("mate");
                                    if (mateIdx !== -1 && mateIdx + 1 < parts.length) {
                                        var mateVal = parseInt(parts[mateIdx + 1]);
                                        var fenParts = currentFen.trim().split(" ");
                                        var isBlackMove = fenParts.length > 1 && fenParts[1] === "b";
                                        if (isBlackMove) {
                                            mateVal = -mateVal;
                                        }
                                        var mateText = (mateVal > 0 ? "+" : "") + "Mat en " + Math.abs(mateVal);
                                        if (window.AndroidInterface) {
                                            window.AndroidInterface.onEvaluation(mateText);
                                        }
                                    }
                                }
                                
                                if (line.includes("multipv ")) {
                                    if (window.AndroidInterface && window.AndroidInterface.onCandidateLine) {
                                        window.AndroidInterface.onCandidateLine(line);
                                    }
                                }

                                if (line.startsWith("bestmove")) {
                                    var parts = line.split(" ");
                                    if (parts.length > 1) {
                                        var bestMove = parts[1];
                                        if (window.AndroidInterface) {
                                            window.AndroidInterface.onBestMove(bestMove);
                                        }
                                    }
                                }
                            };

                            stockfish.postMessage("uci");
                            stockfish.postMessage("isready");
                            if (window.AndroidInterface) {
                                window.AndroidInterface.onStatus("READY");
                            }
                        } catch (e) {
                            console.error("Failed to init Stockfish: " + e);
                            if (window.AndroidInterface) {
                                window.AndroidInterface.onStatus("ERROR: " + e.message);
                            }
                        }
                    }

                    function evaluatePosition(fen, depth, multiPv, threads) {
                        try {
                            if (!stockfish) {
                                initStockfish();
                            }
                            if (window.AndroidInterface && window.AndroidInterface.onClearCandidateLines) {
                                window.AndroidInterface.onClearCandidateLines();
                            }
                            var targetDepth = depth || 15;
                            var targetPV = multiPv || 1;
                            var targetThreads = threads || 4;

                            stockfish.postMessage("stop");
                            stockfish.postMessage("setoption name MultiPV value " + targetPV);
                            stockfish.postMessage("setoption name Threads value " + targetThreads);

                            currentFen = fen;
                            stockfish.postMessage("position fen " + fen);
                            stockfish.postMessage("go depth " + targetDepth);
                        } catch(err) {
                            if (window.AndroidInterface) {
                                window.AndroidInterface.onStatus("ERROR: " + err.message);
                            }
                        }
                    }

                    initStockfish();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    inner class StockfishInterface {
        @JavascriptInterface
        fun onStatus(statusMsg: String) {
            Log.d("StockfishJsEngine", "Status: $statusMsg")
            _status.value = statusMsg
        }

        @JavascriptInterface
        fun onEvaluation(eval: String) {
            Log.d("StockfishJsEngine", "Eval: $eval")
            _evaluation.value = eval
        }

        @JavascriptInterface
        fun onBestMove(bestMoveStr: String) {
            Log.d("StockfishJsEngine", "Best move: $bestMoveStr")
            _bestMove.value = bestMoveStr
        }

        @JavascriptInterface
        fun onClearCandidateLines() {
            _candidateLines.value = emptyList()
        }

        @JavascriptInterface
        fun onCandidateLine(line: String) {
            try {
                val parts = line.split(" ")
                val mpvIdx = parts.indexOf("multipv")
                if (mpvIdx != -1 && mpvIdx + 1 < parts.size) {
                    val pvNum = parts[mpvIdx + 1].toIntOrNull() ?: 1
                    
                    var scoreStr = ""
                    val cpIdx = parts.indexOf("cp")
                    if (cpIdx != -1 && cpIdx + 1 < parts.size) {
                        val cpValue = (parts[cpIdx + 1].toIntOrNull() ?: 0) / 100.0
                        scoreStr = if (cpValue >= 0) "+$cpValue" else "$cpValue"
                    } else {
                        val mateIdx = parts.indexOf("mate")
                        if (mateIdx != -1 && mateIdx + 1 < parts.size) {
                            val mateVal = parts[mateIdx + 1].toIntOrNull() ?: 0
                            scoreStr = "M$mateVal"
                        }
                    }

                    val pvIdx = parts.indexOf("pv")
                    val moves = if (pvIdx != -1 && pvIdx + 1 < parts.size) {
                        parts.subList(pvIdx + 1, parts.size).take(6).joinToString(" ")
                    } else ""

                    if (moves.isNotEmpty()) {
                        val formatted = "Var $pvNum ($scoreStr): $moves"
                        val currentList = _candidateLines.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.startsWith("Var $pvNum") }
                        if (existingIndex != -1) {
                            currentList[existingIndex] = formatted
                        } else {
                            currentList.add(formatted)
                        }
                        _candidateLines.value = currentList.sortedBy { 
                            val num = it.substringAfter("Var ").substringBefore(" ").toIntOrNull() ?: 0
                            num
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StockfishJsEngine", "Candidate PV Parsing error", e)
            }
        }
    }
}
