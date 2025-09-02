package eu.kanade.tachiyomi.lib.tauvideoextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class TauvideoExtractor(private val client: OkHttpClient, private val headers: Headers) {

	override val name            = "TauVideo"
    override val mainUrl         = "https://tau-video.xyz"

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val videoKey = url.split("/").last()
        val refererHeader = Headers.headersOf("referer", "https://animecix.tv")
        val videoUrl = "${mainUrl}/api/video/${videoKey}"
        val api = app.get(videoUrl).parsedSafe<TauVideoUrls>() ?: throw ErrorLoadingException("TauVideo")
        val videoList = mutableListOf<Video>()
        for (video in api.urls) {
            videoList.add(Video(video.url, "TauVideo ${video.label}", video.url, headers = refererHeader)) ,
       } 
       return videoList
    }

    data class TauVideoUrls(
        @JsonProperty("urls") val urls: List<TauVideoData>
    )

    data class TauVideoData(
        @JsonProperty("url")   val url: String,
        @JsonProperty("label") val label: String,
    )
}