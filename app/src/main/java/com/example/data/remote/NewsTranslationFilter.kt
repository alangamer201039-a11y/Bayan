package com.example.data.remote

import com.example.model.AppLanguage
import com.example.model.NewsArticle
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
        val detectedSourceLang = detectLanguage("$baseTitle $baseSummary")

        if (detectedSourceLang == targetLanguage && article.languageCode == targetLanguage.code) {
            return article
        }

        if (detectedSourceLang == targetLanguage) {
            val normalizedTitle = normalizeOfficialTerms(baseTitle, targetLanguage)
            val normalizedSummary = normalizeOfficialTerms(baseSummary, targetLanguage)
            return article.copy(
                title = normalizedTitle,
                summary = normalizedSummary,
                languageCode = targetLanguage.code,
                originalTitle = baseTitle,
                originalSummary = baseSummary,
                originalLanguageCode = detectedSourceLang.code
            )
        }

        val (translatedTitle, translatedSummary) = translatePair(
            title = baseTitle,
            summary = baseSummary,
            sourceLang = detectedSourceLang,
            targetLang = targetLanguage
        )

        return article.copy(
            title = translatedTitle,
            summary = translatedSummary,
            languageCode = targetLanguage.code,
            originalTitle = baseTitle,
            originalSummary = baseSummary,
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
        var result = text
        val entries = if (targetLang == AppLanguage.KURDISH) {
            sortedArabicToKurdish
        } else {
            sortedKurdishToArabic
        }
        for ((from, to) in entries) {
            if (result.contains(from)) {
                result = result.replace(from, to)
            }
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    fun enforceOfficialTerms(
        translatedText: String,
        originalText: String,
        targetLanguage: AppLanguage
    ): String {
        var result = normalizeOfficialTerms(translatedText, targetLanguage)
        for (pair in officialTerms) {
            if (targetLanguage == AppLanguage.KURDISH) {
                val matchedAr = (listOf(pair.arabic) + pair.arabicVariants).any { originalText.contains(it) }
                if (matchedAr && !result.contains(pair.kurdish)) {
                    pair.kurdishVariants.forEach { variant ->
                        if (result.contains(variant)) {
                            result = result.replace(variant, pair.kurdish)
                        }
                    }
                }
            } else {
                val matchedCkb = (listOf(pair.kurdish) + pair.kurdishVariants).any { originalText.contains(it) }
                if (matchedCkb && !result.contains(pair.arabic)) {
                    pair.arabicVariants.forEach { variant ->
                        if (result.contains(variant)) {
                            result = result.replace(variant, pair.arabic)
                        }
                    }
                }
            }
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeOfficialTerms(text: String, targetLanguage: AppLanguage): String {
        var result = text
        for (pair in officialTerms) {
            if (targetLanguage == AppLanguage.KURDISH) {
                pair.kurdishVariants.forEach { variant ->
                    if (variant != pair.kurdish && result.contains(variant)) {
                        result = result.replace(variant, pair.kurdish)
                    }
                }
            } else {
                pair.arabicVariants.forEach { variant ->
                    if (variant != pair.arabic && result.contains(variant)) {
                        result = result.replace(variant, pair.arabic)
                    }
                }
            }
        }
        return result
    }

    companion object {
        private const val BATCH_SEPARATOR_TAG = "BAYAN_SEP"
        private const val BATCH_SEPARATOR = "|||BAYAN_SEP|||"

        private val kurdishExclusiveChars = setOf(
            'ۆ', 'ێ', 'ڵ', 'ڕ', 'ە', 'ڤ', 'گ', 'پ', 'چ', 'ژ', 'ھ'
        )

        private val kurdishIndicatorWords = listOf(
            "دەکات", "دەكات", "ڕاگەیاند", "راگەیاند", "ڕایگەیاند",
            "بڕیار", "هەرێم", "هه‌رێم", "عێراق", "عێراقى", "هەولێر",
            "سلێمانی", "دهۆک", "هەڵەبجە", "کەرکووک", "پشوو", "دەوام",
            "وەزیران", "وەزارەتی", "فەرمانگەی", "سەبارەت", "ئەمڕۆ",
            "پێشمەرگە", "پارێزگا", "بەغدا", "لە ", "بۆ ", "بە "
        )

        private val arabicIndicatorWords = listOf(
            "الأمانة", "الوزراء", "وزارة ", "محافظة ", "العراق", "بغداد",
            "أعلنت", "أعلن", "تعلن", "في ", "على ", "إلى ", "من ", "عن ",
            "بشأن", "تعطيل", "عطلة", "الرسمية", "الوطني", "الإنتاج"
        )

        fun detectLanguage(text: String): AppLanguage {
            if (text.isBlank()) return AppLanguage.ARABIC
            val kurdishCharCount = text.count { it in kurdishExclusiveChars }
            if (kurdishCharCount >= 1) return AppLanguage.KURDISH

            val kurdishScore = kurdishIndicatorWords.count { text.contains(it) }
            val arabicScore = arabicIndicatorWords.count { text.contains(it) }
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
            // Common news verbs & connectors for offline fallback translation
            OfficialTermPair("في عموم المحافظات", "لە سەرتاسەری پارێزگاکان"),
            OfficialTermPair("في جميع المحافظات", "لە هەموو پارێزگاکان"),
            OfficialTermPair("الدوائر الحكومية", "فەرمانگە حکومییەکان"),
            OfficialTermPair("تعلن", "ڕادەگەیەنێت"),
            OfficialTermPair("أعلنت", "ڕایگەیاند"),
            OfficialTermPair("أعلن", "ڕایگەیاند"),
            OfficialTermPair("قررت", "بڕیاری دا"),
            OfficialTermPair("قرر", "بڕیاری دا"),
            OfficialTermPair("أكد", "جەختی کردەوە"),
            OfficialTermPair("افتتح", "کردەوە"),
            OfficialTermPair("اليوم", "ئەمڕۆ"),
            OfficialTermPair("غداً", "سبەی"),
            OfficialTermPair("بشأن", "سەبارەت بە"),
            OfficialTermPair("بمناسبة", "بەبۆنەی"),
            OfficialTermPair("رسمياً", "بە فەرمی"),
            OfficialTermPair("محافظة", "پارێزگای"),
            OfficialTermPair("دعم", "پشتگیریکردنی"),
            OfficialTermPair("مشاريع", "پڕۆژەکانی"),
            OfficialTermPair("مشروع", "پڕۆژەی"),
            OfficialTermPair("قرار", "بڕیاری"),
            OfficialTermPair("ارتفاع", "بەرزبوونەوەی"),
            OfficialTermPair("انخفاض", "نزمبوونەوەی"),
            OfficialTermPair("سعر صرف الدولار", "نرخی دۆلار"),
            OfficialTermPair("في", "لە"),
            OfficialTermPair("إلى", "بۆ"),
            OfficialTermPair("على", "لەسەر"),
            OfficialTermPair("عن", "دەربارەی")
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
