package com.fetchr.app

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class FetchrJsBridge(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun isAndroid(): Boolean = true

    @JavascriptInterface
    fun openDownloadsFolder() {
        Toast.makeText(context, "Check your Downloads/Fetchr folder", Toast.LENGTH_LONG).show()
    }
}
