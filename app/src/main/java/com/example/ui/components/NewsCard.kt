package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.SourceTrustLevel
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@Composable
fun FeaturedHeroCard(
    article: NewsArticle,
    onArticleClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val categoryLabel = NewsCategory.fromId(article.categoryId).labelAr

    Surface(
        onClick = onArticleClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.3f)),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("featured_article_card_${article.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_iraq_editorial_banner),
                contentDescription = stringResource(R.string.cd_editorial_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0F1C).copy(alpha = 0.35f),
                                Color(0xFF0A0F1C).copy(alpha = 0.88f),
                                Color(0xFF0A0F1C).copy(alpha = 0.97f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (article.isBreaking) {
                            BreakingPulseBadge()
                        }
                        if (article.trustLevel != SourceTrustLevel.GENERAL) {
                            SourceTrustBadge(trustLevel = article.trustLevel, onDarkSurface = true)
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB).copy(alpha = 0.30f),
                            border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_button_${article.id}")
                    ) {
                        Icon(
                            imageVector = if (article.isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_bookmark),
                            tint = if (article.isSaved) bayanColors.accentEmber else Color.White
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = article.source,
                            style = MaterialTheme.typography.labelMedium,
                            color = bayanColors.accentEmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = article.relativeTimeAr(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    onArticleClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val categoryLabel = NewsCategory.fromId(article.categoryId).labelAr

    Surface(
        onClick = onArticleClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (article.isBreaking) bayanColors.accentEmber.copy(alpha = 0.4f) else bayanColors.glassBorder
        ),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("article_card_${article.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (article.isBreaking) {
                        BreakingPulseBadge()
                    }
                    if (article.trustLevel != SourceTrustLevel.GENERAL) {
                        SourceTrustBadge(trustLevel = article.trustLevel)
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("save_button_${article.id}")
                ) {
                    Icon(
                        imageVector = if (article.isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.cd_bookmark),
                        tint = if (article.isSaved) bayanColors.accentEmber else bayanColors.mutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = bayanColors.mutedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (article.sourceDomain.isNotBlank()) {
                        "${article.source} • ${article.sourceDomain}"
                    } else {
                        article.source
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = bayanColors.accentEmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = bayanColors.mutedText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = article.relativeTimeAr(),
                        style = MaterialTheme.typography.labelSmall,
                        color = bayanColors.mutedText
                    )
                }
            }
        }
    }
}

@Composable
fun SourceTrustBadge(
    trustLevel: SourceTrustLevel,
    onDarkSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val isOfficial = trustLevel == SourceTrustLevel.OFFICIAL
    val badgeColor = if (isOfficial) EmeraldLive else bayanColors.accentBlue
    val label = when (trustLevel) {
        SourceTrustLevel.OFFICIAL -> stringResource(R.string.trust_official_badge)
        SourceTrustLevel.VERIFIED -> stringResource(R.string.trust_verified_badge)
        SourceTrustLevel.GENERAL -> stringResource(R.string.trust_general_badge)
    }

    Surface(
        shape = CircleShape,
        color = badgeColor.copy(alpha = if (onDarkSurface) 0.25f else 0.14f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = stringResource(R.string.cd_verified_source),
                tint = if (onDarkSurface && !isOfficial) Color(0xFF93C5FD) else badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (onDarkSurface && !isOfficial) Color(0xFF93C5FD) else badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BreakingPulseBadge(modifier: Modifier = Modifier) {
    val bayanColors = LocalBayanColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "breaking_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        shape = CircleShape,
        color = bayanColors.accentEmber.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, bayanColors.accentEmber.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(bayanColors.accentEmber)
            )
            Text(
                text = stringResource(R.string.breaking_badge),
                style = MaterialTheme.typography.labelSmall,
                color = bayanColors.accentEmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
