package com.example.data.remote

import android.util.Base64
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ExtractedArticleContent(
    val fullText: String,
    val leadSummary: String,
    val imageUrl: String? = null
)

class ArticleContentFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    suspend fun fetchFullArticle(
        articleUrl: String,
        titleHint: String = ""
    ): ExtractedArticleContent? = withContext(Dispatchers.IO) {
        val initialUrl = resolveArticleUrl(articleUrl.trim())
        if (initialUrl.isBlank() || (!initialUrl.startsWith("http://") && !initialUrl.startsWith("https://"))) {
            return@withContext null
        }

        val firstFetch = fetchHtmlPage(initialUrl) ?: return@withContext null
        var (finalUrl, html) = firstFetch

        // If we are still on a Google News / intermediate wrapper page, try one hop to embedded target URL
        if (finalUrl.contains("news.google.com", ignoreCase = true) || html.length < 1500) {
            val redirectTarget = extractRedirectTarget(html, finalUrl)
            if (redirectTarget != null && redirectTarget != finalUrl) {
                fetchHtmlPage(redirectTarget)?.let { (resolved, nextHtml) ->
                    finalUrl = resolved
                    html = nextHtml
                }
            }
        }

        extractFromHtml(html = html, baseUrl = finalUrl, titleHint = titleHint)
    }

    private fun fetchHtmlPage(url: String): Pair<String, String>? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ar,ckb;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            if (contentType.isNotBlank() && !contentType.contains("html") && !contentType.contains("xml") && !contentType.contains("text")) {
                return@runCatching null
            }
            val finalUrl = response.request.url.toString()
            val rawBytes = response.body?.bytes() ?: return@runCatching null
            val capped = if (rawBytes.size > MAX_HTML_BYTES) rawBytes.copyOf(MAX_HTML_BYTES) else rawBytes
            finalUrl to String(capped, Charsets.UTF_8)
        }
    }.getOrNull()

    internal fun resolveArticleUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (!trimmed.contains("news.google.com", ignoreCase = true)) return trimmed
        return decodeGoogleNewsUrl(trimmed) ?: trimmed
    }

    internal fun decodeGoogleNewsUrl(url: String): String? {
        val token = url.substringAfter("/articles/", "")
            .ifBlank { url.substringAfter("/read/", "") }
            .substringBefore("?")
            .substringBefore("/")
            .trim()
        if (token.isBlank()) return null

        val decodedBytes = runCatching {
            val padded = token.replace('-', '+').replace('_', '/')
                .let { s -> s + "=".repeat((4 - s.length % 4) % 4) }
            Base64.decode(padded, Base64.DEFAULT)
        }.getOrNull() ?: return null

        val rawAscii = String(decodedBytes, Charsets.ISO_8859_1)
        val match = embeddedUrlRegex.find(rawAscii) ?: return null
        val candidate = match.value.trimEnd { ch ->
            ch.code < 0x21 || ch.code > 0x7E || ch == '"' || ch == '\'' || ch == '<' || ch == '>'
        }
        return candidate.takeIf {
            (it.startsWith("https://") || it.startsWith("http://")) &&
                !it.contains("news.google.com", ignoreCase = true)
        }
    }

    private fun extractRedirectTarget(html: String, baseUrl: String): String? {
        metaRefreshRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            resolveRelativeUrl(candidate, baseUrl)?.let { return it }
        }
        dataRedirectRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            resolveRelativeUrl(candidate, baseUrl)?.let { return it }
        }
        canonicalRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (!candidate.contains("news.google.com", ignoreCase = true)) {
                resolveRelativeUrl(candidate, baseUrl)?.let { return it }
            }
        }
        return null
    }

    internal fun extractFromHtml(
        html: String,
        baseUrl: String = "",
        titleHint: String = ""
    ): ExtractedArticleContent? {
        if (html.isBlank()) return null

        val ogImage = extractMetaContent(html, "property", "og:image")
            ?: extractMetaContent(html, "name", "twitter:image")
            ?: extractMetaContent(html, "property", "twitter:image")
        val resolvedImage = ogImage?.let { resolveRelativeUrl(it, baseUrl) }
            ?.takeIf { !isNonContentImage(it) }

        // 1. Try JSON-LD articleBody
        val jsonLdParagraphs = extractJsonLdBody(html)
            .let { filterArticleParagraphs(it, titleHint) }

        // 2. Try semantic article / content containers
        val containerParagraphs = if (jsonLdParagraphs.sumOf { it.length } < 180) {
            extractSemanticContainerParagraphs(html, titleHint)
        } else {
            emptyList()
        }

        // 3. Fallback: scan <p> tags across cleaned body
        val documentParagraphs = if (
            jsonLdParagraphs.sumOf { it.length } < 180 &&
            containerParagraphs.sumOf { it.length } < 180
        ) {
            extractAllDocumentParagraphs(html, titleHint)
        } else {
            emptyList()
        }

        val bestParagraphs = listOf(jsonLdParagraphs, containerParagraphs, documentParagraphs)
            .maxByOrNull { list -> list.sumOf { it.length } }
            .orEmpty()

        val finalParagraphs = if (bestParagraphs.isNotEmpty()) {
            bestParagraphs
        } else {
            val metaDesc = extractMetaContent(html, "property", "og:description")
                ?: extractMetaContent(html, "name", "description")
                ?: extractMetaContent(html, "name", "twitter:description")
            val cleanMeta = metaDesc?.let { cleanSingleText(it) }.orEmpty()
            if (cleanMeta.length >= 40 && cleanMeta != titleHint.trim()) {
                listOf(cleanMeta)
            } else {
                emptyList()
            }
        }

        if (finalParagraphs.isEmpty()) return null

        val fullText = finalParagraphs.joinToString("\n\n")
        val leadSummary = buildLeadSummary(finalParagraphs)
        return ExtractedArticleContent(
            fullText = fullText,
            leadSummary = leadSummary,
            imageUrl = resolvedImage
        )
    }

    private fun extractJsonLdBody(html: String): List<String> {
        val bodies = mutableListOf<String>()
        for (match in jsonLdScriptRegex.findAll(html)) {
            val rawJson = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rawJson.isBlank()) continue
            runCatching {
                val trimmed = rawJson.trimStart()
                when {
                    trimmed.startsWith("{") -> collectArticleBodies(JSONObject(trimmed), bodies)
                    trimmed.startsWith("[") -> collectArticleBodies(JSONArray(trimmed), bodies)
                }
            }
        }
        val bestBody = bodies.maxByOrNull { it.length } ?: return emptyList()
        return if (bestBody.contains("<")) {
            htmlToParagraphs(bestBody)
        } else {
            bestBody.split(Regex("\\r?\\n+"))
                .map { cleanSingleText(it) }
                .filter { it.isNotBlank() }
        }
    }

    private fun collectArticleBodies(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> {
                val body = node.optString("articleBody", "").trim()
                if (body.length > 60) out.add(body)
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != "articleBody") {
                        collectArticleBodies(node.opt(key), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectArticleBodies(node.opt(i), out)
                }
            }
        }
    }

    private fun extractSemanticContainerParagraphs(html: String, titleHint: String): List<String> {
        val cleanedHtml = stripNoiseTags(html)
        val candidates = mutableListOf<List<String>>()

        articleTagRegex.findAll(cleanedHtml).forEach { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val paras = extractParagraphTags(inner).ifEmpty { htmlToParagraphs(inner) }
            val filtered = filterArticleParagraphs(paras, titleHint)
            if (filtered.isNotEmpty()) candidates.add(filtered)
        }

        mainTagRegex.findAll(cleanedHtml).forEach { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val paras = extractParagraphTags(inner).ifEmpty { htmlToParagraphs(inner) }
            val filtered = filterArticleParagraphs(paras, titleHint)
            if (filtered.isNotEmpty()) candidates.add(filtered)
        }

        contentContainerRegex.findAll(cleanedHtml).forEach { match ->
            val startIdx = match.range.last + 1
            val sliceEnd = (startIdx + 24_000).coerceAtMost(cleanedHtml.length)
            val slice = cleanedHtml.substring(startIdx, sliceEnd)
            val paras = filterArticleParagraphs(extractParagraphTags(slice), titleHint)
            if (paras.isNotEmpty()) candidates.add(paras)
        }

        return candidates.maxByOrNull { list -> list.sumOf { it.length } }.orEmpty()
    }

    private fun extractAllDocumentParagraphs(html: String, titleHint: String): List<String> {
        val cleaned = stripNoiseTags(html)
        val pTags = extractParagraphTags(cleaned)
        return filterArticleParagraphs(pTags, titleHint, minParagraphLength = 35)
    }

    private fun extractParagraphTags(html: String): List<String> {
        return paragraphTagRegex.findAll(html)
            .map { match -> cleanSingleText(match.groupValues.getOrNull(1).orEmpty()) }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun filterArticleParagraphs(
        paragraphs: List<String>,
        titleHint: String,
        minParagraphLength: Int = 24
    ): List<String> {
        val cleanTitle = cleanSingleText(titleHint)
        val seen = HashSet<String>()
        return paragraphs.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.length < minParagraphLength) return@mapNotNull null
            if (cleanTitle.isNotBlank() && (trimmed == cleanTitle || trimmed.equals(cleanTitle, ignoreCase = true))) {
                return@mapNotNull null
            }
            if (isBoilerplateParagraph(trimmed)) return@mapNotNull null
            if (!seen.add(trimmed)) return@mapNotNull null
            trimmed
        }
    }

    private fun isBoilerplateParagraph(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (boilerplateMarkers.any { lower.contains(it) }) return true
        // Skip lines packed with links/separators typical of nav bars
        if (text.count { it == '|' } >= 3 || text.count { it == '•' } >= 4) return true
        return false
    }

    private fun stripNoiseTags(html: String): String {
        return html
            .replace(scriptStyleNoiseRegex, " ")
            .replace(chromeNoiseRegex, " ")
            .replace(htmlCommentRegex, " ")
    }

    private fun extractMetaContent(html: String, attrName: String, attrValue: String): String? {
        val patternA = Regex(
            """<meta\b[^>]*\b$attrName\s*=\s*["']${Regex.escape(attrValue)}["'][^>]*\bcontent\s*=\s*["']([^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        patternA.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val patternB = Regex(
            """<meta\b[^>]*\bcontent\s*=\s*["']([^"']+)["'][^>]*\b$attrName\s*=\s*["']${Regex.escape(attrValue)}["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return patternB.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun resolveRelativeUrl(candidate: String, baseUrl: String): String? {
        val unescaped = candidate.replace("&amp;", "&").trim()
        if (unescaped.isBlank()) return null
        val lower = unescaped.lowercase(Locale.ROOT)
        if (lower.startsWith("https://") || lower.startsWith("http://")) return unescaped
        if (unescaped.startsWith("//")) return "https:$unescaped"
        if (baseUrl.isBlank()) return null
        return runCatching { URI(baseUrl).resolve(unescaped).toString() }.getOrNull()
    }

    private fun isNonContentImage(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return nonContentImageMarkers.any { lower.contains(it) }
    }

    companion object {
        private const val MAX_HTML_BYTES = 750_000

        private val embeddedUrlRegex = Regex("""https?://[^\s"'<>\\^\x00-\x1f\x7f-\xff]+""")
        private val metaRefreshRegex = Regex(
            """<meta\b[^>]*http-equiv\s*=\s*["']refresh["'][^>]*content\s*=\s*["'][^"']*?url\s*=\s*([^"'\s>]+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val dataRedirectRegex = Regex(
            """data-n-au\s*=\s*["'](https?://[^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val canonicalRegex = Regex(
            """<link\b[^>]*rel\s*=\s*["']canonical["'][^>]*href\s*=\s*["'](https?://[^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val jsonLdScriptRegex = Regex(
            """<script\b[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val scriptStyleNoiseRegex = Regex(
            """<(script|style|noscript|iframe|svg|canvas|form)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val chromeNoiseRegex = Regex(
            """<(nav|header|footer|aside)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val htmlCommentRegex = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        private val articleTagRegex = Regex(
            """<article\b[^>]*>(.*?)</article>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val mainTagRegex = Regex(
            """<main\b[^>]*>(.*?)</main>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val contentContainerRegex = Regex(
            """<(div|section)\b[^>]*(?:id|class)\s*=\s*["'][^"']*(?:article[-_]?body|article[-_]?content|article[-_]?text|entry[-_]?content|post[-_]?content|story[-_]?body|news[-_]?details|news[-_]?body|content[-_]?body|main[-_]?content)[^"']*["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val paragraphTagRegex = Regex(
            """<p\b[^>]*>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val blockBreakRegex = Regex(
            """<(?:br\s*/?|/(?:p|div|li|h[1-6]|blockquote|tr|article|section))>""",
            RegexOption.IGNORE_CASE
        )
        private val nonContentImageMarkers = listOf(
            ".svg", "1x1", "/pixel", "pixel.", "spacer.", "blank.gif",
            "transparent.", "favicon", "doubleclick.net", "scorecardresearch.com"
        )
        private val boilerplateMarkers = listOf(
            "جميع الحقوق محفوظة",
            "سياسة الخصوصية",
            "شروط الاستخدام",
            "ملفات تعريف الارتباط",
            "اشترك في النشرة",
            "تابعونا على",
            "اقرأ أيضاً:",
            "اقرأ ايضا:",
            "مواضيع ذات صلة",
            "all rights reserved",
            "privacy policy",
            "terms of use",
            "cookie policy",
            "هەموو مافەکان پارێزراوە",
            "سیاسەتی تایبەتمەندی"
        )

        fun cleanSingleText(htmlOrText: String): String {
            if (htmlOrText.isBlank()) return ""
            return HtmlCompat.fromHtml(htmlOrText, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun htmlToParagraphs(html: String): List<String> {
            if (html.isBlank()) return emptyList()
            val cleaned = html
                .replace(scriptStyleNoiseRegex, " ")
                .replace(htmlCommentRegex, " ")
                .replace(blockBreakRegex, "\n")

            val plain = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace('\u00A0', ' ')

            return plain.split(Regex("\\r?\\n+"))
                .map { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
                .filter { it.isNotBlank() }
        }

        fun buildLeadSummary(paragraphs: List<String>, maxChars: Int = 220): String {
            val first = paragraphs.firstOrNull()?.trim().orEmpty()
            if (first.length <= maxChars) return first
            val cut = first.take(maxChars)
            val lastSpace = cut.lastIndexOf(' ')
            return if (lastSpace > maxChars / 2) {
                cut.substring(0, lastSpace).trimEnd() + "…"
            } else {
                cut.trimEnd() + "…"
            }
        }
    }
}
