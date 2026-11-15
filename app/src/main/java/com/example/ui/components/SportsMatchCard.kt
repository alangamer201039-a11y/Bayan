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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.IraqSportsLocalizer
import com.example.model.LineupSourceType
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import com.example.ui.LocalAppLanguage
import com.example.ui.bayanString
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@Composable
fun SportsMatchCard(
    match: SportsMatch,
    onMatchClick: (SportsMatch) -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current
    val nowMs = System.currentTimeMillis()

    val statusColor = when (match.status) {
        MatchStatus.LIVE -> EmeraldLive
        MatchStatus.SCHEDULED -> bayanColors.accentBlue
        MatchStatus.FINISHED -> bayanColors.accentEmber
        MatchStatus.POSTPONED -> bayanColors.mutedText
    }

    val statusLabel = when (match.status) {
        MatchStatus.LIVE -> match.statusDetail.ifBlank { bayanString(R.string.sports_status_live) }
        MatchStatus.SCHEDULED -> bayanString(R.string.sports_status_upcoming)
        MatchStatus.FINISHED -> bayanString(R.string.sports_status_finished)
        MatchStatus.POSTPONED -> bayanString(R.string.sports_status_postponed)
    }

    val lineupBadgeText = when (match.lineupSourceType()) {
        LineupSourceType.OFFICIAL -> bayanString(R.string.sports_lineup_official)
        LineupSourceType.EXPECTED -> bayanString(R.string.sports_lineup_expected)
        LineupSourceType.UNAVAILABLE -> bayanString(R.string.sports_view_lineup_stats)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (match.status == MatchStatus.LIVE) EmeraldLive.copy(alpha = 0.55f) else bayanColors.glassBorder
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onMatchClick(match) }
            .testTag("sports_match_card_${match.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar: Competition + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsSoccer,
                        contentDescription = null,
                        tint = bayanColors.accentEmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = match.localizedCompetition(language),
                        style = MaterialTheme.typography.labelMedium,
                        color = bayanColors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Baghdad Date/Time strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = bayanColors.accentBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = IraqSportsLocalizer.formatBaghdadDateTime(match.kickoffEpochMs, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = bayanColors.accentBlue,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sports_match_time_${match.id}")
                )
            }

            // Teams & Score / Kickoff Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TeamColumn(
                    initials = match.homeInitials(),
                    teamName = match.localizedHomeTeam(language),
                    accentColor = bayanColors.accentBlue,
                    isIraqTeam = match.homeTeamRaw.equals("Iraq", ignoreCase = true),
                    modifier = Modifier.weight(1f)
                )

                // Center Score / Time badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (match.status == MatchStatus.FINISHED || match.status == MatchStatus.LIVE) {
                            val hScore = match.homeScore?.toString() ?: "0"
                            val aScore = match.awayScore?.toString() ?: "0"
                            Text(
                                text = "$hScore - $aScore",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = if (match.status == MatchStatus.LIVE) EmeraldLive else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("sports_match_score_${match.id}")
                            )
                            Text(
                                text = if (match.status == MatchStatus.FINISHED) {
                                    bayanString(R.string.sports_status_finished)
                                } else {
                                    match.statusDetail.ifBlank { bayanString(R.string.sports_status_live) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = IraqSportsLocalizer.formatBaghdadTimeOnly(match.kickoffEpochMs, language),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = IraqSportsLocalizer.formatCountdownBadge(match.kickoffEpochMs, nowMs, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = bayanColors.accentBlue,
                                maxLines = 1
                            )
                        }
                    }
                }

                TeamColumn(
                    initials = match.awayInitials(),
                    teamName = match.localizedAwayTeam(language),
                    accentColor = bayanColors.accentEmber,
                    isIraqTeam = match.awayTeamRaw.equals("Iraq", ignoreCase = true),
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom strip: Venue + Lineups & Stats CTA chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (match.venue.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.Stadium,
                            contentDescription = null,
                            tint = bayanColors.mutedText,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = match.venue,
                            style = MaterialTheme.typography.labelSmall,
                            color = bayanColors.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (match.roundLabel.isNotBlank()) {
                        Text(
                            text = match.roundLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = bayanColors.mutedText
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = when (match.lineupSourceType()) {
                        LineupSourceType.OFFICIAL -> EmeraldLive.copy(alpha = 0.14f)
                        LineupSourceType.EXPECTED -> bayanColors.accentBlue.copy(alpha = 0.14f)
                        LineupSourceType.UNAVAILABLE -> bayanColors.pillBackground
                    },
                    border = BorderStroke(
                        1.dp,
                        when (match.lineupSourceType()) {
                            LineupSourceType.OFFICIAL -> EmeraldLive.copy(alpha = 0.45f)
                            LineupSourceType.EXPECTED -> bayanColors.accentBlue.copy(alpha = 0.40f)
                            LineupSourceType.UNAVAILABLE -> bayanColors.glassBorder
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = when (match.lineupSourceType()) {
                                LineupSourceType.OFFICIAL -> EmeraldLive
                                LineupSourceType.EXPECTED -> bayanColors.accentBlue
                                LineupSourceType.UNAVAILABLE -> bayanColors.mutedText
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = lineupBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (match.lineupSourceType()) {
                                LineupSourceType.OFFICIAL -> EmeraldLive
                                LineupSourceType.EXPECTED -> bayanColors.accentBlue
                                LineupSourceType.UNAVAILABLE -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamColumn(
    initials: String,
    teamName: String,
    accentColor: Color,
    isIraqTeam: Boolean,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isIraqTeam) {
            IraqFlagBadge(
                width = 40.dp,
                height = 28.dp,
                cornerRadius = 7.dp
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            text = teamName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(bayanColors.glassBorder, CircleShape)
        )
    }
}
