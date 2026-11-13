package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherClient
import com.example.model.IraqRelevanceFilter
import com.example.model.NewsCategory
import com.example.model.OfficialFeeds
import com.example.model.SourceTrustLevel
import com.example.model.TrustedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("بيان العراق", appName)
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
}
