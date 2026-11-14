package com.example.data.remote

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.example.model.AppLanguage
import com.example.model.FeedEndpoint
import com.example.model.IraqRelevanceFilter
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.OfficialFeeds
import com.example.model.SourceTrustLevel
import com.example.model.TrustedSource
import com.example.model.TrustedSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class RssNewsService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val translationFilter: NewsTranslationFilter = NewsTranslationFilter(),
    private val articleContentFetcher: ArticleContentFetcher = ArticleContentFetcher(client)
) {
    suspend fun fetchCategoryFeed(
        category: NewsCategory,
        verifiedSourcesOnly: Boolean = true,
        language: AppLanguage = AppLanguage.ARABIC
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val configuredFeeds = OfficialFeeds.unifiedFeedsFor(category)
        val endpoints: List<FeedEndpoint> = if (verifiedSourcesOnly && configuredFeeds.isNotEmpty()) {
            configuredFeeds
        } else {
            val queries = listOfNotNull(category.rssQuery, category.rssQueryCkb).distinct()
            val fallbackEndpoints = queries.map { q ->
                val encoded = URLEncoder.encode(q, "UTF-8")
                FeedEndpoint(
                    url = "https://news.google.com/rss/search?q=$encoded&hl=ar&gl=IQ&ceid=IQ:ar",
                    filterIraqOnly = true
                )
            }
            fallbackEndpoints + configuredFeeds
        }

        if (endpoints.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val fetchedLists = endpoints.map { endpoint ->
                async {
                    runCatching { fetchAndParseEndpoint(endpoint, category, language) }.getOrNull().orEmpty()
                }
            }.awaitAll()

            val merged = fetchedLists.flatten()
                .filter { IraqRelevanceFilter.isRelatedToIraq(it) }
                .distinctBy { it.originalTitle.ifBlank { it.title } }
                .sortedWith(
                    compareByDescending<NewsArticle> { it.trustLevel == SourceTrustLevel.OFFICIAL }
                        .thenByDescending { it.publishedEpochMs }
                )

            val filteredTrust = if (verifiedSourcesOnly) {
                val trusted = merged.filter { it.trustLevel != SourceTrustLevel.GENERAL }
                trusted.ifEmpty { merged }
            } else {
                merged
            }

            // Cap per-category candidates to keep latency low and UI snappy
            val candidates = filteredTrust.take(25)
            val enriched = enrichTopShortArticles(candidates, maxToEnrich = 16)
            translationFilter.translateArticles(enriched, language)
        }
    }

    private suspend fun enrichTopShortArticles(
        articles: List<NewsArticle>,
        maxToEnrich: Int
    ): List<NewsArticle> = coroutineScope {
        val targetIds = articles
            .filter { (it.fullContent.isNullOrBlank() || it.imageUrl.isNullOrBlank()) && it.link.isNotBlank() }
            .take(maxToEnrich)
            .map { it.id }
            .toSet()

        if (targetIds.isEmpty()) return@coroutineScope articles

        val semaphore = Semaphore(6)
        articles.map { article ->
            if (article.id !in targetIds) {
                async { article }
            } else {
                async {
                    val extracted = semaphore.withPermit {
                        withTimeoutOrNull(5500L) {
                            runCatching {
                                articleContentFetcher.fetchFullArticle(
                                    articleUrl = article.link,
                                    titleHint = article.originalTitle.ifBlank { article.title }
                                )
                            }.getOrNull()
                        }
                    }
                    if (extracted != null) {
                        val hasNewText = extracted.fullText.isNotBlank()
                        val bestFullText = if (hasNewText && (article.fullContent.isNullOrBlank() || extracted.fullText.length > article.fullContent.length)) {
                            extracted.fullText
                        } else {
                            article.fullContent
                        }
                        val detectedLang = if (hasNewText) {
                            NewsTranslationFilter.detectLanguage(bestFullText.orEmpty())
                        } else {
                            AppLanguage.fromCode(article.originalLanguageCode)
                        }
                        val updatedSummary = article.summary.ifBlank { extracted.leadSummary }
                        val updatedOriginalSummary = article.originalSummary.ifBlank { extracted.leadSummary }
                        article.copy(
                            summary = updatedSummary,
                            fullContent = bestFullText,
                            originalSummary = updatedOriginalSummary,
                            originalFullContent = bestFullText ?: article.originalFullContent,
                            originalLanguageCode = detectedLang.code,
                            imageUrl = article.imageUrl ?: extracted.imageUrl
                        )
                    } else {
                        article
                    }
                }
            }
        }.awaitAll()
    }

    private fun fetchAndParseEndpoint(
        endpoint: FeedEndpoint,
        category: NewsCategory,
        language: AppLanguage
    ): List<NewsArticle> {
        val request = Request.Builder()
            .url(endpoint.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) BayanIraqNews/1.0")
            .header("Accept", "application/rss+xml, application/xml, text/xml, application/atom+xml")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("RSS HTTP ${response.code}")
            }
            val xml = response.body?.string().orEmpty().trimStart()
            if (xml.isBlank() || (!xml.startsWith("<") || (!xml.contains("<rss") && !xml.contains("<feed") && !xml.contains("<item")))) {
                return emptyList()
            }
            val parsed = parseRssXml(xml, category, endpoint.forcedSource, language)
            return if (endpoint.filterIraqOnly) {
                parsed.filter { IraqRelevanceFilter.isRelatedToIraq(it) }
            } else {
                parsed
            }
        }
    }

    internal fun parseRssXml(
        xml: String,
        category: NewsCategory,
        forcedSource: TrustedSource? = null,
        language: AppLanguage = AppLanguage.ARABIC
    ): List<NewsArticle> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val articles = mutableListOf<NewsArticle>()
        var insideItem = false
        var insideItemImage = false
        var rawTitle = ""
        var rawLink = ""
        var rawPubDate = ""
        var rawDescription = ""
        var rawSummary = ""
        var rawContent = ""
        var rawContentEncoded = ""
        var rawSource = ""
        var rawSourceUrl = ""
        var rawMediaContentUrl = ""
        var rawMediaThumbnailUrl = ""
        var rawEnclosureUrl = ""
        var rawItemImageUrl = ""

        val nowMs = System.currentTimeMillis()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "item", "entry" -> {
                            insideItem = true
                            insideItemImage = false
                            rawTitle = ""
                            rawLink = ""
                            rawPubDate = ""
                            rawDescription = ""
                            rawSummary = ""
                            rawContent = ""
                            rawContentEncoded = ""
                            rawSource = ""
                            rawSourceUrl = ""
                            rawMediaContentUrl = ""
                            rawMediaThumbnailUrl = ""
                            rawEnclosureUrl = ""
                            rawItemImageUrl = ""
                        }
                        "title" -> if (insideItem) rawTitle = safeNextText(parser)
                        "link" -> if (insideItem) {
                            val rel = parser.getAttributeValue(null, "rel").orEmpty().lowercase(Locale.ROOT)
                            val type = parser.getAttributeValue(null, "type").orEmpty().lowercase(Locale.ROOT)
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            if (rel == "enclosure" && (type.startsWith("image/") || isLikelyImageUrl(href))) {
                                if (rawEnclosureUrl.isBlank()) rawEnclosureUrl = href
                            } else if (rawLink.isBlank() || rel == "alternate" || rel.isBlank()) {
                                rawLink = if (href.isNotBlank()) href else safeNextText(parser)
                            }
                        }
                        "pubdate", "published", "updated" -> if (insideItem && rawPubDate.isBlank()) {
                            rawPubDate = safeNextText(parser)
                        }
                        "description" -> if (insideItem && rawDescription.isBlank()) {
                            rawDescription = safeNextText(parser)
                        }
                        "summary" -> if (insideItem && rawSummary.isBlank()) {
                            rawSummary = safeNextText(parser)
                        }
                        "content" -> if (insideItem && rawContent.isBlank()) {
                            rawContent = safeNextText(parser)
                        }
                        "content:encoded" -> if (insideItem && rawContentEncoded.isBlank()) {
                            rawContentEncoded = safeNextText(parser)
                        }
                        "source" -> if (insideItem) {
                            rawSourceUrl = parser.getAttributeValue(null, "url").orEmpty()
                            rawSource = safeNextText(parser)
                        }
                        "media:content" -> if (insideItem && rawMediaContentUrl.isBlank()) {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            val medium = parser.getAttributeValue(null, "medium").orEmpty().lowercase(Locale.ROOT)
                            val type = parser.getAttributeValue(null, "type").orEmpty().lowercase(Locale.ROOT)
                            val isNonImage = medium == "video" || medium == "audio" ||
                                type.startsWith("video/") || type.startsWith("audio/")
                            if (url.isNotBlank() && !isNonImage &&
                                (medium == "image" || type.startsWith("image/") || isLikelyImageUrl(url) || (medium.isBlank() && type.isBlank()))
                            ) {
                                rawMediaContentUrl = url
                            }
                        }
                        "media:thumbnail" -> if (insideItem && rawMediaThumbnailUrl.isBlank()) {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            if (url.isNotBlank()) rawMediaThumbnailUrl = url
                        }
                        "enclosure" -> if (insideItem && rawEnclosureUrl.isBlank()) {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty().lowercase(Locale.ROOT)
                            if (url.isNotBlank() && (type.startsWith("image/") || (type.isBlank() && isLikelyImageUrl(url)))) {
                                rawEnclosureUrl = url
                            }
                        }
                        "itunes:image" -> if (insideItem && rawItemImageUrl.isBlank()) {
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                                .ifBlank { parser.getAttributeValue(null, "url").orEmpty() }
                            if (href.isNotBlank()) rawItemImageUrl = href
                        }
                        "image" -> if (insideItem) {
                            insideItemImage = true
                            val attrUrl = parser.getAttributeValue(null, "url").orEmpty()
                                .ifBlank { parser.getAttributeValue(null, "href").orEmpty() }
                            if (attrUrl.isNotBlank() && rawItemImageUrl.isBlank()) {
                                rawItemImageUrl = attrUrl
                            }
                        }
                        "url" -> if (insideItem && insideItemImage && rawItemImageUrl.isBlank()) {
                            rawItemImageUrl = safeNextText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagLower = parser.name.lowercase(Locale.ROOT)
                    if (tagLower == "image" && insideItemImage) {
                        insideItemImage = false
                    }
                    if ((tagLower == "item" || tagLower == "entry") && insideItem) {
                        insideItem = false
                        insideItemImage = false
                        val normalized = buildArticle(
                            rawTitle = rawTitle,
                            rawLink = rawLink,
                            rawPubDate = rawPubDate,
                            rawDescription = rawDescription,
                            rawSummary = rawSummary,
                            rawContent = rawContent,
                            rawContentEncoded = rawContentEncoded,
                            rawSource = rawSource,
                            rawSourceUrl = rawSourceUrl,
                            rawImageCandidates = listOf(
                                rawMediaContentUrl,
                                rawEnclosureUrl,
                                rawMediaThumbnailUrl,
                                rawItemImageUrl
                            ),
                            category = category,
                            forcedSource = forcedSource,
                            language = language,
                            nowMs = nowMs
                        )
                        if (normalized != null) {
                            articles.add(normalized)
                        }
                        if (articles.size >= 15) break
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildArticle(
        rawTitle: String,
        rawLink: String,
        rawPubDate: String,
        rawDescription: String,
        rawSummary: String,
        rawContent: String,
        rawContentEncoded: String,
        rawSource: String,
        rawSourceUrl: String,
        rawImageCandidates: List<String>,
        category: NewsCategory,
        forcedSource: TrustedSource?,
        language: AppLanguage,
        nowMs: Long
    ): NewsArticle? {
        var cleanTitle = stripHtml(rawTitle)
        if (cleanTitle.isBlank()) return null

        val defaultSourceLabel = if (language == AppLanguage.KURDISH) "سەرچاوەی هەواڵ" else "مصدر إخباري"
        var source = forcedSource?.displayName(language) ?: stripHtml(rawSource).ifBlank { defaultSourceLabel }
        val separatorIdx = cleanTitle.lastIndexOf(" - ")
        if (separatorIdx > 0) {
            val extractedSource = cleanTitle.substring(separatorIdx + 3).trim()
            if (extractedSource.isNotBlank()) {
                if (forcedSource == null) {
                    source = extractedSource
                }
                cleanTitle = cleanTitle.substring(0, separatorIdx).trim()
            }
        }

        val cleanTitleNormalized = cleanTitle.trim()
        val parsedBodies = listOf(rawContentEncoded, rawContent, rawDescription, rawSummary)
            .filter { it.isNotBlank() }
            .map { cleanBodyParagraphs(it, cleanTitleNormalized, source) }
            .filter { it.isNotEmpty() }

        val richParagraphs = parsedBodies
            .maxByOrNull { list -> list.sumOf { it.length } }
            .orEmpty()
        val fullBody = richParagraphs.joinToString("\n\n").trim()

        val shortTeaser = parsedBodies
            .filter { it != richParagraphs }
            .map { it.joinToString(" ").trim() }
            .firstOrNull { it.length > 24 }
            .orEmpty()

        val hasSubstantialFullText = fullBody.length > 180 || richParagraphs.size >= 2
        val fullContent: String? = if (hasSubstantialFullText) fullBody else null
        val summary = when {
            shortTeaser.length in 25..240 -> shortTeaser
            hasSubstantialFullText -> ArticleContentFetcher.buildLeadSummary(richParagraphs, maxChars = 220)
            fullBody.length > 24 -> fullBody
            else -> ""
        }

        val detectedSourceLang = NewsTranslationFilter.detectLanguage("$cleanTitle $summary ${fullContent.orEmpty().take(200)}")

        val (trustLevel, sourceDomain) = if (forcedSource != null) {
            forcedSource.trustLevel to forcedSource.domain
        } else {
            TrustedSources.classify(source, rawSourceUrl, rawLink)
        }

        val htmlImageCandidates = extractImageUrlsFromHtml(rawContentEncoded) +
            extractImageUrlsFromHtml(rawContent) +
            extractImageUrlsFromHtml(rawDescription) +
            extractImageUrlsFromHtml(rawSummary)
        val imageUrl = (rawImageCandidates + htmlImageCandidates)
            .firstNotNullOfOrNull { candidate ->
                normalizeImageUrl(
                    candidate = candidate,
                    articleLink = rawLink,
                    sourceUrl = rawSourceUrl,
                    fallbackDomain = sourceDomain
                )
            }

        val publishedMs = parseRssDate(rawPubDate) ?: (nowMs - 45L * 60_000L)
        val ageMinutes = ((nowMs - publishedMs) / 60_000L).coerceAtLeast(1L)
        val isBreaking = category == NewsCategory.BREAKING && ageMinutes < 180L
        val idKey = rawLink.ifBlank { cleanTitle }
        val id = "n" + idKey.hashCode().toUInt().toString(36)

        return NewsArticle(
            id = id,
            categoryId = category.id,
            isBreaking = isBreaking,
            source = source,
            sourceDomain = sourceDomain,
            trustLevel = trustLevel,
            publishedEpochMs = publishedMs,
            title = cleanTitle,
            summary = summary,
            link = rawLink.trim(),
            imageUrl = imageUrl,
            fullContent = fullContent,
            isSaved = false,
            languageCode = detectedSourceLang.code,
            originalTitle = cleanTitle,
            originalSummary = summary,
            originalFullContent = fullContent,
            originalLanguageCode = detectedSourceLang.code
        )
    }

    private fun cleanBodyParagraphs(
        rawHtml: String,
        cleanTitle: String,
        source: String
    ): List<String> {
        val rawParagraphs = ArticleContentFetcher.htmlToParagraphs(rawHtml)
        if (rawParagraphs.isEmpty()) return emptyList()

        return rawParagraphs.mapNotNull { paragraph ->
            var cleaned = paragraph.trim()
            if (cleaned.startsWith(cleanTitle)) {
                cleaned = cleaned.removePrefix(cleanTitle).trimStart(' ', '-', ':', '—', '•')
            }
            if (source.isNotBlank() && cleaned.endsWith(source)) {
                cleaned = cleaned.removeSuffix(source).trimEnd(' ', '-', ':', '—', '•')
            }
            val lower = cleaned.lowercase(Locale.ROOT)
            val isWrapper = lower.contains("عرض التغطية الكاملة") ||
                lower.contains("view full coverage") ||
                lower.contains("تفاصيل الخبر كاملة متوفرة")
            cleaned.trim().takeIf {
                it.isNotBlank() &&
                    !isWrapper &&
                    it != cleanTitle &&
                    it != source &&
                    it.length > 12
            }
        }
    }

    private fun extractImageUrlsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val urls = mutableListOf<String>()
        imgTagRegex.findAll(html).forEach { tagMatch ->
            val tag = tagMatch.value
            val lower = tag.lowercase(Locale.ROOT)
            if (lower.contains("avatar") || lower.contains("logo") ||
                lower.contains("emoji") || lower.contains("wp-smiley") ||
                lower.contains("icon") || lower.contains("badge")
            ) {
                return@forEach
            }
            val w = imgWidthRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val h = imgHeightRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if ((w != null && w in 1..48) || (h != null && h in 1..48)) {
                return@forEach
            }
            imgSrcRegex.find(tag)?.groupValues?.getOrNull(1)?.trim()
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
        return urls
    }

    private fun normalizeImageUrl(
        candidate: String,
        articleLink: String,
        sourceUrl: String,
        fallbackDomain: String
    ): String? {
        val unescaped = candidate
            .replace("&amp;", "&")
            .trim()
        if (unescaped.isBlank()) return null

        val lower = unescaped.lowercase(Locale.ROOT)
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("cid:")) {
            return null
        }

        val resolved = when {
            lower.startsWith("https://") || lower.startsWith("http://") -> unescaped
            unescaped.startsWith("//") -> "https:$unescaped"
            unescaped.startsWith("/") -> {
                val baseOrigin = extractOrigin(articleLink)
                    ?: extractOrigin(sourceUrl)
                    ?: fallbackDomain.takeIf { it.isNotBlank() }?.let { "https://$it" }
                if (baseOrigin != null) "$baseOrigin$unescaped" else return null
            }
            else -> return null
        }

        return ArticleContentFetcher.sanitizeArticleImageUrl(resolved)
    }

    private fun extractOrigin(url: String): String? {
        val trimmed = url.trim()
        val schemeIdx = trimmed.indexOf("://")
        if (schemeIdx <= 0) return null
        val afterScheme = trimmed.substring(schemeIdx + 3)
        val host = afterScheme.substringBefore("/").substringBefore("?")
        if (host.isBlank() || !host.contains(".")) return null
        val scheme = trimmed.substring(0, schemeIdx).lowercase(Locale.ROOT)
        return "$scheme://$host"
    }

    private fun isLikelyImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT).substringBefore("?").substringBefore("#")
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".avif") || lower.endsWith(".gif") ||
            lower.contains("/image") || lower.contains("/img") || lower.contains("/media")
    }

    companion object {
        private val imgTagRegex = Regex(
            """<img\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val imgSrcRegex = Regex(
            """\b(?:src|data-src|data-original|data-lazy-src)\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val imgSrcSetRegex = Regex(
            """\bsrcset\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val imgWidthRegex = Regex("""\bwidth\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
        private val imgHeightRegex = Regex("""\bheight\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseRssDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                val parsed = sdf.parse(dateStr.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }
}
