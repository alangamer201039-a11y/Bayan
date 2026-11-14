package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherClient
import com.example.model.AppLanguage
import com.example.model.IraqRelevanceFilter
import com.example.model.IraqiProvinces
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.OfficialFeeds
import com.example.model.ProvinceWeather
import com.example.model.SourceTrustLevel
import com.example.model.TrustedSources
import com.example.ui.LocalAppLanguage
import com.example.ui.LocalLocalizedResources
import com.example.ui.components.NewsArticleCard
import com.example.ui.components.WeatherWidget
import com.example.ui.resourcesForLanguage
import com.example.ui.theme.BayanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("بيان العراق", appName)
  }

  @Test
  fun `Sorani Kurdish localization resolves values-ckb strings, provinces, categories, and weather`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ckbRes = context.resourcesForLanguage(AppLanguage.KURDISH)
    val arRes = context.resourcesForLanguage(AppLanguage.ARABIC)

    assertEquals("سەرەکی", ckbRes.getString(R.string.tab_home))
    assertEquals("بەپەلە", ckbRes.getString(R.string.tab_breaking))
    assertEquals("پاشەکەوت", ckbRes.getString(R.string.tab_saved))
    assertEquals("ڕێکخستن", ckbRes.getString(R.string.tab_settings))
    assertEquals("زمانی بەرنامە", ckbRes.getString(R.string.settings_language_title))
    assertEquals("الرئيسية", arRes.getString(R.string.tab_home))

    assertEquals("پشووە فەرمییەکان", NewsCategory.HOLIDAYS.label(AppLanguage.KURDISH))
    assertEquals("بڕیار و بەرهەمهێنان", NewsCategory.DECISIONS_PROD.label(AppLanguage.KURDISH))
    assertEquals("هەواڵی پارێزگاکان", NewsCategory.GOVERNORATES.label(AppLanguage.KURDISH))

    assertEquals(19, IraqiProvinces.all.size)
    assertTrue(IraqiProvinces.all.all { it.nameCkb.isNotBlank() })
    assertEquals("هەولێر", IraqiProvinces.findByName("أربيل").displayName(AppLanguage.KURDISH))
    assertEquals("سەڵاحەدین", IraqiProvinces.findByName("صلاح الدين").displayName(AppLanguage.KURDISH))
    assertEquals("سلێمانی", IraqiProvinces.findByName("السليمانية").displayName(AppLanguage.KURDISH))
    assertEquals("هەڵەبجە", IraqiProvinces.findByName("حلبجة").displayName(AppLanguage.KURDISH))

    assertEquals("ئاسمانی ساماڵ", WeatherClient.weatherCodeToLocalized(0, AppLanguage.KURDISH))
    assertEquals("باران", WeatherClient.weatherCodeToLocalized(61, AppLanguage.KURDISH))
  }

  @Test
  fun `Kurdish Sorani feeds include Rudaw Sorani, Kurdistan 24 Sorani, and KRG DMI`() {
    val kurdishBreaking = OfficialFeeds.feedsFor(NewsCategory.BREAKING, AppLanguage.KURDISH)
    assertTrue(kurdishBreaking.any { it.url.contains("rudaw.net/rss") })
    assertTrue(kurdishBreaking.any { it.url.contains("kurdistan24.net/ckb/rss") })
    assertTrue(kurdishBreaking.any { it.url.contains("gov.krd/ckb/rss") })

    val displayedKurdish = TrustedSources.displayedForLanguage(AppLanguage.KURDISH)
    assertTrue(displayedKurdish.any { it.id == "rudaw_sorani" && it.nameCkb.contains("ڕووداو") })
    assertTrue(displayedKurdish.any { it.id == "kurdistan24_sorani" && it.nameCkb.contains("کوردستان ٢٤") })
    assertTrue(displayedKurdish.any { it.id == "krg_dmi" && it.nameCkb.contains("فەرمانگەی میدیا و زانیاری") })

    assertTrue(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "ئەنجومەنی وەزیرانی هەرێم پشووی فەرمی لە هەولێر و سلێمانی ڕاگەیاند",
        summary = "بەپێی بڕیاری فەرمانگەی میدیا و زانیاری دەوام ڕادەگیرێت.",
        sourceDomain = "gov.krd"
      )
    )
    assertFalse(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "ئەنجامی خولی تۆپی پێی ئینگلتەرا لە لەندەن",
        summary = "یارییەکانی هەفتەی نوێ لە لەندەن و مانچێستەر بەڕێوەچوون.",
        sourceDomain = "rudaw.net"
      )
    )

    val sampleKurdishRss = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <item>
            <title>هەولێر پڕۆژەیەکی نوێی خزمەتگوزاری جێبەجێ دەکات</title>
            <link>https://www.rudaw.net/sorani/kurdistan/12345</link>
            <pubDate>Fri, 13 Nov 2026 10:00:00 GMT</pubDate>
            <description>لە شاری هەولێر پڕۆژەی نوێی ڕێگاوبان دەستی پێکرد.</description>
          </item>
        </channel>
      </rss>
    """.trimIndent()
    val rudawSorani = TrustedSources.all.first { it.id == "rudaw_sorani" }
    val parsed = RssNewsService().parseRssXml(
      xml = sampleKurdishRss,
      category = NewsCategory.GOVERNORATES,
      forcedSource = rudawSorani,
      language = AppLanguage.KURDISH
    )
    assertEquals(1, parsed.size)
    assertEquals(AppLanguage.KURDISH.code, parsed.first().languageCode)
    assertEquals(SourceTrustLevel.VERIFIED, parsed.first().trustLevel)
    assertEquals("تۆڕی میدیایی ڕووداو (سۆرانی)", parsed.first().source)
  }

  @Test
  fun `IraqiProvinces contains all 19 Iraqi governorates`() {
    val expected = listOf(
      "صلاح الدين", "بغداد", "البصرة", "أربيل", "الموصل (نينوى)",
      "النجف", "كربلاء", "كركوك", "الأنبار", "السليمانية",
      "بابل", "ذي قار", "ديالى", "واسط", "ميسان",
      "المثنى", "القادسية (الديوانية)", "دهوك", "حلبجة"
    )
    assertEquals(19, IraqiProvinces.all.size)
    assertEquals(expected, IraqiProvinces.all.map { it.nameAr })
  }

  @Test
  fun `official FEEDS contains INA and government sources across categories`() {
    val breakingFeeds = OfficialFeeds.FEEDS[NewsCategory.BREAKING].orEmpty()
    assertTrue(breakingFeeds.any { it.url.contains("ina.iq") })
    assertTrue(breakingFeeds.any { it.url.contains("gov.iq") })

    val (mofaTrust, mofaDomain) = TrustedSources.classify("وزارة الخارجية", "", "https://mofa.gov.iq/news/1")
    assertEquals(SourceTrustLevel.OFFICIAL, mofaTrust)
    assertEquals("mofa.gov.iq", mofaDomain)
  }

  @Test
  fun `holidays, decisions, and governorates categories and feeds are configured`() {
    assertEquals("العطل الرسمية", NewsCategory.HOLIDAYS.labelAr)
    assertEquals("القرارات والإنتاج", NewsCategory.DECISIONS_PROD.labelAr)
    assertEquals("أخبار المحافظات", NewsCategory.GOVERNORATES.labelAr)

    val holidayFeeds = OfficialFeeds.FEEDS[NewsCategory.HOLIDAYS].orEmpty()
    assertTrue(holidayFeeds.any { it.url.contains("cabinet.iq") })
    assertTrue(holidayFeeds.any { it.url.contains("epedu.gov.iq") && it.url.contains("moedu.gov.iq") })

    val decisionFeeds = OfficialFeeds.FEEDS[NewsCategory.DECISIONS_PROD].orEmpty()
    assertTrue(decisionFeeds.any { it.url.contains("cabinet.iq") && it.url.contains("ina.iq") })
    assertTrue(decisionFeeds.any {
      it.url.contains("industry.gov.iq") &&
        it.url.contains("zeraa.gov.iq") &&
        it.url.contains("moelc.gov.iq")
    })

    val officialDomains = listOf(
      "https://cabinet.iq/news/10",
      "https://epedu.gov.iq/news/20",
      "https://moedu.gov.iq/news/21",
      "https://industry.gov.iq/news/30",
      "https://zeraa.gov.iq/news/40",
      "https://moelc.gov.iq/news/50",
      "https://moch.gov.iq/news/60"
    )
    officialDomains.forEach { url ->
      val (trust, _) = TrustedSources.classify("مصدر إخباري", "", url)
      assertEquals("Expected OFFICIAL trust for $url", SourceTrustLevel.OFFICIAL, trust)
    }
  }

  @Test
  fun `Kurdistan Region Arabic official and verified sources are integrated across feeds`() {
    val sourceIds = TrustedSources.all.map { it.id }
    assertTrue(sourceIds.containsAll(listOf("rudaw", "kurdistan24", "krg_dmi")))

    val (krgTrust, krgDomain) = TrustedSources.classify(
      "دائرة الإعلام والمعلومات",
      "https://gov.krd/dmi",
      "https://gov.krd/arabic/news/1"
    )
    assertEquals(SourceTrustLevel.OFFICIAL, krgTrust)
    assertEquals("gov.krd", krgDomain)

    val (rudawTrust, rudawDomain) = TrustedSources.classify(
      "شبكة رووداو الإعلامية",
      "https://www.rudawarabia.net",
      "https://www.rudawarabia.net/arabic/kurdistan/1"
    )
    assertEquals(SourceTrustLevel.VERIFIED, rudawTrust)
    assertEquals("rudawarabia.net", rudawDomain)

    val (k24Trust, k24Domain) = TrustedSources.classify(
      "كوردستان 24",
      "https://www.kurdistan24.net/ar",
      "https://www.kurdistan24.net/ar/story/123"
    )
    assertEquals(SourceTrustLevel.VERIFIED, k24Trust)
    assertEquals("kurdistan24.net", k24Domain)

    val holidayFeeds = OfficialFeeds.FEEDS[NewsCategory.HOLIDAYS].orEmpty()
    assertTrue(holidayFeeds.any {
      it.url.contains("gov.krd") &&
        it.url.contains("rudawarabia.net") &&
        it.url.contains("kurdistan24.net")
    })

    val decisionFeeds = OfficialFeeds.FEEDS[NewsCategory.DECISIONS_PROD].orEmpty()
    assertTrue(decisionFeeds.any {
      it.url.contains("gov.krd") &&
        it.url.contains("rudawarabia.net") &&
        it.url.contains("kurdistan24.net")
    })

    val govFeeds = OfficialFeeds.FEEDS[NewsCategory.GOVERNORATES].orEmpty()
    assertTrue(govFeeds.any { it.url.contains("rudawarabia.net") })
    assertTrue(govFeeds.any { it.url.contains("kurdistan24.net") })
    assertTrue(govFeeds.any { it.url.contains("gov.krd") })

    assertTrue(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "دائرة الإعلام والمعلومات تعلن جدول العطل الرسمية في الإقليم",
        summary = "بيان رسمي حول عطلة نهاية الأسبوع والمناسبات الرسمية.",
        sourceDomain = "gov.krd"
      )
    )
  }

  @Test
  fun `IraqRelevanceFilter rejects foreign-only news and keeps Iraq news`() {
    assertFalse(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "ارتفاع مؤشرات البورصة الأوروبية في ختام التداولات",
        summary = "سجلت الأسهم الأوروبية مكاسب جديدة اليوم في باريس ولندن.",
        sourceDomain = "ina.iq"
      )
    )
    assertTrue(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "تأهيل شبكة الطرق الرئيسية في البصرة وبغداد",
        summary = "أعلنت وزارة الإعمار خطة جديدة لتطوير الممرات المرورية.",
        sourceDomain = "ina.iq"
      )
    )
    assertTrue(
      IraqRelevanceFilter.isRelatedToIraq(
        title = "إعلان تعطيل الدوام الرسمي ليوم غد في عموم الدوائر",
        summary = "أعلنت الأمانة العامة لمجلس الوزراء تعطيل الدوام الرسمي.",
        sourceDomain = "cabinet.iq"
      )
    )
  }

  @Test
  fun `parseRssXml extracts official source and trust level`() {
    val sampleRss = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0">
        <channel>
          <item>
            <title>بغداد تفتتح مشروعاً جديداً للنقل الحضري - وكالة الأنباء العراقية</title>
            <link>https://news.google.com/rss/articles/123</link>
            <source url="https://www.ina.iq">وكالة الأنباء العراقية</source>
            <pubDate>Fri, 13 Nov 2026 10:00:00 GMT</pubDate>
            <description>&lt;p&gt;أعلنت الجهات المعنية في بغداد عن إطلاق خطة لتحديث خطوط النقل العام.&lt;/p&gt;</description>
          </item>
        </channel>
      </rss>
    """.trimIndent()

    val items = RssNewsService().parseRssXml(sampleRss, NewsCategory.GOVERNORATES)
    assertEquals(1, items.size)
    val item = items.first()
    assertEquals("بغداد تفتتح مشروعاً جديداً للنقل الحضري", item.title)
    assertEquals("وكالة الأنباء العراقية", item.source)
    assertEquals(SourceTrustLevel.OFFICIAL, item.trustLevel)
    assertEquals("ina.iq", item.sourceDomain)
    assertTrue(item.summary.contains("إطلاق خطة لتحديث خطوط النقل العام"))
  }

  @Test
  fun `weatherCodeToArabic maps clear sky correctly`() {
    assertEquals("سماء صافية", WeatherClient.weatherCodeToArabic(0))
  }

  @Test
  fun `NewsTranslationFilter detects Arabic vs Sorani Kurdish accurately`() {
    assertEquals(
      AppLanguage.ARABIC,
      com.example.data.remote.NewsTranslationFilter.detectLanguage(
        "الأمانة العامة لمجلس الوزراء تعلن تعطيل الدوام الرسمي في بغداد"
      )
    )
    assertEquals(
      AppLanguage.KURDISH,
      com.example.data.remote.NewsTranslationFilter.detectLanguage(
        "ئەنجومەنی وەزیران پشووی فەرمی لە هەولێر و سلێمانی ڕاگەیاند"
      )
    )
  }

  @Test
  fun `NewsTranslationFilter translates official terms consistently in both directions`() = kotlinx.coroutines.runBlocking {
    val filter = com.example.data.remote.NewsTranslationFilter(enableNetworkTranslation = false)

    val arabicArticle = com.example.model.NewsArticle(
      id = "ar1",
      categoryId = NewsCategory.HOLIDAYS.id,
      isBreaking = false,
      source = "الأمانة العامة لمجلس الوزراء",
      sourceDomain = "cabinet.iq",
      trustLevel = SourceTrustLevel.OFFICIAL,
      publishedEpochMs = 1_700_000_000_000L,
      title = "الأمانة العامة لمجلس الوزراء تعلن تعطيل الدوام الرسمي في بغداد",
      summary = "وزارة التربية تؤكد أن القرار يشمل عطلة رسمية في عموم المحافظات.",
      link = "https://cabinet.iq/news/100",
      languageCode = AppLanguage.ARABIC.code
    )

    val toKurdish = filter.translateArticle(arabicArticle, AppLanguage.KURDISH)
    assertEquals(AppLanguage.KURDISH.code, toKurdish.languageCode)
    assertTrue(toKurdish.title.contains("ئەمیندارێتی گشتی ئەنجومەنی وەزیران"))
    assertTrue(toKurdish.title.contains("ڕاگرتنی دەوامی فەرمی"))
    assertTrue(toKurdish.title.contains("بەغدا"))
    assertTrue(toKurdish.summary.contains("وەزارەتی پەروەردە"))
    assertTrue(toKurdish.summary.contains("پشووی فەرمی"))
    assertEquals(arabicArticle.title, toKurdish.originalTitle)
    assertEquals(AppLanguage.ARABIC.code, toKurdish.originalLanguageCode)

    // Re-translating back to Arabic must preserve the clean original Arabic content
    val backToArabic = filter.translateArticle(toKurdish, AppLanguage.ARABIC)
    assertEquals(AppLanguage.ARABIC.code, backToArabic.languageCode)
    assertEquals(arabicArticle.title, backToArabic.title)

    val kurdishArticle = com.example.model.NewsArticle(
      id = "ckb1",
      categoryId = NewsCategory.DECISIONS_PROD.id,
      isBreaking = false,
      source = "فەرمانگەی میدیا و زانیاری (KRG)",
      sourceDomain = "gov.krd",
      trustLevel = SourceTrustLevel.OFFICIAL,
      publishedEpochMs = 1_700_000_000_000L,
      title = "فەرمانگەی میدیا و زانیاری و حکومەتی هەرێمی کوردستان لە هەولێر ڕایگەیاند",
      summary = "وەزارەتی پەروەردە دەربارەی پشووی فەرمی و ڕاگرتنی دەوامی فەرمی بڕیاری دا.",
      link = "https://gov.krd/ckb/news/200",
      languageCode = AppLanguage.KURDISH.code
    )

    val toArabic = filter.translateArticle(kurdishArticle, AppLanguage.ARABIC)
    assertEquals(AppLanguage.ARABIC.code, toArabic.languageCode)
    assertTrue(toArabic.title.contains("دائرة الإعلام والمعلومات"))
    assertTrue(toArabic.title.contains("حكومة إقليم كردستان"))
    assertTrue(toArabic.title.contains("أربيل"))
    assertTrue(toArabic.summary.contains("وزارة التربية"))
    assertTrue(toArabic.summary.contains("عطلة رسمية"))
    assertTrue(toArabic.summary.contains("تعطيل الدوام الرسمي"))
  }

  @Test
  fun `NewsTranslationFilter enforces official terminology and parses Google Translate JSON`() {
    val filter = com.example.data.remote.NewsTranslationFilter(enableNetworkTranslation = false)
    val enforcedCkb = filter.enforceOfficialTerms(
      translatedText = "سکرتاریەتی گشتی ئەنجومەنی وەزیران پشووێکی فەرمی لە هه‌ولێر راگەیاند",
      originalText = "الأمانة العامة لمجلس الوزراء تعلن عطلة رسمية في أربيل",
      targetLanguage = AppLanguage.KURDISH
    )
    assertTrue(enforcedCkb.contains("ئەمیندارێتی گشتی ئەنجومەنی وەزیران"))
    assertTrue(enforcedCkb.contains("پشووی فەرمی"))
    assertTrue(enforcedCkb.contains("هەولێر"))

    val sampleJson = """[[["ئەمیندارێتی گشتی ئەنجومەنی وەزیران ","الأمانة العامة لمجلس الوزراء",null,null,1],["پشووی فەرمی ڕادەگەیەنێت","تعلن عطلة رسمية",null,null,1]],null,"ar"]"""
    val parsed = filter.parseGoogleTranslateJson(sampleJson)
    assertEquals("ئەمیندارێتی گشتی ئەنجومەنی وەزیران پشووی فەرمی ڕادەگەیەنێت", parsed)
  }

  @Test
  fun `OfficialFeeds unifiedFeedsFor merges Arabic and Kurdish official sources`() {
    val unifiedBreaking = OfficialFeeds.unifiedFeedsFor(NewsCategory.BREAKING)
    assertTrue(unifiedBreaking.any { it.url.contains("ina.iq") })
    assertTrue(unifiedBreaking.any { it.url.contains("rudaw.net/rss") })
    assertTrue(unifiedBreaking.any { it.url.contains("kurdistan24.net/ckb/rss") })
    assertTrue(unifiedBreaking.any { it.url.contains("gov.krd/ckb/rss") })

    val unifiedHolidays = OfficialFeeds.unifiedFeedsFor(NewsCategory.HOLIDAYS)
    assertTrue(unifiedHolidays.any { it.url.contains("cabinet.iq") })
    assertTrue(unifiedHolidays.any { it.url.contains("epedu.gov.iq") })
    assertTrue(unifiedHolidays.any { it.url.contains("gov.krd/ckb/rss") })
  }

  @Test
  fun `parseRssXml extracts official images from media content, enclosure, thumbnail, and HTML img tags`() {
    val rssWithImages = """
      <?xml version="1.0" encoding="UTF-8"?>
      <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/">
        <channel>
          <item>
            <title>مجلس الوزراء يقر مشروعاً وطنياً في بغداد</title>
            <link>https://www.ina.iq/news/1</link>
            <media:content url="https://www.ina.iq/uploads/project.jpg?w=800&amp;q=80" medium="image" type="image/jpeg" />
            <description>تفاصيل المشروع الخدمي الجديد في بغداد.</description>
          </item>
          <item>
            <title>وزارة التربية تعلن جدول الامتحانات في البصرة</title>
            <link>https://epedu.gov.iq/news/2</link>
            <enclosure url="https://epedu.gov.iq/media/exams.png" type="image/png" />
            <description>توجيهات رسمية للطلبة في عموم المحافظات.</description>
          </item>
          <item>
            <title>حكومة إقليم كردستان تفتتح طريقاً في أربيل</title>
            <link>https://gov.krd/news/3</link>
            <media:thumbnail url="/images/erbil_road.webp" />
            <description>مشروع خدمي متكامل في محافظة أربيل.</description>
          </item>
          <item>
            <title>بيان رسمي من وزارة الكهرباء حول الإنتاج في ذي قار</title>
            <link>https://moelc.gov.iq/news/4</link>
            <description>&lt;img src="https://moelc.gov.iq/1x1.gif" /&gt;&lt;img src="https://moelc.gov.iq/assets/station.jpg" /&gt;&lt;p&gt;زيادة ساعات التجهيز في ذي قار.&lt;/p&gt;</description>
          </item>
          <item>
            <title>إعلان عطلة رسمية ليوم الخميس في العراق</title>
            <link>https://cabinet.iq/news/5</link>
            <description>الأمانة العامة لمجلس الوزراء تعلن تعطيل الدوام الرسمي.</description>
          </item>
        </channel>
      </rss>
    """.trimIndent()

    val items = RssNewsService().parseRssXml(rssWithImages, NewsCategory.DECISIONS_PROD)
    assertEquals(5, items.size)
    assertEquals("https://www.ina.iq/uploads/project.jpg?w=800&q=80", items[0].imageUrl)
    assertEquals("https://epedu.gov.iq/media/exams.png", items[1].imageUrl)
    assertEquals("https://gov.krd/images/erbil_road.webp", items[2].imageUrl)
    assertEquals("https://moelc.gov.iq/assets/station.jpg", items[3].imageUrl)
    assertNull(items[4].imageUrl)
  }

  @Test
  fun `NewsArticleCard renders image slot conditionally based on imageUrl`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val res = context.resourcesForLanguage(AppLanguage.ARABIC)

    val withImage = NewsArticle(
      id = "with_img",
      categoryId = NewsCategory.DECISIONS_PROD.id,
      isBreaking = false,
      source = "وكالة الأنباء العراقية",
      sourceDomain = "ina.iq",
      trustLevel = SourceTrustLevel.OFFICIAL,
      publishedEpochMs = 1_700_000_000_000L,
      title = "بغداد تطلق المرحلة الجديدة من طريق التنمية",
      summary = "تفاصيل المشروع الوطني الاستراتيجي في العراق.",
      link = "https://www.ina.iq/1",
      imageUrl = "android.resource://${context.packageName}/${R.drawable.img_iraq_editorial_banner}"
    )
    val withoutImage = NewsArticle(
      id = "no_img",
      categoryId = NewsCategory.HOLIDAYS.id,
      isBreaking = false,
      source = "الأمانة العامة لمجلس الوزراء",
      sourceDomain = "cabinet.iq",
      trustLevel = SourceTrustLevel.OFFICIAL,
      publishedEpochMs = 1_700_000_000_000L,
      title = "تعطيل الدوام الرسمي يوم غد في عموم المحافظات",
      summary = "أعلنت الأمانة العامة لمجلس الوزراء أن يوم غد عطلة رسمية.",
      link = "https://cabinet.iq/2",
      imageUrl = null
    )

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalAppLanguage provides AppLanguage.ARABIC,
        LocalLocalizedResources provides res
      ) {
        BayanTheme {
          androidx.compose.foundation.layout.Column {
            NewsArticleCard(
              article = withImage,
              onArticleClick = {},
              onToggleSave = {}
            )
            NewsArticleCard(
              article = withoutImage,
              onArticleClick = {},
              onToggleSave = {}
            )
          }
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("article_card_with_img").assertIsDisplayed()
    composeTestRule.onNodeWithTag("article_image_with_img", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("article_card_no_img").assertIsDisplayed()
    composeTestRule.onNodeWithTag("article_image_no_img", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `IraqiProvinces validates Iraq bounds and resolves nearest governorate from user coordinates`() {
    assertTrue(IraqiProvinces.isWithinIraqBounds(33.31, 44.36)) // Baghdad
    assertTrue(IraqiProvinces.isWithinIraqBounds(36.19, 44.01)) // Erbil
    assertTrue(IraqiProvinces.isWithinIraqBounds(30.51, 47.78)) // Basra
    assertFalse(IraqiProvinces.isWithinIraqBounds(37.42, -122.08)) // Mountain View
    assertFalse(IraqiProvinces.isWithinIraqBounds(51.50, -0.12)) // London

    assertEquals("بغداد", IraqiProvinces.nearestProvince(33.32, 44.37).nameAr)
    assertEquals("أربيل", IraqiProvinces.nearestProvince(36.20, 44.02).nameAr)
    assertEquals("البصرة", IraqiProvinces.nearestProvince(30.52, 47.77).nameAr)
  }

  @Test
  fun `WeatherWidget displays GPS-detected Iraq location weather and controls cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val res = context.resourcesForLanguage(AppLanguage.ARABIC)
    val sampleWeather = ProvinceWeather(
      provinceName = "بغداد",
      temperatureCelsius = 27,
      conditionAr = "سماء صافية",
      weatherCode = 0,
      apparentTempCelsius = 26,
      humidityPercent = 32,
      windSpeedKmh = 14,
      isDay = true,
      isFromGpsLocation = true
    )

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalAppLanguage provides AppLanguage.ARABIC,
        LocalLocalizedResources provides res
      ) {
        BayanTheme {
          WeatherWidget(
            weather = sampleWeather,
            selectedProvince = "بغداد",
            isWeatherLoading = false,
            isDetectingLocation = false,
            onLocateGranted = {},
            onLocationPermissionDenied = {},
            onRefreshWeather = {},
            onProvinceClick = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("weather_widget").assertIsDisplayed()
    composeTestRule.onNodeWithTag("weather_widget_temp", useUnmergedTree = true).assertTextContains("27°C")
    composeTestRule.onNodeWithTag("weather_widget_condition", useUnmergedTree = true).assertTextContains("سماء صافية")
    composeTestRule.onNodeWithTag("weather_widget_location", useUnmergedTree = true).assertTextContains("موقعك الحالي • بغداد")
    composeTestRule.onNodeWithTag("weather_widget_locate_button", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("weather_widget_refresh_button", useUnmergedTree = true).assertIsDisplayed()
  }
}
