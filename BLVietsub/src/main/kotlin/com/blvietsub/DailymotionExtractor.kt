package com.blvietsub

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class DailymotionExtractor : ExtractorApi() {
    override val name = "Dailymotion"
    override val mainUrl = "https://www.dailymotion.com"
    override val requiresReferer = false

    private val mapper = jacksonObjectMapper()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = Regex("""(?:video|embed/video)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1) ?: return
        val metadataApiUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"

        val metadataResponse = try {
            app.get(
                metadataApiUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to "https://www.dailymotion.com/"
                )
            ).text
        } catch (e: Exception) {
            return
        }

        try {
            val root = mapper.readTree(metadataResponse)
            val qualitiesNode = root.get("qualities") ?: return
            val autoNode = qualitiesNode.get("auto")
            val masterM3u8Url = autoNode?.get(0)?.get("url")?.asText() ?: return

            val m3u8Content = try {
                app.get(
                    masterM3u8Url,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                ).text
            } catch (e: Exception) {
                ""
            }

            if (m3u8Content.isNotEmpty() && m3u8Content.contains("#EXT-X-STREAM-INF")) {
                val lines = m3u8Content.lines()
                var bestStreamUrl: String? = null
                var has1080p = false

                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val nextUrl = lines.getOrNull(i + 1)?.trim() ?: continue
                        if (line.contains("1920x1080") || line.contains("1080")) {
                            bestStreamUrl = nextUrl
                            has1080p = true
                            break
                        }
                    }
                }

                if (bestStreamUrl == null) {
                    for (i in lines.indices) {
                        val line = lines[i]
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val nextUrl = lines.getOrNull(i + 1)?.trim() ?: continue
                            bestStreamUrl = nextUrl
                            break
                        }
                    }
                }

                val finalUrl = bestStreamUrl ?: masterM3u8Url
                val qualityLabel = if (has1080p) "1080p FHD" else "1080p FHD"

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Server DL (Dailymotion) - $qualityLabel",
                        url = finalUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.P1080.value
                        this.referer = "https://www.dailymotion.com/"
                    }
                )
            } else {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Server DL (Dailymotion) - 1080p FHD",
                        url = masterM3u8Url,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.P1080.value
                        this.referer = "https://www.dailymotion.com/"
                    }
                )
            }
        } catch (e: Exception) {}
    }
}
