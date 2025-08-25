package com.yusiqo.animecix

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import eu.kanade.tachiyomi.util.asJsoup

class Animecix : AnimeHttpSource() {

    override val name = "Animecix"
    override val baseUrl = "https://animecix.tv"
    override val lang = "tr"
    override val supportsLatest = true

    private val jsonParser = Json { ignoreUnknownKeys = true }

    // --- POPULAR / LATEST ---
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/secure/last-episodes?page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val body = response.body?.string() ?: return AnimesPage(emptyList(), false)
        val apiResponse = jsonParser.decodeFromString<ApiResponse>(body)

        val episodes = apiResponse.data.map { ep ->
            SAnime.create().apply {
                title = ep.title_name
                thumbnail_url = ep.title_poster
                setUrlWithoutDomain("$baseUrl/secure/titles/${ep.title_id}?titleId=${ep.title_id}")
            }
        }

        val hasNext = apiResponse.current_page < apiResponse.last_page
        return AnimesPage(episodes, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // --- SEARCH ---
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search?q=$query&page=$page", headers)

    override fun searchAnimeParse(response: Response) = popularAnimeParse(response)

    // --- ANIME DETAILS ---
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            title = doc.selectFirst("h1.title")?.text() ?: ""
            description = doc.selectFirst(".description")?.text()
            genre = doc.select(".genres a").joinToString { it.text() }
            thumbnail_url = doc.selectFirst(".thumbnail img")?.attr("abs:src")
            status = SAnime.UNKNOWN
        }
    }

    // --- EPISODES ---
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        return doc.select(".episode-list li").mapIndexed { idx, element ->
            SEpisode.create().apply {
                episode_number = idx + 1F
                name = element.selectFirst("a")?.text() ?: "Episode ${idx + 1}"
                setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: "")
            }
        }.reversed()
    }

    // --- VIDEOS ---
    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        return doc.select("iframe").mapNotNull { element ->
            val videoUrl = element.attr("abs:src")
            if (videoUrl.isNotBlank()) Video(videoUrl, "Default", videoUrl) else null
        }
    }
}

// --- JSON DATA CLASSES ---
@Serializable
data class ApiResponse(
    val current_page: Int,
    val last_page: Int,
    val data: List<EpisodeData>,
)

@Serializable
data class EpisodeData(
    val title_name: String,
    val title_id: Int,
    val title_poster: String,
)

@Serializable
data class VideoData(
    val url: String,
    val quality: String,
)
