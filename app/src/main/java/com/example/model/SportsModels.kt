package com.example.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class SportsFilter(val key: String) {
    ALL("all"),
    SCHEDULE("schedule"),
    RESULTS("results")
}

enum class MatchStatus(val key: String) {
    SCHEDULED("scheduled"),
    LIVE("live"),
    FINISHED("finished"),
    POSTPONED("postponed");

    companion object {
        fun fromKey(key: String): MatchStatus = entries.find { it.key == key } ?: SCHEDULED
    }
}

enum class LineupSourceType(val key: String) {
    OFFICIAL("official"),
    EXPECTED("expected"),
    UNAVAILABLE("unavailable");

    companion object {
        fun fromKey(key: String): LineupSourceType = entries.find { it.key == key } ?: UNAVAILABLE
    }
}

enum class PlayerRole(val key: String) {
    GOALKEEPER("gk"),
    DEFENDER("df"),
    MIDFIELDER("mf"),
    FORWARD("fw");

    companion object {
        fun fromPosition(raw: String): PlayerRole {
            val lower = raw.trim().lowercase(Locale.ROOT)
            return when {
                lower.contains("goal") || lower == "gk" || lower == "g" || lower.contains("حارس") -> GOALKEEPER
                lower.contains("back") || lower.contains("def") || lower == "df" || lower == "d" ||
                    lower.contains("cb") || lower.contains("lb") || lower.contains("rb") || lower.contains("wing-back") -> DEFENDER
                lower.contains("mid") || lower == "mf" || lower == "m" || lower.contains("cm") ||
                    lower.contains("dm") || lower.contains("am") -> MIDFIELDER
                lower.contains("forw") || lower.contains("strik") || lower.contains("wing") ||
                    lower.contains("att") || lower == "fw" || lower == "f" || lower == "st" || lower == "cf" -> FORWARD
                else -> MIDFIELDER
            }
        }

        fun fromKey(key: String): PlayerRole = entries.find { it.key == key } ?: MIDFIELDER
    }
}

data class LineupPlayer(
    val name: String,
    val number: String = "",
    val positionLabel: String = "",
    val role: PlayerRole = PlayerRole.MIDFIELDER,
    val isStarter: Boolean = true
)

data class TeamLineup(
    val teamNameRaw: String,
    val formation: String = "",
    val sourceType: LineupSourceType = LineupSourceType.UNAVAILABLE,
    val starters: List<LineupPlayer> = emptyList(),
    val substitutes: List<LineupPlayer> = emptyList()
) {
    val hasPlayers: Boolean
        get() = starters.isNotEmpty() || substitutes.isNotEmpty()
}

data class MatchStatRow(
    val labelKey: String,
    val labelAr: String,
    val labelCkb: String,
    val homeValue: String,
    val awayValue: String
) {
    fun localizedLabel(language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> labelAr
        AppLanguage.KURDISH -> labelCkb
    }
}

data class MatchGoalEvent(
    val minute: String,
    val scorer: String,
    val isHomeTeam: Boolean,
    val eventType: String = "goal"
)

data class SportsMatch(
    val id: String,
    val provider: String,
    val competitionRaw: String,
    val homeTeamRaw: String,
    val awayTeamRaw: String,
    val homeTeamId: String? = null,
    val awayTeamId: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val kickoffEpochMs: Long,
    val status: MatchStatus,
    val statusDetail: String = "",
    val venue: String = "",
    val roundLabel: String = "",
    val homeLineup: TeamLineup? = null,
    val awayLineup: TeamLineup? = null,
    val stats: List<MatchStatRow> = emptyList(),
    val goalEvents: List<MatchGoalEvent> = emptyList(),
    val isNationalTeamMatch: Boolean = false
) {
    fun hasAnyLineup(): Boolean =
        (homeLineup?.hasPlayers == true) || (awayLineup?.hasPlayers == true)

    fun lineupSourceType(): LineupSourceType {
        val types = listOfNotNull(homeLineup?.sourceType, awayLineup?.sourceType)
        return when {
            LineupSourceType.OFFICIAL in types -> LineupSourceType.OFFICIAL
            LineupSourceType.EXPECTED in types -> LineupSourceType.EXPECTED
            else -> LineupSourceType.UNAVAILABLE
        }
    }

    fun localizedCompetition(language: AppLanguage): String =
        IraqSportsLocalizer.localizeCompetition(competitionRaw, language)

    fun localizedHomeTeam(language: AppLanguage): String =
        IraqSportsLocalizer.localizeTeam(homeTeamRaw, language)

    fun localizedAwayTeam(language: AppLanguage): String =
        IraqSportsLocalizer.localizeTeam(awayTeamRaw, language)

    fun homeInitials(): String =
        homeTeamRaw.trim().take(2).uppercase(Locale.ROOT).ifBlank { "H" }

    fun awayInitials(): String =
        awayTeamRaw.trim().take(2).uppercase(Locale.ROOT).ifBlank { "A" }
}

object IraqSportsLocalizer {
    val BAGHDAD_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Baghdad")

    private val teamMap: Map<String, Pair<String, String>> = mapOf(
        "iraq" to ("منتخب العراق" to "هەڵبژاردەی عێراق"),
        "al-shorta" to ("الشرطة" to "شورتە"),
        "al shorta" to ("الشرطة" to "شورتە"),
        "shorta" to ("الشرطة" to "شورتە"),
        "al-quwa al-jawiya" to ("القوة الجوية" to "هێزی ئاسمانی"),
        "al quwa al jawiya" to ("القوة الجوية" to "هێزی ئاسمانی"),
        "al-jawiya" to ("القوة الجوية" to "هێزی ئاسمانی"),
        "al-zawraa" to ("الزوراء" to "زەورا"),
        "al zawraa" to ("الزوراء" to "زەورا"),
        "zawraa" to ("الزوراء" to "زەورا"),
        "al-talaba" to ("الطلبة" to "تەلەبە"),
        "al talaba" to ("الطلبة" to "تەلەبە"),
        "erbil" to ("أربيل" to "هەولێر"),
        "erbil sc" to ("نادي أربيل" to "یانەی هەولێر"),
        "duhok" to ("دهوك" to "دهۆک"),
        "dohuk" to ("دهوك" to "دهۆک"),
        "zakho" to ("زاخو" to "زاخۆ"),
        "newroz" to ("نوروز" to "نەورۆز"),
        "al-minaa" to ("الميناء" to "مینا"),
        "al minaa" to ("الميناء" to "مینا"),
        "al-najaf" to ("النجف" to "نەجەف"),
        "al najaf" to ("النجف" to "نەجەف"),
        "najaf" to ("النجف" to "نەجەف"),
        "karbala" to ("كربلاء" to "کەربەلا"),
        "karbalaa" to ("كربلاء" to "کەربەلا"),
        "naft al-basra" to ("نفط البصرة" to "نەوتی بەسرە"),
        "naft al basra" to ("نفط البصرة" to "نەوتی بەسرە"),
        "al-kahrabaa" to ("الكهرباء" to "کارەبا"),
        "al kahrabaa" to ("الكهرباء" to "کارەبا"),
        "al-hudood" to ("الحدود" to "حدود"),
        "al hudood" to ("الحدود" to "حدود"),
        "al-karkh" to ("الكرخ" to "کەرخ"),
        "al karkh" to ("الكرخ" to "کەرخ"),
        "al-qasim" to ("القاسم" to "قاسم"),
        "al qasim" to ("القاسم" to "قاسم"),
        "diyala" to ("ديالى" to "دیالە"),
        "al-karma" to ("الكرمة" to "کەرمە"),
        "al karma" to ("الكرمة" to "کەرمە"),
        "naft maysan" to ("نفط ميسان" to "نەوتی میسان"),
        "amanat baghdad" to ("أمانة بغداد" to "ئەمانەی بەغدا"),
        "al-naft" to ("النفط" to "نەوت"),
        "al naft" to ("النفط" to "نەوت"),
        "naft al-wasat" to ("نفط الوسط" to "نەوتی ناوەڕاست"),
        "al-mosul" to ("الموصل" to "مووسڵ"),
        "al-gharraf" to ("الغراف" to "غەڕاف"),
        "masafi al-wasat" to ("مصافي الوسط" to "پاڵاوگەکانی ناوەڕاست"),
        "jordan" to ("الأردن" to "ئوردن"),
        "south korea" to ("كوريا الجنوبية" to "کۆریای باشوور"),
        "korea republic" to ("كوريا الجنوبية" to "کۆریای باشوور"),
        "oman" to ("عُمان" to "عوممان"),
        "kuwait" to ("الكويت" to "کوێت"),
        "palestine" to ("فلسطين" to "فەلەستین"),
        "saudi arabia" to ("السعودية" to "سعوودیە"),
        "qatar" to ("قطر" to "قەتەر"),
        "iran" to ("إيران" to "ئێران"),
        "ir iran" to ("إيران" to "ئێران"),
        "japan" to ("اليابان" to "ژاپۆن"),
        "united arab emirates" to ("الإمارات" to "ئیمارات"),
        "uae" to ("الإمارات" to "ئیمارات"),
        "bahrain" to ("البحرين" to "بەحرەین"),
        "australia" to ("أستراليا" to "ئوسترالیا"),
        "uzbekistan" to ("أوزبكستان" to "ئۆزبەکستان"),
        "syria" to ("سوريا" to "سووریا"),
        "indonesia" to ("إندونيسيا" to "ئیندۆنیزیا"),
        "vietnam" to ("فيتنام" to "ڤێتنام"),
        "philippines" to ("الفلبين" to "فلیپین"),
        "thailand" to ("تايلاند" to "تایلەند"),
        "china" to ("الصين" to "چین")
    )

    fun localizeTeam(raw: String, language: AppLanguage): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return raw
        val normalized = cleaned.lowercase(Locale.ROOT)
            .replace(" sc", "")
            .replace(" fc", "")
            .trim()
        val match = teamMap[cleaned.lowercase(Locale.ROOT)] ?: teamMap[normalized]
        if (match != null) {
            return if (language == AppLanguage.ARABIC) match.first else match.second
        }
        return cleaned
    }

    fun localizeCompetition(raw: String, language: AppLanguage): String {
        val lower = raw.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("iraq") && lower.contains("league") ->
                if (language == AppLanguage.ARABIC) "دوري نجوم العراق" else "خولی ئەستێرەکانی عێراق"
            lower.contains("world cup") && (lower.contains("qual") || lower.contains("afc")) ->
                if (language == AppLanguage.ARABIC) "تصفيات كأس العالم - آسيا" else "پاڵاوتنەکانی جامی جیهانی - ئاسیا"
            lower.contains("world cup") ->
                if (language == AppLanguage.ARABIC) "كأس العالم" else "جامی جیهانی"
            lower.contains("asian cup") || lower.contains("afc cup") ->
                if (language == AppLanguage.ARABIC) "كأس آسيا" else "جامی ئاسیا"
            lower.contains("afc champions") || lower.contains("champions league") ->
                if (language == AppLanguage.ARABIC) "دوري أبطال آسيا" else "خولی یانە پاڵەوانەکانی ئاسیا"
            lower.contains("arabian gulf") || lower.contains("gulf cup") ->
                if (language == AppLanguage.ARABIC) "كأس الخليج العربي" else "جامی کەنداو"
            lower.contains("friendly") || lower.contains("friendlies") ->
                if (language == AppLanguage.ARABIC) "مباراة دولية ودية" else "یاری دۆستانەی نێودەوڵەتی"
            raw.isBlank() ->
                if (language == AppLanguage.ARABIC) "البطولات العراقية" else "پاڵەوانێتییەکانی عێراق"
            else -> raw
        }
    }

    fun localizePosition(role: PlayerRole, language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> when (role) {
            PlayerRole.GOALKEEPER -> "حارس مرمى"
            PlayerRole.DEFENDER -> "مدافع"
            PlayerRole.MIDFIELDER -> "وسط"
            PlayerRole.FORWARD -> "مهاجم"
        }
        AppLanguage.KURDISH -> when (role) {
            PlayerRole.GOALKEEPER -> "گۆڵپارێز"
            PlayerRole.DEFENDER -> "بەرگریکار"
            PlayerRole.MIDFIELDER -> "ناوەڕاست"
            PlayerRole.FORWARD -> "هێرشبەر"
        }
    }

    fun formatBaghdadDateTime(epochMs: Long, language: AppLanguage): String {
        if (epochMs <= 0L) return ""
        val cal = Calendar.getInstance(BAGHDAD_TIMEZONE).apply { timeInMillis = epochMs }
        val dayName = baghdadDayName(cal.get(Calendar.DAY_OF_WEEK), language)
        val dayNum = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = baghdadMonthName(cal.get(Calendar.MONTH), language)
        val timeStr = formatBaghdadTimeOnly(epochMs, language)
        return "$dayName $dayNum $monthName • $timeStr"
    }

    fun formatBaghdadTimeOnly(epochMs: Long, language: AppLanguage): String {
        if (epochMs <= 0L) return ""
        val cal = Calendar.getInstance(BAGHDAD_TIMEZONE).apply { timeInMillis = epochMs }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val amPm = when (language) {
            AppLanguage.ARABIC -> if (hour24 < 12) "صباحاً" else "مساءً"
            AppLanguage.KURDISH -> if (hour24 < 12) "بەیانی" else "ئێوارە"
        }
        val formattedTime = String.format(Locale.US, "%02d:%02d", hour12, minute)
        return "$formattedTime $amPm"
    }

    fun formatCountdownBadge(
        kickoffEpochMs: Long,
        nowMs: Long,
        language: AppLanguage
    ): String {
        val diffMs = kickoffEpochMs - nowMs
        if (diffMs <= 0L) {
            return if (language == AppLanguage.ARABIC) "قريباً" else "بەم زووانە"
        }
        val totalMinutes = diffMs / 60_000L
        val hours = totalMinutes / 60L
        val days = hours / 24L
        return when (language) {
            AppLanguage.ARABIC -> when {
                days >= 1L -> "بعد $days يوم"
                hours >= 1L -> "بعد $hours س"
                else -> "بعد ${totalMinutes.coerceAtLeast(1L)} د"
            }
            AppLanguage.KURDISH -> when {
                days >= 1L -> "پاش $days ڕۆژ"
                hours >= 1L -> "پاش $hours ک"
                else -> "پاش ${totalMinutes.coerceAtLeast(1L)} خ"
            }
        }
    }

    fun parseIsoToEpochMs(iso: String): Long {
        val trimmed = iso.trim()
        if (trimmed.isEmpty()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { sdf.parse(trimmed) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return 0L
    }

    fun isIraqiRelatedTeamOrClub(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower == "iraq" ||
            lower.contains("iraq") ||
            lower.contains("al-shorta") || lower.contains("al shorta") ||
            lower.contains("quwa al-jawiya") || lower.contains("quwa al jawiya") ||
            lower.contains("zawraa") ||
            lower.contains("talaba") ||
            lower.contains("erbil") ||
            lower.contains("duhok") || lower.contains("dohuk") ||
            lower.contains("zakho") ||
            lower.contains("newroz") ||
            lower.contains("al-minaa") ||
            lower.contains("al-najaf") ||
            lower.contains("karbala")
    }

    private fun baghdadDayName(dayOfWeek: Int, language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> when (dayOfWeek) {
            Calendar.SATURDAY -> "السبت"
            Calendar.SUNDAY -> "الأحد"
            Calendar.MONDAY -> "الاثنين"
            Calendar.TUESDAY -> "الثلاثاء"
            Calendar.WEDNESDAY -> "الأربعاء"
            Calendar.THURSDAY -> "الخميس"
            else -> "الجمعة"
        }
        AppLanguage.KURDISH -> when (dayOfWeek) {
            Calendar.SATURDAY -> "شەممە"
            Calendar.SUNDAY -> "یەکشەممە"
            Calendar.MONDAY -> "دووشەممە"
            Calendar.TUESDAY -> "سێشەممە"
            Calendar.WEDNESDAY -> "چوارشەممە"
            Calendar.THURSDAY -> "پێنجشەممە"
            else -> "هەینی"
        }
    }

    private fun baghdadMonthName(month: Int, language: AppLanguage): String = when (language) {
        AppLanguage.ARABIC -> listOf(
            "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
            "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"
        ).getOrElse(month) { "" }
        AppLanguage.KURDISH -> listOf(
            "کانوونی دووەم", "شوبات", "ئازار", "نیسان", "ئایار", "حوزەیران",
            "تەمموز", "ئاب", "ئەیلوول", "تشرینی یەکەم", "تشرینی دووەم", "کانوونی یەکەم"
        ).getOrElse(month) { "" }
    }

    fun statLabelPair(rawName: String): Triple<String, String, String>? {
        val lower = rawName.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("possession") -> Triple("possession", "الاستحواذ", "کۆنترۆڵی تۆپ")
            lower == "shots" || lower == "total shots" || lower == "shotsummary" -> Triple("shots", "التسديدات", "لێدانەکان")
            lower.contains("on target") || lower == "shotsontarget" -> Triple("on_target", "على المرمى", "بەرەو گۆڵ")
            lower.contains("corner") -> Triple("corners", "الركنيات", "گۆشەلێدان")
            lower.contains("foul") -> Triple("fouls", "الأخطاء", "هەڵەکان")
            lower.contains("yellow") -> Triple("yellow", "بطاقات صفراء", "کارتی زەرد")
            lower.contains("red") -> Triple("red", "بطاقات حمراء", "کارتی سوور")
            lower.contains("offside") -> Triple("offside", "التسلل", "ئۆفساید")
            else -> null
        }
    }

    fun currentSeasonString(nowMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(BAGHDAD_TIMEZONE).apply { timeInMillis = nowMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-indexed (7 = August)
        return if (month >= Calendar.JULY) {
            "$year-${year + 1}"
        } else {
            "${year - 1}-$year"
        }
    }

    fun formatBaghdadDateRangeForEspn(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = BAGHDAD_TIMEZONE }
        val start = Date(nowMs - 14L * 24L * 60L * 60L * 1000L)
        val end = Date(nowMs + 30L * 24L * 60L * 60L * 1000L)
        return "${sdf.format(start)}-${sdf.format(end)}"
    }

    fun isRecentlyPlayedOrLive(match: SportsMatch, nowMs: Long): Boolean {
        if (match.status == MatchStatus.LIVE) return true
        val diffFromKickoff = nowMs - match.kickoffEpochMs
        return match.status == MatchStatus.SCHEDULED && diffFromKickoff in -900_000L..(3L * 3_600_000L)
    }

    fun absMinutesBetween(aMs: Long, bMs: Long): Long = abs(aMs - bMs) / 60_000L
}
