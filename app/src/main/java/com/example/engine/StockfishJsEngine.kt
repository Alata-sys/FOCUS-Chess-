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
        handler.post {
            webView?.evaluateJavascript("javascript:evaluatePosition('${fen.replace("'", "\\'")}');", null)
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

                    function evaluatePosition(fen) {
                        try {
                            if (!stockfish) {
                                initStockfish();
                            }
                            currentFen = fen;
                            stockfish.postMessage("stop");
                            stockfish.postMessage("position fen " + fen);
                            // Increase depth for better analysis and ensure it runs continuously.
                            // Real-time feels: stockfish runs until evaluation is decent
                            stockfish.postMessage("go depth 15");
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
    }
}
