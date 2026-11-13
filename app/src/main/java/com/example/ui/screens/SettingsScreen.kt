package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.IraqiProvinces
import com.example.model.ProvinceWeather
import com.example.model.ReadingFontSize
import com.example.model.TrustedSources
import com.example.ui.components.SourceTrustBadge
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@Composable
fun SettingsScreen(
    selectedProvince: String,
    weather: ProvinceWeather?,
    fontSize: ReadingFontSize,
    isDarkTheme: Boolean,
    verifiedSourcesOnly: Boolean,
    autoRefreshEnabled: Boolean,
    onProvinceSelected: (String) -> Unit,
    onFontSizeSelected: (ReadingFontSize) -> Unit,
    onThemeSelected: (Boolean) -> Unit,
    onVerifiedSourcesToggle: (Boolean) -> Unit,
    onAutoRefreshToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Official & Verified News Sources Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = EmeraldLive,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_sources_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_sources_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bayanColors.mutedText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_verified_only_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = verifiedSourcesOnly,
                                onCheckedChange = onVerifiedSourcesToggle,
                                modifier = Modifier.testTag("switch_verified_only")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_refresh_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoRefreshEnabled,
                                onCheckedChange = onAutoRefreshToggle,
                                modifier = Modifier.testTag("switch_auto_refresh")
                            )
                        }

                        HorizontalDivider(color = bayanColors.glassBorder)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrustedSources.all.forEach { src ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = bayanColors.pillBackground,
                                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = src.nameAr,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = src.domain,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = bayanColors.mutedText
                                            )
                                        }
                                        Spacer(modifier = Modifier.size(8.dp))
                                        SourceTrustBadge(trustLevel = src.trustLevel)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Governorate & Weather Selector Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(bayanColors.accentEmber)
                            )
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bayanColors.mutedText
                        )

                        // Live Weather Banner for Selected Province
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = bayanColors.accentEmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = selectedProvince,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = weather?.conditionAr ?: "جاري جلب حالة الطقس...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = bayanColors.mutedText
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Thermostat,
                                        contentDescription = null,
                                        tint = bayanColors.accentBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (weather != null) "${weather.temperatureCelsius}°C" else "--°",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.settings_province_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = bayanColors.accentBlue
                        )

                        // 3-column grid of Iraqi Governorates
                        val rows = IraqiProvinces.all.chunked(3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { province ->
                                        val isSelected = province.nameAr == selectedProvince
                                        Surface(
                                            onClick = { onProvinceSelected(province.nameAr) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) {
                                                bayanColors.accentEmber.copy(alpha = 0.14f)
                                            } else {
                                                bayanColors.pillBackground
                                            },
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) bayanColors.accentEmber else bayanColors.glassBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("province_chip_${province.nameAr}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = province.nameAr,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isSelected) bayanColors.accentEmber else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(bayanColors.accentEmber)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reading Font Size Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_font_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReadingFontSize.entries.forEach { sizeOption ->
                                val isSelected = sizeOption == fontSize
                                Surface(
                                    onClick = { onFontSizeSelected(sizeOption) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.pillBackground,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.glassBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("font_size_${sizeOption.key}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sizeOption.labelAr,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = bayanColors.pillBackground,
                            border = BorderStroke(1.dp, bayanColors.glassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "معاينة نص القراءة: متابعة شاملة لأبرز التطورات والأخبار العراقية المحلية والاقتصادية.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize.bodySp.sp,
                                    lineHeight = (fontSize.bodySp * 1.7f).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // Appearance Mode Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeOptionButton(
                                label = stringResource(R.string.settings_theme_dark),
                                icon = Icons.Rounded.DarkMode,
                                isSelected = isDarkTheme,
                                onClick = { onThemeSelected(true) },
                                testTag = "theme_option_dark",
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionButton(
                                label = stringResource(R.string.settings_theme_light),
                                icon = Icons.Rounded.LightMode,
                                isSelected = !isDarkTheme,
                                onClick = { onThemeSelected(false) },
                                testTag = "theme_option_light",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.pillBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.glassBorder
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else bayanColors.accentEmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
