package app.audioscribe.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * AudioScribe companion: the companion web app bundled into an Android app.
 * The page is served from https://appassets.androidplatform.net (so storage works offline) and talks to
 * AudioScribe on your PC over your home network. Downloaded books live in the app's own storage.
 * MediaPlaybackService puts the player on the lock screen, in the notification and in Android Auto.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView

    /** The page tells Android what's playing. */
    inner class MediaBridge {
        @JavascriptInterface
        fun update(json: String) {
            runOnUiThread {
                try {
                    MediaPlaybackService.instance?.update(JSONObject(json))
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW   // the PC is plain http on your LAN
        }
        web.addJavascriptInterface(MediaBridge(), "AndroidMedia")
        MediaPlaybackService.commandSink = { cmd, arg ->
            runOnUiThread {
                web.evaluateJavascript("window.nativeMedia && window.nativeMedia(${JSONObject.quote(cmd)}, " +
                    "${JSONObject.quote(arg)})", null)
            }
        }
        startService(Intent(this, MediaPlaybackService::class.java))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        web.setDownloadListener { url, _, disposition, mime, _ ->          // "Save the EPUB file"
            val name = URLUtil.guessFileName(url, disposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Saving $name to Downloads", Toast.LENGTH_SHORT).show()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)   // keep playing
            }
        })
        pendingWidget = intent?.getStringExtra("widget_cmd")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)
            override fun onPageFinished(view: WebView, url: String?) {   // play pressed on the widget while closed
                val cmd = pendingWidget ?: return
                pendingWidget = null
                view.postDelayed({ MediaPlaybackService.commandSink?.invoke(cmd, "") }, 1500)
            }
        }
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private var pendingWidget: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("widget_cmd")?.let { MediaPlaybackService.commandSink?.invoke(it, "") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        MediaPlaybackService.commandSink = null
        super.onDestroy()
    }
}
