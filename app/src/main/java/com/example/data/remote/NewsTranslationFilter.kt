package com.example.data.remote

import com.example.model.AppLanguage
import com.example.model.NewsArticle
import com.example.model.TrustedSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class OfficialTermPair(
    val arabic: String,
    val kurdish: String,
    val arabicVariants: List<String> = emptyList(),
    val kurdishVariants: List<String> = emptyList()
)

class NewsTranslationFilter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build(),
    private val enableNetworkTranslation: Boolean = true
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val boundaryRegexCache = ConcurrentHashMap<String, Regex>()
    private val semaphore = Semaphore(6)

    suspend fun translateArticles(
        articles: List<NewsArticle>,
        targetLanguage: AppLanguage
    ): List<NewsArticle> = coroutineScope {
        articles.map { article ->
            async {
                translateArticle(article, targetLanguage)
            }
        }.awaitAll()
    }

    suspend fun translateArticle(
        article: NewsArticle,
        targetLanguage: AppLanguage
    ): NewsArticle {
        val baseTitle = article.originalTitle.ifBlank { article.title }
        val baseSummary = article.originalSummary.ifBlank { article.summary }
        val baseFullContent = article.originalFullContent?.takeIf { it.isNotBlank() }
            ?: article.fullContent?.takeIf { it.isNotBlank() }
        val detectedSourceLang = detectLanguage("$baseTitle $baseSummary ${baseFullContent.orEmpty().take(300)}")
        val knownLocalizedSource = TrustedSources.localizedSourceName(
            rawSource = article.source,
            sourceDomain = article.sourceDomain,
            articleLink = article.link,
            language = targetLanguage
        )

        val alreadyInTargetLang = detectedSourceLang == targetLanguage &&
            article.languageCode == targetLanguage.code &&
            (baseFullContent == null || !article.fullContent.isNullOrBlank()) &&
            (knownLocalizedSource == null || knownLocalizedSource == article.source)
        if (alreadyInTargetLang) {
            return article
        }

        val targetSource = knownLocalizedSource ?: translateText(article.source, targetLanguage)

        if (detectedSourceLang == targetLanguage) {
            val normalizedTitle = normalizeOfficialTerms(baseTitle, targetLanguage)
            val normalizedSummary = normalizeOfficialTerms(baseSummary, targetLanguage)
            val normalizedFull = baseFullContent?.let { normalizeOfficialTerms(it, targetLanguage) }
            return article.copy(
                source = targetSource,
                title = normalizedTitle,
                summary = normalizedSummary,
                fullContent = normalizedFull,
                languageCode = targetLanguage.code,
                originalTitle = baseTitle,
                originalSummary = baseSummary,
                originalFullContent = baseFullContent,
                originalLanguageCode = detectedSourceLang.code
            )
        }

        val (translatedTitle, translatedSummary) = translatePair(
            title = baseTitle,
            summary = baseSummary,
            sourceLang = detectedSourceLang,
            targetLang = targetLanguage
        )

        val translatedFull = baseFullContent?.let { full ->
            translateLongText(
                text = full,
                sourceLang = detectedSourceLang,
                targetLanguage = targetLanguage
            )
        }

        return article.copy(
            source = targetSource,
            title = translatedTitle,
            summary = translatedSummary,
            fullContent = translatedFull,
            languageCode = targetLanguage.code,
            originalTitle = baseTitle,
            originalSummary = baseSummary,
            originalFullContent = baseFullContent,
            originalLanguageCode = detectedSourceLang.code
        )
    }

    suspend fun translateText(
        text: String,
        targetLanguage: AppLanguage
    ): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return text
        val sourceLang = detectLanguage(trimmed)
        if (sourceLang == targetLanguage) {
            return normalizeOfficialTerms(trimmed, targetLanguage)
        }

        val cacheKey = "${sourceLang.code}->${targetLanguage.code}|$trimmed"
        cache[cacheKey]?.let { return it }

        val translated = if (enableNetworkTranslation) {
            fetchRemoteTranslation(trimmed, sourceLang, targetLanguage)
                ?.let { enforceOfficialTerms(it, trimmed, targetLanguage) }
                ?: translateWithGlossary(trimmed, sourceLang, targetLanguage)
        } else {
            translateWithGlossary(trimmed, sourceLang, targetLanguage)
        }

        cache[cacheKey] = translated
        return translated
    }

    suspend fun translateLongText(
        text: String,
        sourceLang: AppLanguage = detectLanguage(text),
        targetLanguage: AppLanguage
    ): String {
        val paragraphs = text.split(Regex("\\r?\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return ""

        if (sourceLang == targetLanguage) {
            return paragraphs.joinToString("\n\n") { normalizeOfficialTerms(it, targetLanguage) }
        }

        // Fast path if all paragraphs are already cached
        val translatedMap = HashMap<Int, String>(paragraphs.size)
        val uncachedIndices = mutableListOf<Int>()
        paragraphs.forEachIndexed { index, para ->
            val key = "${sourceLang.code}->${targetLanguage.code}|$para"
            val hit = cache[key]
            if (hit != null) {
                translatedMap[index] = hit
            } else {
                uncachedIndices.add(index)
            }
        }

        if (uncachedIndices.isNotEmpty()) {
            // Group uncached paragraphs into batches of <= MAX_BATCH_CHARS to respect URL length limits
            val batches = mutableListOf<List<Int>>()
            var currentBatch = mutableListOf<Int>()
            var currentLen = 0
            for (idx in uncachedIndices) {
                val paraLen = paragraphs[idx].length
                if (currentBatch.isNotEmpty() && currentLen + paraLen + BATCH_SEPARATOR.length + 2 > MAX_BATCH_CHARS) {
                    batches.add(currentBatch)
                    currentBatch = mutableListOf()
                    currentLen = 0
                }
                currentBatch.add(idx)
                currentLen += paraLen + BATCH_SEPARATOR.length + 2
            }
            if (currentBatch.isNotEmpty()) {
                batches.add(currentBatch)
            }

            coroutineScope {
                batches.map { batchIndices ->
                    async {
                        if (enableNetworkTranslation && batchIndices.size > 1 && batchIndices.all { paragraphs[it].length <= MAX_BATCH_CHARS }) {
                            val combined = batchIndices.joinToString("\n$BATCH_SEPARATOR\n") { paragraphs[it] }
                            val remoteCombined = fetchRemoteTranslation(combined, sourceLang, targetLanguage)
                            val splitParts = remoteCombined?.split(batchSplitRegex)
                            if (splitParts != null && splitParts.size == batchIndices.size && splitParts.all { it.isNotBlank() }) {
                                batchIndices.forEachIndexed { pos, idx ->
                                    val origPara = paragraphs[idx]
                                    val finalPara = enforceOfficialTerms(splitParts[pos].trim(), origPara, targetLanguage)
                                    cache["${sourceLang.code}->${targetLanguage.code}|$origPara"] = finalPara
                                    translatedMap[idx] = finalPara
                                }
                                return@async
                            }
                        }

                        // Fallback: translate each paragraph individually (splitting oversized single paragraphs if needed)
                        for (idx in batchIndices) {
                            val origPara = paragraphs[idx]
                            val translatedPara = if (origPara.length > MAX_BATCH_CHARS && enableNetworkTranslation) {
                                translateOversizedParagraph(origPara, sourceLang, targetLanguage)
                            } else {
                                translateText(origPara, targetLanguage)
                            }
                            cache["${sourceLang.code}->${targetLanguage.code}|$origPara"] = translatedPara
                            translatedMap[idx] = translatedPara
                        }
                    }
                }.awaitAll()
            }
        }

        return paragraphs.indices.joinToString("\n\n") { idx ->
            translatedMap[idx] ?: paragraphs[idx]
        }
    }

    private suspend fun translateOversizedParagraph(
        paragraph: String,
        sourceLang: AppLanguage,
        targetLanguage: AppLanguage
    ): String {
        val chunks = mutableListOf<String>()
        var remaining = paragraph.trim()
        while (remaining.length > MAX_BATCH_CHARS) {
            val cutZone = remaining.take(MAX_BATCH_CHARS)
            val splitAt = listOf(
                cutZone.lastIndexOf(". "),
                cutZone.lastIndexOf("، "),
                cutZone.lastIndexOf("؛ "),
                cutZone.lastIndexOf(' ')
            ).maxOrNull()?.takeIf { it > MAX_BATCH_CHARS / 2 } ?: MAX_BATCH_CHARS
            chunks.add(remaining.substring(0, splitAt + 1).trim())
            remaining = remaining.substring(splitAt + 1).trim()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)

        val translatedChunks = chunks.map { chunk ->
            if (enableNetworkTranslation) {
                fetchRemoteTranslation(chunk, sourceLang, targetLanguage)
                    ?.let { enforceOfficialTerms(it, chunk, targetLanguage) }
                    ?: translateWithGlossary(chunk, sourceLang, targetLanguage)
            } else {
                translateWithGlossary(chunk, sourceLang, targetLanguage)
            }
        }
        return translatedChunks.joinToString(" ").trim()
    }

    private suspend fun translatePair(
        title: String,
        summary: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage
    ): Pair<String, String> {
        val cleanTitle = title.trim()
        val cleanSummary = summary.trim()
        val titleKey = "${sourceLang.code}->${targetLang.code}|$cleanTitle"
        val summaryKey = "${sourceLang.code}->${targetLang.code}|$cleanSummary"

        val cachedTitle = cache[titleKey]
        val cachedSummary = cache[summaryKey]
        if (cachedTitle != null && cachedSummary != null) {
            return cachedTitle to cachedSummary
        }

        if (enableNetworkTranslation && cleanSummary.isNotBlank() && cleanSummary != cleanTitle) {
            val combined = "$cleanTitle\n$BATCH_SEPARATOR\n$cleanSummary"
            val remoteCombined = fetchRemoteTranslation(combined, sourceLang, targetLang)
            if (remoteCombined != null && remoteCombined.contains(BATCH_SEPARATOR_TAG)) {
                val parts = remoteCombined.split(Regex("\\s*\\|\\|\\|\\s*BAYAN_SEP\\s*\\|\\|\\|\\s*"), limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    val finalTitle = enforceOfficialTerms(parts[0].trim(), cleanTitle, targetLang)
                    val finalSummary = enforceOfficialTerms(parts[1].trim(), cleanSummary, targetLang)
                    cache[titleKey] = finalTitle
                    cache[summaryKey] = finalSummary
                    return finalTitle to finalSummary
                }
            }
        }

        val finalTitle = translateText(cleanTitle, targetLang)
        val finalSummary = if (cleanSummary.isBlank() || cleanSummary == cleanTitle) {
            finalTitle
        } else {
            translateText(cleanSummary, targetLang)
        }
        return finalTitle to finalSummary
    }

    private suspend fun fetchRemoteTranslation(
        text: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage
    ): String? = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            runCatching {
                val sl = if (sourceLang == AppLanguage.KURDISH) "ckb" else "ar"
                val tl = if (targetLang == AppLanguage.KURDISH) "ckb" else "ar"
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) BayanIraqNews/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string().orEmpty()
                    parseGoogleTranslateJson(body)
                }
            }.getOrNull()
        }
    }

    internal fun parseGoogleTranslateJson(json: String): String? {
        if (json.isBlank()) return null
        return runCatching {
            val root = JSONArray(json)
            val segments = root.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONArray(i) ?: continue
                val piece = seg.optString(0, "")
                sb.append(piece)
            }
            sb.toString().trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun translateWithGlossary(
        text: String,
        sourceLang: AppLanguage = detectLanguage(text),
        targetLang: AppLanguage
    ): String {
        if (sourceLang == targetLang) return normalizeOfficialTerms(text, targetLang)
        var result = if (sourceLang == AppLanguage.KURDISH) normalizeKurdishOrthography(text) else text
        val entries = if (targetLang == AppLanguage.KURDISH) {
            sortedArabicToKurdish
        } else {
            sortedKurdishToArabic
        }
        for ((from, to) in entries) {
            result = replaceTermSafe(result, from, to)
        }
        val normalized = if (targetLang == AppLanguage.KURDISH) {
            normalizeKurdishOrthography(result)
        } else {
            result
        }
        return normalized.replace(Regex("[ \\t\\r]+"), " ").trim()
    }

    fun enforceOfficialTerms(
        translatedText: String,
        originalText: String,
        targetLanguage: AppLanguage
    ): String {
        var result = normalizeOfficialTerms(translatedText, targetLanguage)
        for (pair in officialTerms) {
            if (targetLanguage == AppLanguage.KURDISH) {
                val matchedAr = (listOf(pair.arabic) + pair.arabicVariants).any { containsTermSafe(originalText, it) }
                if (matchedAr && !containsTermSafe(result, pair.kurdish)) {
                    pair.kurdishVariants.forEach { variant ->
                        val normVariant = normalizeKurdishOrthography(variant)
                        result = replaceTermSafe(result, normVariant, pair.kurdish)
                    }
                }
            } else {
                val normOrig = normalizeKurdishOrthography(originalText)
                val matchedCkb = (listOf(pair.kurdish) + pair.kurdishVariants).any {
                    val normKw = normalizeKurdishOrthography(it)
                    containsTermSafe(originalText, it) || containsTermSafe(normOrig, normKw)
                }
                if (matchedCkb && !containsTermSafe(result, pair.arabic)) {
                    pair.arabicVariants.forEach { variant ->
                        result = replaceTermSafe(result, variant, pair.arabic)
                    }
                }
            }
        }
        return result.replace(Regex("[ \\t\\r]+"), " ").trim()
    }

    private fun normalizeOfficialTerms(text: String, targetLanguage: AppLanguage): String {
        var result = if (targetLanguage == AppLanguage.KURDISH) {
            normalizeKurdishOrthography(text)
        } else {
            text
        }
        for (pair in officialTerms) {
            if (targetLanguage == AppLanguage.KURDISH) {
                pair.kurdishVariants.forEach { variant ->
                    val normVariant = normalizeKurdishOrthography(variant)
                    if (normVariant != pair.kurdish) {
                        result = replaceTermSafe(result, normVariant, pair.kurdish)
                    }
                }
            } else {
                pair.arabicVariants.forEach { variant ->
                    if (variant != pair.arabic) {
                        result = replaceTermSafe(result, variant, pair.arabic)
                    }
                }
            }
        }
        return result
    }

    private fun containsTermSafe(text: String, term: String): Boolean {
        if (term.isBlank() || !text.contains(term)) return false
        if (term.length <= 5 && !term.contains(' ')) {
            return boundaryRegexFor(term).containsMatchIn(text)
        }
        return true
    }

    private fun replaceTermSafe(text: String, from: String, to: String): String {
        if (from.isBlank() || !text.contains(from)) return text
        if (from.length <= 5 && !from.contains(' ')) {
            return boundaryRegexFor(from).replace(text) { to }
        }
        return text.replace(from, to)
    }

    private fun boundaryRegexFor(term: String): Regex = boundaryRegexCache.getOrPut(term) {
        Regex("(?<=^|[\\s،؛.:!؟\"'()\\[\\]«»\\-/])${Regex.escape(term)}(?=$|[\\s،؛.:!؟\"'()\\[\\]«»\\-/])")
    }

    companion object {
        private const val MAX_BATCH_CHARS = 800
        private const val BATCH_SEPARATOR_TAG = "BAYAN_SEP"
        private const val BATCH_SEPARATOR = "|||BAYAN_SEP|||"
        private val batchSplitRegex = Regex("""\s*\|\|\|\s*BAYAN_SEP\s*\|\|\|\s*""")

        private val kurdishStrongChars = setOf(
            'ۆ', 'ێ', 'ڵ', 'ڕ', 'ە', 'ھ'
        )

        private val kurdishBorrowedChars = setOf(
            'ڤ', 'گ', 'پ', 'چ', 'ژ'
        )

        private val arabicExclusiveChars = setOf(
            'ة', 'ث', 'ذ', 'ض', 'ظ', 'أ', 'إ', 'آ'
        )

        private val kurdishIndicatorWords = listOf(
            "دەکات", "دەكات", "ڕاگەیاند", "راگەیاند", "ڕایگەیاند",
            "بڕیار", "هەرێم", "هه‌رێم", "عێراق", "عێراقى", "هەولێر",
            "سلێمانی", "دهۆک", "هەڵەبجە", "کەرکووک", "پشوو", "دەوام",
            "وەزیران", "وەزارەتی", "فەرمانگەی", "سەبارەت", "ئەمڕۆ",
            "پێشمەرگە", "پارێزگا", "بەغدا", "حکومەت", "حکوومەت",
            "لە ", "بۆ ", "بە ", "کوردستان"
        )

        private val arabicIndicatorWords = listOf(
            "الأمانة", "الوزراء", "وزارة ", "محافظة ", "العراق", "بغداد",
            "أعلنت", "أعلن", "تعلن", "في ", "على ", "إلى ", "من ", "عن ",
            "بشأن", "تعطيل", "عطلة", "الرسمية", "الوطني", "الإنتاج", "مشروع"
        )

        fun normalizeKurdishOrthography(text: String): String {
            if (text.isBlank()) return text
            return text
                .replace("\u0647\u200C", "\u06D5") // ه + ZWNJ -> Sorani Ae (ە)
                .replace("\u06C0", "\u06D5\u06CC") // ۀ -> ەی
                .replace('\u064A', '\u06CC')       // Arabic Yeh (ي) -> Sorani Yeh (ی)
                .replace('\u0649', '\u06CC')       // Alef Maksura (ى) -> Sorani Yeh (ی)
                .replace('\u0643', '\u06A9')       // Arabic Kaf (ك) -> Sorani Keheh (ک)
                .replace('\u06C1', '\u06D5')       // Urdu/variant Heh Goal -> ە
                .replace("\u200C", "")             // Strip leftover ZWNJ artifacts
        }

        fun detectLanguage(text: String): AppLanguage {
            if (text.isBlank()) return AppLanguage.ARABIC
            val strongKurdishCount = text.count { it in kurdishStrongChars }
            val zwnjHehCount = Regex("\u0647\u200C").findAll(text).count()
            val borrowedKurdishCount = text.count { it in kurdishBorrowedChars }
            val arabicCharCount = text.count { it in arabicExclusiveChars }

            val kurdishWordHits = kurdishIndicatorWords.count { text.contains(it) }
            val arabicWordHits = arabicIndicatorWords.count { text.contains(it) }

            val kurdishScore = (strongKurdishCount + zwnjHehCount) * 3 + borrowedKurdishCount + kurdishWordHits * 4
            val arabicScore = arabicCharCount * 3 + arabicWordHits * 4

            if (kurdishScore == 0 && arabicScore == 0) return AppLanguage.ARABIC
            return if (kurdishScore > arabicScore) AppLanguage.KURDISH else AppLanguage.ARABIC
        }

        val officialTerms: List<OfficialTermPair> = listOf(
            OfficialTermPair(
                arabic = "الأمانة العامة لمجلس الوزراء",
                kurdish = "ئەمیندارێتی گشتی ئەنجومەنی وەزیران",
                arabicVariants = listOf("الأمانة العامة لمجلس الوزراء العراقي", "أمانة مجلس الوزراء"),
                kurdishVariants = listOf("ئەمیندارێتی گشتیی ئەنجوومەنی وەزیران", "سکرتاریەتی گشتی ئەنجومەنی وەزیران", "ئەمیندارێتی ئەنجومەنی وەزیران")
            ),
            OfficialTermPair(
                arabic = "المكتب الإعلامي لرئيس الوزراء",
                kurdish = "نووسینگەی ڕاگەیاندنی سەرۆک وەزیران",
                kurdishVariants = listOf("نوسینگەی راگەیاندنی سەرۆک وەزیران", "نووسینگەی سەرۆک وەزیران")
            ),
            OfficialTermPair(
                arabic = "مجلس وزراء إقليم كردستان",
                kurdish = "ئەنجومەنی وەزیرانی هەرێمی کوردستان",
                kurdishVariants = listOf("ئەنجوومەنی وەزیرانی هەرێمی کوردستان")
            ),
            OfficialTermPair(
                arabic = "مجلس الوزراء العراقي",
                kurdish = "ئەنجومەنی وەزیرانی عێراق",
                kurdishVariants = listOf("ئەنجوومەنی وەزیرانی عێراق")
            ),
            OfficialTermPair(
                arabic = "مجلس الوزراء",
                kurdish = "ئەنجومەنی وەزیران",
                kurdishVariants = listOf("ئەنجوومەنی وەزیران")
            ),
            OfficialTermPair(
                arabic = "دائرة الإعلام والمعلومات",
                kurdish = "فەرمانگەی میدیا و زانیاری",
                arabicVariants = listOf("دائرة الإعلام والمعلومات في حكومة إقليم كردستان", "مكتب الإعلام والمعلومات"),
                kurdishVariants = listOf("فەرمانگەی میدیا و زانیاریی")
            ),
            OfficialTermPair(
                arabic = "حكومة إقليم كردستان",
                kurdish = "حکومەتی هەرێمی کوردستان",
                kurdishVariants = listOf("حکوومەتی هەرێمی کوردستان", "حكومه‌تی هه‌رێمی كوردستان")
            ),
            OfficialTermPair(
                arabic = "الحكومة العراقية",
                kurdish = "حکومەتی عێراق",
                kurdishVariants = listOf("حکوومەتی عێراق")
            ),
            OfficialTermPair(
                arabic = "الحكومة الاتحادية",
                kurdish = "حکومەتی فیدراڵی",
                kurdishVariants = listOf("حکوومەتی فیدراڵی", "حکومەتی ناوەندی")
            ),
            OfficialTermPair(
                arabic = "وكالة الأنباء العراقية",
                kurdish = "ئاژانسی هەواڵی عێراقی",
                arabicVariants = listOf("وكالة الأنباء العراقية (واع)")
            ),
            OfficialTermPair(
                arabic = "البنك المركزي العراقي",
                kurdish = "بانکی ناوەندی عێراق"
            ),
            OfficialTermPair(
                arabic = "مجلس النواب العراقي",
                kurdish = "ئەنجومەنی نوێنەرانی عێراق"
            ),
            OfficialTermPair(
                arabic = "مجلس النواب",
                kurdish = "ئەنجومەنی نوێنەران"
            ),
            OfficialTermPair(
                arabic = "برلمان كردستان",
                kurdish = "پەرلەمانی کوردستان"
            ),
            OfficialTermPair(
                arabic = "وزارة التربية العراقية",
                kurdish = "وەزارەتی پەروەردەی عێراق"
            ),
            OfficialTermPair(
                arabic = "وزارة التربية",
                kurdish = "وەزارەتی پەروەردە"
            ),
            OfficialTermPair(
                arabic = "وزارة التعليم العالي",
                kurdish = "وەزارەتی خوێندنی باڵا"
            ),
            OfficialTermPair(
                arabic = "وزارة الصناعة والمعادن",
                kurdish = "وەزارەتی پیشەسازی و کانزاکان"
            ),
            OfficialTermPair(
                arabic = "وزارة الصناعة",
                kurdish = "وەزارەتی پیشەسازی"
            ),
            OfficialTermPair(
                arabic = "وزارة الكهرباء",
                kurdish = "وەزارەتی کارەبا"
            ),
            OfficialTermPair(
                arabic = "وزارة الزراعة",
                kurdish = "وەزارەتی کشتوکاڵ"
            ),
            OfficialTermPair(
                arabic = "وزارة الإعمار والإسكان",
                kurdish = "وەزارەتی ئاوەدانکردنەوە و نیشتەجێکردن"
            ),
            OfficialTermPair(
                arabic = "وزارة النفط",
                kurdish = "وەزارەتی نەوت"
            ),
            OfficialTermPair(
                arabic = "وزارة المالية",
                kurdish = "وەزارەتی دارایی"
            ),
            OfficialTermPair(
                arabic = "وزارة الصحة",
                kurdish = "وەزارەتی تەندروستی"
            ),
            OfficialTermPair(
                arabic = "وزارة الداخلية",
                kurdish = "وەزارەتی ناوخۆ"
            ),
            OfficialTermPair(
                arabic = "وزارة الخارجية",
                kurdish = "وەزارەتی دەرەوە"
            ),
            OfficialTermPair(
                arabic = "وزارة التخطيط",
                kurdish = "وەزارەتی پلاندانان"
            ),
            OfficialTermPair(
                arabic = "وزارة التجارة",
                kurdish = "وەزارەتی بازرگانی"
            ),
            OfficialTermPair(
                arabic = "وزارة النقل",
                kurdish = "وەزارەتی گواستنەوە"
            ),
            OfficialTermPair(
                arabic = "وزارة الموارد المائية",
                kurdish = "وەزارەتی سەرچاوەکانی ئاو"
            ),
            OfficialTermPair(
                arabic = "تعطيل الدوام الرسمي",
                kurdish = "ڕاگرتنی دەوامی فەرمی",
                kurdishVariants = listOf("راگرتنی دەوامی فەرمی", "پشوو لە دەوامی فەرمی")
            ),
            OfficialTermPair(
                arabic = "تعطيل الدوام",
                kurdish = "ڕاگرتنی دەوام",
                kurdishVariants = listOf("راگرتنی دەوام")
            ),
            OfficialTermPair(
                arabic = "عطلة رسمية",
                kurdish = "پشووی فەرمی",
                arabicVariants = listOf("إجازة رسمية"),
                kurdishVariants = listOf("پشووی فەرمى", "پشووێکی فەرمی")
            ),
            OfficialTermPair(
                arabic = "العطل الرسمية",
                kurdish = "پشووە فەرمییەکان"
            ),
            OfficialTermPair(
                arabic = "قرارات مجلس الوزراء",
                kurdish = "بڕیارەکانی ئەنجومەنی وەزیران"
            ),
            OfficialTermPair(
                arabic = "جلسة مجلس الوزراء",
                kurdish = "دانیشتنی ئەنجومەنی وەزیران"
            ),
            OfficialTermPair(
                arabic = "المشاريع الوطنية",
                kurdish = "پڕۆژە نیشتمانییەکان"
            ),
            OfficialTermPair(
                arabic = "الإنتاج الوطني",
                kurdish = "بەرهەمی نیشتمانی"
            ),
            OfficialTermPair(
                arabic = "الإنتاج الصناعي",
                kurdish = "بەرهەمی پیشەسازی"
            ),
            OfficialTermPair(
                arabic = "الإنتاج الزراعي",
                kurdish = "بەرهەمی کشتوکاڵی"
            ),
            OfficialTermPair(
                arabic = "طريق التنمية",
                kurdish = "ڕێگای گەشەپێدان"
            ),
            OfficialTermPair(
                arabic = "ميناء الفاو",
                kurdish = "بەندەری فاو"
            ),
            OfficialTermPair(
                arabic = "الدينار العراقي",
                kurdish = "دیناری عێراقی"
            ),
            OfficialTermPair(
                arabic = "المنتخب العراقي",
                kurdish = "هەڵبژاردەی عێراق"
            ),
            OfficialTermPair(
                arabic = "إقليم كردستان",
                kurdish = "هەرێمی کوردستان",
                kurdishVariants = listOf("هه‌رێمی كوردستان")
            ),
            OfficialTermPair("العراق", "عێراق", kurdishVariants = listOf("عێراقى")),
            OfficialTermPair("بغداد", "بەغدا", kurdishVariants = listOf("به‌غدا")),
            OfficialTermPair("أربيل", "هەولێر", arabicVariants = listOf("اربيل"), kurdishVariants = listOf("هه‌ولێر")),
            OfficialTermPair("البصرة", "بەسرە"),
            OfficialTermPair("السليمانية", "سلێمانی", kurdishVariants = listOf("سلێمانى")),
            OfficialTermPair("دهوك", "دهۆک"),
            OfficialTermPair("حلبجة", "هەڵەبجە", kurdishVariants = listOf("هه‌ڵه‌بجه‌")),
            OfficialTermPair("كركوك", "کەرکووک", kurdishVariants = listOf("کەرکوک", "كه‌ركووك")),
            OfficialTermPair("صلاح الدين", "سەڵاحەدین"),
            OfficialTermPair("الموصل", "مووسڵ", kurdishVariants = listOf("موسڵ")),
            OfficialTermPair("نينوى", "نەینەوا"),
            OfficialTermPair("النجف", "نەجەف"),
            OfficialTermPair("كربلاء", "کەربەلا"),
            OfficialTermPair("الأنبار", "ئەنبار", arabicVariants = listOf("الانبار")),
            OfficialTermPair("بابل", "بابل"),
            OfficialTermPair("ذي قار", "زیقار"),
            OfficialTermPair("ديالى", "دیالە"),
            OfficialTermPair("واسط", "واست"),
            OfficialTermPair("ميسان", "میسان"),
            OfficialTermPair("المثنى", "موسەننا"),
            OfficialTermPair("القادسية", "قادسیە"),
            OfficialTermPair("الديوانية", "دیوانیە"),
            // Presidency, cabinet, ministries & public administration
            OfficialTermPair("رئيس مجلس الوزراء", "سەرۆکی ئەنجومەنی وەزیران", arabicVariants = listOf("رئيس الوزراء العراقي")),
            OfficialTermPair("رئيس الوزراء", "سەرۆک وەزیران"),
            OfficialTermPair("نائب رئيس الوزراء", "جێگری سەرۆک وەزیران"),
            OfficialTermPair("رئيس الجمهورية", "سەرۆک کۆمار"),
            OfficialTermPair("رئيس مجلس النواب", "سەرۆکی ئەنجومەنی نوێنەران"),
            OfficialTermPair("المتحدث الرسمي", "وتەبێژی فەرمی"),
            OfficialTermPair("بيان رسمي", "ڕاگەیەندراوی فەرمی"),
            OfficialTermPair("بيان صحفي", "ڕاگەیەندراوی ڕۆژنامەوانی"),
            OfficialTermPair("مجلس المحافظة", "ئەنجومەنی پارێزگا"),
            OfficialTermPair("ديوان المحافظة", "دیوانی پارێزگا"),
            OfficialTermPair("مديرية التربية", "بەڕێوەبەرایەتی گشتی پەروەردە"),
            OfficialTermPair("وزارة الدفاع", "وەزارەتی بەرگری"),
            OfficialTermPair("وزارة العدل", "وەزارەتی داد"),
            OfficialTermPair("وزارة العمل والشؤون الاجتماعية", "وەزارەتی کار و کاروباری کۆمەڵایەتی"),
            OfficialTermPair("وزارة الشباب والرياضة", "وەزارەتی وەرزش و لاوان"),
            OfficialTermPair("وزارة الاتصالات", "وەزارەتی گەیاندن"),
            OfficialTermPair("وزارة الثقافة", "وەزارەتی ڕۆشنبیری"),
            OfficialTermPair("وزارة الهجرة والمهجرين", "وەزارەتی کۆچ و کۆچبەران"),
            OfficialTermPair("القوات الأمنية", "هێزە ئەمنییەکان", arabicVariants = listOf("قوات الأمن")),
            OfficialTermPair("قوات البيشمركة", "هێزەکانی پێشمەرگە", arabicVariants = listOf("البيشمركة")),
            OfficialTermPair("الحشد الشعبي", "حەشدی شەعبی"),
            OfficialTermPair("المنافذ الحدودية", "دەروازە سنوورییەکان"),
            OfficialTermPair("الأنواء الجوية", "کەشناسی"),
            OfficialTermPair("درجات الحرارة", "پلەکانی گەرما"),
            OfficialTermPair("رواتب الموظفين", "مووچەی فەرمانبەران"),
            OfficialTermPair("الموظفين", "فەرمانبەران"),
            OfficialTermPair("المتقاعدين", "خانەنشینان"),
            OfficialTermPair("الامتحانات", "تاقیکردنەوەکان"),
            OfficialTermPair("الطلبة", "قوتابیان", arabicVariants = listOf("الطلاب")),
            OfficialTermPair("الجامعات", "زانکۆکان"),
            OfficialTermPair("جامعة", "زانکۆی"),
            OfficialTermPair("مستشفى", "نەخۆشخانەی"),
            OfficialTermPair("مطار أربيل الدولي", "فڕۆکەخانەی نێودەوڵەتی هەولێر"),
            OfficialTermPair("مطار بغداد الدولي", "فڕۆکەخانەی نێودەوڵەتی بەغدا"),
            OfficialTermPair("مطار", "فڕۆکەخانەی"),
            OfficialTermPair("تصدير النفط", "هەناردەکردنی نەوت"),
            OfficialTermPair("أسعار النفط", "نرخی نەوت"),
            OfficialTermPair("الموازنة الاتحادية", "بودجەی فیدراڵی"),
            OfficialTermPair("الموازنة", "بودجە"),
            OfficialTermPair("الرواتب", "مووچە"),
            OfficialTermPair("الخدمات", "خزمەتگوزارییەکان"),
            OfficialTermPair("الاستثمار", "وەبەرهێنان"),
            // Common news verbs & connectors for offline fallback translation
            OfficialTermPair("في عموم المحافظات", "لە سەرتاسەری پارێزگاکان"),
            OfficialTermPair("في جميع المحافظات", "لە هەموو پارێزگاکان"),
            OfficialTermPair("الدوائر الحكومية", "فەرمانگە حکومییەکان"),
            OfficialTermPair("تعلن", "ڕادەگەیەنێت"),
            OfficialTermPair("أعلنت", "ڕایگەیاند"),
            OfficialTermPair("أعلن", "ڕایگەیاند"),
            OfficialTermPair("قررت", "بڕیاری دا"),
            OfficialTermPair("قرر", "بڕیاری دا"),
            OfficialTermPair("أكدت", "جەختی کردەوە"),
            OfficialTermPair("أكد", "جەختی کردەوە"),
            OfficialTermPair("أشارت", "ئاماژەی بەوە کرد"),
            OfficialTermPair("أشار", "ئاماژەی بەوە کرد"),
            OfficialTermPair("أوضحت", "ڕوونی کردەوە"),
            OfficialTermPair("أوضح", "ڕوونی کردەوە"),
            OfficialTermPair("بحث", "تاوتوێی کرد"),
            OfficialTermPair("ناقش", "گفتوگۆی لەسەر کرد"),
            OfficialTermPair("افتتحت", "کردەوە"),
            OfficialTermPair("افتتح", "کردەوە"),
            OfficialTermPair("توقيع اتفاقية", "واژۆکردنی ڕێککەوتننامەی"),
            OfficialTermPair("العمل", "کار"),
            OfficialTermPair("توفير", "دابینکردنی"),
            OfficialTermPair("تطوير", "پەرەپێدانی"),
            OfficialTermPair("تأهيل", "نۆژەنکردنەوەی"),
            OfficialTermPair("إطلاق", "خستنەگەڕی"),
            OfficialTermPair("الأسبوع المقبل", "هەفتەی داهاتوو"),
            OfficialTermPair("الشهر المقبل", "مانگی داهاتوو"),
            OfficialTermPair("اليوم", "ئەمڕۆ"),
            OfficialTermPair("غداً", "سبەی", arabicVariants = listOf("غدا")),
            OfficialTermPair("أمس", "دوێنێ"),
            OfficialTermPair("بشأن", "سەبارەت بە"),
            OfficialTermPair("بمناسبة", "بەبۆنەی"),
            OfficialTermPair("رسمياً", "بە فەرمی"),
            OfficialTermPair("محافظة", "پارێزگای"),
            OfficialTermPair("محافظ", "پارێزگاری"),
            OfficialTermPair("وزير", "وەزیری"),
            OfficialTermPair("دعم", "پشتگیریکردنی"),
            OfficialTermPair("مشاريع", "پڕۆژەکانی"),
            OfficialTermPair("مشروع", "پڕۆژەی"),
            OfficialTermPair("قرار", "بڕیاری"),
            OfficialTermPair("ارتفاع", "بەرزبوونەوەی"),
            OfficialTermPair("انخفاض", "نزمبوونەوەی"),
            OfficialTermPair("سعر صرف الدولار", "نرخی دۆلار"),
            OfficialTermPair("عاجل", "بەپەلە"),
            OfficialTermPair("في", "لە"),
            OfficialTermPair("إلى", "بۆ"),
            OfficialTermPair("على", "لەسەر"),
            OfficialTermPair("عن", "دەربارەی"),
            OfficialTermPair("من", "لە")
        )

        private val sortedArabicToKurdish: List<Pair<String, String>> by lazy {
            officialTerms.flatMap { pair ->
                (listOf(pair.arabic) + pair.arabicVariants).map { it to pair.kurdish }
            }.sortedByDescending { it.first.length }
        }

        private val sortedKurdishToArabic: List<Pair<String, String>> by lazy {
            officialTerms.flatMap { pair ->
                (listOf(pair.kurdish) + pair.kurdishVariants).map { it to pair.arabic }
            }.sortedByDescending { it.first.length }
        }
    }
}
