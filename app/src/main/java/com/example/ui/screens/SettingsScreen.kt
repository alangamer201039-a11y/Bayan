package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.IraqiProvinces
import com.example.model.ProvinceWeather
import com.example.model.ReadingFontSize
import com.example.model.TrustedSources
import com.example.ui.bayanString
import com.example.ui.components.SourceTrustBadge
import com.example.ui.components.WeatherWidget
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@Composable
fun SettingsScreen(
    selectedProvince: String,
    selectedLanguage: AppLanguage,
    weather: ProvinceWeather?,
    fontSize: ReadingFontSize,
    isDarkTheme: Boolean,
    verifiedSourcesOnly: Boolean,
    autoRefreshEnabled: Boolean,
    onProvinceSelected: (String) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFontSizeSelected: (ReadingFontSize) -> Unit,
    onThemeSelected: (Boolean) -> Unit,
    onVerifiedSourcesToggle: (Boolean) -> Unit,
    onAutoRefreshToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isWeatherLoading: Boolean = false,
    isDetectingLocation: Boolean = false,
    onLocateGranted: () -> Unit = {},
    onLocationPermissionDenied: () -> Unit = {},
    onRefreshWeather: () -> Unit = {}
) {
    val bayanColors = LocalBayanColors.current
    val displayedSources = TrustedSources.displayedForLanguage(selectedLanguage)
    val selectedProvinceModel = IraqiProvinces.findByName(selectedProvince)

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
            // Instant Language Switcher Card (Arabic / Sorani Kurdish)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, bayanColors.accentEmber.copy(alpha = 0.35f)),
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
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = bayanColors.accentEmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = bayanString(R.string.settings_language_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            text = bayanString(R.string.settings_language_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bayanColors.mutedText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppLanguage.entries.forEach { langOption ->
                                val isSelected = langOption == selectedLanguage
                                Surface(
                                    onClick = { onLanguageSelected(langOption) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.pillBackground,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else bayanColors.glassBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("language_option_${langOption.code}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                        }
                                        Text(
                                            text = langOption.labelNative,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldLive,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = bayanString(R.string.settings_sources_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = EmeraldLive.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = bayanString(R.string.count_sources_format, displayedSources.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLive,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = bayanString(R.string.settings_sources_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bayanColors.mutedText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bayanString(R.string.settings_verified_only_label),
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
                                text = bayanString(R.string.settings_auto_refresh_label),
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

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 500.dp) 2 else 1
                            val rows = displayedSources.chunked(columns)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rows.forEach { rowSources ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowSources.forEach { src ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = bayanColors.pillBackground,
                                                border = BorderStroke(1.dp, bayanColors.glassBorder),
                                                modifier = Modifier.weight(1f)
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
                                                            text = src.displayName(selectedLanguage),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = src.domain,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = bayanColors.mutedText,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.size(8.dp))
                                                    SourceTrustBadge(trustLevel = src.trustLevel)
                                                }
                                            }
                                        }
                                        repeat(columns - rowSources.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Governorate & Weather Selector Card (All 19 Iraqi Governorates)
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
                                text = bayanString(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            text = bayanString(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bayanColors.mutedText
                        )

                        // Live Location-Aware Weather Widget
                        WeatherWidget(
                            weather = weather,
                            selectedProvince = selectedProvince,
                            isWeatherLoading = isWeatherLoading,
                            isDetectingLocation = isDetectingLocation,
                            onLocateGranted = onLocateGranted,
                            onLocationPermissionDenied = onLocationPermissionDenied,
                            onRefreshWeather = onRefreshWeather,
                            onProvinceClick = {}
                        )

                        Text(
                            text = bayanString(R.string.settings_province_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = bayanColors.accentBlue
                        )

                        // Responsive grid of all 19 Iraqi Governorates
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = when {
                                maxWidth >= 620.dp -> 4
                                maxWidth >= 460.dp -> 3
                                else -> 2
                            }
                            val rows = IraqiProvinces.all.chunked(columns)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { province ->
                                            val isSelected = province.nameAr == selectedProvinceModel.nameAr
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
                                                        text = province.displayName(selectedLanguage),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (isSelected) bayanColors.accentEmber else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.size(4.dp))
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
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
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
                            text = bayanString(R.string.settings_font_title),
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
                                            text = sizeOption.label(selectedLanguage),
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
                                text = bayanString(R.string.settings_font_preview),
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
                            text = bayanString(R.string.settings_theme_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeOptionButton(
                                label = bayanString(R.string.settings_theme_dark),
                                icon = Icons.Rounded.DarkMode,
                                isSelected = isDarkTheme,
                                onClick = { onThemeSelected(true) },
                                testTag = "theme_option_dark",
                                modifier = Modifier.weight(1f)
                            )
                            ThemeOptionButton(
                                label = bayanString(R.string.settings_theme_light),
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
