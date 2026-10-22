package com.saikishore.kalicodepocket

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.saikishore.kalicodepocket.data.MimeTypes
import com.saikishore.kalicodepocket.data.Workspace
import com.saikishore.kalicodepocket.data.WorkspaceManager
import com.saikishore.kalicodepocket.data.WorkspacePaths
import com.saikishore.kalicodepocket.databinding.ActivityPreviewBinding
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreviewBinding
    private lateinit var workspace: Workspace
    private lateinit var mode: String
    private var path: String = ""
    private var script: String = ""
    private var scriptEvaluated = false
    private var htmlPath: String? = null
    private var jsUsesHtml = false
    private val consoleLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        workspace = WorkspaceManager.current
            ?: WorkspaceManager.restoreLast(this)
            ?: run {
                finish()
                return
            }
        mode = intent.getStringExtra(EXTRA_MODE) ?: "html"
        path = runCatching { WorkspacePaths.normalize(intent.getStringExtra(EXTRA_PATH).orEmpty()) }
            .getOrDefault("")
        val title = intent.getStringExtra(EXTRA_TITLE) ?: path.substringAfterLast('/')

        binding.toolbar.title = title
        binding.toolbar.subtitle = when (mode) {
            "css" -> "CSS preview"
            "js" -> "JavaScript console"
            else -> "local preview"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.inflateMenu(R.menu.menu_preview)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_reload) {
                loadPreview()
                true
            } else {
                false
            }
        }
        binding.clearConsole.setOnClickListener {
            consoleLog.clear()
            binding.consoleText.text = ""
        }

        setupWebView()
        setupBackHandling()
        loadPreview()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.removeJavascriptInterface(BRIDGE_NAME)
        binding.webView.stopLoading()
        binding.webView.loadUrl("about:blank")
        binding.webView.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.setBackgroundColor(Color.rgb(11, 15, 12))
        binding.webView.addJavascriptInterface(PreviewBridge(), BRIDGE_NAME)
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host != ASSET_HOST) return null
                return serveAsset(url, request.isForMainFrame)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE
                if (mode == "js" && !jsUsesHtml && !scriptEvaluated) {
                    scriptEvaluated = true
                    evaluateScript()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    showError(error?.description?.toString().orEmpty())
                }
            }
        }
    }

    private fun loadPreview() {
        binding.progress.visibility = View.VISIBLE
        binding.previewError.visibility = View.GONE
        scriptEvaluated = false
        jsUsesHtml = false
        when (mode) {
            "html" -> loadHtml()
            "css" -> loadCss()
            "js" -> loadJs()
            else -> showError("Unknown preview mode")
        }
    }

    private fun loadHtml() {
        if (path.isEmpty()) {
            showError("No HTML file selected")
            return
        }
        htmlPath = path
        binding.consolePanel.visibility = View.GONE
        binding.webView.loadUrl(assetUrl(path))
    }

    private fun loadCss() {
        htmlPath = null
        binding.consolePanel.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val css = withContext(Dispatchers.IO) { workspace.read(path) }
                val html = demoHtml(css)
                binding.webView.loadDataWithBaseURL(
                    ASSET_BASE,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.preview_error))
            }
        }
    }

    private fun loadJs() {
        htmlPath = null
        binding.consolePanel.visibility = View.VISIBLE
        consoleLog.clear()
        binding.consoleText.text = ""
        appendConsole("KaliCode Pocket JS console")
        lifecycleScope.launch {
            try {
                // If the script lives next to an index.html, run it in that page
                // so DOM-dependent scripts actually have their usual elements.
                val siblingHtml = withContext(Dispatchers.IO) { findSiblingHtml(path) }
                if (siblingHtml != null) {
                    jsUsesHtml = true
                    htmlPath = siblingHtml
                    binding.webView.loadUrl(assetUrl(siblingHtml))
                } else {
                    script = withContext(Dispatchers.IO) { workspace.read(path) }
                    binding.webView.loadDataWithBaseURL(
                        null,
                        JS_WRAPPER,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.preview_error))
            }
        }
    }

    private fun findSiblingHtml(jsPath: String): String? {
        val parent = WorkspacePaths.parentOf(jsPath)
        return workspace.list(parent)
            .firstOrNull { !it.isDirectory && it.name.equals("index.html", ignoreCase = true) }
            ?.path
    }

    private fun evaluateScript() {
        if (script.isBlank()) {
            appendConsole("(empty script)")
            return
        }
        val wrapped = "try {\n$script\n} catch (e) {\n" +
            "  if (window.$BRIDGE_NAME) $BRIDGE_NAME.postMessage('error  ' + (e && e.stack ? e.stack : e));\n" +
            "}"
        binding.webView.evaluateJavascript(wrapped) { result ->
            if (result != null && result != "null") {
                appendConsole("result  $result")
            }
        }
    }

    private fun serveAsset(url: Uri, mainFrame: Boolean): WebResourceResponse {
        val requested = Uri.decode(url.path.orEmpty()).trimStart('/')
        val clean = runCatching { WorkspacePaths.normalize(requested) }.getOrNull()
            ?: return notFound()
        if (clean.isEmpty()) return notFound()
        val bytes = runCatching { workspace.readBytes(clean) }.getOrElse { return notFound() }
        val mime = MimeTypes.forName(clean)

        if (mainFrame && clean == htmlPath && (mode == "html" || mode == "js")) {
            var html = injectConsole(String(bytes, StandardCharsets.UTF_8))
            if (mode == "js") html = injectSelectedScript(html)
            return textResponse("text/html", html)
        }
        return WebResourceResponse(
            mime,
            if (isTextMime(mime)) "UTF-8" else null,
            ByteArrayInputStream(bytes),
        )
    }

    private fun injectConsole(html: String): String {
        val script = CONSOLE_SCRIPT
        val index = html.indexOf("</head>", ignoreCase = true)
        return if (index >= 0) {
            html.substring(0, index) + script + html.substring(index)
        } else {
            script + html
        }
    }

    private fun injectSelectedScript(html: String): String {
        val name = path.substringAfterLast('/')
        if (name.isEmpty()) return html
        val referenced = Regex(
            """src\s*=\s*["'][^"']*${Regex.escape(name)}(?:[?#][^"']*)?["']""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(html)
        if (referenced) return html
        val tag = """<script src="${Uri.encode(name)}"></script>"""
        val body = html.indexOf("</body>", ignoreCase = true)
        return if (body >= 0) {
            html.substring(0, body) + tag + html.substring(body)
        } else {
            html + tag
        }
    }

    private fun demoHtml(css: String): String {
        val safeCss = css.replace("</style", "<\\/style")
        return """<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
* { box-sizing: border-box; }
html, body { margin: 0; min-height: 100%; }
body {
  background: #0b0f0c; color: #d7e0d5; padding: 30px 22px;
  font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
}
.kicker { color: #6ee7a8; font: 700 11px ui-monospace, monospace; letter-spacing: .16em; }
h1 { margin: 18px 0 12px; font-size: clamp(32px, 9vw, 52px); line-height: 1; letter-spacing: -.04em; }
p { color: #8fa394; line-height: 1.6; }
.demo-card {
  margin-top: 26px; padding: 20px; border: 1px solid #2a3a2f; border-radius: 16px;
  background: #121a14;
}
button {
  border: 0; border-radius: 12px; padding: 13px 20px; background: #6ee7a8;
  color: #061109; font-weight: 700;
}
</style>
<style>$safeCss</style>
</head><body>
<p class="kicker">CSS PREVIEW</p>
<h1>Style it.</h1>
<p>This sample page is rendered locally with your stylesheet. Edit the CSS, then tap Reload.</p>
<div class="demo-card">
  <h3>Demo card</h3>
  <p>Headings, paragraphs, buttons and borders respond to your rules.</p>
  <button>Button</button>
</div>
</body></html>"""
    }

    private fun showError(message: String) {
        binding.progress.visibility = View.GONE
        binding.previewError.text = message.ifBlank { getString(R.string.preview_error) }
        binding.previewError.visibility = View.VISIBLE
    }

    private fun appendConsole(message: String) {
        if (consoleLog.length > 120_000) {
            consoleLog.delete(0, consoleLog.length - 60_000)
        }
        if (consoleLog.isNotEmpty()) consoleLog.append('\n')
        consoleLog.append(message)
        binding.consoleText.text = consoleLog.toString()
        binding.consoleScroll.post { binding.consoleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        404,
        "Not Found",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )

    private fun textResponse(mime: String, text: String): WebResourceResponse =
        WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))

    private fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime == "application/javascript" ||
            mime == "application/json" ||
            mime == "application/xml" ||
            mime == "image/svg+xml"

    private fun assetUrl(path: String): String =
        ASSET_BASE + path.split('/').joinToString("/") { Uri.encode(it) }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })
    }

    private inner class PreviewBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            runOnUiThread { appendConsole(message) }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val ASSET_BASE = "https://$ASSET_HOST/"
        private const val BRIDGE_NAME = "KaliPreview"

        private const val CONSOLE_SCRIPT = """<script>
(function() {
  function stringify(value) {
    if (typeof value === 'object') {
      try { return JSON.stringify(value); } catch (e) { return String(value); }
    }
    return String(value);
  }
  function send(level, args) {
    try {
      if (window.KaliPreview) {
        KaliPreview.postMessage(level + '  ' + args.map(stringify).join(' '));
      }
    } catch (e) {}
  }
  ['log', 'info', 'warn', 'error'].forEach(function(level) {
    var original = console[level];
    console[level] = function() {
      send(level, Array.prototype.slice.call(arguments));
      if (original) original.apply(console, arguments);
    };
  });
  window.addEventListener('error', function(e) {
    send('error', [e.message + ' (' + e.lineno + ':' + e.colno + ')']);
  });
  window.addEventListener('unhandledrejection', function(e) {
    send('error', ['Unhandled promise: ' + e.reason]);
  });
})();
</script>"""

        private const val JS_WRAPPER = """<!doctype html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
html, body { margin: 0; min-height: 100%; background: #0b0f0c; color: #d7e0d5; }
body { padding: 22px; font-family: system-ui, sans-serif; }
h1 { font: 700 18px ui-monospace, monospace; color: #6ee7a8; }
p { color: #8fa394; font-size: 13px; line-height: 1.5; }
</style>
</head><body>
<h1>js / run</h1>
<p>Script output and errors appear in the console below.</p>
<script>
(function() {
  function stringify(value) {
    if (typeof value === 'object') {
      try { return JSON.stringify(value); } catch (e) { return String(value); }
    }
    return String(value);
  }
  function send(level, args) {
    try {
      if (window.KaliPreview) {
        KaliPreview.postMessage(level + '  ' + args.map(stringify).join(' '));
      }
    } catch (e) {}
  }
  ['log', 'info', 'warn', 'error'].forEach(function(level) {
    var original = console[level];
    console[level] = function() {
      send(level, Array.prototype.slice.call(arguments));
      if (original) original.apply(console, arguments);
    };
  });
  window.addEventListener('error', function(e) {
    send('error', [e.message + ' (' + e.lineno + ':' + e.colno + ')']);
  });
  window.addEventListener('unhandledrejection', function(e) {
    send('error', ['Unhandled promise: ' + e.reason]);
  });
})();
</script>
</body></html>"""
    }
}
