package eu.kanade.tachiyomi.lib.vidmolyextractor

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient

class VidmolyExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, quality: String = "Vidmoly", subtitleList: List<Track> = emptyList()): List<Video> {
        val document = try {
            client.newCall(GET(url)).execute().asJsoup()
        } catch (_: Exception) {
            return emptyList()
        }

        // Find the script tag containing the player configuration
        val scriptData = document.selectFirst("script:containsData(sources:)")?.data()
            ?: return emptyList()

        // Primary regex matching the sources array directly
        var videoUrl = Regex("""sources:\s*\[\s*\{\s*file:\s*['"]([^'"]+)['"]""").find(scriptData)?.groupValues?.get(1)

        // Fallback regex looking directly for a master.m3u8 or video file URL
        if (videoUrl == null) {
            videoUrl = Regex("""file:\s*['"](https?://[^'"]+\.(?:m3u8|mp4)[^'"]*)['"]""").find(scriptData)?.groupValues?.get(1)
        }

        if (videoUrl == null) return emptyList()

        if (videoUrl.contains(".m3u8")) {
            val hlsVideos = playlistUtils.extractFromHls(
                videoUrl,
                referer = url,
                videoNameGen = { q -> "$quality - $q" },
                subtitleList = subtitleList,
            )
            if (hlsVideos.isNotEmpty()) return hlsVideos
        }

        return listOf(Video(videoUrl, quality, videoUrl, subtitleTracks = subtitleList))
    }

    fun videoFromUrl(url: String, quality: String = "Vidmoly", subtitleList: List<Track> = emptyList()): Video? {
        return videosFromUrl(url, quality, subtitleList).firstOrNull()
    }
}