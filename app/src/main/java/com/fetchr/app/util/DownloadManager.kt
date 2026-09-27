package com.fetchr.app.util

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.fetchr.app.FetchrApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ProgressInfo(
    val status: String,
    val pct: Int,
    val speed: String = "",
    val eta: String = "",
    val downloaded: String = "",
    val total: String = "",
    val error: String = ""
)

object DownloadManager {

    private val progressStore = ConcurrentHashMap<String, ProgressInfo>()
    private var nextNotifId = 1000

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .build()
            chain.proceed(req)
        }
        .build()

    fun getProgress(jobId: String): ProgressInfo =
        progressStore[jobId] ?: ProgressInfo("unknown", 0)

    suspend fun download(context: Context, jobId: String, downloadUrl: String, title: String, ext: String): Result<File> =
        withContext(Dispatchers.IO) {
            progressStore[jobId] = ProgressInfo("starting", 0)
            val nid = nextNotifId++

            try {
                val response = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
                if (!response.isSuccessful) {
                    val err = "HTTP ${response.code}"
                    progressStore[jobId] = ProgressInfo("error", 0, error = err)
                    return@withContext Result.failure(Exception(err))
                }

                val contentLength = response.body?.contentLength() ?: -1L
                val tempFile = File(context.cacheDir, "$jobId.$ext")
                var downloaded = 0L
                var lastUpdate = System.currentTimeMillis()
                var lastBytes = 0L

                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastUpdate
                            if (elapsed >= 800) {
                                val pct = if (contentLength > 0) (downloaded * 100 / contentLength).toInt() else 0
                                val speed = if (elapsed > 0) (downloaded - lastBytes) * 1000 / elapsed else 0
                                val eta = if (speed > 0 && contentLength > 0) (contentLength - downloaded) / speed else 0
                                progressStore[jobId] = ProgressInfo(
                                    status = "downloading", pct = pct,
                                    speed = if (speed > 0) FormatHelper.formatSpeed(speed) else "",
                                    eta = if (eta > 0) "${eta}s" else "",
                                    downloaded = FormatHelper.formatSize(downloaded),
                                    total = FormatHelper.formatSize(contentLength.takeIf { it > 0 })
                                )
                                showProgress(context, nid, title, pct)
                                lastUpdate = now
                                lastBytes = downloaded
                            }
                        }
                    }
                }

                progressStore[jobId] = ProgressInfo("processing", 99)
                val savedFile = saveToDownloads(context, tempFile, "${FormatHelper.sanitizeFileName(title)}.$ext", ext)
                tempFile.delete()
                progressStore[jobId] = ProgressInfo("done", 100)
                showComplete(context, nid, title)
                Result.success(savedFile)

            } catch (e: Exception) {
                progressStore[jobId] = ProgressInfo("error", 0, error = e.message ?: "Unknown error")
                Result.failure(e)
            }
        }

    private fun saveToDownloads(context: Context, tempFile: File, fileName: String, ext: String): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val isAudio = ext in listOf("mp3", "m4a")
            val collection = if (isAudio)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(ext))
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isAudio) Environment.DIRECTORY_MUSIC + "/Fetchr"
                    else Environment.DIRECTORY_DOWNLOADS + "/Fetchr")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)!!
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return tempFile
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Fetchr").apply { mkdirs() }
            val dest = File(dir, fileName)
            tempFile.copyTo(dest, overwrite = true)
            return dest
        }
    }

    private fun mimeFor(ext: String) = when (ext) {
        "mp4" -> "video/mp4"; "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private fun notifManager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun showProgress(context: Context, nid: Int, title: String, pct: Int) {
        try {
            val notif = NotificationCompat.Builder(context, FetchrApplication.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading: $title")
                .setContentText("$pct%")
                .setProgress(100, pct, pct == 0)
                .setOngoing(true).setSilent(true).build()
            notifManager(context).notify(nid, notif)
        } c
