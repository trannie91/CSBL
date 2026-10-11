package com.blvietsub

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class OkRuExtractor : ExtractorApi() {
    override val name = "OK.ru"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    private val mapper = jacksonObjectMapper()

    data class OkVideo(
        @JsonProperty("name") val name: String?,
        @JsonProperty("url") val url: String?
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetUrl = when {
            url.contains("videoembed") -> url
            url.contains("/video/") -> {
                val videoId = Regex("""/video/(\d+)""").find(url)?.groupValues?.get(1) ?: return
                "https://ok.ru/videoembed/$videoId"
            }
            else -> url
        }

        val html = try {
            app.get(
                targetUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to (referer ?: "https://blvietsub.com/")
                )
            ).text
        } catch (e: Exception) {
            return
        }

        val dataOptionsMatch = Regex("""data-options="([^"]+)"""").find(html)?.groupValues?.get(1) ?: return
        val cleanJson = dataOptionsMatch
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&amp;", "&")

        try {
            val rootNode = mapper.readTree(cleanJson)
            val flashvarsNode = rootNode.get("flashvars") ?: return

            val metadataNode = when {
                flashvarsNode.get("metadata")?.isTextual == true -> {
                    mapper.readTree(flashvarsNode.get("metadata").asText())
                }
                flashvarsNode.get("metadata")?.isObject == true -> {
                    flashvarsNode.get("metadata")
                }
                else -> null
            } ?: return

            val videosNode = metadataNode.get("videos")
            if (videosNode != null && videosNode.isArray) {
                val videos: List<OkVideo> = mapper.readValue(videosNode.toString())

                // Ưu tiên cao nhất chất lượng 1080p FHD (full)
                val fullVideo = videos.firstOrNull { it.name.equals("full", ignoreCase = true) }

                if (fullVideo?.url != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Server DL (OK.ru) - 1080p FHD",
                            url = fullVideo.url,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.P1080.value
                            this.referer = "https://ok.ru/"
                        }
                    )
                    return
                }

                // Fallback nếu nguồn video chỉ upload tối đa 720p HD
                val hdVideo = videos.firstOrNull { it.name.equals("hd", ignoreCase = true) }
                if (hdVideo?.url != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Server DL (OK.ru) - 720p HD",
                            url = hdVideo.url,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.quality = Qualities.P720.value
                            this.referer = "https://ok.ru/"
                        }
                    )
                    return
                }
            }

            val hlsManifestUrl = metadataNode.get("hlsManifestUrl")?.asText()
            if (!hlsManifestUrl.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Server OK (OK.ru) - 1080p FHD",
                        url = hlsManifestUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = Qualities.P1080.value
                        this.referer = "https://ok.ru/"
                    }
                )
            }
        } catch (e: Exception) {}
    }
}
