package com.chesshelper;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.*;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private int lines = 1;
    private int depth = 12;
    private boolean isChessCom = false;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("chess_helper", MODE_PRIVATE);
        lines = prefs.getInt("lines", 1);
        depth = prefs.getInt("depth", 12);

        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.addJavascriptInterface(new ChessDebugInterface(), "ChessDebug");
        webView.setWebViewClient(new WebViewClient() {
            private String lastUrl = "";
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.contains("lichess.org") || url.contains("chess.com")) {
                    injectChessHelper();
                }
            }
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (!url.equals(lastUrl) && url.contains("chess.com")) {
                    lastUrl = url;
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        injectChessHelper();
                    }, 2000);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                android.util.Log.d("ChessHelper", cm.message());
                return true;
            }
        });
        webView.loadUrl("https://lichess.org");

        findViewById(R.id.settingsBtn).setOnClickListener(v -> showSettings());

        Button switchBtn = findViewById(R.id.switchSiteBtn);
        switchBtn.setOnClickListener(v -> {
            isChessCom = !isChessCom;
            if (isChessCom) {
                switchBtn.setText("lichess.org");
                switchBtn.setTextColor(0xFF22C55E);
                webView.loadUrl("https://chess.com");
            } else {
                switchBtn.setText("chess.com");
                switchBtn.setTextColor(0xFF3B82F6);
                webView.loadUrl("https://lichess.org");
            }
        });
    }

    private void showSettings() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        SeekBar depthBar = view.findViewById(R.id.depthBar);
        TextView depthVal = view.findViewById(R.id.depthVal);
        RadioGroup linesGroup = view.findViewById(R.id.linesGroup);

        depthBar.setMax(17);
        depthBar.setProgress(depth - 8);
        depthVal.setText("Глубина: " + depth);
        depthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { depthVal.setText("Глубина: " + (p + 8)); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        if (lines == 1) linesGroup.check(R.id.lines1);
        else if (lines == 2) linesGroup.check(R.id.lines2);
        else linesGroup.check(R.id.lines3);

        new AlertDialog.Builder(this)
            .setTitle("⚙️ Настройки")
            .setView(view)
            .setPositiveButton("Сохранить", (d, w) -> {
                depth = depthBar.getProgress() + 8;
                int checkedId = linesGroup.getCheckedRadioButtonId();
                if (checkedId == R.id.lines1) lines = 1;
                else if (checkedId == R.id.lines2) lines = 2;
                else lines = 3;
                prefs.edit().putInt("lines", lines).putInt("depth", depth).apply();
                injectChessHelper();
                Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private String loadStockfish() {
        try {
            InputStream is = getAssets().open("stockfish.js");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private void injectChessHelper() {
        String sfCode = loadStockfish()
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$");

        // getFen function depends on site
        String getFenJs = isChessCom ? getChessComFen() : getLichessFen();

        String js =
            "(function() {" +
            "  if (window.__sfWorker) { window.__sfWorker.terminate(); window.__sfWorker = null; }" +
            "  var LINES = " + lines + ";" +
            "  var DEPTH = " + depth + ";" +
            "  var sfCode = `" + sfCode + "`;" +
            "  var blob = new Blob([sfCode], {type:'application/javascript'});" +
            "  var sf = new Worker(URL.createObjectURL(blob));" +
            "  window.__sfWorker = sf;" +
            "  var lastFen = '';" +
            "  var svgEl = null;" +
            "  var COLORS = ['#22c55e','#3b82f6','#f59e0b'];" +
            "  var collectedMoves = [];" +
            "  sf.onmessage = function(e) {" +
            "    var line = e.data;" +
            "    if (line === 'uciok') { sf.postMessage('setoption name MultiPV value ' + LINES); sf.postMessage('isready'); }" +
            "    else if (line === 'readyok') { setInterval(checkPos, 1000); }" +
            "    else if (line.indexOf(' pv ') >= 0) {" +
            "      var pvM = line.match(/ pv ([a-h][1-8][a-h][1-8][qrbn]?)/);" +
            "      var mpM = line.match(/ multipv (\\d+)/);" +
            "      if (pvM && mpM) collectedMoves[parseInt(mpM[1])-1] = pvM[1];" +
            "    }" +
            "    else if (line.startsWith('bestmove')) {" +
            "      var bm = line.split(' ')[1];" +
            "      if (bm && bm !== '(none)' && !collectedMoves[0]) collectedMoves[0] = bm;" +
            "      drawArrows(collectedMoves.slice(0, LINES));" +
            "      collectedMoves = [];" +
            "    }" +
            "  };" +
            "  sf.postMessage('uci');" +
            "  function checkPos() {" +
            "    var fen = getFen();" +
            "    if (!fen || fen === lastFen) return;" +
            "    lastFen = fen;" +
            "    clearArrows();" +
            "    sf.postMessage('stop');" +
            "    sf.postMessage('position fen ' + fen);" +
            "    sf.postMessage('go depth ' + DEPTH);" +
            "  }" +
            getFenJs +
            "  function ensureSVG(boardEl) {" +
            "    var parent=boardEl.parentElement; if(!parent) return null;" +
            "    if(!parent.style.position||parent.style.position==='static') parent.style.position='relative';" +
            "    if(svgEl&&parent.contains(svgEl)) return svgEl;" +
            "    svgEl=document.createElementNS('http://www.w3.org/2000/svg','svg');" +
            "    svgEl.setAttribute('viewBox','0 0 8 8');" +
            "    svgEl.setAttribute('preserveAspectRatio','xMidYMid meet');" +
            "    svgEl.style.cssText='position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:100;';" +
            "    var defs=document.createElementNS('http://www.w3.org/2000/svg','defs');" +
            "    COLORS.forEach(function(color,i){" +
            "      var marker=document.createElementNS('http://www.w3.org/2000/svg','marker');" +
            "      marker.setAttribute('id','ch-arrow-'+i); marker.setAttribute('markerWidth','4'); marker.setAttribute('markerHeight','4');" +
            "      marker.setAttribute('refX','2.5'); marker.setAttribute('refY','2'); marker.setAttribute('orient','auto');" +
            "      var poly=document.createElementNS('http://www.w3.org/2000/svg','polygon');" +
            "      poly.setAttribute('points','0 0, 4 2, 0 4'); poly.setAttribute('fill',color);" +
            "      marker.appendChild(poly); defs.appendChild(marker);" +
            "    });" +
            "    svgEl.appendChild(defs); parent.appendChild(svgEl); return svgEl;" +
            "  }" +
            "  function drawArrows(moves) {" +
            "    var board = document.querySelector('cg-board, chess-board, wc-chess-board');" +
            "    if(!board) return;" +
            "    var svg=ensureSVG(board); if(!svg) return;" +
            "    clearArrows();" +
            "    var flipped = isFlipped();" +
            "    moves.forEach(function(move, i) {" +
            "      if(!move||move.length<4) return;" +
            "      var from=move.substring(0,2); var to=move.substring(2,4);" +
            "      var fx=(flipped?7-(from.charCodeAt(0)-97):(from.charCodeAt(0)-97))+0.5;" +
            "      var fy=(flipped?parseInt(from[1])-1:7-(parseInt(from[1])-1))+0.5;" +
            "      var tx=(flipped?7-(to.charCodeAt(0)-97):(to.charCodeAt(0)-97))+0.5;" +
            "      var ty=(flipped?parseInt(to[1])-1:7-(parseInt(to[1])-1))+0.5;" +
            "      var dx=tx-fx; var dy=ty-fy; var len=Math.sqrt(dx*dx+dy*dy);" +
            "      if(len<0.01) return;" +
            "      var ux=dx/len; var uy=dy/len;" +
            "      var line=document.createElementNS('http://www.w3.org/2000/svg','line');" +
            "      line.setAttribute('x1',fx); line.setAttribute('y1',fy);" +
            "      line.setAttribute('x2',tx-ux*0.38); line.setAttribute('y2',ty-uy*0.38);" +
            "      line.setAttribute('stroke',COLORS[i]||'#22c55e'); line.setAttribute('stroke-width',i===0?'0.2':'0.14');" +
            "      line.setAttribute('stroke-linecap','round'); line.setAttribute('marker-end','url(#ch-arrow-'+i+')');" +
            "      line.setAttribute('opacity',i===0?'0.9':'0.65'); line.classList.add('ch-arrow');" +
            "      svg.appendChild(line);" +
            "    });" +
            "  }" +
            "  function clearArrows() { if(svgEl) svgEl.querySelectorAll('.ch-arrow').forEach(function(el){el.remove();}); }" +
            "})();";

        webView.evaluateJavascript(js, null);
        if (isChessCom) {
            // Следим за появлением chess-board через MutationObserver
            webView.evaluateJavascript(
                "(function() {" +
                "  if (window.__chessObserver) window.__chessObserver.disconnect();" +
                "  window.__chessObserver = new MutationObserver(function() {" +
                "    var board = document.querySelector('chess-board');" +
                "    if (board && !window.__boardFound) {" +
                "      window.__boardFound = true;" +
                "      var sr = board.shadowRoot;" +
                "      var pieces = sr ? sr.querySelectorAll('[class*=piece]') : board.querySelectorAll('[class*=piece]');" +
                "      ChessDebug.show('FOUND! pieces=' + pieces.length + ' sr=' + (sr?'yes':'no') + ' cls=' + board.className);" +
                "    }" +
                "  });" +
                "  window.__chessObserver.observe(document.body, {childList:true, subtree:true});" +
                "  setTimeout(function() { var all = Array.from(document.querySelectorAll('*')).filter(function(e){return e.tagName.toLowerCase().indexOf('chess')>=0||e.tagName.toLowerCase().indexOf('board')>=0;}).map(function(e){return e.tagName;}).join(','); ChessDebug.show('Chess tags: ' + (all||'none')); }, 3000);" +
                "})();",
                null
            );
        }
    }

    private String getLichessFen() {
        return
            "  function isFlipped() { return document.querySelector('.cg-wrap')&&document.querySelector('.cg-wrap').classList.contains('orientation-black'); }" +
            "  function getFen() {" +
            "    ChessDebug.show(document.querySelector('chess-board')?'board OK: '+document.querySelector('chess-board').className:'no board on: '+window.location.href.substring(0,50));" +
            "    var board = document.querySelector('cg-board');" +
            "    if (!board) return null;" +
            "    var pieces = board.querySelectorAll('piece');" +
            "    if (!pieces.length) return null;" +
            "    var flipped = isFlipped();" +
            "    var bw = board.clientWidth || 480;" +
            "    var grid = [];" +
            "    for(var i=0;i<8;i++){grid.push([null,null,null,null,null,null,null,null]);}" +
            "    pieces.forEach(function(p) {" +
            "      var cls = Array.from(p.classList);" +
            "      var color = cls.find(function(c){return c==='white'||c==='black';});" +
            "      var type = cls.find(function(c){return ['king','queen','rook','bishop','knight','pawn'].indexOf(c)>=0;});" +
            "      if (!color||!type) return;" +
            "      var style = p.getAttribute('style')||'';" +
            "      var col=-1,row=-1;" +
            "      var pct = style.match(/translate\\(\\s*(\\d+(?:\\.\\d+)?)%\\s*,\\s*(\\d+(?:\\.\\d+)?)%\\s*\\)/);" +
            "      if (pct) { col=Math.round(parseFloat(pct[1])/12.5); row=Math.round(parseFloat(pct[2])/12.5); }" +
            "      else { var px=style.match(/translate\\(\\s*(\\d+(?:\\.\\d+)?)px\\s*,\\s*(\\d+(?:\\.\\d+)?)px\\s*\\)/); if(px){col=Math.round(parseFloat(px[1])/bw*8);row=Math.round(parseFloat(px[2])/bw*8);} }" +
            "      if(col<0||col>7||row<0||row>7) return;" +
            "      var gc=flipped?7-col:col; var gr=flipped?7-row:row;" +
            "      var m={king:'k',queen:'q',rook:'r',bishop:'b',knight:'n',pawn:'p'};" +
            "      var ch=m[type]; if(!ch) return;" +
            "      grid[gr][gc]=color==='white'?ch.toUpperCase():ch;" +
            "    });" +
            "    var fen=''; for(var r=0;r<8;r++){var e=0;for(var c=0;c<8;c++){if(grid[r][c]){if(e){fen+=e;e=0;}fen+=grid[r][c];}else e++;}if(e)fen+=e;if(r<7)fen+='/';}" +
            "    var all=document.querySelectorAll('u8t,kwdb,m2');var idx=-1;all.forEach(function(m,i){if(m.classList.contains('a1t'))idx=i;});" +
            "    var turn=idx>=0?(idx%2===0?'b':'w'):'w';" +
            "    return fen+' '+turn+' - - 0 1';" +
            "  }";
    }

    private String getChessComFen() {
        return
            "  function isFlipped() {" +
            "    try {" +
            "      var board = document.querySelector('wc-chess-board');" +
            "      if (board && board.game) return board.game.getIsFlipped ? board.game.getIsFlipped() : false;" +
            "    } catch(e) {}" +
            "    return false;" +
            "  }" +
            "  function getFen() {" +
            "    try {" +
            "      var board = document.querySelector('chess-board');" +
            "      if (!board) return null;" +
            "      if (board.game) {" +
            "        if (board.game.getFEN) return board.game.getFEN();" +
            "        if (board.game.fen) return board.game.fen();" +
            "      }" +
            "      var el = board.shadowRoot || board;" +
            "      var pieces = el.querySelectorAll('[class*=piece]');" +
            "      ChessDebug.show('wc-board pieces: ' + pieces.length + ' sr=' + (board.shadowRoot?'yes':'no'));" +
            "      if (!pieces.length) return null;" +
            "      var grid = [];" +
            "      for(var i=0;i<8;i++){grid.push([null,null,null,null,null,null,null,null]);}" +
            "      var size = board.offsetWidth / 8;" +
            "      pieces.forEach(function(p) {" +
            "        var cls = p.className || '';" +
            "        var typeM = cls.match(/\b([bwBW][prnbqkPRNBQK])\b/);" +
            "        if (!typeM) return;" +
            "        var pc = typeM[1];" +
            "        var style = p.getAttribute('style') || '';" +
            "        var topM = style.indexOf('top:') >= 0 ? style.substring(style.indexOf('top:')+4).match(/([0-9.]+)%/) : null;" +
            "        var leftM = style.indexOf('left:') >= 0 ? style.substring(style.indexOf('left:')+5).match(/([0-9.]+)%/) : null;" +
            "        if (!topM || !leftM) return;" +
            "        var col = Math.round(parseFloat(leftM[1]) / 12.5);" +
            "        var row = Math.round(parseFloat(topM[1]) / 12.5);" +
            "        if(col<0||col>7||row<0||row>7) return;" +
            "        var color = pc[0].toLowerCase() === 'w' ? 'white' : 'black';" +
            "        var typeMap = {p:'p',r:'r',n:'n',b:'b',q:'q',k:'k'};" +
            "        var ch = typeMap[pc[1].toLowerCase()];" +
            "        if (!ch) return;" +
            "        grid[row][col] = color === 'white' ? ch.toUpperCase() : ch;" +
            "      });" +
            "      var fen='';" +
            "      for(var r=0;r<8;r++){var e=0;for(var c=0;c<8;c++){if(grid[r][c]){if(e){fen+=e;e=0;}fen+=grid[r][c];}else e++;}if(e)fen+=e;if(r<7)fen+='/';}" +
            "      return fen + ' w - - 0 1';" +
            "    } catch(e) { return null; }" +
            "  }";
    }

    class ChessDebugInterface {
        @android.webkit.JavascriptInterface
        public void show(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
        }
        @android.webkit.JavascriptInterface
        public void log(final String msg) {
            android.util.Log.d("ChessHelper", msg);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
