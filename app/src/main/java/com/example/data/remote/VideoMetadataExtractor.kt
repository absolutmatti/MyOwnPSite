package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExtractedVideoMetadata(
    val title: String?,
    val thumbnailUrl: String?,
)

class VideoMetadataExtractor(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun fetchMetadata(rawUrl: String): ExtractedVideoMetadata = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeHttpUrl(rawUrl)
        if (normalizedUrl.isEmpty()) {
            return@withContext ExtractedVideoMetadata(title = null, thumbnailUrl = null)
        }

        val slugFallback = deriveReadableTitleFromUrl(normalizedUrl)

        val epornerId = extractEpornerVideoId(normalizedUrl)
        if (epornerId != null) {
            val apiResult = runCatching { fetchEpornerApiMetadata(epornerId) }.getOrNull()
            if (apiResult != null && (!apiResult.title.isNullOrBlank() || !apiResult.thumbnailUrl.isNullOrBlank())) {
                return@withContext ExtractedVideoMetadata(
                    title = apiResult.title?.takeIf { it.isNotBlank() } ?: slugFallback,
                    thumbnailUrl = apiResult.thumbnailUrl?.takeIf { it.isNotBlank() },
                )
            }
        }

        val htmlResult = runCatching { fetchHtmlMetadata(normalizedUrl) }.getOrNull()
        ExtractedVideoMetadata(
            title = htmlResult?.title?.takeIf { it.isNotBlank() } ?: slugFallback,
            thumbnailUrl = htmlResult?.thumbnailUrl?.takeIf { it.isNotBlank() },
        )
    }

    private fun fetchEpornerApiMetadata(videoId: String): ExtractedVideoMetadata? {
        val apiUrl = "https://www.eporner.com/api/v2/video/id/?id=$videoId&thumbsize=big&format=json"
        val body = executeGetText(apiUrl, maxBytes = 256 * 1024) ?: return null
        return parseEpornerApiJson(body)
    }

    private fun fetchHtmlMetadata(url: String): ExtractedVideoMetadata? {
        val html = executeGetText(url, maxBytes = 512 * 1024) ?: return null
        return parseHtmlMetadata(html, baseUrl = url)
    }

    private fun executeGetText(url: String, maxBytes: Int): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val source = body.source()
            source.request(maxBytes.toLong())
            val buffer = source.buffer
            val byteCount = minOf(buffer.size, maxBytes.toLong())
            buffer.clone().readByteArray(byteCount).toString(Charsets.UTF_8)
        }
    }

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private val EPORNER_URL_REGEX = Regex(
            """(?:https?://)?(?:[\w-]+\.)?eporner\.com/(?:video-|hd-porn/)([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE,
        )

        private val META_TAG_REGEX = Regex(
            """<meta\s+[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val LINK_TAG_REGEX = Regex(
            """<link\s+[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val ATTR_REGEX = Regex(
            """([a-zA-Z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""",
        )

        private val TITLE_TAG_REGEX = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val JSON_LD_REGEX = Regex(
            """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val VIDEO_POSTER_REGEX = Regex(
            """<video[^>]*\sposter\s*=\s*(?:"([^"]+)"|'([^']+)')""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        private val PLAYER_THUMB_REGEX = Regex(
            """["'](?:preview_url|thumb_url|poster|thumbnailUrl|thumbnail_url)["']\s*:\s*["'](https?:\\?/\\?/[^"']+|//[^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun extractEpornerVideoId(url: String): String? {
            return EPORNER_URL_REGEX.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }

        fun parseEpornerApiJson(jsonText: String): ExtractedVideoMetadata? {
            return runCatching {
                val root = JSONObject(jsonText)
                val videoObj = root.optJSONArray("videos")?.optJSONObject(0) ?: root
                val title = videoObj.optString("title", "").trim().takeIf { it.isNotEmpty() }
                val defaultThumb = videoObj.optJSONObject("default_thumb")?.optString("src", "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                val fallbackThumb = videoObj.optJSONArray("thumbs")?.let { arr ->
                    if (arr.length() > 0) arr.optJSONObject(0)?.optString("src", "")?.trim() else null
                }?.takeIf { it.isNotEmpty() }

                ExtractedVideoMetadata(
                    title = title?.let(::decodeHtmlEntities),
                    thumbnailUrl = (defaultThumb ?: fallbackThumb)?.let { resolveImageUrl(it, "https://www.eporner.com") },
                )
            }.getOrNull()
        }

        fun parseHtmlMetadata(html: String, baseUrl: String): ExtractedVideoMetadata {
            val metaValues = mutableMapOf<String, String>()
            META_TAG_REGEX.findAll(html).forEach { match ->
                val attrs = parseAttributes(match.value)
                val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase(Locale.ROOT)
                val content = attrs["content"]?.trim()
                if (!key.isNullOrEmpty() && !content.isNullOrEmpty() && !metaValues.containsKey(key)) {
                    metaValues[key] = content
                }
            }

            var linkImageSrc: String? = null
            LINK_TAG_REGEX.findAll(html).forEach { match ->
                if (linkImageSrc == null) {
                    val attrs = parseAttributes(match.value)
                    if (attrs["rel"]?.lowercase(Locale.ROOT) == "image_src") {
                        linkImageSrc = attrs["href"]?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }
            }

            val jsonLd = extractJsonLdVideoMetadata(html)

            val rawTitle = metaValues["og:title"]
                ?: metaValues["twitter:title"]
                ?: jsonLd.title
                ?: TITLE_TAG_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()

            val cleanedTitle = rawTitle
                ?.let(::decodeHtmlEntities)
                ?.let(::cleanSiteSuffix)
                ?.takeIf { it.isNotEmpty() }

            val rawThumb = metaValues["og:image:secure_url"]
                ?: metaValues["og:image"]
                ?: metaValues["twitter:image:src"]
                ?: metaValues["twitter:image"]
                ?: jsonLd.thumbnailUrl
                ?: linkImageSrc
                ?: VIDEO_POSTER_REGEX.find(html)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                ?: PLAYER_THUMB_REGEX.find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/")

            val resolvedThumb = rawThumb
                ?.let(::decodeHtmlEntities)
                ?.let { resolveImageUrl(it, baseUrl) }

            return ExtractedVideoMetadata(
                title = cleanedTitle,
                thumbnailUrl = resolvedThumb,
            )
        }

        fun deriveReadableTitleFromUrl(rawUrl: String): String {
            val clean = rawUrl.substringBefore('?').substringBefore('#').trimEnd('/')
            val segments = clean.split('/').filter { it.isNotBlank() }
            val candidate = segments.lastOrNull { seg ->
                !seg.startsWith("http", ignoreCase = true) &&
                    !seg.startsWith("www.", ignoreCase = true) &&
                    !seg.equals("video", ignoreCase = true) &&
                    !seg.equals("watch", ignoreCase = true) &&
                    !seg.startsWith("video-", ignoreCase = true) &&
                    !seg.all(Char::isDigit)
            } ?: segments.lastOrNull().orEmpty()

            val withoutExt = candidate.replace(Regex("""\.(html?|php|mp4|mkv|webm)$""", RegexOption.IGNORE_CASE), "")
            val spaced = withoutExt.replace(Regex("""[-_+]+"""), " ").replace(Regex("""\s+"""), " ").trim()
            if (spaced.isEmpty()) {
                return rawUrl.removePrefix("https://").removePrefix("http://").take(60)
            }
            return spaced.split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }.take(200)
        }

        private fun extractJsonLdVideoMetadata(html: String): ExtractedVideoMetadata {
            JSON_LD_REGEX.findAll(html).forEach { match ->
                val rawJson = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (rawJson.isEmpty()) return@forEach
                val parsed = parseJsonLdBlock(rawJson)
                if (parsed != null && (!parsed.title.isNullOrBlank() || !parsed.thumbnailUrl.isNullOrBlank())) {
                    return parsed
                }
            }
            return ExtractedVideoMetadata(null, null)
        }

        private fun parseJsonLdBlock(rawJson: String): ExtractedVideoMetadata? {
            return runCatching {
                val candidates = mutableListOf<JSONObject>()
                val trimmed = rawJson.trim()
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let(candidates::add)
                    }
                } else {
                    val obj = JSONObject(trimmed)
                    candidates.add(obj)
                    obj.optJSONArray("@graph")?.let { graph ->
                        for (i in 0 until graph.length()) {
                            graph.optJSONObject(i)?.let(candidates::add)
                        }
                    }
                }

                val videoObj = candidates.firstOrNull { obj ->
                    val type = obj.opt("@type")?.toString().orEmpty()
                    type.contains("VideoObject", ignoreCase = true)
                } ?: candidates.firstOrNull()

                if (videoObj == null) return@runCatching null

                val title = videoObj.optString("name", "").trim().takeIf { it.isNotEmpty() }
                val thumb = when (val rawThumb = videoObj.opt("thumbnailUrl") ?: videoObj.opt("thumbnail")) {
                    is String -> rawThumb.trim().takeIf { it.isNotEmpty() }
                    is JSONArray -> if (rawThumb.length() > 0) rawThumb.optString(0, "").trim().takeIf { it.isNotEmpty() } else null
                    is JSONObject -> rawThumb.optString("url", "").trim().takeIf { it.isNotEmpty() }
                    else -> null
                }
                ExtractedVideoMetadata(title = title, thumbnailUrl = thumb)
            }.getOrNull()
        }

        private fun parseAttributes(tag: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            ATTR_REGEX.findAll(tag).forEach { match ->
                val key = match.groupValues[1].lowercase(Locale.ROOT)
                val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
                map[key] = value
            }
            return map
        }

        private fun cleanSiteSuffix(title: String): String {
            return title
                .replace(Regex("""\s*[-–|•]\s*(EPORNER|PORN00(?:\.com)?)\s*$""", RegexOption.IGNORE_CASE), "")
                .trim()
                .take(200)
        }

        private fun decodeHtmlEntities(input: String): String {
            var decoded = input
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#039;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
            decoded = Regex("""&#(\d+);""").replace(decoded) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
            decoded = Regex("""&#x([0-9a-fA-F]+);""").replace(decoded) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
            }
            return decoded.trim()
        }

        private fun normalizeHttpUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return trimmed
            }
            return "https://$trimmed"
        }

        private fun resolveImageUrl(raw: String, baseUrl: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("/") -> {
                    val schemeAndHost = Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
                        .find(baseUrl)?.groupValues?.getOrNull(1)
                    if (schemeAndHost != null) "$schemeAndHost$trimmed" else null
                }
                else -> "https://$trimmed"
            }?.takeIf { it.length <= 250_000 }
        }
    }
}
