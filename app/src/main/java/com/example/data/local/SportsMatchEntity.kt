package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.LineupPlayer
import com.example.model.LineupSourceType
import com.example.model.MatchGoalEvent
import com.example.model.MatchStatRow
import com.example.model.MatchStatus
import com.example.model.PlayerRole
import com.example.model.SportsMatch
import com.example.model.TeamLineup
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "sports_matches")
data class SportsMatchEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val competitionRaw: String,
    val homeTeamRaw: String,
    val awayTeamRaw: String,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val kickoffEpochMs: Long,
    val statusKey: String,
    val statusDetail: String,
    val venue: String,
    val roundLabel: String,
    val homeLineupJson: String?,
    val awayLineupJson: String?,
    val statsJson: String?,
    val goalEventsJson: String?,
    val isNationalTeamMatch: Boolean,
    val cachedAtMs: Long = System.currentTimeMillis()
) {
    fun toDomain(): SportsMatch = SportsMatch(
        id = id,
        provider = provider,
        competitionRaw = competitionRaw,
        homeTeamRaw = homeTeamRaw,
        awayTeamRaw = awayTeamRaw,
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
        homeScore = homeScore,
        awayScore = awayScore,
        kickoffEpochMs = kickoffEpochMs,
        status = MatchStatus.fromKey(statusKey),
        statusDetail = statusDetail,
        venue = venue,
        roundLabel = roundLabel,
        homeLineup = homeLineupJson?.let(::deserializeLineup),
        awayLineup = awayLineupJson?.let(::deserializeLineup),
        stats = statsJson?.let(::deserializeStats).orEmpty(),
        goalEvents = goalEventsJson?.let(::deserializeGoals).orEmpty(),
        isNationalTeamMatch = isNationalTeamMatch
    )

    companion object {
        fun fromDomain(match: SportsMatch): SportsMatchEntity = SportsMatchEntity(
            id = match.id,
            provider = match.provider,
            competitionRaw = match.competitionRaw,
            homeTeamRaw = match.homeTeamRaw,
            awayTeamRaw = match.awayTeamRaw,
            homeTeamId = match.homeTeamId,
            awayTeamId = match.awayTeamId,
            homeScore = match.homeScore,
            awayScore = match.awayScore,
            kickoffEpochMs = match.kickoffEpochMs,
            statusKey = match.status.key,
            statusDetail = match.statusDetail,
            venue = match.venue,
            roundLabel = match.roundLabel,
            homeLineupJson = match.homeLineup?.let(::serializeLineup),
            awayLineupJson = match.awayLineup?.let(::serializeLineup),
            statsJson = if (match.stats.isNotEmpty()) serializeStats(match.stats) else null,
            goalEventsJson = if (match.goalEvents.isNotEmpty()) serializeGoals(match.goalEvents) else null,
            isNationalTeamMatch = match.isNationalTeamMatch
        )

        private fun serializeLineup(lineup: TeamLineup): String {
            val root = JSONObject()
            root.put("team", lineup.teamNameRaw)
            root.put("formation", lineup.formation)
            root.put("sourceType", lineup.sourceType.key)
            val startersArr = JSONArray()
            lineup.starters.forEach { startersArr.put(playerToJson(it)) }
            root.put("starters", startersArr)
            val subsArr = JSONArray()
            lineup.substitutes.forEach { subsArr.put(playerToJson(it)) }
            root.put("subs", subsArr)
            return root.toString()
        }

        private fun playerToJson(p: LineupPlayer): JSONObject = JSONObject().apply {
            put("name", p.name)
            put("num", p.number)
            put("pos", p.positionLabel)
            put("role", p.role.key)
            put("starter", p.isStarter)
        }

        private fun deserializeLineup(raw: String): TeamLineup? = runCatching {
            val root = JSONObject(raw)
            val starters = jsonArrayToPlayers(root.optJSONArray("starters"))
            val subs = jsonArrayToPlayers(root.optJSONArray("subs"))
            TeamLineup(
                teamNameRaw = root.optString("team"),
                formation = root.optString("formation"),
                sourceType = LineupSourceType.fromKey(root.optString("sourceType")),
                starters = starters,
                substitutes = subs
            )
        }.getOrNull()

        private fun jsonArrayToPlayers(arr: JSONArray?): List<LineupPlayer> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        LineupPlayer(
                            name = o.optString("name"),
                            number = o.optString("num"),
                            positionLabel = o.optString("pos"),
                            role = PlayerRole.fromKey(o.optString("role")),
                            isStarter = o.optBoolean("starter", true)
                        )
                    )
                }
            }
        }

        private fun serializeStats(stats: List<MatchStatRow>): String {
            val arr = JSONArray()
            stats.forEach { row ->
                arr.put(
                    JSONObject().apply {
                        put("k", row.labelKey)
                        put("ar", row.labelAr)
                        put("ckb", row.labelCkb)
                        put("h", row.homeValue)
                        put("a", row.awayValue)
                    }
                )
            }
            return arr.toString()
        }

        private fun deserializeStats(raw: String): List<MatchStatRow> = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        MatchStatRow(
                            labelKey = o.optString("k"),
                            labelAr = o.optString("ar"),
                            labelCkb = o.optString("ckb"),
                            homeValue = o.optString("h"),
                            awayValue = o.optString("a")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        private fun serializeGoals(goals: List<MatchGoalEvent>): String {
            val arr = JSONArray()
            goals.forEach { g ->
                arr.put(
                    JSONObject().apply {
                        put("min", g.minute)
                        put("sc", g.scorer)
                        put("h", g.isHomeTeam)
                        put("t", g.eventType)
                    }
                )
            }
            return arr.toString()
        }

        private fun deserializeGoals(raw: String): List<MatchGoalEvent> = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        MatchGoalEvent(
                            minute = o.optString("min"),
                            scorer = o.optString("sc"),
                            isHomeTeam = o.optBoolean("h"),
                            eventType = o.optString("t", "goal")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
