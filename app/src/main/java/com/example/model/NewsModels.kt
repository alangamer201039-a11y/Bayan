package com.example.model

enum class SourceTrustLevel(val key: String, val labelAr: String) {
    OFFICIAL("official", "مصدر رسمي"),
    VERIFIED("verified", "مصدر موثوق"),
    GENERAL("general", "مصدر إخباري");

    companion object {
        fun fromKey(key: String): SourceTrustLevel = entries.find { it.key == key } ?: GENERAL
    }
}

data class TrustedSource(
    val id: String,
    val nameAr: String,
    val domain: String,
    val trustLevel: SourceTrustLevel,
    val aliases: List<String> = emptyList(),
    val directRssUrl: String? = null
)

object IraqRelevanceFilter {
    private val iraqTextKeywords = listOf(
        "العراق", "العراقي", "العراقية", "العراقيين", "العراقيون", "عراق",
        "بغداد", "البصرة", "أربيل", "اربيل", "الموصل", "نينوى", "كركوك",
        "صلاح الدين", "تكريت", "سامراء", "الأنبار", "الانبار", "الرمادي", "الفلوجة",
        "النجف", "كربلاء", "السليمانية", "بابل", "الحلة", "ذي قار", "الناصرية",
        "ديالى", "بعقوبة", "واسط", "الكوت", "ميسان", "العمارة", "المثنى", "السماوة",
        "القادسية", "الديوانية", "دهوك", "زاخو", "الزبير", "الفاو", "حلبجة",
        "سنجار", "تلعفر", "الرصافة", "الكرخ", "دجلة", "الفرات", "شط العرب",
        "الرافدين", "أسود الرافدين", "المنتخب العراقي", "دوري نجوم العراق", "الدوري العراقي",
        "الدينار العراقي", "البنك المركزي العراقي", "البرلمان العراقي", "مجلس النواب العراقي",
        "مجلس الوزراء العراقي", "الحكومة العراقية", "إقليم كردستان", "كردستان العراق",
        "طريق التنمية", "ميناء الفاو", "الحشد الشعبي", "البيشمركة", "السوداني",
        "أمانة بغداد", "الأنواء الجوية", "سومو"
    )

    private val strictlyIraqiGovDomains = setOf(
        "pmo.iq", "cabinet.iq", "gov.iq", "mofa.gov.iq", "moi.gov.iq",
        "oil.gov.iq", "cbi.iq", "parliament.iq", "moc.gov.iq", "moys.gov.iq",
        "mof.gov.iq", "mohesr.gov.iq"
    )

    /**
     * يتحقق من أن الخبر متعلق بالعراق فعلياً (عبر العنوان أو النص، وليس اسم الوكالة فقط)
     * لمنع ظهور الأخبار الأجنبية أو الدولية البحتة غير المتعلقة بالعراق.
     */
    fun isRelatedToIraq(
        title: String,
        summary: String,
        sourceDomain: String = ""
    ): Boolean {
        val text = "$title $summary"
        if (iraqTextKeywords.any { kw -> text.contains(kw) }) {
            return true
        }
        val domainLower = sourceDomain.lowercase()
        return strictlyIraqiGovDomains.any { gov -> domainLower == gov || domainLower.endsWith(".$gov") }
    }

    fun isRelatedToIraq(article: NewsArticle): Boolean =
        isRelatedToIraq(article.title, article.summary, article.sourceDomain)
}

object TrustedSources {
    val all: List<TrustedSource> = listOf(
        TrustedSource(
            id = "ina",
            nameAr = "وكالة الأنباء العراقية (واع)",
            domain = "ina.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وكالة الأنباء العراقية", "واع", "ina.iq", "iraqi news agency"),
            directRssUrl = "https://www.ina.iq/rss.xml"
        ),
        TrustedSource(
            id = "alsabaah",
            nameAr = "جريدة الصباح الرسمية",
            domain = "alsabaah.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("الصباح", "جريدة الصباح", "alsabaah.iq"),
            directRssUrl = "https://alsabaah.iq/rss.xml"
        ),
        TrustedSource(
            id = "pmo",
            nameAr = "المكتب الإعلامي لرئيس الوزراء",
            domain = "pmo.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("رئيس مجلس الوزراء", "المكتب الإعلامي لرئيس الوزراء", "pmo.iq")
        ),
        TrustedSource(
            id = "cabinet",
            nameAr = "الأمانة العامة لمجلس الوزراء",
            domain = "cabinet.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("مجلس الوزراء", "الأمانة العامة لمجلس الوزراء", "cabinet.iq")
        ),
        TrustedSource(
            id = "mofa",
            nameAr = "وزارة الخارجية العراقية",
            domain = "mofa.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الخارجية", "mofa.gov.iq")
        ),
        TrustedSource(
            id = "moi",
            nameAr = "وزارة الداخلية العراقية",
            domain = "moi.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الداخلية", "moi.gov.iq")
        ),
        TrustedSource(
            id = "oil",
            nameAr = "وزارة النفط العراقية",
            domain = "oil.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة النفط", "oil.gov.iq")
        ),
        TrustedSource(
            id = "cbi",
            nameAr = "البنك المركزي العراقي",
            domain = "cbi.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("البنك المركزي العراقي", "cbi.iq")
        ),
        TrustedSource(
            id = "parliament",
            nameAr = "مجلس النواب العراقي",
            domain = "parliament.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("مجلس النواب", "parliament.iq")
        ),
        TrustedSource(
            id = "goviq",
            nameAr = "بوابة الحكومة العراقية",
            domain = "gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("الحكومة العراقية", "gov.iq", "moc.gov.iq", "moys.gov.iq", "mof.gov.iq", "mohesr.gov.iq")
        ),
        TrustedSource(
            id = "shafaq",
            nameAr = "شفق نيوز",
            domain = "shafaq.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("شفق نيوز", "shafaq")
        ),
        TrustedSource(
            id = "alsumaria",
            nameAr = "السومرية نيوز",
            domain = "alsumaria.tv",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("السومرية", "السومرية نيوز", "alsumaria")
        ),
        TrustedSource(
            id = "alsharqiya",
            nameAr = "الشرقية نيوز",
            domain = "alsharqiya.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("الشرقية", "alsharqiya")
        ),
        TrustedSource(
            id = "rudaw",
            nameAr = "رووداو عربية",
            domain = "rudawarabia.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("رووداو", "rudaw")
        ),
        TrustedSource(
            id = "aljazeera",
            nameAr = "الجزيرة نت",
            domain = "aljazeera.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("الجزيرة", "الجزيرة نت", "aljazeera"),
            directRssUrl = "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdff8b8cab9"
        ),
        TrustedSource(
            id = "skynews",
            nameAr = "سكاي نيوز عربية",
            domain = "skynewsarabia.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("سكاي نيوز", "سكاي نيوز عربية", "skynewsarabia"),
            directRssUrl = "https://www.skynewsarabia.com/rss.xml"
        ),
        TrustedSource(
            id = "bbc",
            nameAr = "بي بي سي عربي",
            domain = "bbc.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("bbc", "بي بي سي", "بي بي سي عربي")
        ),
        TrustedSource(
            id = "france24",
            nameAr = "فرانس 24",
            domain = "france24.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("فرانس 24", "france 24", "france24")
        ),
        TrustedSource(
            id = "alarabiya",
            nameAr = "العربية",
            domain = "alarabiya.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("العربية", "alarabiya")
        )
    )

    val trustedSiteFilterQuery: String =
        "(site:ina.iq OR site:alsabaah.iq OR site:pmo.iq OR site:cabinet.iq OR site:gov.iq OR site:mofa.gov.iq OR site:moi.gov.iq OR site:oil.gov.iq OR site:cbi.iq OR site:parliament.iq OR site:shafaq.com OR site:alsumaria.tv OR site:alsharqiya.com OR site:rudawarabia.net OR site:aljazeera.net OR site:skynewsarabia.com OR site:bbc.com OR site:france24.com OR site:alarabiya.net)"

    fun classify(sourceName: String, sourceUrl: String, articleLink: String): Pair<SourceTrustLevel, String> {
        val urlText = "$sourceUrl $articleLink".lowercase()
        val nameLower = sourceName.lowercase()

        for (src in all) {
            if (urlText.contains(src.domain)) {
                return src.trustLevel to src.domain
            }
        }
        for (src in all) {
            if (src.aliases.any { alias -> nameLower.contains(alias.lowercase()) }) {
                return src.trustLevel to src.domain
            }
        }
        if (urlText.contains(".gov.iq") || urlText.contains(".iq/")) {
            return if (urlText.contains(".gov.iq")) {
                SourceTrustLevel.OFFICIAL to extractDomain(sourceUrl.ifBlank { articleLink })
            } else {
                SourceTrustLevel.GENERAL to extractDomain(sourceUrl.ifBlank { articleLink })
            }
        }
        return SourceTrustLevel.GENERAL to extractDomain(sourceUrl.ifBlank { articleLink })
    }

    private fun extractDomain(url: String): String {
        return try {
            val cleaned = url.substringAfter("://", url)
                .substringBefore("/")
                .removePrefix("www.")
            if (cleaned.contains(".") && !cleaned.contains("news.google.com")) cleaned else ""
        } catch (_: Exception) {
            ""
        }
    }
}

data class FeedEndpoint(
    val url: String,
    val forcedSource: TrustedSource? = null,
    val filterIraqOnly: Boolean = true
)

object OfficialFeeds {
    private fun gn(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return "https://news.google.com/rss/search?q=$encoded&hl=ar&gl=IQ&ceid=IQ:ar"
    }

    private val inaSource = TrustedSources.all.first { it.id == "ina" }
    private val alsabaahSource = TrustedSources.all.first { it.id == "alsabaah" }
    private val aljazeeraSource = TrustedSources.all.first { it.id == "aljazeera" }
    private val skynewsSource = TrustedSources.all.first { it.id == "skynews" }

    /**
     * مصفوفة FEEDS الرسمية والموثوقة الخاصة بالعراق فقط:
     * تضم خلاصات وكالة الأنباء العراقية (INA) والمواقع الحكومية العراقية (.gov.iq)
     * مع فلترة صارمة لضمان أن جميع الأخبار تخص العراق فقط.
     */
    val FEEDS: Map<NewsCategory, List<FeedEndpoint>> = mapOf(
        NewsCategory.BREAKING to listOf(
            FeedEndpoint(url = "https://www.ina.iq/rss.xml", forcedSource = inaSource, filterIraqOnly = true),
            FeedEndpoint(url = "https://alsabaah.iq/rss.xml", forcedSource = alsabaahSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق site:ina.iq"), filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق (site:alsabaah.iq OR site:pmo.iq OR site:cabinet.iq OR site:moi.gov.iq OR site:gov.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = aljazeeraSource.directRssUrl!!, forcedSource = aljazeeraSource, filterIraqOnly = true),
            FeedEndpoint(url = skynewsSource.directRssUrl!!, forcedSource = skynewsSource, filterIraqOnly = true)
        ),
        NewsCategory.GOVERNORATES to listOf(
            FeedEndpoint(url = "https://www.ina.iq/rss.xml", forcedSource = inaSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("(بغداد OR البصرة OR أربيل OR الموصل OR صلاح الدين OR النجف OR كربلاء OR كركوك OR الأنبار) (site:ina.iq OR site:alsabaah.iq OR site:pmo.iq OR site:gov.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("(بغداد OR البصرة OR أربيل OR الموصل OR صلاح الدين OR كربلاء OR النجف) ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.ECONOMY to listOf(
            FeedEndpoint(url = gn("(اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي OR الدينار العراقي OR الموازنة العراقية) (site:ina.iq OR site:oil.gov.iq OR site:cbi.iq OR site:mof.gov.iq OR site:gov.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("(اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي) ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.TECH_PROJECTS to listOf(
            FeedEndpoint(url = gn("(طريق التنمية OR ميناء الفاو OR مشاريع العراق OR التحول الرقمي العراق OR الاتصالات العراق) (site:ina.iq OR site:moc.gov.iq OR site:mohesr.gov.iq OR site:cabinet.iq OR site:gov.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("(مشاريع العراق OR طريق التنمية OR تكنولوجيا العراق) ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.SPORTS to listOf(
            FeedEndpoint(url = gn("(المنتخب العراقي OR دوري نجوم العراق OR الدوري العراقي OR الكرة العراقية) (site:ina.iq OR site:alsabaah.iq OR site:moys.gov.iq OR site:gov.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("(المنتخب العراقي OR دوري نجوم العراق OR الدوري العراقي) ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.INTERNATIONAL to listOf(
            FeedEndpoint(url = gn("(العراق OR الخارجية العراقية) (site:mofa.gov.iq OR site:pmo.iq OR site:ina.iq)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق (إيران OR تركيا OR السعودية OR أمريكا OR سوريا OR الأردن OR الكويت) ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = aljazeeraSource.directRssUrl!!, forcedSource = aljazeeraSource, filterIraqOnly = true),
            FeedEndpoint(url = skynewsSource.directRssUrl!!, forcedSource = skynewsSource, filterIraqOnly = true)
        )
    )
}

enum class NewsCategory(
    val id: String,
    val labelAr: String,
    val rssQuery: String?
) {
    ALL("all", "الكل", null),
    BREAKING("brk", "عاجل العراق", "العراق"),
    GOVERNORATES("gov", "بغداد والمحافظات", "بغداد OR البصرة OR أربيل OR الموصل OR صلاح الدين OR النجف OR كربلاء"),
    ECONOMY("eco", "اقتصاد ونفط", "اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي"),
    TECH_PROJECTS("tech", "تكنولوجيا ومشاريع", "مشاريع العراق OR طريق التنمية OR تكنولوجيا العراق"),
    SPORTS("spo", "رياضة عراقية", "المنتخب العراقي OR الدوري العراقي"),
    INTERNATIONAL("intl", "شأن دولي", "العراق إيران OR العراق تركيا OR العراق السعودية OR العراق أمريكا");

    companion object {
        fun fromId(id: String): NewsCategory = entries.find { it.id == id } ?: ALL
    }
}

data class IraqiProvince(
    val nameAr: String,
    val lat: Double,
    val lon: Double
)

object IraqiProvinces {
    val all: List<IraqiProvince> = listOf(
        IraqiProvince("صلاح الدين", 34.6098, 43.6793),
        IraqiProvince("بغداد", 33.3152, 44.3661),
        IraqiProvince("البصرة", 30.5085, 47.7804),
        IraqiProvince("أربيل", 36.1911, 44.0092),
        IraqiProvince("الموصل", 36.3489, 43.1577),
        IraqiProvince("النجف", 32.0000, 44.3360),
        IraqiProvince("كربلاء", 32.6160, 44.0249),
        IraqiProvince("كركوك", 35.4669, 44.3929),
        IraqiProvince("الأنبار", 33.4271, 43.2954),
        IraqiProvince("السليمانية", 35.5558, 45.4351),
        IraqiProvince("بابل", 32.4637, 44.4196),
        IraqiProvince("ذي قار", 31.0579, 46.2573)
    )

    fun findByName(name: String): IraqiProvince =
        all.find { it.nameAr == name } ?: all.first()
}

enum class ReadingFontSize(
    val key: String,
    val labelAr: String,
    val bodySp: Int
) {
    SMALL("sm", "صغير", 15),
    MEDIUM("md", "متوسط", 17),
    LARGE("lg", "كبير", 20);

    companion object {
        fun fromKey(key: String): ReadingFontSize = entries.find { it.key == key } ?: MEDIUM
    }
}

enum class BayanTab(val route: String) {
    HOME("home"),
    BREAKING("breaking"),
    SAVED("saved"),
    SETTINGS("settings")
}

data class ProvinceWeather(
    val provinceName: String,
    val temperatureCelsius: Int,
    val conditionAr: String
)

data class NewsArticle(
    val id: String,
    val categoryId: String,
    val isBreaking: Boolean,
    val source: String,
    val sourceDomain: String = "",
    val trustLevel: SourceTrustLevel = SourceTrustLevel.GENERAL,
    val publishedEpochMs: Long,
    val title: String,
    val summary: String,
    val link: String,
    val isSaved: Boolean = false
) {
    fun relativeTimeAr(nowMs: Long = System.currentTimeMillis()): String {
        val diffMinutes = ((nowMs - publishedEpochMs) / 60_000L).coerceAtLeast(1L)
        return when {
            diffMinutes < 60L -> "منذ $diffMinutes د"
            diffMinutes < 1440L -> "منذ ${diffMinutes / 60L} س"
            else -> "منذ ${diffMinutes / 1440L} ي"
        }
    }
}
