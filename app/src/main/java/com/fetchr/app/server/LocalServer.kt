package com.fetchr.app.server

import android.content.Context
import com.fetchr.app.extractor.YouTubeExtractor
import com.fetchr.app.util.DownloadManager
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LocalServer(private val context: Context) {

    companion object {
        const val PORT = 8765
        const val BASE_URL = "http://127.0.0.1:$PORT"
    }

    private val gson = Gson()
    private var server: NettyApplicationEngine? = null

    data class SearchRequest(val query: String = "")
    data class InfoRequest(val url: String = "")
    data class DownloadRequest(
        val url: String = "", val format_id: String = "",
        val ext: String = "mp4", val convert: Boolean = false,
        val title: String = "video", val job_id: String = "",
        val download_url: String = ""
    )

    fun start() {
        server = embeddedServer(Netty, port = PORT, host = "127.0.0.1") {
            install(CORS) { anyHost(); allowHeader(HttpHeaders.ContentType) }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Error")))
                }
            }
            routing {
                get("/") {
                    val html = context.assets.open("web/index.html").bufferedReader().readText()
                    call.respondText(html, ContentType.Text.Html)
                }
                post("/api/search") {
                    val body = gson.fromJson(call.receiveText(), SearchRequest::class.java)
                    if (body.query.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No query")); return@post }
                    try { call.respond(mapOf("results" to YouTubeExtractor.search(body.query))) }
                    catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Search failed"))) }
                }
                post("/api/info") {
                    val body = gson.fromJson(call.receiveText(), InfoRequest::class.java)
                    if (body.url.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No URL")); return@post }
                    try { call.respond(YouTubeExtractor.getInfo(body.url)) }
                    catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed"))) }
                }
                post("/api/download") {
                    val body = gson.fromJson(call.receiveText(), DownloadRequest::class.java)
                    if (body.download_url.isBlank()) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No URL")); return@post }
                    val jobId = body.job_id.ifBlank { java.util.UUID.randomUUID().toString() }
                    GlobalScope.launch(Dispatchers.IO) {
                        DownloadManager.download(context, jobId, body.download_url, body.title.ifBlank { "video" }, body.ext)
                    }
                    call.respond(HttpStatusCode.Accepted, mapOf("job_id" to jobId, "status" to "starting"))
                }
                get("/api/progress/{jobId}") {
                    val jobId = call.parameters["jobId"] ?: ""
                    call.respond(DownloadManager.getProgress(jobId))
                }
            }
        }.start(wait = false)
    }

    fun stop() { server?.stop(500, 1000) }
}
