package com.example.model

enum class AppLanguage(
    val code: String,
    val localeTag: String,
    val labelNative: String
) {
    ARABIC("ar", "ar-IQ", "العربية"),
    KURDISH("ckb", "ckb-IQ", "کوردی");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: ARABIC
    }
}

enum class SourceTrustLevel(
    val key: String,
    val labelAr: String,
    val labelCkb: String
) {
    OFFICIAL("official", "مصدر رسمي", "سەرچاوەی فەرمی"),
    VERIFIED("verified", "مصدر موثوق", "سەرچاوەی باوەڕپێکراو"),
    GENERAL("general", "مصدر إخباري", "سەرچاوەی هەواڵ");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> labelAr
        AppLanguage.KURDISH -> labelCkb
    }

    companion object {
        fun fromKey(key: String): SourceTrustLevel = entries.find { it.key == key } ?: GENERAL
    }
}

data class TrustedSource(
    val id: String,
    val nameAr: String,
    val nameCkb: String = nameAr,
    val domain: String,
    val trustLevel: SourceTrustLevel,
    val aliases: List<String> = emptyList(),
    val directRssUrl: String? = null,
    val languageScope: AppLanguage? = null
) {
    fun displayName(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> nameAr
        AppLanguage.KURDISH -> nameCkb
    }
}

object IraqRelevanceFilter {
    private val iraqTextKeywords = listOf(
        // Arabic keywords
        "العراق", "العراقي", "العراقية", "العراقيين", "العراقيون", "عراق",
        "بغداد", "البصرة", "أربيل", "اربيل", "الموصل", "نينوى", "كركوك",
        "صلاح الدين", "تكريت", "سامراء", "الأنبار", "الانبار", "الرمادي", "الفلوجة",
        "النجف", "كربلاء", "السليمانية", "بابل", "الحلة", "ذي قار", "الناصرية",
        "ديالى", "بعقوبة", "واسط", "الكوت", "ميسان", "العمارة", "المثنى", "السماوة",
        "القادسية", "الديوانية", "دهوك", "زاخو", "الزبير", "الفاو", "حلبجة",
        "سنجار", "تلعفر", "الرصافة", "الكرخ", "دجلة", "الفرات", "شط العرب",
        "الرافدين", "أسود الرافدين", "المنتخب العراقي", "دوري نجوم العراق", "الدوري العراقي",
        "الدينار العراقي", "البنك المركزي العراقي", "البرلمان العراقي", "مجلس النواب العراقي",
        "مجلس الوزراء العراقي", "الأمانة العامة لمجلس الوزراء", "الحكومة العراقية",
        "إقليم كردستان", "كردستان العراق", "وزارة التربية", "تعطيل الدوام",
        "وزارة الصناعة", "وزارة الزراعة", "وزارة الكهرباء", "وزارة الإعمار",
        "وزارة الصحة", "وزارة المالية", "طريق التنمية", "ميناء الفاو",
        "الحشد الشعبي", "البيشمركة", "السوداني", "أمانة بغداد", "الأنواء الجوية", "سومو",
        "حكومة إقليم كردستان", "دائرة الإعلام والمعلومات", "برلمان كردستان",
        "مطار أربيل", "مطار السليمانية", "كوردستان 24", "رووداو",
        // Kurdish Sorani keywords
        "عێراق", "عێراقی", "عێراقى", "بەغدا", "به‌غدا", "هەولێر", "هه‌ولێر",
        "سلێمانی", "سلێمانى", "دهۆک", "هەڵەبجە", "هه‌ڵه‌بجه‌", "کەرکووک", "کەرکوک",
        "كه‌ركووك", "مووسڵ", "موسڵ", "نەینەوا", "بەسرە", "سەڵاحەدین", "تکریت",
        "نەجەف", "کەربەلا", "ئەنبار", "بابل", "زیقار", "دیالە", "واست", "میسان",
        "موسەننا", "قادسیە", "دیوانیە", "زاخۆ", "سۆران", "ڕاپەڕین", "گەرمیان",
        "هەرێمی کوردستان", "كوردستان", "حکومەتی هەرێم", "حكومه‌تی هه‌رێم",
        "ئەنجومەنی وەزیران", "فەرمانگەی میدیا و زانیاری", "وەزارەتی پەروەردە",
        "پشووی فەرمی", "پشوو", "ڕاگرتنی دەوام", "پەرلەمانی کوردستان",
        "پەرلەمانی عێراق", "پێشمەرگە", "دیناری عێراقی", "بانکی ناوەندی",
        "هەڵبژاردەی عێراق", "فڕۆکەخانەی هەولێر", "فڕۆکەخانەی سلێمانی",
        "کوردستان ٢٤", "ڕووداو", "شەفەق", "سۆمۆ"
    )

    private val strictlyIraqiGovDomains = setOf(
        "pmo.iq", "cabinet.iq", "gov.iq", "mofa.gov.iq", "moi.gov.iq",
        "oil.gov.iq", "cbi.iq", "parliament.iq", "moc.gov.iq", "moys.gov.iq",
        "mof.gov.iq", "mohesr.gov.iq", "epedu.gov.iq", "moedu.gov.iq",
        "industry.gov.iq", "zeraa.gov.iq", "moelc.gov.iq", "moch.gov.iq", "moh.gov.iq",
        "gov.krd", "cabinet.gov.krd", "krg.org"
    )

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
            nameCkb = "ئاژانسی هەواڵی عێراقی (واع)",
            domain = "ina.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وكالة الأنباء العراقية", "واع", "ina.iq", "iraqi news agency"),
            directRssUrl = "https://www.ina.iq/rss.xml"
        ),
        TrustedSource(
            id = "krg_dmi",
            nameAr = "دائرة الإعلام (حكومة إقليم كردستان)",
            nameCkb = "فەرمانگەی میدیا و زانیاری (KRG)",
            domain = "gov.krd",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf(
                "دائرة الإعلام والمعلومات",
                "حكومة إقليم كردستان",
                "فەرمانگەی میدیا و زانیاری",
                "حکومەتی هەرێمی کوردستان",
                "إقليم كردستان",
                "gov.krd",
                "cabinet.gov.krd",
                "krg.org"
            ),
            directRssUrl = "https://gov.krd/ckb/rss"
        ),
        TrustedSource(
            id = "cabinet",
            nameAr = "الأمانة العامة لمجلس الوزراء",
            nameCkb = "ئەمیندارێتی گشتی ئەنجومەنی وەزیران",
            domain = "cabinet.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("الأمانة العامة لمجلس الوزراء", "مجلس الوزراء", "cabinet.iq")
        ),
        TrustedSource(
            id = "pmo",
            nameAr = "المكتب الإعلامي لرئيس الوزراء",
            nameCkb = "نووسینگەی ڕاگەیاندنی سەرۆک وەزیران",
            domain = "pmo.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("رئيس مجلس الوزراء", "المكتب الإعلامي لرئيس الوزراء", "pmo.iq")
        ),
        TrustedSource(
            id = "moedu",
            nameAr = "وزارة التربية العراقية",
            nameCkb = "وەزارەتی پەروەردەی عێراق",
            domain = "epedu.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة التربية", "التربية العراقية", "وەزارەتی پەروەردە", "epedu.gov.iq", "moedu.gov.iq")
        ),
        TrustedSource(
            id = "alsabaah",
            nameAr = "جريدة الصباح الرسمية",
            nameCkb = "ڕۆژنامەی سەباحی فەرمی",
            domain = "alsabaah.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("الصباح", "جريدة الصباح", "alsabaah.iq"),
            directRssUrl = "https://alsabaah.iq/rss.xml"
        ),
        TrustedSource(
            id = "industry",
            nameAr = "وزارة الصناعة والمعادن",
            nameCkb = "وەزارەتی پیشەسازی و کانزاکان",
            domain = "industry.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الصناعة", "الصناعة والمعادن", "industry.gov.iq")
        ),
        TrustedSource(
            id = "moelc",
            nameAr = "وزارة الكهرباء العراقية",
            nameCkb = "وەزارەتی کارەبای عێراق",
            domain = "moelc.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الكهرباء", "moelc.gov.iq")
        ),
        TrustedSource(
            id = "zeraa",
            nameAr = "وزارة الزراعة العراقية",
            nameCkb = "وەزارەتی کشتوکاڵی عێراق",
            domain = "zeraa.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الزراعة", "zeraa.gov.iq")
        ),
        TrustedSource(
            id = "moch",
            nameAr = "وزارة الإعمار والإسكان",
            nameCkb = "وەزارەتی ئاوەدانکردنەوە و نیشتەجێکردن",
            domain = "moch.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الإعمار", "الإعمار والإسكان", "moch.gov.iq")
        ),
        TrustedSource(
            id = "oil",
            nameAr = "وزارة النفط العراقية",
            nameCkb = "وەزارەتی نەوتی عێراق",
            domain = "oil.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة النفط", "oil.gov.iq")
        ),
        TrustedSource(
            id = "cbi",
            nameAr = "البنك المركزي العراقي",
            nameCkb = "بانکی ناوەندی عێراق",
            domain = "cbi.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("البنك المركزي العراقي", "cbi.iq")
        ),
        TrustedSource(
            id = "mof",
            nameAr = "وزارة المالية العراقية",
            nameCkb = "وەزارەتی دارایی عێراق",
            domain = "mof.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة المالية", "mof.gov.iq")
        ),
        TrustedSource(
            id = "moh",
            nameAr = "وزارة الصحة العراقية",
            nameCkb = "وەزارەتی تەندروستی عێراق",
            domain = "moh.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الصحة", "moh.gov.iq")
        ),
        TrustedSource(
            id = "moi",
            nameAr = "وزارة الداخلية العراقية",
            nameCkb = "وەزارەتی ناوخۆی عێراق",
            domain = "moi.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الداخلية", "moi.gov.iq")
        ),
        TrustedSource(
            id = "mofa",
            nameAr = "وزارة الخارجية العراقية",
            nameCkb = "وەزارەتی دەرەوەی عێراق",
            domain = "mofa.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("وزارة الخارجية", "mofa.gov.iq")
        ),
        TrustedSource(
            id = "mohesr",
            nameAr = "وزارة التعليم العالي",
            nameCkb = "وەزارەتی خوێندنی باڵا",
            domain = "mohesr.gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("التعليم العالي", "mohesr.gov.iq")
        ),
        TrustedSource(
            id = "parliament",
            nameAr = "مجلس النواب العراقي",
            nameCkb = "ئەنجومەنی نوێنەرانی عێراق",
            domain = "parliament.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("مجلس النواب", "parliament.iq")
        ),
        TrustedSource(
            id = "goviq",
            nameAr = "بوابة الحكومة العراقية",
            nameCkb = "دەروازەی حکومەتی عێراق",
            domain = "gov.iq",
            trustLevel = SourceTrustLevel.OFFICIAL,
            aliases = listOf("الحكومة العراقية", "gov.iq", "moc.gov.iq", "moys.gov.iq")
        ),
        TrustedSource(
            id = "rudaw_sorani",
            nameAr = "شبكة رووداو (كردي سوراني)",
            nameCkb = "تۆڕی میدیایی ڕووداو (سۆرانی)",
            domain = "rudaw.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("ڕووداو", "تۆڕی میدیایی ڕووداو", "rudaw.net"),
            directRssUrl = "https://www.rudaw.net/rss",
            languageScope = AppLanguage.KURDISH
        ),
        TrustedSource(
            id = "kurdistan24_sorani",
            nameAr = "كوردستان 24 (كردي سوراني)",
            nameCkb = "کوردستان ٢٤ (سۆرانی)",
            domain = "kurdistan24.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("کوردستان ٢٤", "کوردستان 24", "kurdistan24.net/ckb"),
            directRssUrl = "https://www.kurdistan24.net/ckb/rss",
            languageScope = AppLanguage.KURDISH
        ),
        TrustedSource(
            id = "rudaw",
            nameAr = "شبكة رووداو الإعلامية (عربي)",
            nameCkb = "تۆڕی میدیایی ڕووداو (عەرەبی)",
            domain = "rudawarabia.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("رووداو", "شبكة رووداو", "رووداو عربية", "rudaw", "rudawarabia.net"),
            directRssUrl = "https://www.rudawarabia.net/rss",
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "kurdistan24",
            nameAr = "كوردستان 24 (عربي)",
            nameCkb = "کوردستان ٢٤ (عەرەبی)",
            domain = "kurdistan24.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("كوردستان 24", "كردستان 24", "مؤسسة كوردستان 24", "kurdistan24", "kurdistan 24"),
            directRssUrl = "https://www.kurdistan24.net/ar/rss",
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "shafaq",
            nameAr = "شفق نيوز",
            nameCkb = "شەفەق نیوز",
            domain = "shafaq.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("شفق نيوز", "شەفەق نیوز", "shafaq"),
            directRssUrl = "https://shafaq.com/ku/rss"
        ),
        TrustedSource(
            id = "alsumaria",
            nameAr = "السومرية نيوز",
            nameCkb = "سۆمەریە نیوز",
            domain = "alsumaria.tv",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("السومرية", "السومرية نيوز", "alsumaria"),
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "alsharqiya",
            nameAr = "الشرقية نيوز",
            nameCkb = "شەرقییە نیوز",
            domain = "alsharqiya.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("الشرقية", "alsharqiya"),
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "aljazeera",
            nameAr = "الجزيرة نت",
            nameCkb = "جەزیرە نێت",
            domain = "aljazeera.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("الجزيرة", "الجزيرة نت", "aljazeera"),
            directRssUrl = "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdff8b8cab9",
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "skynews",
            nameAr = "سكاي نيوز عربية",
            nameCkb = "سکای نیوز عەرەبی",
            domain = "skynewsarabia.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("سكاي نيوز", "سكاي نيوز عربية", "skynewsarabia"),
            directRssUrl = "https://www.skynewsarabia.com/rss.xml",
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "bbc",
            nameAr = "بي بي سي عربي",
            nameCkb = "بی بی سی عەرەبی",
            domain = "bbc.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("bbc", "بي بي سي", "بي بي سي عربي"),
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "france24",
            nameAr = "فرانس 24",
            nameCkb = "فرانس ٢٤",
            domain = "france24.com",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("فرانس 24", "france 24", "france24"),
            languageScope = AppLanguage.ARABIC
        ),
        TrustedSource(
            id = "alarabiya",
            nameAr = "العربية",
            nameCkb = "عەرەبییە",
            domain = "alarabiya.net",
            trustLevel = SourceTrustLevel.VERIFIED,
            aliases = listOf("العربية", "alarabiya"),
            languageScope = AppLanguage.ARABIC
        )
    )

    fun displayedForLanguage(language: AppLanguage): List<TrustedSource> {
        val scoped = all.filter { it.languageScope == null || it.languageScope == language }
        return if (language == AppLanguage.KURDISH) {
            scoped.sortedWith(
                compareByDescending<TrustedSource> {
                    it.id in setOf("krg_dmi", "rudaw_sorani", "kurdistan24_sorani", "shafaq")
                }.thenByDescending { it.trustLevel == SourceTrustLevel.OFFICIAL }
            )
        } else {
            scoped
        }
    }

    val officialMinistriesSitesQuery: String =
        "(site:ina.iq OR site:cabinet.iq OR site:pmo.iq OR site:alsabaah.iq OR site:gov.iq OR site:gov.krd OR site:krg.org OR site:epedu.gov.iq OR site:moedu.gov.iq OR site:industry.gov.iq OR site:zeraa.gov.iq OR site:moelc.gov.iq OR site:moch.gov.iq OR site:oil.gov.iq OR site:cbi.iq OR site:mof.gov.iq OR site:moh.gov.iq OR site:moi.gov.iq OR site:mofa.gov.iq OR site:mohesr.gov.iq OR site:moc.gov.iq OR site:moys.gov.iq OR site:parliament.iq)"

    val trustedSiteFilterQuery: String =
        "(site:ina.iq OR site:cabinet.iq OR site:pmo.iq OR site:alsabaah.iq OR site:gov.iq OR site:gov.krd OR site:krg.org OR site:epedu.gov.iq OR site:moedu.gov.iq OR site:industry.gov.iq OR site:zeraa.gov.iq OR site:moelc.gov.iq OR site:moch.gov.iq OR site:oil.gov.iq OR site:cbi.iq OR site:mof.gov.iq OR site:moh.gov.iq OR site:moi.gov.iq OR site:mofa.gov.iq OR site:mohesr.gov.iq OR site:moc.gov.iq OR site:moys.gov.iq OR site:parliament.iq OR site:shafaq.com OR site:alsumaria.tv OR site:alsharqiya.com OR site:rudawarabia.net OR site:rudaw.net OR site:kurdistan24.net OR site:aljazeera.net OR site:skynewsarabia.com OR site:bbc.com OR site:france24.com OR site:alarabiya.net)"

    val kurdishTrustedSitesQuery: String =
        "(site:gov.krd OR site:krg.org OR site:rudaw.net/sorani OR site:rudaw.net OR site:kurdistan24.net/ckb OR site:kurdistan24.net OR site:shafaq.com/ku)"

    fun classify(sourceName: String, sourceUrl: String, articleLink: String): Pair<SourceTrustLevel, String> {
        val urlText = "$sourceUrl $articleLink".lowercase()
        val nameLower = sourceName.lowercase()

        for (src in all) {
            if (urlText.contains(src.domain)) {
                return src.trustLevel to src.domain
            }
        }
        for (src in all) {
            if (src.aliases.any { alias ->
                    val aliasLower = alias.lowercase()
                    nameLower.contains(aliasLower) || urlText.contains(aliasLower)
                }
            ) {
                return src.trustLevel to src.domain
            }
        }
        if (urlText.contains(".gov.iq") || urlText.contains(".gov.krd") || urlText.contains(".iq/")) {
            return if (urlText.contains(".gov.iq") || urlText.contains(".gov.krd")) {
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
    private val krgDmiSource = TrustedSources.all.first { it.id == "krg_dmi" }
    private val alsabaahSource = TrustedSources.all.first { it.id == "alsabaah" }
    private val rudawSource = TrustedSources.all.first { it.id == "rudaw" }
    private val rudawSoraniSource = TrustedSources.all.first { it.id == "rudaw_sorani" }
    private val kurdistan24Source = TrustedSources.all.first { it.id == "kurdistan24" }
    private val kurdistan24SoraniSource = TrustedSources.all.first { it.id == "kurdistan24_sorani" }
    private val shafaqSource = TrustedSources.all.first { it.id == "shafaq" }
    private val aljazeeraSource = TrustedSources.all.first { it.id == "aljazeera" }
    private val skynewsSource = TrustedSources.all.first { it.id == "skynews" }

    val FEEDS: Map<NewsCategory, List<FeedEndpoint>> = mapOf(
        NewsCategory.BREAKING to listOf(
            FeedEndpoint(url = "https://www.ina.iq/rss.xml", forcedSource = inaSource, filterIraqOnly = true),
            FeedEndpoint(url = "https://alsabaah.iq/rss.xml", forcedSource = alsabaahSource, filterIraqOnly = true),
            FeedEndpoint(url = rudawSource.directRssUrl!!, forcedSource = rudawSource, filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24Source.directRssUrl!!, forcedSource = kurdistan24Source, filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق site:ina.iq"), filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق ${TrustedSources.officialMinistriesSitesQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = gn("(إقليم كردستان OR أربيل OR السليمانية OR دهوك OR حلبجة OR حكومة إقليم كردستان) (site:gov.krd OR site:rudawarabia.net OR site:kurdistan24.net OR site:shafaq.com)"), filterIraqOnly = true),
            FeedEndpoint(url = gn("العراق ${TrustedSources.trustedSiteFilterQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = aljazeeraSource.directRssUrl!!, forcedSource = aljazeeraSource, filterIraqOnly = true),
            FeedEndpoint(url = skynewsSource.directRssUrl!!, forcedSource = skynewsSource, filterIraqOnly = true)
        ),
        NewsCategory.HOLIDAYS to listOf(
            FeedEndpoint(
                url = gn("(عطلة رسمية OR تعطيل الدوام الرسمي OR تعطيل الدوام OR العطل الرسمية) العراق (site:ina.iq OR site:cabinet.iq OR site:pmo.iq OR site:alsabaah.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(وزارة التربية العراقية OR العطلة المدرسية OR امتحانات OR تأجيل الدوام OR تعطيل المدارس) (site:ina.iq OR site:epedu.gov.iq OR site:moedu.gov.iq OR site:alsabaah.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(عطلة رسمية OR تعطيل الدوام OR العطل الرسمية OR إجازة رسمية) (إقليم كردستان OR حكومة إقليم كردستان OR أربيل OR السليمانية OR دهوك OR حلبجة OR دائرة الإعلام والمعلومات) (site:gov.krd OR site:krg.org OR site:rudawarabia.net OR site:kurdistan24.net OR site:shafaq.com)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(تعطيل الدوام OR عطلة رسمية OR الأمانة العامة لمجلس الوزراء OR وزارة التربية) العراق ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.DECISIONS_PROD to listOf(
            FeedEndpoint(
                url = gn("(قرارات مجلس الوزراء OR جلسة مجلس الوزراء OR قرار حكومي OR الأمانة العامة لمجلس الوزراء) العراق (site:ina.iq OR site:cabinet.iq OR site:pmo.iq OR site:alsabaah.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(حكومة إقليم كردستان OR دائرة الإعلام والمعلومات OR مجلس وزراء الإقليم OR مشاريع أربيل) (site:gov.krd OR site:krg.org OR site:rudawarabia.net OR site:kurdistan24.net OR site:ina.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(الإنتاج الوطني OR المشاريع الوطنية OR الإنتاج الصناعي OR الإنتاج الزراعي OR إنتاج الطاقة OR مشاريع خدمية) العراق (site:ina.iq OR site:industry.gov.iq OR site:zeraa.gov.iq OR site:moelc.gov.iq OR site:moch.gov.iq OR site:oil.gov.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(قرارات مجلس الوزراء OR الإنتاج العراقي OR مشاريع العراق الخدمية) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.GOVERNORATES to listOf(
            FeedEndpoint(url = "https://www.ina.iq/rss.xml", forcedSource = inaSource, filterIraqOnly = true),
            FeedEndpoint(url = rudawSource.directRssUrl!!, forcedSource = rudawSource, filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24Source.directRssUrl!!, forcedSource = kurdistan24Source, filterIraqOnly = true),
            FeedEndpoint(
                url = gn("(بغداد OR البصرة OR أربيل OR الموصل OR نينوى OR صلاح الدين OR النجف OR كربلاء OR كركوك OR الأنبار OR السليمانية OR بابل OR ذي قار OR ديالى OR واسط OR ميسان OR المثنى OR الديوانية OR دهوك OR حلبجة) (site:ina.iq OR site:alsabaah.iq OR site:pmo.iq OR site:cabinet.iq OR site:gov.iq OR site:gov.krd)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(أربيل OR السليمانية OR دهوك OR حلبجة OR كركوك OR نينوى OR إقليم كردستان) (site:gov.krd OR site:rudawarabia.net OR site:kurdistan24.net OR site:shafaq.com)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(أخبار المحافظات العراقية OR بغداد OR البصرة OR أربيل OR الموصل OR صلاح الدين OR كربلاء OR النجف) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.ECONOMY to listOf(
            FeedEndpoint(
                url = gn("(اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي OR الدينار العراقي OR الموازنة العراقية OR وزارة المالية) (site:ina.iq OR site:oil.gov.iq OR site:cbi.iq OR site:mof.gov.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.TECH_PROJECTS to listOf(
            FeedEndpoint(
                url = gn("(طريق التنمية OR ميناء الفاو OR مشاريع العراق OR التحول الرقمي العراق OR الاتصالات العراق) (site:ina.iq OR site:moc.gov.iq OR site:mohesr.gov.iq OR site:cabinet.iq OR site:moch.gov.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(مشاريع العراق OR طريق التنمية OR تكنولوجيا العراق) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.SPORTS to listOf(
            FeedEndpoint(
                url = gn("(المنتخب العراقي OR دوري نجوم العراق OR الدوري العراقي OR الكرة العراقية) (site:ina.iq OR site:alsabaah.iq OR site:moys.gov.iq OR site:gov.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("(المنتخب العراقي OR دوري نجوم العراق OR الدوري العراقي) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            )
        ),
        NewsCategory.INTERNATIONAL to listOf(
            FeedEndpoint(
                url = gn("(العراق OR الخارجية العراقية) (site:mofa.gov.iq OR site:pmo.iq OR site:ina.iq)"),
                filterIraqOnly = true
            ),
            FeedEndpoint(
                url = gn("العراق (إيران OR تركيا OR السعودية OR أمريكا OR سوريا OR الأردن OR الكويت) ${TrustedSources.trustedSiteFilterQuery}"),
                filterIraqOnly = true
            ),
            FeedEndpoint(url = aljazeeraSource.directRssUrl!!, forcedSource = aljazeeraSource, filterIraqOnly = true),
            FeedEndpoint(url = skynewsSource.directRssUrl!!, forcedSource = skynewsSource, filterIraqOnly = true)
        )
    )

    val KURDISH_FEEDS: Map<NewsCategory, List<FeedEndpoint>> = mapOf(
        NewsCategory.BREAKING to listOf(
            FeedEndpoint(url = rudawSoraniSource.directRssUrl!!, forcedSource = rudawSoraniSource, filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24SoraniSource.directRssUrl!!, forcedSource = kurdistan24SoraniSource, filterIraqOnly = true),
            FeedEndpoint(url = krgDmiSource.directRssUrl!!, forcedSource = krgDmiSource, filterIraqOnly = true),
            FeedEndpoint(url = shafaqSource.directRssUrl!!, forcedSource = shafaqSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("(کوردستان OR هەولێر OR بەغدا OR عێراق) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.HOLIDAYS to listOf(
            FeedEndpoint(url = krgDmiSource.directRssUrl!!, forcedSource = krgDmiSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("(پشووی فەرمی OR پشوو OR ڕاگرتنی دەوام OR وەزارەتی پەروەردە) (هەرێمی کوردستان OR عێراق OR هەولێر OR سلێمانی OR دهۆک) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.DECISIONS_PROD to listOf(
            FeedEndpoint(url = krgDmiSource.directRssUrl!!, forcedSource = krgDmiSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("(ئەنجومەنی وەزیران OR حکومەتی هەرێم OR فەرمانگەی میدیا و زانیاری OR بڕیار OR پڕۆژە) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.GOVERNORATES to listOf(
            FeedEndpoint(url = rudawSoraniSource.directRssUrl!!, forcedSource = rudawSoraniSource, filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24SoraniSource.directRssUrl!!, forcedSource = kurdistan24SoraniSource, filterIraqOnly = true),
            FeedEndpoint(url = gn("(هەولێر OR سلێمانی OR دهۆک OR هەڵەبجە OR کەرکووک OR بەغدا OR مووسڵ OR بەسرە) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true)
        ),
        NewsCategory.ECONOMY to listOf(
            FeedEndpoint(url = gn("(ئابووری OR نەوت OR دینار OR بودجە OR مووچە) (عێراق OR کوردستان) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24SoraniSource.directRssUrl!!, forcedSource = kurdistan24SoraniSource, filterIraqOnly = true)
        ),
        NewsCategory.TECH_PROJECTS to listOf(
            FeedEndpoint(url = gn("(پڕۆژە OR تەکنەلۆژیا OR ڕێگای گەشەپێدان OR بەندەری فاو) (عێراق OR کوردستان) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = rudawSoraniSource.directRssUrl!!, forcedSource = rudawSoraniSource, filterIraqOnly = true)
        ),
        NewsCategory.SPORTS to listOf(
            FeedEndpoint(url = gn("(وەرزش OR هەڵبژاردەی عێراق OR خولی عێراق OR خولی کوردستان) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = rudawSoraniSource.directRssUrl!!, forcedSource = rudawSoraniSource, filterIraqOnly = true)
        ),
        NewsCategory.INTERNATIONAL to listOf(
            FeedEndpoint(url = gn("(عێراق OR هەرێمی کوردستان) (نێودەوڵەتی OR ئەمریکا OR تورکیا OR ئێران) ${TrustedSources.kurdishTrustedSitesQuery}"), filterIraqOnly = true),
            FeedEndpoint(url = kurdistan24SoraniSource.directRssUrl!!, forcedSource = kurdistan24SoraniSource, filterIraqOnly = true)
        )
    )

    fun feedsFor(category: NewsCategory, language: AppLanguage): List<FeedEndpoint> = when (language) {
        AppLanguage.ARABIC -> FEEDS[category].orEmpty()
        AppLanguage.KURDISH -> KURDISH_FEEDS[category].orEmpty()
    }

    fun unifiedFeedsFor(category: NewsCategory): List<FeedEndpoint> {
        val arabic = FEEDS[category].orEmpty()
        val kurdish = KURDISH_FEEDS[category].orEmpty()
        return (arabic + kurdish).distinctBy { it.url }
    }
}

enum class NewsCategory(
    val id: String,
    val labelAr: String,
    val labelCkb: String,
    val rssQuery: String?,
    val rssQueryCkb: String?
) {
    ALL("all", "الكل", "هەموو", null, null),
    BREAKING("brk", "عاجل العراق", "بەپەلەی عێراق", "العراق", "عێراق OR هەرێمی کوردستان"),
    HOLIDAYS("hol", "العطل الرسمية", "پشووە فەرمییەکان", "عطلة رسمية OR تعطيل الدوام العراق OR وزارة التربية العراقية", "پشووی فەرمی OR ڕاگرتنی دەوام کوردستان OR عێراق"),
    DECISIONS_PROD("dec", "القرارات والإنتاج", "بڕیار و بەرهەمهێنان", "قرارات مجلس الوزراء العراق OR الإنتاج الوطني العراق", "ئەنجومەنی وەزیران کوردستان OR عێراق OR پڕۆژە"),
    GOVERNORATES("gov", "أخبار المحافظات", "هەواڵی پارێزگاکان", "بغداد OR البصرة OR أربيل OR الموصل OR صلاح الدين OR النجف OR كربلاء", "هەولێر OR سلێمانی OR دهۆک OR هەڵەبجە OR کەرکووک OR بەغدا"),
    ECONOMY("eco", "اقتصاد ونفط", "ئابووری و نەوت", "اقتصاد العراق OR النفط العراقي OR البنك المركزي العراقي", "ئابووری OR نەوت OR بودجە عێراق کوردستان"),
    TECH_PROJECTS("tech", "تكنولوجيا ومشاريع", "تەکنەلۆژیا و پڕۆژەکان", "مشاريع العراق OR طريق التنمية OR تكنولوجيا العراق", "پڕۆژە OR تەکنەلۆژیا عێراق کوردستان"),
    SPORTS("spo", "رياضة عراقية", "وەرزشی عێراق", "المنتخب العراقي OR الدوري العراقي", "هەڵبژاردەی عێراق OR خولی کوردستان"),
    INTERNATIONAL("intl", "شأن دولي", "هەواڵی نێودەوڵەتی", "العراق إيران OR العراق تركيا OR العراق السعودية OR العراق أمريكا", "عێراق کوردستان نێودەوڵەتی");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> labelAr
        AppLanguage.KURDISH -> labelCkb
    }

    fun query(language: AppLanguage): String? = when (language) {
        AppLanguage.ARABIC -> rssQuery
        AppLanguage.KURDISH -> rssQueryCkb
    }

    companion object {
        fun fromId(id: String): NewsCategory = entries.find { it.id == id } ?: ALL
    }
}

data class IraqiProvince(
    val nameAr: String,
    val nameCkb: String,
    val lat: Double,
    val lon: Double
) {
    fun displayName(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> nameAr
        AppLanguage.KURDISH -> nameCkb
    }
}

object IraqiProvinces {
    val all: List<IraqiProvince> = listOf(
        IraqiProvince("صلاح الدين", "سەڵاحەدین", 34.6098, 43.6793),
        IraqiProvince("بغداد", "بەغدا", 33.3152, 44.3661),
        IraqiProvince("البصرة", "بەسرە", 30.5085, 47.7804),
        IraqiProvince("أربيل", "هەولێر", 36.1911, 44.0092),
        IraqiProvince("الموصل (نينوى)", "مووسڵ (نەینەوا)", 36.3489, 43.1577),
        IraqiProvince("النجف", "نەجەف", 32.0000, 44.3360),
        IraqiProvince("كربلاء", "کەربەلا", 32.6160, 44.0249),
        IraqiProvince("كركوك", "کەرکووک", 35.4669, 44.3929),
        IraqiProvince("الأنبار", "ئەنبار", 33.4271, 43.2954),
        IraqiProvince("السليمانية", "سلێمانی", 35.5558, 45.4351),
        IraqiProvince("بابل", "بابل", 32.4637, 44.4196),
        IraqiProvince("ذي قار", "زیقار", 31.0579, 46.2573),
        IraqiProvince("ديالى", "دیالە", 33.7540, 44.6052),
        IraqiProvince("واسط", "واست", 32.5128, 45.8182),
        IraqiProvince("ميسان", "میسان", 31.8356, 47.1448),
        IraqiProvince("المثنى", "موسەننا", 31.3320, 45.2944),
        IraqiProvince("القادسية (الديوانية)", "قادسیە (دیوانیە)", 31.9889, 44.9255),
        IraqiProvince("دهوك", "دهۆک", 36.8669, 42.9503),
        IraqiProvince("حلبجة", "هەڵەبجە", 35.1778, 45.9861)
    )

    fun findByName(name: String): IraqiProvince =
        all.find { it.nameAr == name || it.nameCkb == name }
            ?: all.find { it.nameAr.startsWith(name) || name.startsWith(it.nameAr.substringBefore(" ")) }
            ?: all.first()

    fun isWithinIraqBounds(lat: Double, lon: Double): Boolean =
        lat in 28.5..37.8 && lon in 38.5..49.0

    fun nearestProvince(lat: Double, lon: Double): IraqiProvince =
        all.minByOrNull { province ->
            val dLat = Math.toRadians(province.lat - lat)
            val dLon = Math.toRadians(province.lon - lon)
            val meanLat = Math.toRadians((province.lat + lat) / 2.0)
            val x = dLon * kotlin.math.cos(meanLat)
            x * x + dLat * dLat
        } ?: all.first()
}

enum class ReadingFontSize(
    val key: String,
    val labelAr: String,
    val labelCkb: String,
    val bodySp: Int
) {
    SMALL("sm", "صغير", "بچووک", 15),
    MEDIUM("md", "متوسط", "مامناوەند", 17),
    LARGE("lg", "كبير", "گەورە", 20);

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> labelAr
        AppLanguage.KURDISH -> labelCkb
    }

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
    val conditionAr: String,
    val weatherCode: Int? = null,
    val apparentTempCelsius: Int? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Int? = null,
    val isDay: Boolean = true,
    val isFromGpsLocation: Boolean = false
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
    val imageUrl: String? = null,
    val isSaved: Boolean = false,
    val languageCode: String = AppLanguage.ARABIC.code,
    val originalTitle: String = title,
    val originalSummary: String = summary,
    val originalLanguageCode: String = languageCode
) {
    fun relativeTimeAr(nowMs: Long = System.currentTimeMillis()): String =
        relativeTime(AppLanguage.ARABIC, nowMs)

    fun relativeTime(language: AppLanguage, nowMs: Long = System.currentTimeMillis()): String {
        val diffMinutes = ((nowMs - publishedEpochMs) / 60_000L).coerceAtLeast(1L)
        return when (language) {
            AppLanguage.ARABIC -> when {
                diffMinutes < 60L -> "منذ $diffMinutes د"
                diffMinutes < 1440L -> "منذ ${diffMinutes / 60L} س"
                else -> "منذ ${diffMinutes / 1440L} ي"
            }
            AppLanguage.KURDISH -> when {
                diffMinutes < 60L -> "پێش $diffMinutes خ"
                diffMinutes < 1440L -> "پێش ${diffMinutes / 60L} ک"
                else -> "پێش ${diffMinutes / 1440L} ڕ"
            }
        }
    }
}
