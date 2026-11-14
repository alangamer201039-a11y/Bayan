package com.example.data.remote

import android.util.Base64
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
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
        val rawTrimmed = articleUrl.trim()
        val initialUrl = resolveArticleUrl(rawTrimmed)
        if (initialUrl.isBlank() || (!initialUrl.startsWith("http://") && !initialUrl.startsWith("https://"))) {
            return@withContext null
        }

        val firstFetch = fetchHtmlPage(initialUrl) ?: return@withContext null
        var (finalUrl, html) = firstFetch

        // If we landed on a Google News wrapper page (encrypted AU_yqL tokens, etc.), unwrap it
        if (finalUrl.contains("news.google.com", ignoreCase = true) || html.length < 1500) {
            val redirectTarget = extractRedirectTarget(html, finalUrl)
                ?: if (finalUrl.contains("news.google.com", ignoreCase = true)) {
                    decodeGoogleNewsViaBatchExecute(rawTrimmed, html)
                } else {
                    null
                }
            if (redirectTarget != null && redirectTarget != finalUrl) {
                fetchHtmlPage(redirectTarget)?.let { (resolved, nextHtml) ->
                    finalUrl = resolved
                    html = nextHtml
                }
            }
        }

        if (finalUrl.contains("news.google.com", ignoreCase = true)) {
            return@withContext null
        }

        extractFromHtml(html = html, baseUrl = finalUrl, titleHint = titleHint)
    }

    private fun fetchHtmlPage(url: String): Pair<String, String>? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ckb,ar;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            if (contentType.isNotBlank() &&
                !contentType.contains("html") &&
                !contentType.contains("xml") &&
                !contentType.contains("text")
            ) {
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
        val token = extractGoogleNewsToken(url) ?: return null

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
        return candidate.takeIf { isValidPublisherUrl(it) }
    }

    private fun extractGoogleNewsToken(url: String): String? {
        val token = url.substringAfter("/articles/", "")
            .ifBlank { url.substringAfter("/read/", "") }
            .substringBefore("?")
            .substringBefore("/")
            .trim()
        return token.takeIf { it.isNotBlank() }
    }

    private fun decodeGoogleNewsViaBatchExecute(articleUrl: String, html: String): String? = runCatching {
        val token = extractGoogleNewsToken(articleUrl) ?: return@runCatching null
        val signature = gnSignatureRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return@runCatching null
        val timestamp = gnTimestampRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return@runCatching null

        val payloadJson = JSONArray().apply {
            put(
                JSONArray().apply {
                    put(
                        JSONArray().apply {
                            put("Fbv4je")
                            put("[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"$token\",$timestamp,\"$signature\"]")
                            put(JSONObject.NULL)
                            put("generic")
                        }
                    )
                }
            )
        }.toString()

        val formBody = "f.req=${URLEncoder.encode(payloadJson, "UTF-8")}"
            .toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType())

        val request = Request.Builder()
            .url("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
            .post(formBody)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body?.string().orEmpty()
            val decodedMatch = gartUrlResRegex.find(body)?.groupValues?.getOrNull(1)
                ?.replace("\\/", "/")
                ?.replace("\\u003d", "=")
                ?.replace("\\u0026", "&")
                ?: embeddedUrlRegex.findAll(body.replace("\\/", "/"))
                    .map { it.value }
                    .firstOrNull { isValidPublisherUrl(it) }
            decodedMatch?.takeIf { isValidPublisherUrl(it) }
        }
    }.getOrNull()

    internal fun extractRedirectTarget(html: String, baseUrl: String): String? {
        metaRefreshRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            resolveRelativeUrl(candidate, baseUrl)?.takeIf { isValidPublisherUrl(it) }?.let { return it }
        }
        dataRedirectRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            resolveRelativeUrl(candidate, baseUrl)?.takeIf { isValidPublisherUrl(it) }?.let { return it }
        }
        canonicalRegex.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            resolveRelativeUrl(candidate, baseUrl)?.takeIf { isValidPublisherUrl(it) }?.let { return it }
        }
        if (baseUrl.contains("news.google.com", ignoreCase = true)) {
            anchorHrefRegex.findAll(html).forEach { match ->
                val href = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val resolved = resolveRelativeUrl(href, baseUrl)
                if (resolved != null && isValidPublisherUrl(resolved)) {
                    return resolved
                }
            }
        }
        return null
    }

    private fun isValidPublisherUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false
        return googleWrapperDomains.none { domain -> lower.contains(domain) }
    }

    internal fun extractFromHtml(
        html: String,
        baseUrl: String = "",
        titleHint: String = ""
    ): ExtractedArticleContent? {
        if (html.isBlank()) return null

        val jsonLdData = extractJsonLdData(html)
        val jsonLdParagraphs = filterArticleParagraphs(jsonLdData.paragraphs, titleHint)

        // 2. Try semantic article / content containers
        val containerParagraphs = if (jsonLdParagraphs.sumOf { it.length } < 180) {
            extractSemanticContainerParagraphs(html, titleHint)
        } else {
            emptyList()
        }

        // 3. Try Next.js __NEXT_DATA__ SSR payload if needed
        val nextData = if (jsonLdParagraphs.sumOf { it.length } < 180 && containerParagraphs.sumOf { it.length } < 180) {
            extractNextData(html, titleHint)
        } else {
            NextDataResult(emptyList(), null)
        }

        // 4. Fallback: scan <p> tags across cleaned body
        val documentParagraphs = if (
            jsonLdParagraphs.sumOf { it.length } < 180 &&
            containerParagraphs.sumOf { it.length } < 180 &&
            nextData.paragraphs.sumOf { it.length } < 180
        ) {
            extractAllDocumentParagraphs(html, titleHint)
        } else {
            emptyList()
        }

        val bestParagraphs = listOf(jsonLdParagraphs, containerParagraphs, nextData.paragraphs, documentParagraphs)
            .maxByOrNull { list -> list.sumOf { it.length } }
            .orEmpty()

        val finalParagraphs = if (bestParagraphs.isNotEmpty()) {
            bestParagraphs
        } else {
            val metaDesc = extractMetaContent(html, "property", "og:description")
                ?: extractMetaContent(html, "name", "description")
                ?: extractMetaContent(html, "name", "twitter:description")
            val cleanMeta = metaDesc?.let { cleanSingleText(it) }.orEmpty()
            if (cleanMeta.length >= 35 &&
                cleanMeta != titleHint.trim() &&
                !isBoilerplateParagraph(cleanMeta)
            ) {
                listOf(cleanMeta)
            } else {
                emptyList()
            }
        }

        val resolvedImage = extractBestImageUrl(
            html = html,
            baseUrl = baseUrl,
            jsonLdImages = jsonLdData.imageCandidates,
            nextDataImage = nextData.imageCandidate
        )

        if (finalParagraphs.isEmpty() && resolvedImage == null) return null

        val fullText = finalParagraphs.joinToString("\n\n")
        val leadSummary = if (finalParagraphs.isNotEmpty()) buildLeadSummary(finalParagraphs) else ""
        return ExtractedArticleContent(
            fullText = fullText,
            leadSummary = leadSummary,
            imageUrl = resolvedImage
        )
    }

    private fun extractBestImageUrl(
        html: String,
        baseUrl: String,
        jsonLdImages: List<String>,
        nextDataImage: String?
    ): String? {
        val metaCandidates = listOfNotNull(
            extractMetaContent(html, "property", "og:image:secure_url"),
            extractMetaContent(html, "property", "og:image"),
            extractMetaContent(html, "name", "twitter:image:src"),
            extractMetaContent(html, "name", "twitter:image"),
            extractMetaContent(html, "property", "twitter:image"),
            extractMetaContent(html, "itemprop", "image"),
            extractLinkHref(html, "image_src")
        )
        val inlineImages = extractInlineArticleImages(html)
        val allCandidates = metaCandidates + jsonLdImages + listOfNotNull(nextDataImage) + inlineImages
        return allCandidates
            .asSequence()
            .mapNotNull { candidate -> resolveRelativeUrl(candidate, baseUrl) }
            .firstOrNull { resolved -> sanitizeArticleImageUrl(resolved) != null }
    }

    private fun extractLinkHref(html: String, relValue: String): String? {
        val pattern = Regex(
            """<link\b[^>]*rel\s*=\s*["']${Regex.escape(relValue)}["'][^>]*href\s*=\s*["']([^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return pattern.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractInlineArticleImages(html: String): List<String> {
        val cleaned = stripNoiseTags(html)
        val sections = buildList {
            articleTagRegex.findAll(cleaned).forEach { add(it.groupValues.getOrNull(1).orEmpty()) }
            mainTagRegex.findAll(cleaned).forEach { add(it.groupValues.getOrNull(1).orEmpty()) }
            figureTagRegex.findAll(cleaned).forEach { add(it.groupValues.getOrNull(1).orEmpty()) }
        }
        if (sections.isEmpty()) return emptyList()

        val urls = mutableListOf<String>()
        for (section in sections) {
            imgTagRegex.findAll(section).forEach { tagMatch ->
                val tag = tagMatch.value
                if (isSmallOrUiImgTag(tag)) return@forEach
                imgTagAttrRegex.find(tag)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { urls.add(it) }
                imgSrcSetRegex.find(tag)?.groupValues?.getOrNull(1)
                    ?.split(",")
                    ?.firstOrNull()
                    ?.trim()
                    ?.substringBefore(" ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { urls.add(it) }
            }
        }
        return urls
    }

    private fun isSmallOrUiImgTag(tag: String): Boolean {
        val lower = tag.lowercase(Locale.ROOT)
        if (lower.contains("avatar") || lower.contains("logo") ||
            lower.contains("emoji") || lower.contains("wp-smiley") ||
            lower.contains("icon") || lower.contains("badge")
        ) {
            return true
        }
        val width = imgWidthRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val height = imgHeightRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return (width != null && width in 1..48) || (height != null && height in 1..48)
    }

    private data class JsonLdResult(
        val paragraphs: List<String>,
        val imageCandidates: List<String>
    )

    private fun extractJsonLdData(html: String): JsonLdResult {
        val bodies = mutableListOf<String>()
        val images = mutableListOf<String>()
        for (match in jsonLdScriptRegex.findAll(html)) {
            val rawJson = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rawJson.isBlank()) continue
            runCatching {
                val trimmed = rawJson.trimStart()
                when {
                    trimmed.startsWith("{") -> collectJsonLdNode(JSONObject(trimmed), bodies, images)
                    trimmed.startsWith("[") -> collectJsonLdNode(JSONArray(trimmed), bodies, images)
                }
            }
        }
        val bestBody = bodies.maxByOrNull { it.length }
        val paragraphs = when {
            bestBody == null -> emptyList()
            bestBody.contains("<") -> htmlToParagraphs(bestBody)
            else -> bestBody.split(Regex("\\r?\\n+"))
                .map { cleanSingleText(it) }
                .filter { it.isNotBlank() }
        }
        return JsonLdResult(paragraphs = paragraphs, imageCandidates = images)
    }

    private fun collectJsonLdNode(
        node: Any?,
        bodies: MutableList<String>,
        images: MutableList<String>,
        insideNonArticleMeta: Boolean = false
    ) {
        when (node) {
            is JSONObject -> {
                val typeStr = node.optString("@type", "").lowercase(Locale.ROOT)
                val isMetaNode = insideNonArticleMeta ||
                    typeStr in setOf("organization", "person", "website", "breadcrumblist", "listitem")

                val body = node.optString("articleBody", "").trim()
                if (body.length > 60) bodies.add(body)

                if (!isMetaNode) {
                    extractImageFieldFromJson(node.opt("image"), images)
                    extractImageFieldFromJson(node.opt("thumbnailUrl"), images)
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLower = key.lowercase(Locale.ROOT)
                    if (key != "articleBody" && key != "image" && key != "thumbnailUrl") {
                        val childIsMeta = isMetaNode ||
                            keyLower in setOf("publisher", "author", "creator", "provider", "brand", "logo", "breadcrumb")
                        collectJsonLdNode(node.opt(key), bodies, images, childIsMeta)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectJsonLdNode(node.opt(i), bodies, images, insideNonArticleMeta)
                }
            }
        }
    }

    private fun extractImageFieldFromJson(value: Any?, out: MutableList<String>) {
        when (value) {
            is String -> if (value.isNotBlank()) out.add(value.trim())
            is JSONObject -> {
                val url = value.optString("url", "").ifBlank { value.optString("contentUrl", "") }
                if (url.isNotBlank()) out.add(url.trim())
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    extractImageFieldFromJson(value.opt(i), out)
                }
            }
        }
    }

    private data class NextDataResult(
        val paragraphs: List<String>,
        val imageCandidate: String?
    )

    private fun extractNextData(html: String, titleHint: String): NextDataResult {
        val rawJson = nextDataScriptRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
            ?: return NextDataResult(emptyList(), null)
        val bodies = mutableListOf<String>()
        val images = mutableListOf<String>()
        runCatching {
            val root = JSONObject(rawJson)
            collectNextDataNode(root, bodies, images)
        }
        val bestBody = bodies.maxByOrNull { it.length }
        val paragraphs = if (bestBody != null) {
            val parsed = if (bestBody.contains("<")) {
                htmlToParagraphs(bestBody)
            } else {
                bestBody.split(Regex("\\r?\\n+"))
                    .map { cleanSingleText(it) }
                    .filter { it.isNotBlank() }
            }
            filterArticleParagraphs(parsed, titleHint)
        } else {
            emptyList()
        }
        return NextDataResult(
            paragraphs = paragraphs,
            imageCandidate = images.firstOrNull { !isNonContentImage(it) }
        )
    }

    private fun collectNextDataNode(
        node: Any?,
        bodies: MutableList<String>,
        images: MutableList<String>,
        skipImagesInSubtree: Boolean = false
    ) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val lower = key.lowercase(Locale.ROOT)
                    val value = node.opt(key)
                    val isExcludedImageBranch = skipImagesInSubtree ||
                        lower in nextDataExcludedImageBranches
                    if (value is String) {
                        val trimmed = value.trim()
                        if (lower in setOf("content", "body", "articlebody", "fulltext", "details", "description") &&
                            trimmed.length > 140 &&
                            !trimmed.startsWith("http")
                        ) {
                            bodies.add(trimmed)
                        } else if (!isExcludedImageBranch &&
                            lower in setOf("image", "imageurl", "coverimage", "featuredimage", "mainimage", "thumbnail", "photo") &&
                            (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/"))
                        ) {
                            images.add(trimmed)
                        }
                    } else {
                        collectNextDataNode(value, bodies, images, isExcludedImageBranch)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    // Arrays in __NEXT_DATA__ often hold related posts, nav items, or categories
                    collectNextDataNode(node.opt(i), bodies, images, skipImagesInSubtree = true)
                }
            }
        }
    }

    private fun extractSemanticContainerParagraphs(html: String, titleHint: String): List<String> {
        val cleanedHtml = stripNoiseTags(html)
        val candidates = mutableListOf<List<String>>()

        articleTagRegex.findAll(cleanedHtml).forEach { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val pTags = filterArticleParagraphs(extractParagraphTags(inner), titleHint)
            val fallback = if (pTags.sumOf { it.length } < 140) {
                filterArticleParagraphs(htmlToParagraphs(inner), titleHint)
            } else {
                emptyList()
            }
            val chosen = if (fallback.sumOf { it.length } > pTags.sumOf { it.length }) fallback else pTags
            if (chosen.isNotEmpty()) candidates.add(chosen)
        }

        mainTagRegex.findAll(cleanedHtml).forEach { match ->
            val inner = match.groupValues.getOrNull(1).orEmpty()
            val pTags = filterArticleParagraphs(extractParagraphTags(inner), titleHint)
            val fallback = if (pTags.sumOf { it.length } < 140) {
                filterArticleParagraphs(htmlToParagraphs(inner), titleHint)
            } else {
                emptyList()
            }
            val chosen = if (fallback.sumOf { it.length } > pTags.sumOf { it.length }) fallback else pTags
            if (chosen.isNotEmpty()) candidates.add(chosen)
        }

        contentContainerRegex.findAll(cleanedHtml).forEach { match ->
            val startIdx = match.range.last + 1
            val sliceEnd = (startIdx + 28_000).coerceAtMost(cleanedHtml.length)
            val slice = cleanedHtml.substring(startIdx, sliceEnd)
            val pTags = filterArticleParagraphs(extractParagraphTags(slice), titleHint)
            val fallback = if (pTags.sumOf { it.length } < 140) {
                filterArticleParagraphs(htmlToParagraphs(slice.take(12_000)), titleHint, minParagraphLength = 35)
            } else {
                emptyList()
            }
            val chosen = if (fallback.sumOf { it.length } > pTags.sumOf { it.length }) fallback else pTags
            if (chosen.isNotEmpty()) candidates.add(chosen)
        }

        return candidates.maxByOrNull { list -> list.sumOf { it.length } }.orEmpty()
    }

    private fun extractAllDocumentParagraphs(html: String, titleHint: String): List<String> {
        val cleaned = stripNoiseTags(html)
        val pTags = extractParagraphTags(cleaned)
        return filterArticleParagraphs(pTags, titleHint, minParagraphLength = 32)
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
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("cid:")) {
            return null
        }
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

        private val googleWrapperDomains = listOf(
            "news.google.com", "accounts.google.com", "support.google.com",
            "policies.google.com", "play.google.com", "gstatic.com", "googleusercontent.com"
        )

        private val embeddedUrlRegex = Regex("""https?://[^\s"'<>\\^\x00-\x1f\x7f-\xff]+""")
        private val gnSignatureRegex = Regex("""data-n-a-sg\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val gnTimestampRegex = Regex("""data-n-a-ts\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val gartUrlResRegex = Regex("""\[\s*\\"garturlres\\"\s*,\s*\\"(https?://[^"\\]+)\\"""")
        private val anchorHrefRegex = Regex("""<a\b[^>]*\bhref\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)

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
        private val nextDataScriptRegex = Regex(
            """<script\b[^>]*id\s*=\s*["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
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
        private val figureTagRegex = Regex(
            """<figure\b[^>]*>(.*?)</figure>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val contentContainerRegex = Regex(
            """<(div|section)\b[^>]*(?:id|class)\s*=\s*["'][^"']*(?:article[-_]?body|article[-_]?content|article[-_]?text|article[-_]?details|entry[-_]?content|post[-_]?content|post[-_]?body|post[-_]?text|single[-_]?content|story[-_]?body|story[-_]?content|news[-_]?details|news[-_]?body|news[-_]?content|detail[-_]?content|content[-_]?body|main[-_]?content|text[-_]?content|wp-block-post-content|td-post-content|field-item|\bprose\b)[^"']*["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val paragraphTagRegex = Regex(
            """<p\b[^>]*>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val imgTagRegex = Regex(
            """<img\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val imgTagAttrRegex = Regex(
            """\b(?:src|data-src|data-original|data-lazy-src)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val imgSrcSetRegex = Regex(
            """\bsrcset\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val imgWidthRegex = Regex("""\bwidth\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
        private val imgHeightRegex = Regex("""\bheight\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
        private val blockBreakRegex = Regex(
            """<(?:br\s*/?|/(?:p|div|li|h[1-6]|blockquote|tr|article|section))>""",
            RegexOption.IGNORE_CASE
        )
        private val nextDataExcludedImageBranches = setOf(
            "author", "authors", "writer", "reporter", "publisher", "organization",
            "related", "relatedposts", "relatednews", "recommended", "trending",
            "popular", "morenews", "menu", "menus", "navbar", "navigation",
            "header", "footer", "sidebar", "site", "settings", "sitesettings",
            "categories", "tags", "ads", "banners", "social"
        )
        private val nonContentImageMarkers = listOf(
            ".svg", ".ico", "1x1", "/pixel", "pixel.", "spacer.", "blank.gif",
            "transparent.", "favicon", "doubleclick.net", "scorecardresearch.com",
            "googlesyndication.com", "adservice.",
            "lh3.googleusercontent.com", "news.google.com", "gstatic.com",
            "/logo", "logo.", "logo-", "-logo", "_logo", "site-logo", "brand-logo",
            "placeholder", "no-image", "no_image", "noimage", "not-found",
            "default-img", "default_img", "default-image", "default_image",
            "default-thumb", "og-default", "default-og", "fallback-img", "fallback.",
            "/avatar", "avatar.", "gravatar.com", "s.w.org/images/core/emoji",
            "/icons/", "icon-", "-icon.", "/badges/", "/badge"
        )

        fun isNonContentImage(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return nonContentImageMarkers.any { lower.contains(it) }
        }

        fun sanitizeArticleImageUrl(url: String?, allowLocalResource: Boolean = false): String? {
            val trimmed = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val lower = trimmed.lowercase(Locale.ROOT)
            if (lower == "null" || lower == "none" || lower == "undefined" || lower == "about:blank") {
                return null
            }
            val validScheme = lower.startsWith("https://") || lower.startsWith("http://") ||
                (allowLocalResource && (lower.startsWith("android.resource://") || lower.startsWith("content://")))
            if (!validScheme) return null
            if (isNonContentImage(lower)) return null
            return trimmed
        }
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
            "عرض التغطية الكاملة",
            "تفاصيل الخبر كاملة متوفرة",
            "التوجيه للمتصفح",
            "جاري التحويل",
            "enable javascript",
            "redirecting",
            "view full coverage",
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
