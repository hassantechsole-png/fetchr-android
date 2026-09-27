package com.fetchr.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest

class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        @Volatile private var instance: NewPipeDownloader? = null
        fun getInstance(): NewPipeDownloader =
            instance ?: synchronized(this) {
                instance ?: NewPipeDownloader().also { instance = it }
            }
    }

    override fun execute(request: ExtractorRequest): Response {
        val dataToSend = request.dataToSend()
        val requestBody = dataToSend?.toRequestBody("application/json".toMediaTypeOrNull())

        val requestBuilder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)

        request.headers().forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) throw ReCaptchaException("Rate limited", request.url())

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string() ?: "",
            response.request.url.toString()
        )
    }
}
