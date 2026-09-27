package com.fetchr.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "File"
        Toast.makeText(context, "✅ $title saved to Downloads/Fetchr", Toast.LENGTH_LONG).show()
    }
}
