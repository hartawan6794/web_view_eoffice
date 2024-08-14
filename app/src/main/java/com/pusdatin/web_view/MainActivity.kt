package com.pusdatin.web_view

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.util.Log
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var database: DatabaseReference
    private val FILECHOOSER_RESULTCODE = 1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Database reference
        database = FirebaseDatabase.getInstance().getReference("webview")

        // Request storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            }
        }

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportMultipleWindows(true)

        // Set WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            // Handle file chooser for Android 5.0+
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "File Chooser"), FILECHOOSER_RESULTCODE)
                return true
            }

            // Handle file chooser for older devices
            fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String? = "") {
                filePathCallback = ValueCallback { uris -> uploadMsg.onReceiveValue(uris?.firstOrNull()) }
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "File Chooser"), FILECHOOSER_RESULTCODE)
            }
        }

        // Set WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        // Set DownloadListener to handle downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimetype)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading file...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Downloading File", Toast.LENGTH_LONG).show()
        }


        // Ambil URL dari Firebase dan muat di WebView setelah data diambil
        loadUrlFromFirebase()

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            loadUrlFromFirebase()
        }
    }

    private fun loadUrlFromFirebase() {
        getUrlFromDatabase { url ->
            url?.let {
                webView.loadUrl(it)
            } ?: run {
                Toast.makeText(this, "Failed to load URL from Firebase", Toast.LENGTH_SHORT).show()
            }
            // Stop the refreshing animation
            swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILECHOOSER_RESULTCODE) {
            val results = if (resultCode == Activity.RESULT_OK) {
                data?.dataString?.let { arrayOf(Uri.parse(it)) }
            } else {
                null
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    private fun getUrlFromDatabase(callback: (String?) -> Unit) {
        // Implementasikan fungsi ini sesuai dengan cara Anda mengambil data dari Firebase
        // Misalnya:
        FirebaseDatabase.getInstance().getReference("webview").child("url").get().addOnSuccessListener {
            callback(it.value as? String)
        }.addOnFailureListener {
            callback(null)
        }
    }
}
