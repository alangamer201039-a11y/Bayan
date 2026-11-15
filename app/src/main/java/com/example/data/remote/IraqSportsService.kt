package com.example.data.remote

import com.example.model.IraqSportsLocalizer
import com.example.model.LineupPlayer
import com.example.model.LineupSourceType
import com.example.model.MatchGoalEvent
import com.example.model.MatchStatRow
import com.example.model.MatchStatus
import com.example.model.PlayerRole
import com.example.model.SportsMatch
import com.example.model.TeamLineup
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
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class IraqSportsService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    @Volatile
    private var cachedIraqTeamId: String? = null

    suspend fun fetchIraqiSportsMatches(nowMs: Long = System.currentTimeMillis()): List<SportsMatch> =
        withContext(Dispatchers.IO) {
            val iraqTeamIdDeferred = async { resolveIraqNationalTeamId() }
            val season = IraqSportsLocalizer.currentSeasonString(nowMs)
            val dateRange = IraqSportsLocalizer.formatBaghdadDateRangeForEspn(nowMs)

            val leagueNextDeferred = async { fetchUrl("$TSDB_BASE/eventsnextleague.php?id=$IRAQ_STARS_LEAGUE_ID") }
            val leaguePastDeferred = async { fetchUrl("$TSDB_BASE/eventspastleague.php?id=$IRAQ_STARS_LEAGUE_ID") }
            val leagueSeasonDeferred = async { fetchUrl("$TSDB_BASE/eventsseason.php?id=$IRAQ_STARS_LEAGUE_ID&s=$season") }

            val espnSlugs = listOf("fifa.worldq.afc", "afc.cup", "afc.champions", "fifa.friendly")
            val espnScoreboardsDeferred = espnSlugs.map { slug ->
                async {
                    slug to fetchUrl("$ESPN_BASE/$slug/scoreboard?dates=$dateRange")
                }
            }

            val iraqTeamId = iraqTeamIdDeferred.await()
            val teamNextDeferred = async {
                iraqTeamId?.let { fetchUrl("$TSDB_BASE/eventsnext.php?id=$it") }
            }
            val teamLastDeferred = async {
                iraqTeamId?.let { fetchUrl("$TSDB_BASE/eventslast.php?id=$it") }
            }

            val tsdbMatches = mutableListOf<SportsMatch>()
            listOfNotNull(
                leagueNextDeferred.await(),
                leaguePastDeferred.await(),
                leagueSeasonDeferred.await(),
                teamNextDeferred.await(),
                teamLastDeferred.await()
            ).forEach { json ->
                tsdbMatches += parseTheSportsDbEventsJson(json, nowMs)
            }

            val espnMatches = mutableListOf<SportsMatch>()
            espnScoreboardsDeferred.awaitAll().forEach { (slug, json) ->
                if (!json.isNullOrBlank()) {
                    espnMatches += parseEspnScoreboardJson(json, slug, nowMs)
                }
            }

            // Deduplicate within TSDB first
            val uniqueTsdb = tsdbMatches
                .distinctBy { it.id }
                .filter { it.homeTeamRaw.isNotBlank() && it.awayTeamRaw.isNotBlank() }

            val uniqueEspn = espnMatches
                .distinctBy { it.id }
                .filter { it.homeTeamRaw.isNotBlank() && it.awayTeamRaw.isNotBlank() }

            // Enrich top upcoming / live / recent matches with real lineups & stats
            val semaphore = Semaphore(5)
            val espnEnriched = coroutineScope {
                uniqueEspn.take(12).map { match ->
                    async {
                        semaphore.withPermit { enrichEspnMatch(match) }
                    }
                }.awaitAll()
            }

            val tsdbTargets = selectPriorityMatchesForEnrichment(uniqueTsdb, nowMs, limit = 14)
            val tsdbEnrichedMap = coroutineScope {
                tsdbTargets.map { match ->
                    async {
                        semaphore.withPermit { match.id to enrichTheSportsDbMatch(match) }
                    }
                }.awaitAll().toMap()
            }
            val tsdbFinal = uniqueTsdb.map { tsdbEnrichedMap[it.id] ?: it }

            val merged = mergeMatches(espnEnriched, tsdbFinal)
            merged.sortedWith(
                compareBy<SportsMatch> {
                    when (it.status) {
                        MatchStatus.LIVE -> 0
                        MatchStatus.SCHEDULED -> 1
                        MatchStatus.FINISHED -> 2
                        MatchStatus.POSTPONED -> 3
                    }
                }.thenBy {
                    if (it.status == MatchStatus.FINISHED) -it.kickoffEpochMs else it.kickoffEpochMs
                }
            )
        }

    suspend fun fetchMatchDetails(match: SportsMatch): SportsMatch = withContext(Dispatchers.IO) {
        when (match.provider) {
            PROVIDER_ESPN -> enrichEspnMatch(match)
            PROVIDER_TSDB -> enrichTheSportsDbMatch(match)
            else -> match
        }
    }

    private suspend fun resolveIraqNationalTeamId(): String? {
        cachedIraqTeamId?.let { return it }
        val json = fetchUrl("$TSDB_BASE/searchteams.php?t=Iraq") ?: return DEFAULT_IRAQ_TEAM_ID
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return DEFAULT_IRAQ_TEAM_ID
        val teams = root.optJSONArray("teams") ?: return DEFAULT_IRAQ_TEAM_ID
        for (i in 0 until teams.length()) {
            val obj = teams.optJSONObject(i) ?: continue
            val name = obj.optString("strTeam").trim()
            val sport = obj.optString("strSport").trim()
            if (sport.equals("Soccer", ignoreCase = true) &&
                (name.equals("Iraq", ignoreCase = true) || name.equals("Iraq National Team", ignoreCase = true))
            ) {
                val id = obj.optString("idTeam").trim()
                if (id.isNotEmpty()) {
                    cachedIraqTeamId = id
                    return id
                }
            }
        }
        return DEFAULT_IRAQ_TEAM_ID
    }

    private suspend fun enrichTheSportsDbMatch(match: SportsMatch): SportsMatch {
        val rawId = match.id.removePrefix("tsdb_")
        val eventDetailDeferred = coroutineScope {
            val lineupJsonDeferred = async { fetchUrl("$TSDB_BASE/lookuplineup.php?id=$rawId") }
            val eventLookupDeferred = async { fetchUrl("$TSDB_BASE/lookupevent.php?id=$rawId") }
            val statsDeferred = async { fetchUrl("$TSDB_BASE/lookupeventstats.php?id=$rawId") }
            Triple(lineupJsonDeferred.await(), eventLookupDeferred.await(), statsDeferred.await())
        }

        val (lineupJson, eventJson, statsJson) = eventDetailDeferred
        val refreshedBase = if (!eventJson.isNullOrBlank()) {
            parseTheSportsDbEventsJson(eventJson, System.currentTimeMillis()).firstOrNull() ?: match
        } else {
            match
        }

        val (officialHome, officialAway) = if (!lineupJson.isNullOrBlank()) {
            parseTheSportsDbLineupJson(
                json = lineupJson,
                homeTeamName = refreshedBase.homeTeamRaw,
                awayTeamName = refreshedBase.awayTeamRaw,
                homeTeamId = refreshedBase.homeTeamId,
                awayTeamId = refreshedBase.awayTeamId
            )
        } else {
            null to null
        }

        val parsedStats = if (!statsJson.isNullOrBlank()) {
            parseTheSportsDbEventStatsJson(statsJson)
        } else {
            emptyList()
        }

        var finalHomeLineup = officialHome ?: refreshedBase.homeLineup
        var finalAwayLineup = officialAway ?: refreshedBase.awayLineup

        // If upcoming match hasn't released official XI yet, build data-driven Expected Starting XI from team rosters
        if ((finalHomeLineup == null || !finalHomeLineup.hasPlayers) && !refreshedBase.homeTeamId.isNullOrBlank()) {
            val rosterJson = fetchUrl("$TSDB_BASE/lookup_all_players.php?id=${refreshedBase.homeTeamId}")
            if (!rosterJson.isNullOrBlank()) {
                finalHomeLineup = parseExpectedLineupFromRosterJson(rosterJson, refreshedBase.homeTeamRaw)
            }
        }
        if ((finalAwayLineup == null || !finalAwayLineup.hasPlayers) && !refreshedBase.awayTeamId.isNullOrBlank()) {
            val rosterJson = fetchUrl("$TSDB_BASE/lookup_all_players.php?id=${refreshedBase.awayTeamId}")
            if (!rosterJson.isNullOrBlank()) {
                finalAwayLineup = parseExpectedLineupFromRosterJson(rosterJson, refreshedBase.awayTeamRaw)
            }
        }

        return refreshedBase.copy(
            homeLineup = finalHomeLineup,
            awayLineup = finalAwayLineup,
            stats = parsedStats.ifEmpty { refreshedBase.stats }
        )
    }

    private suspend fun enrichEspnMatch(match: SportsMatch): SportsMatch {
        val parts = match.id.removePrefix("espn_").split(":", limit = 2)
        if (parts.size != 2) return match
        val slug = parts[0]
        val eventId = parts[1]
        val summaryJson = fetchUrl("$ESPN_BASE/$slug/summary?event=$eventId") ?: return match
        return parseEspnSummaryJson(summaryJson, match)
    }

    fun parseTheSportsDbEventsJson(json: String, nowMs: Long = System.currentTimeMillis()): List<SportsMatch> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("events") ?: root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<SportsMatch>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val idEvent = obj.optString("idEvent").trim()
            if (idEvent.isEmpty()) continue

            val homeTeam = obj.optString("strHomeTeam").trim()
            val awayTeam = obj.optString("strAwayTeam").trim()
            if (homeTeam.isEmpty() || awayTeam.isEmpty()) continue

            val league = obj.optString("strLeague").trim()
            val isIraqLeague = obj.optString("idLeague").trim() == IRAQ_STARS_LEAGUE_ID ||
                league.contains("Iraq", ignoreCase = true)
            val isNationalTeam = homeTeam.equals("Iraq", ignoreCase = true) ||
                awayTeam.equals("Iraq", ignoreCase = true)
            val isIraqClub = IraqSportsLocalizer.isIraqiRelatedTeamOrClub(homeTeam) ||
                IraqSportsLocalizer.isIraqiRelatedTeamOrClub(awayTeam)
            if (!isIraqLeague && !isNationalTeam && !isIraqClub) continue

            val timestampStr = obj.optString("strTimestamp").trim()
            val dateStr = obj.optString("dateEvent").trim()
            val timeStr = obj.optString("strTime").trim().ifEmpty { "15:00:00" }
            val kickoffEpochMs = IraqSportsLocalizer.parseIsoToEpochMs(timestampStr).takeIf { it > 0L }
                ?: IraqSportsLocalizer.parseIsoToEpochMs("$dateStr $timeStr")
            if (kickoffEpochMs <= 0L) continue

            val homeScore = parseNullableInt(obj.optString("intHomeScore"))
            val awayScore = parseNullableInt(obj.optString("intAwayScore"))
            val statusRaw = obj.optString("strStatus").trim()
            val status = inferMatchStatus(statusRaw, homeScore, awayScore, kickoffEpochMs, nowMs)

            // Intentionally ignore strVideo / strTVStation to enforce zero-video policy
            list += SportsMatch(
                id = "tsdb_$idEvent",
                provider = PROVIDER_TSDB,
                competitionRaw = league.ifEmpty { "Iraq Stars League" },
                homeTeamRaw = homeTeam,
                awayTeamRaw = awayTeam,
                homeTeamId = obj.optString("idHomeTeam").trim().ifEmpty { null },
                awayTeamId = obj.optString("idAwayTeam").trim().ifEmpty { null },
                homeScore = homeScore,
                awayScore = awayScore,
                kickoffEpochMs = kickoffEpochMs,
                status = status,
                statusDetail = formatStatusDetail(statusRaw, status),
                venue = obj.optString("strVenue").trim(),
                roundLabel = obj.optString("intRound").trim().takeIf { it.isNotEmpty() && it != "0" }?.let { "R$it" }.orEmpty(),
                isNationalTeamMatch = isNationalTeam
            )
        }
        return list
    }

    fun parseTheSportsDbLineupJson(
        json: String,
        homeTeamName: String,
        awayTeamName: String,
        homeTeamId: String? = null,
        awayTeamId: String? = null
    ): Pair<TeamLineup?, TeamLineup?> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null to null
        val lineupArray = root.optJSONArray("lineup") ?: return null to null
        if (lineupArray.length() == 0) return null to null

        val homeStarters = mutableListOf<LineupPlayer>()
        val homeSubs = mutableListOf<LineupPlayer>()
        val awayStarters = mutableListOf<LineupPlayer>()
        val awaySubs = mutableListOf<LineupPlayer>()
        var homeFormation = ""
        var awayFormation = ""

        for (i in 0 until lineupArray.length()) {
            val item = lineupArray.optJSONObject(i) ?: continue
            val playerName = item.optString("strPlayer").trim()
            if (playerName.isEmpty()) continue

            val teamStr = item.optString("strTeam").trim()
            val idTeamStr = item.optString("idTeam").trim()
            val homeFlag = item.optString("strHome").trim().lowercase(Locale.ROOT)
            val isHome = when {
                homeFlag == "yes" || homeFlag == "true" || homeFlag == "1" -> true
                homeFlag == "no" || homeFlag == "false" || homeFlag == "0" -> false
                !homeTeamId.isNullOrBlank() && idTeamStr == homeTeamId -> true
                !awayTeamId.isNullOrBlank() && idTeamStr == awayTeamId -> false
                teamStr.equals(homeTeamName, ignoreCase = true) -> true
                teamStr.equals(awayTeamName, ignoreCase = true) -> false
                else -> continue
            }

            val posRaw = item.optString("strPosition").trim()
            val number = item.optString("intSquadNumber").trim()
                .ifEmpty { item.optString("strSquadNumber").trim() }
                .takeIf { it != "0" && it != "null" }.orEmpty()
            val subFlag = item.optString("strSubstitute").trim().lowercase(Locale.ROOT)
            val isSub = subFlag == "yes" || subFlag == "true" || subFlag == "1"
            val formation = item.optString("strFormation").trim().takeIf { it.contains("-") }.orEmpty()

            val player = LineupPlayer(
                name = playerName,
                number = number,
                positionLabel = posRaw,
                role = PlayerRole.fromPosition(posRaw),
                isStarter = !isSub
            )

            if (isHome) {
                if (homeFormation.isEmpty() && formation.isNotEmpty()) homeFormation = formation
                if (isSub) homeSubs += player else homeStarters += player
            } else {
                if (awayFormation.isEmpty() && formation.isNotEmpty()) awayFormation = formation
                if (isSub) awaySubs += player else awayStarters += player
            }
        }

        val homeLineup = if (homeStarters.isNotEmpty() || homeSubs.isNotEmpty()) {
            TeamLineup(
                teamNameRaw = homeTeamName,
                formation = homeFormation,
                sourceType = LineupSourceType.OFFICIAL,
                starters = sortLineupPlayers(homeStarters),
                substitutes = homeSubs
            )
        } else null

        val awayLineup = if (awayStarters.isNotEmpty() || awaySubs.isNotEmpty()) {
            TeamLineup(
                teamNameRaw = awayTeamName,
                formation = awayFormation,
                sourceType = LineupSourceType.OFFICIAL,
                starters = sortLineupPlayers(awayStarters),
                substitutes = awaySubs
            )
        } else null

        return homeLineup to awayLineup
    }

    fun parseExpectedLineupFromRosterJson(json: String, teamNameRaw: String): TeamLineup? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val playersArray = root.optJSONArray("player") ?: return null
        if (playersArray.length() == 0) return null

        val allPlayers = mutableListOf<LineupPlayer>()
        for (i in 0 until playersArray.length()) {
            val item = playersArray.optJSONObject(i) ?: continue
            val name = item.optString("strPlayer").trim()
            if (name.isEmpty()) continue
            val status = item.optString("strStatus").trim().lowercase(Locale.ROOT)
            if (status == "retired" || status == "injured") continue
            val pos = item.optString("strPosition").trim()
            val number = item.optString("strNumber").trim()
                .takeIf { it.isNotEmpty() && it != "0" && it != "null" }.orEmpty()
            allPlayers += LineupPlayer(
                name = name,
                number = number,
                positionLabel = pos,
                role = PlayerRole.fromPosition(pos),
                isStarter = true
            )
        }
        if (allPlayers.isEmpty()) return null

        val gk = allPlayers.filter { it.role == PlayerRole.GOALKEEPER }.take(1)
        val df = allPlayers.filter { it.role == PlayerRole.DEFENDER }.take(4)
        val mf = allPlayers.filter { it.role == PlayerRole.MIDFIELDER }.take(4)
        val fw = allPlayers.filter { it.role == PlayerRole.FORWARD }.take(2)
        val picked = (gk + df + mf + fw).toMutableList()
        if (picked.size < 11) {
            val pickedNames = picked.map { it.name }.toSet()
            picked += allPlayers.filterNot { it.name in pickedNames }.take(11 - picked.size)
        }
        val starters = sortLineupPlayers(picked.take(11))
        val starterNames = starters.map { it.name }.toSet()
        val subs = allPlayers
            .filterNot { it.name in starterNames }
            .take(7)
            .map { it.copy(isStarter = false) }

        return TeamLineup(
            teamNameRaw = teamNameRaw,
            formation = "4-4-2",
            sourceType = LineupSourceType.EXPECTED,
            starters = starters,
            substitutes = subs
        )
    }

    fun parseTheSportsDbEventStatsJson(json: String): List<MatchStatRow> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val statsArr = root.optJSONArray("eventstats") ?: return emptyList()
        val rows = mutableListOf<MatchStatRow>()
        for (i in 0 until statsArr.length()) {
            val item = statsArr.optJSONObject(i) ?: continue
            val statName = item.optString("strStat").trim()
            val homeVal = item.optString("intHome").trim()
            val awayVal = item.optString("intAway").trim()
            if (homeVal.isEmpty() || awayVal.isEmpty()) continue
            val mapped = IraqSportsLocalizer.statLabelPair(statName) ?: continue
            rows += MatchStatRow(
                labelKey = mapped.first,
                labelAr = mapped.second,
                labelCkb = mapped.third,
                homeValue = if (mapped.first == "possession" && !homeVal.endsWith("%")) "$homeVal%" else homeVal,
                awayValue = if (mapped.first == "possession" && !awayVal.endsWith("%")) "$awayVal%" else awayVal
            )
        }
        return rows.distinctBy { it.labelKey }
    }

    fun parseEspnScoreboardJson(
        json: String,
        leagueSlug: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<SportsMatch> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val leagueTitle = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name").orEmpty()
        val events = root.optJSONArray("events") ?: return emptyList()
        val list = mutableListOf<SportsMatch>()

        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val eventId = event.optString("id").trim()
            if (eventId.isEmpty()) continue

            val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = competition.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeObj: JSONObject? = null
            var awayObj: JSONObject? = null
            for (cIdx in 0 until competitors.length()) {
                val c = competitors.optJSONObject(cIdx) ?: continue
                if (c.optString("homeAway") == "home") homeObj = c
                else if (c.optString("homeAway") == "away") awayObj = c
            }
            if (homeObj == null || awayObj == null) continue

            val homeTeamObj = homeObj.optJSONObject("team") ?: continue
            val awayTeamObj = awayObj.optJSONObject("team") ?: continue
            val homeName = homeTeamObj.optString("displayName").ifEmpty { homeTeamObj.optString("name") }.trim()
            val awayName = awayTeamObj.optString("displayName").ifEmpty { awayTeamObj.optString("name") }.trim()

            if (!IraqSportsLocalizer.isIraqiRelatedTeamOrClub(homeName) &&
                !IraqSportsLocalizer.isIraqiRelatedTeamOrClub(awayName)
            ) {
                continue
            }

            val kickoffMs = IraqSportsLocalizer.parseIsoToEpochMs(event.optString("date"))
            if (kickoffMs <= 0L) continue

            val statusObj = competition.optJSONObject("status") ?: event.optJSONObject("status")
            val statusType = statusObj?.optJSONObject("type")
            val stateStr = statusType?.optString("state").orEmpty().lowercase(Locale.ROOT)
            val completed = statusType?.optBoolean("completed", false) ?: false
            val displayClock = statusObj?.optString("displayClock").orEmpty().trim()
            val shortDetail = statusType?.optString("shortDetail").orEmpty().trim()

            val matchStatus = when {
                completed || stateStr == "post" -> MatchStatus.FINISHED
                stateStr == "in" -> MatchStatus.LIVE
                shortDetail.contains("PPD", ignoreCase = true) || shortDetail.contains("Postponed", ignoreCase = true) -> MatchStatus.POSTPONED
                else -> inferMatchStatus(shortDetail, null, null, kickoffMs, nowMs)
            }

            val homeScore = if (matchStatus == MatchStatus.SCHEDULED) null else parseNullableInt(homeObj.optString("score"))
            val awayScore = if (matchStatus == MatchStatus.SCHEDULED) null else parseNullableInt(awayObj.optString("score"))
            val venue = competition.optJSONObject("venue")?.optString("fullName").orEmpty().trim()
            val isNational = homeName.equals("Iraq", ignoreCase = true) || awayName.equals("Iraq", ignoreCase = true)

            // Deliberately ignore geoBroadcasts / broadcasts / video links
            list += SportsMatch(
                id = "espn_$leagueSlug:$eventId",
                provider = PROVIDER_ESPN,
                competitionRaw = leagueTitle.ifEmpty { leagueSlug },
                homeTeamRaw = homeName,
                awayTeamRaw = awayName,
                homeTeamId = homeTeamObj.optString("id").trim().ifEmpty { null },
                awayTeamId = awayTeamObj.optString("id").trim().ifEmpty { null },
                homeScore = homeScore,
                awayScore = awayScore,
                kickoffEpochMs = kickoffMs,
                status = matchStatus,
                statusDetail = if (matchStatus == MatchStatus.LIVE && displayClock.isNotEmpty()) displayClock else formatStatusDetail(shortDetail, matchStatus),
                venue = venue,
                isNationalTeamMatch = isNational
            )
        }
        return list
    }

    fun parseEspnSummaryJson(json: String, baseMatch: SportsMatch): SportsMatch {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return baseMatch

        // Refresh latest score/status from header if available
        var updatedMatch = baseMatch
        val headerComp = root.optJSONObject("header")
            ?.optJSONArray("competitions")
            ?.optJSONObject(0)
        if (headerComp != null) {
            val comps = headerComp.optJSONArray("competitors")
            var homeScore = updatedMatch.homeScore
            var awayScore = updatedMatch.awayScore
            if (comps != null) {
                for (i in 0 until comps.length()) {
                    val c = comps.optJSONObject(i) ?: continue
                    val sc = parseNullableInt(c.optString("score"))
                    if (c.optString("homeAway") == "home" && sc != null) homeScore = sc
                    if (c.optString("homeAway") == "away" && sc != null) awayScore = sc
                }
            }
            val statusType = headerComp.optJSONObject("status")?.optJSONObject("type")
            val completed = statusType?.optBoolean("completed", false) ?: false
            val state = statusType?.optString("state").orEmpty().lowercase(Locale.ROOT)
            val newStatus = when {
                completed || state == "post" -> MatchStatus.FINISHED
                state == "in" -> MatchStatus.LIVE
                else -> updatedMatch.status
            }
            updatedMatch = updatedMatch.copy(
                homeScore = if (newStatus == MatchStatus.SCHEDULED) null else homeScore,
                awayScore = if (newStatus == MatchStatus.SCHEDULED) null else awayScore,
                status = newStatus
            )
        }

        // Parse rosters / official or expected lineups
        var homeLineup: TeamLineup? = updatedMatch.homeLineup
        var awayLineup: TeamLineup? = updatedMatch.awayLineup
        val rosters = root.optJSONArray("rosters")
        if (rosters != null) {
            for (i in 0 until rosters.length()) {
                val rObj = rosters.optJSONObject(i) ?: continue
                val isHome = rObj.optString("homeAway") == "home"
                val teamName = rObj.optJSONObject("team")?.optString("displayName").orEmpty()
                    .ifEmpty { if (isHome) updatedMatch.homeTeamRaw else updatedMatch.awayTeamRaw }
                val formation = rObj.optString("formation").trim()
                val rosterArr = rObj.optJSONArray("roster") ?: continue
                val starters = mutableListOf<LineupPlayer>()
                val subs = mutableListOf<LineupPlayer>()
                var anyStarterMarked = false
                for (pIdx in 0 until rosterArr.length()) {
                    val pObj = rosterArr.optJSONObject(pIdx) ?: continue
                    val athlete = pObj.optJSONObject("athlete") ?: continue
                    val name = athlete.optString("displayName").ifEmpty { athlete.optString("shortName") }.trim()
                    if (name.isEmpty()) continue
                    val jersey = pObj.optString("jersey").trim()
                    val posObj = pObj.optJSONObject("position") ?: athlete.optJSONObject("position")
                    val posName = posObj?.optString("abbreviation").orEmpty()
                        .ifEmpty { posObj?.optString("displayName").orEmpty() }
                    val isStarter = pObj.optBoolean("starter", false)
                    if (isStarter) anyStarterMarked = true
                    val player = LineupPlayer(
                        name = name,
                        number = jersey,
                        positionLabel = posName,
                        role = PlayerRole.fromPosition(posName),
                        isStarter = isStarter
                    )
                    if (isStarter) starters += player else subs += player
                }
                if (starters.isNotEmpty() || subs.isNotEmpty()) {
                    val sourceType = if (anyStarterMarked) LineupSourceType.OFFICIAL else LineupSourceType.EXPECTED
                    val finalStarters = if (anyStarterMarked) sortLineupPlayers(starters) else sortLineupPlayers(subs.take(11).map { it.copy(isStarter = true) })
                    val finalSubs = if (anyStarterMarked) subs else subs.drop(11)
                    val built = TeamLineup(
                        teamNameRaw = teamName,
                        formation = formation,
                        sourceType = sourceType,
                        starters = finalStarters,
                        substitutes = finalSubs
                    )
                    if (isHome) homeLineup = built else awayLineup = built
                }
            }
        }

        // Parse match statistics from boxscore.teams
        val statsList = parseEspnBoxScoreStats(root, updatedMatch.homeTeamRaw)

        // Parse goal scorers from keyEvents or details
        val goalsList = parseEspnGoals(root, updatedMatch.homeTeamId)

        return updatedMatch.copy(
            homeLineup = homeLineup,
            awayLineup = awayLineup,
            stats = statsList.ifEmpty { updatedMatch.stats },
            goalEvents = goalsList.ifEmpty { updatedMatch.goalEvents }
        )
    }

    private fun parseEspnBoxScoreStats(root: JSONObject, homeTeamName: String): List<MatchStatRow> {
        val teamsArr = root.optJSONObject("boxscore")?.optJSONArray("teams") ?: return emptyList()
        if (teamsArr.length() < 2) return emptyList()
        val first = teamsArr.optJSONObject(0) ?: return emptyList()
        val second = teamsArr.optJSONObject(1) ?: return emptyList()
        val firstName = first.optJSONObject("team")?.optString("displayName").orEmpty()
        val (homeObj, awayObj) = if (first.optString("homeAway") == "home" || firstName.equals(homeTeamName, ignoreCase = true)) {
            first to second
        } else {
            second to first
        }
        val homeStats = homeObj.optJSONArray("statistics") ?: return emptyList()
        val awayStats = awayObj.optJSONArray("statistics") ?: return emptyList()
        val awayMap = mutableMapOf<String, String>()
        for (i in 0 until awayStats.length()) {
            val s = awayStats.optJSONObject(i) ?: continue
            awayMap[s.optString("name")] = s.optString("displayValue").ifEmpty { s.optString("value") }
        }
        val rows = mutableListOf<MatchStatRow>()
        for (i in 0 until homeStats.length()) {
            val s = homeStats.optJSONObject(i) ?: continue
            val name = s.optString("name").ifEmpty { s.optString("label") }
            val mapped = IraqSportsLocalizer.statLabelPair(name) ?: continue
            val hVal = s.optString("displayValue").ifEmpty { s.optString("value") }.trim()
            val aVal = awayMap[s.optString("name")]?.trim().orEmpty()
            if (hVal.isEmpty() || aVal.isEmpty()) continue
            rows += MatchStatRow(
                labelKey = mapped.first,
                labelAr = mapped.second,
                labelCkb = mapped.third,
                homeValue = if (mapped.first == "possession" && !hVal.endsWith("%")) "$hVal%" else hVal,
                awayValue = if (mapped.first == "possession" && !aVal.endsWith("%")) "$aVal%" else aVal
            )
        }
        return rows.distinctBy { it.labelKey }
    }

    private fun parseEspnGoals(root: JSONObject, homeTeamId: String?): List<MatchGoalEvent> {
        val keyEvents = root.optJSONArray("keyEvents") ?: return emptyList()
        val goals = mutableListOf<MatchGoalEvent>()
        for (i in 0 until keyEvents.length()) {
            val ev = keyEvents.optJSONObject(i) ?: continue
            val scoring = ev.optBoolean("scoringPlay", false)
            val typeText = ev.optJSONObject("type")?.optString("text").orEmpty().lowercase(Locale.ROOT)
            if (!scoring && !typeText.contains("goal")) continue
            val clock = ev.optJSONObject("clock")?.optString("displayValue").orEmpty().trim()
            val teamId = ev.optJSONObject("team")?.optString("id").orEmpty().trim()
            val participants = ev.optJSONArray("participants")
            val scorer = participants?.optJSONObject(0)?.optJSONObject("athlete")?.optString("displayName")
                ?.trim().orEmpty()
            if (scorer.isEmpty()) continue
            goals += MatchGoalEvent(
                minute = clock,
                scorer = scorer,
                isHomeTeam = !homeTeamId.isNullOrBlank() && teamId == homeTeamId
            )
        }
        return goals
    }

    private fun mergeMatches(espnMatches: List<SportsMatch>, tsdbMatches: List<SportsMatch>): List<SportsMatch> {
        val result = espnMatches.toMutableList()
        for (tsdb in tsdbMatches) {
            val duplicateIdx = result.indexOfFirst { existing ->
                val sameTeams =
                    (existing.homeTeamRaw.equals(tsdb.homeTeamRaw, ignoreCase = true) &&
                        existing.awayTeamRaw.equals(tsdb.awayTeamRaw, ignoreCase = true)) ||
                        (existing.homeTeamRaw.equals(tsdb.awayTeamRaw, ignoreCase = true) &&
                            existing.awayTeamRaw.equals(tsdb.homeTeamRaw, ignoreCase = true))
                sameTeams && IraqSportsLocalizer.absMinutesBetween(existing.kickoffEpochMs, tsdb.kickoffEpochMs) <= 720L
            }
            if (duplicateIdx >= 0) {
                val existing = result[duplicateIdx]
                result[duplicateIdx] = existing.copy(
                    homeLineup = existing.homeLineup ?: tsdb.homeLineup,
                    awayLineup = existing.awayLineup ?: tsdb.awayLineup,
                    stats = existing.stats.ifEmpty { tsdb.stats }
                )
            } else {
                result += tsdb
            }
        }
        return result
    }

    private fun selectPriorityMatchesForEnrichment(
        matches: List<SportsMatch>,
        nowMs: Long,
        limit: Int
    ): List<SportsMatch> {
        val live = matches.filter { it.status == MatchStatus.LIVE }
        val upcoming = matches.filter { it.status == MatchStatus.SCHEDULED }.sortedBy { it.kickoffEpochMs }
        val finished = matches.filter { it.status == MatchStatus.FINISHED }.sortedByDescending { it.kickoffEpochMs }
        return (live + upcoming.take(8) + finished.take(6)).distinctBy { it.id }.take(limit)
    }

    private fun sortLineupPlayers(players: List<LineupPlayer>): List<LineupPlayer> {
        val roleOrder = mapOf(
            PlayerRole.GOALKEEPER to 0,
            PlayerRole.DEFENDER to 1,
            PlayerRole.MIDFIELDER to 2,
            PlayerRole.FORWARD to 3
        )
        return players.sortedWith(
            compareBy<LineupPlayer> { roleOrder[it.role] ?: 2 }
                .thenBy { it.number.toIntOrNull() ?: 99 }
        )
    }

    private fun inferMatchStatus(
        rawStatus: String,
        homeScore: Int?,
        awayScore: Int?,
        kickoffMs: Long,
        nowMs: Long
    ): MatchStatus {
        val lower = rawStatus.lowercase(Locale.ROOT)
        return when {
            lower.contains("postponed") || lower == "pst" || lower == "ppd" || lower.contains("canc") -> MatchStatus.POSTPONED
            lower.contains("finished") || lower == "ft" || lower == "aet" || lower == "pen" -> MatchStatus.FINISHED
            lower == "1h" || lower == "2h" || lower == "ht" || lower == "et" || lower.contains("live") || lower.contains("in play") -> MatchStatus.LIVE
            homeScore != null && awayScore != null && kickoffMs < nowMs - 2L * 3_600_000L -> MatchStatus.FINISHED
            homeScore != null && awayScore != null && nowMs >= kickoffMs -> MatchStatus.LIVE
            kickoffMs < nowMs - 4L * 3_600_000L -> MatchStatus.FINISHED
            else -> MatchStatus.SCHEDULED
        }
    }

    private fun formatStatusDetail(raw: String, status: MatchStatus): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return when (status) {
            MatchStatus.LIVE -> trimmed
            else -> ""
        }
    }

    private fun parseNullableInt(raw: String): Int? {
        val clean = raw.trim()
        if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return null
        return clean.toIntOrNull()
    }

    private fun fetchUrl(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "BayanIraqApp/1.0 (+https://bayaniraq.app)")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        const val IRAQ_STARS_LEAGUE_ID = "4893"
        const val DEFAULT_IRAQ_TEAM_ID = "134329"
        const val PROVIDER_TSDB = "thesportsdb"
        const val PROVIDER_ESPN = "espn"
        private const val TSDB_BASE = "https://www.thesportsdb.com/api/v1/json/3"
        private const val ESPN_BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    }
}
