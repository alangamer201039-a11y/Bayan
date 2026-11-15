package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.IraqSportsLocalizer
import com.example.model.LineupPlayer
import com.example.model.LineupSourceType
import com.example.model.MatchStatRow
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import com.example.model.TeamLineup
import com.example.ui.LocalAppLanguage
import com.example.ui.bayanString
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailSheet(
    match: SportsMatch,
    isLoadingDetails: Boolean,
    onDismiss: () -> Unit
) {
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedHomeLineup by remember(match.id) { mutableStateOf(true) }

    val homeName = match.localizedHomeTeam(language)
    val awayName = match.localizedAwayTeam(language)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .background(bayanColors.mutedText.copy(alpha = 0.4f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Competition + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsSoccer,
                            contentDescription = null,
                            tint = bayanColors.accentBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = match.localizedCompetition(language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier
                        .size(38.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("close_match_sheet")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = bayanString(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Scoreboard Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = bayanColors.pillBackground,
                border = BorderStroke(1.dp, bayanColors.glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = homeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.LIVE) {
                                Text(
                                    text = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 28.sp
                                    ),
                                    color = if (match.status == MatchStatus.LIVE) EmeraldLive else MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = IraqSportsLocalizer.formatBaghdadTimeOnly(match.kickoffEpochMs, language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = bayanColors.accentBlue
                                )
                            }
                            Text(
                                text = when (match.status) {
                                    MatchStatus.LIVE -> match.statusDetail.ifBlank { bayanString(R.string.sports_status_live) }
                                    MatchStatus.FINISHED -> bayanString(R.string.sports_status_finished)
                                    MatchStatus.POSTPONED -> bayanString(R.string.sports_status_postponed)
                                    MatchStatus.SCHEDULED -> bayanString(R.string.sports_status_upcoming)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (match.status == MatchStatus.LIVE) EmeraldLive else bayanColors.accentEmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = awayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = bayanColors.glassBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = bayanColors.accentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${IraqSportsLocalizer.formatBaghdadDateTime(match.kickoffEpochMs, language)} (${bayanString(R.string.sports_baghdad_time_note)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = bayanColors.mutedText
                        )
                    }

                    if (match.venue.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stadium,
                                contentDescription = null,
                                tint = bayanColors.mutedText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = match.venue,
                                style = MaterialTheme.typography.labelSmall,
                                color = bayanColors.mutedText
                            )
                        }
                    }
                }
            }

            if (isLoadingDetails) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = bayanColors.accentBlue
                        )
                        Text(
                            text = bayanString(R.string.sports_loading_details),
                            style = MaterialTheme.typography.labelMedium,
                            color = bayanColors.mutedText
                        )
                    }
                }
            }

            // Textual goal scorers (strictly text & minute badges, zero video)
            if (match.goalEvents.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = bayanString(R.string.sports_goals_summary),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        match.goalEvents.forEach { goal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SportsSoccer,
                                        contentDescription = null,
                                        tint = EmeraldLive,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = goal.scorer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${goal.minute} • ${if (goal.isHomeTeam) homeName else awayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bayanColors.mutedText
                                )
                            }
                        }
                    }
                }
            }

            // Lineups Section (تشكيلة الفريقين)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = bayanColors.accentEmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = bayanString(R.string.sports_view_lineup_stats),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (match.hasAnyLineup()) {
                // Toggle Pill between Home & Away team lineups
                Surface(
                    shape = CircleShape,
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LineupTeamTab(
                            title = homeName,
                            selected = selectedHomeLineup,
                            onClick = { selectedHomeLineup = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lineup_tab_home")
                        )
                        LineupTeamTab(
                            title = awayName,
                            selected = !selectedHomeLineup,
                            onClick = { selectedHomeLineup = false },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lineup_tab_away")
                        )
                    }
                }

                val activeLineup: TeamLineup? = if (selectedHomeLineup) match.homeLineup else match.awayLineup
                if (activeLineup != null && activeLineup.hasPlayers) {
                    LineupCard(lineup = activeLineup)
                } else {
                    Text(
                        text = bayanString(R.string.sports_no_lineup_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = bayanColors.mutedText
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bayanString(R.string.sports_no_lineup_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = bayanColors.mutedText,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Statistics Comparison Table
            if (match.stats.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = bayanColors.accentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = bayanString(R.string.sports_match_stats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("match_stats_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        match.stats.forEach { stat ->
                            StatComparisonBar(stat = stat)
                        }
                    }
                }
            } else if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.LIVE) {
                Text(
                    text = bayanString(R.string.sports_no_stats_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = bayanColors.mutedText
                )
            }

            // Explicit text/stats-only compliance badge
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bayanColors.pillBackground,
                border = BorderStroke(1.dp, bayanColors.glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = bayanColors.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = bayanString(R.string.sports_text_stats_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = bayanColors.mutedText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LineupTeamTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else bayanColors.pillBackground,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else bayanColors.mutedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun LineupCard(lineup: TeamLineup) {
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current
    val isOfficial = lineup.sourceType == LineupSourceType.OFFICIAL
    val badgeColor = if (isOfficial) EmeraldLive else bayanColors.accentBlue

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bayanColors.pillBackground,
        border = BorderStroke(1.dp, bayanColors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_lineup_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = if (isOfficial) bayanString(R.string.sports_lineup_official) else bayanString(R.string.sports_lineup_expected),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (lineup.formation.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, bayanColors.glassBorder)
                    ) {
                        Text(
                            text = bayanString(R.string.sports_formation, lineup.formation),
                            style = MaterialTheme.typography.labelSmall,
                            color = bayanColors.accentEmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (lineup.starters.isNotEmpty()) {
                Text(
                    text = bayanString(R.string.sports_starters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                lineup.starters.forEach { player ->
                    PlayerRow(player = player, language = language)
                }
            }

            if (lineup.substitutes.isNotEmpty()) {
                HorizontalDivider(color = bayanColors.glassBorder)
                Text(
                    text = bayanString(R.string.sports_substitutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = bayanColors.mutedText,
                    fontWeight = FontWeight.Bold
                )
                lineup.substitutes.forEach { player ->
                    PlayerRow(player = player, language = language)
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: LineupPlayer,
    language: com.example.model.AppLanguage
) {
    val bayanColors = LocalBayanColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bayanColors.accentBlue.copy(alpha = 0.16f))
            ) {
                Text(
                    text = player.number.ifBlank { "•" },
                    style = MaterialTheme.typography.labelSmall,
                    color = bayanColors.accentBlue,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = IraqSportsLocalizer.localizePosition(player.role, language),
            style = MaterialTheme.typography.labelSmall,
            color = bayanColors.mutedText
        )
    }
}

@Composable
private fun StatComparisonBar(stat: MatchStatRow) {
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current
    val homeNum = stat.homeValue.replace("%", "").trim().toFloatOrNull() ?: 0f
    val awayNum = stat.awayValue.replace("%", "").trim().toFloatOrNull() ?: 0f
    val total = (homeNum + awayNum).coerceAtLeast(1f)
    val fraction = (homeNum / total).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.homeValue,
                style = MaterialTheme.typography.labelLarge,
                color = bayanColors.accentBlue,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stat.localizedLabel(language),
                style = MaterialTheme.typography.labelMedium,
                color = bayanColors.mutedText
            )
            Text(
                text = stat.awayValue,
                style = MaterialTheme.typography.labelLarge,
                color = bayanColors.accentEmber,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = bayanColors.accentBlue,
            trackColor = bayanColors.accentEmber.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )
    }
}
