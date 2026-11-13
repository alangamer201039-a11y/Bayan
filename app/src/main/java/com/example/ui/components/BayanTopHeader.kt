package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.NewsCategory
import com.example.model.ProvinceWeather
import com.example.ui.theme.LocalBayanColors

@Composable
fun BayanTopHeader(
    selectedProvince: String,
    weather: ProvinceWeather?,
    isDarkTheme: Boolean,
    isLoading: Boolean,
    isSearchVisible: Boolean,
    searchQuery: String,
    showCategories: Boolean,
    selectedCategory: NewsCategory,
    onBrandClick: () -> Unit,
    onProvinceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchToggleClick: () -> Unit,
    onThemeToggleClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCategorySelected: (NewsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bayanColors.glassSurface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBrandClick)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("brand_logo")
                    ) {
                        Text(
                            text = stringResource(R.string.brand_title),
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(16.dp)
                                .background(bayanColors.glassBorder)
                        )
                        Text(
                            text = stringResource(R.string.brand_region),
                            style = MaterialTheme.typography.titleMedium,
                            color = bayanColors.accentEmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Weather & Governorate Pill
                    Surface(
                        onClick = onProvinceClick,
                        shape = CircleShape,
                        color = bayanColors.pillBackground.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, bayanColors.glassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("weather_province_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = stringResource(R.string.cd_province_pin),
                                tint = bayanColors.accentEmber,
                                modifier = Modifier.size(14.dp)
                            )
                            val label = if (weather != null && weather.provinceName == selectedProvince) {
                                "$selectedProvince ${weather.temperatureCelsius}°"
                            } else {
                                selectedProvince
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = bayanColors.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderIconCircle(
                        onClick = onRefreshClick,
                        contentDescription = stringResource(R.string.action_refresh),
                        testTag = "refresh_button"
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = bayanColors.accentBlue,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (isLoading) rotation else 0f)
                        )
                    }

                    HeaderIconCircle(
                        onClick = onSearchToggleClick,
                        contentDescription = stringResource(R.string.action_search),
                        testTag = "search_toggle_button",
                        isActive = isSearchVisible
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = if (isSearchVisible) bayanColors.accentEmber else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    HeaderIconCircle(
                        onClick = onThemeToggleClick,
                        contentDescription = stringResource(R.string.action_toggle_theme),
                        testTag = "theme_toggle_button"
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = bayanColors.accentEmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expandable Search Bar
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(bayanColors.pillBackground)
                            .border(
                                width = 1.dp,
                                color = if (searchQuery.isNotEmpty()) bayanColors.accentEmber else bayanColors.glassBorder,
                                shape = CircleShape
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = bayanColors.mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = bayanColors.mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                cursorBrush = SolidColor(bayanColors.accentEmber),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_clear_search),
                                    tint = bayanColors.mutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Horizontal Category Filter Chips
            AnimatedVisibility(
                visible = showCategories,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NewsCategory.entries, key = { it.id }) { category ->
                        val selected = category == selectedCategory
                        Surface(
                            onClick = { onCategorySelected(category) },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else bayanColors.pillBackground.copy(alpha = 0.85f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else bayanColors.glassBorder
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("category_chip_${category.id}")
                        ) {
                            Text(
                                text = category.labelAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else bayanColors.mutedText,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Bottom hairline border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(bayanColors.glassBorder)
            )
        }
    }
}

@Composable
private fun HeaderIconCircle(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bayanColors.pillBackground.copy(alpha = 0.9f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) bayanColors.accentEmber.copy(alpha = 0.7f) else bayanColors.glassBorder
        ),
        modifier = Modifier
            .size(40.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
