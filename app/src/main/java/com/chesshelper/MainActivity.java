package com.chesshelper;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.*;
import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.contains("lichess.org")) injectChessHelper();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("https://lichess.org");
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

        String js =
            "(function() {" +
            "  if (window.__chessHelperInjected) return;" +
            "  window.__chessHelperInjected = true;" +
            "  var badge = document.createElement('div');" +
            "  badge.style.cssText = 'position:fixed;bottom:20px;right:20px;background:#1a1a2e;color:#4caf50;padding:8px 14px;border-radius:8px;font-size:12px;z-index:9999;border:1px solid #4caf50;';" +
            "  badge.textContent = '♟ Chess Helper Active';" +
            "  document.body.appendChild(badge);" +
            "  setTimeout(function(){badge.style.display='none';}, 3000);" +
            "  var sfCode = `" + sfCode + "`;" +
            "  var blob = new Blob([sfCode], {type:'application/javascript'});" +
            "  var sf = new Worker(URL.createObjectURL(blob));" +
            "  var lastFen = '';" +
            "  var svgEl = null;" +
            "  var COLORS = ['#22c55e','#3b82f6','#f59e0b'];" +
            "  sf.onmessage = function(e) {" +
            "    var line = e.data;" +
            "    if (line === 'uciok') sf.postMessage('isready');" +
            "    else if (line === 'readyok') { setInterval(checkPos, 1000); }" +
            "    else if (line.startsWith('bestmove')) {" +
            "      var bm = line.split(' ')[1];" +
            "      if (bm && bm !== '(none)') drawArrow(bm);" +
            "    }" +
            "  };" +
            "  sf.postMessage('uci');" +
            "  function checkPos() {" +
            "    var fen = getFen();" +
            "    if (!fen || fen === lastFen) return;" +
            "    lastFen = fen;" +
            "    clearArrows();" +
            "    sf.postMessage('position fen ' + fen);" +
            "    sf.postMessage('go depth 12');" +
            "  }" +
            "  function getFen() {" +
            "    var board = document.querySelector('cg-board');" +
            "    if (!board) return null;" +
            "    var pieces = board.querySelectorAll('piece');" +
            "    if (!pieces.length) return null;" +
            "    var flipped = document.querySelector('.cg-wrap') && document.querySelector('.cg-wrap').classList.contains('orientation-black');" +
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
            "  }" +
            "  function ensureSVG() {" +
            "    var board=document.querySelector('cg-board'); if(!board) return null;" +
            "    var parent=board.parentElement; if(!parent) return null;" +
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
            "  function drawArrow(move) {" +
            "    var svg=ensureSVG(); if(!svg) return;" +
            "    clearArrows();" +
            "    var flipped=document.querySelector('.cg-wrap')&&document.querySelector('.cg-wrap').classList.contains('orientation-black');" +
            "    var from=move.substring(0,2); var to=move.substring(2,4);" +
            "    var fx=(flipped?7-(from.charCodeAt(0)-97):(from.charCodeAt(0)-97))+0.5;" +
            "    var fy=(flipped?parseInt(from[1])-1:7-(parseInt(from[1])-1))+0.5;" +
            "    var tx=(flipped?7-(to.charCodeAt(0)-97):(to.charCodeAt(0)-97))+0.5;" +
            "    var ty=(flipped?parseInt(to[1])-1:7-(parseInt(to[1])-1))+0.5;" +
            "    var dx=tx-fx; var dy=ty-fy; var len=Math.sqrt(dx*dx+dy*dy);" +
            "    if(len<0.01) return;" +
            "    var ux=dx/len; var uy=dy/len;" +
            "    var line=document.createElementNS('http://www.w3.org/2000/svg','line');" +
            "    line.setAttribute('x1',fx); line.setAttribute('y1',fy);" +
            "    line.setAttribute('x2',tx-ux*0.38); line.setAttribute('y2',ty-uy*0.38);" +
            "    line.setAttribute('stroke','#22c55e'); line.setAttribute('stroke-width','0.2');" +
            "    line.setAttribute('stroke-linecap','round'); line.setAttribute('marker-end','url(#ch-arrow-0)');" +
            "    line.setAttribute('opacity','0.9'); line.classList.add('ch-arrow');" +
            "    svg.appendChild(line);" +
            "  }" +
            "  function clearArrows() { if(svgEl) svgEl.querySelectorAll('.ch-arrow').forEach(function(el){el.remove();}); }" +
            "})();";

        webView.evaluateJavascript(js, null);
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
