package com.fetchr.app.extractor

import com.fetchr.app.util.FormatHelper
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoResult(
    val url: String, val title: String, val channel: String,
    val duration: String, val views: String, val thumbnail: String
)

data class VideoFormat(
    val format_id: String, val ext: String, val height: Int?,
    val label: String, val quality_label: String, val type: String,
    val size: String, val has_audio: Boolean, val abr: Int?,
    val convert: Boolean, val download_url: String
)

data class VideoInfo(
    val title: String, val channel: String, val thumbnail: String,
    val duration: String, val views: String,
    val formats: List<VideoFormat>, val url: String
)

object YouTubeExtractor {

    private val youtube = ServiceList.YouTube

    fun search(query: String): List<VideoResult> {
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        val results = mutableListOf<VideoResult>()
        for (item in extractor.initialPage.items) {
            val s = item as? StreamInfoItem ?: continue
            results.add(VideoResult(
                url = s.url, title = s.name,
                channel = s.uploaderName ?: "",
                duration = FormatHelper.formatDuration(s.duration),
                views = FormatHelper.formatViews(s.viewCount),
                thumbnail = s.thumbnails.lastOrNull()?.url ?: thumbFromUrl(s.url)
            ))
            if (results.size >= 12) break
        }
        return results
    }

    fun getInfo(url: String): VideoInfo {
        val info = StreamInfo.getInfo(youtube, url)
        val formats = mutableListOf<VideoFormat>()
        val seenVideo = mutableSetOf<Pair<String, Int>>()
        val seenAudio = mutableSetOf<String>()

        for (stream in (info.videoStreams + info.videoOnlyStreams)) {
            val h = stream.height; if (h <= 0) continue
            val ext = if ((stream.format?.suffix ?: "") == "webm") "webm" else "mp4"
            if (seenVideo.add(Pair(ext, h))) {
                formats.add(VideoFormat(
                    format_id = stream.itagItem?.id?.toString() ?: ext,
                    ext = ext, height = h,
                    label = "${ext.uppercase()} ${h}p",
                    quality_label = "${h}p", type = "video",
                    size = "~", has_audio = stream in info.videoStreams,
                    abr = null, convert = false,
                    download_url = stream.content ?: ""
                ))
            }
        }

        for (stream in info.audioStreams) {
            val ext = if ((stream.format?.suffix ?: "") == "webm") "webm" else "m4a"
            val abr = stream.averageBitrate
            if (seenAudio.add(ext)) {
                formats.add(VideoFormat(
                    format_id = stream.itagItem?.id?.toString() ?: ext,
                    ext = ext, height = null,
                    label = "${ext.uppercase()} Audio",
                    quality_label = if (abr > 0) "${abr}kbps" else "Audio",
                    type = "audio", size = "~", has_audio = true,
                    abr = if (abr > 0) abr else null, convert = false,
                    download_url = stream.content ?: ""
                ))
            }
        }

        val sorted = formats.filter { it.type == "video" }.sortedByDescending { it.height } +
                     formats.filter { it.type == "audio" }.sortedByDescending { it.abr ?: 0 }.take(1)

        return VideoInfo(
            title = info.name, channel = info.uploaderName,
            thumbnail = info.thumbnails.lastOrNull()?.url ?: "",
            duration = FormatHelper.formatDuration(info.duration),
            view
