package com.example.data.remote

import android.util.Xml
import androidx.core.text.HtmlCompat
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
import kotlinx.coroutines.withContext
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
        .build()
) {
    suspend fun fetchCategoryFeed(
        category: NewsCategory,
        verifiedSourcesOnly: Boolean = true
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val configuredFeeds = OfficialFeeds.FEEDS[category].orEmpty()
        val endpoints: List<FeedEndpoint> = if (verifiedSourcesOnly && configuredFeeds.isNotEmpty()) {
            configuredFeeds
        } else {
            val query = category.rssQuery ?: return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            listOf(
                FeedEndpoint(
                    url = "https://news.google.com/rss/search?q=$encoded&hl=ar&gl=IQ&ceid=IQ:ar",
                    filterIraqOnly = true
                )
            ) + configuredFeeds
        }

        if (endpoints.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val fetchedLists = endpoints.map { endpoint ->
                async {
                    runCatching { fetchAndParseEndpoint(endpoint, category) }.getOrNull().orEmpty()
                }
            }.awaitAll()

            val merged = fetchedLists.flatten()
                .filter { IraqRelevanceFilter.isRelatedToIraq(it) }
                .distinctBy { it.title }
                .sortedWith(
                    compareByDescending<NewsArticle> { it.trustLevel == SourceTrustLevel.OFFICIAL }
                        .thenByDescending { it.publishedEpochMs }
                )

            if (verifiedSourcesOnly) {
                val trusted = merged.filter { it.trustLevel != SourceTrustLevel.GENERAL }
                trusted.ifEmpty { merged }
            } else {
                merged
            }
        }
    }

    private fun fetchAndParseEndpoint(endpoint: FeedEndpoint, category: NewsCategory): List<NewsArticle> {
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
            val parsed = parseRssXml(xml, category, endpoint.forcedSource)
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
        forcedSource: TrustedSource? = null
    ): List<NewsArticle> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val articles = mutableListOf<NewsArticle>()
        var insideItem = false
        var rawTitle = ""
        var rawLink = ""
        var rawPubDate = ""
        var rawDescription = ""
        var rawSource = ""
        var rawSourceUrl = ""

        val nowMs = System.currentTimeMillis()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "item", "entry" -> {
                            insideItem = true
                            rawTitle = ""
                            rawLink = ""
                            rawPubDate = ""
                            rawDescription = ""
                            rawSource = ""
                            rawSourceUrl = ""
                        }
                        "title" -> if (insideItem) rawTitle = safeNextText(parser)
                        "link" -> if (insideItem) {
                            val href = parser.getAttributeValue(null, "href")
                            rawLink = if (!href.isNullOrBlank()) href else safeNextText(parser)
                        }
                        "pubdate", "published", "updated" -> if (insideItem && rawPubDate.isBlank()) {
                            rawPubDate = safeNextText(parser)
                        }
                        "description", "summary", "content" -> if (insideItem && rawDescription.isBlank()) {
                            rawDescription = safeNextText(parser)
                        }
                        "source" -> if (insideItem) {
                            rawSourceUrl = parser.getAttributeValue(null, "url").orEmpty()
                            rawSource = safeNextText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagLower = parser.name.lowercase(Locale.ROOT)
                    if ((tagLower == "item" || tagLower == "entry") && insideItem) {
                        insideItem = false
                        val normalized = buildArticle(
                            rawTitle = rawTitle,
                            rawLink = rawLink,
                            rawPubDate = rawPubDate,
                            rawDescription = rawDescription,
                            rawSource = rawSource,
                            rawSourceUrl = rawSourceUrl,
                            category = category,
                            forcedSource = forcedSource,
                            nowMs = nowMs
                        )
                        if (normalized != null) {
                            articles.add(normalized)
                        }
                        if (articles.size >= 20) break
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
        rawSource: String,
        rawSourceUrl: String,
        category: NewsCategory,
        forcedSource: TrustedSource?,
        nowMs: Long
    ): NewsArticle? {
        var cleanTitle = stripHtml(rawTitle)
        if (cleanTitle.isBlank()) return null

        var source = forcedSource?.nameAr ?: stripHtml(rawSource).ifBlank { "مصدر إخباري" }
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

        var cleanDesc = stripHtml(rawDescription)
        if (cleanDesc.startsWith(cleanTitle)) {
            cleanDesc = cleanDesc.removePrefix(cleanTitle).trim()
        }
        if (cleanDesc.endsWith(source)) {
            cleanDesc = cleanDesc.removeSuffix(source).trim()
        }
        val summary = if (cleanDesc.length > 24) {
            cleanDesc
        } else {
            "تفاصيل الخبر كاملة متوفرة عبر المصدر الرسمي: $source"
        }

        val (trustLevel, sourceDomain) = if (forcedSource != null) {
            forcedSource.trustLevel to forcedSource.domain
        } else {
            TrustedSources.classify(source, rawSourceUrl, rawLink)
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
            isSaved = false
        )
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
