package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.location.LocationTracker
import com.example.data.remote.WeatherClient
import com.example.model.IraqiProvinces
import com.example.model.ProvinceWeather
import com.example.ui.LocalAppLanguage
import com.example.ui.bayanString
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalBayanColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherWidget(
    weather: ProvinceWeather?,
    selectedProvince: String,
    isWeatherLoading: Boolean,
    isDetectingLocation: Boolean,
    onLocateGranted: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onRefreshWeather: () -> Unit,
    onProvinceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onLocateGranted()
        } else {
            onLocationPermissionDenied()
        }
    }

    val activeProvinceName = weather?.provinceName ?: selectedProvince
    val displayProvince = IraqiProvinces.findByName(activeProvinceName).displayName(language)
    val locationSubtitle = when {
        isDetectingLocation -> bayanString(R.string.weather_detecting_location)
        weather?.isFromGpsLocation == true -> bayanString(R.string.weather_my_location, displayProvince)
        else -> bayanString(R.string.weather_selected_province, displayProvince)
    }

    val weatherCode = weather?.weatherCode
    val isDay = weather?.isDay ?: true
    val (weatherIcon, iconTint, atmosphericTint) = weatherVisuals(
        code = weatherCode,
        isDay = isDay,
        defaultBlue = bayanColors.accentBlue,
        defaultEmber = bayanColors.accentEmber
    )

    val conditionText = when {
        weather != null -> WeatherClient.weatherCodeToLocalized(weather.weatherCode, language)
        isWeatherLoading -> bayanString(R.string.weather_loading)
        else -> bayanString(R.string.weather_error_unavailable)
    }

    Surface(
        onClick = onProvinceClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (weather?.isFromGpsLocation == true) {
                EmeraldLive.copy(alpha = 0.42f)
            } else {
                bayanColors.accentBlue.copy(alpha = 0.30f)
            }
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_widget")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            atmosphericTint.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface,
                            bayanColors.accentBlue.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row: Location badge + Locate & Refresh actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = bayanString(R.string.cd_province_pin),
                            tint = if (weather?.isFromGpsLocation == true) EmeraldLive else bayanColors.accentEmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = locationSubtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("weather_widget_location")
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                if (LocationTracker.hasPermission(context)) {
                                    onLocateGranted()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            shape = CircleShape,
                            color = if (weather?.isFromGpsLocation == true) {
                                EmeraldLive.copy(alpha = 0.16f)
                            } else {
                                bayanColors.pillBackground
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (weather?.isFromGpsLocation == true) {
                                    EmeraldLive.copy(alpha = 0.55f)
                                } else {
                                    bayanColors.glassBorder
                                }
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("weather_widget_locate_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isDetectingLocation) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = EmeraldLive,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MyLocation,
                                        contentDescription = bayanString(R.string.cd_weather_locate),
                                        tint = if (weather?.isFromGpsLocation == true) EmeraldLive else bayanColors.accentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = bayanString(R.string.weather_locate_action),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (weather?.isFromGpsLocation == true) EmeraldLive else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefreshWeather,
                            modifier = Modifier
                                .size(38.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("weather_widget_refresh_button")
                        ) {
                            if (isWeatherLoading && !isDetectingLocation) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = bayanColors.accentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = bayanString(R.string.cd_weather_refresh),
                                    tint = bayanColors.accentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Main temperature + condition + weather icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = iconTint.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, iconTint.copy(alpha = 0.35f)),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = weatherIcon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = conditionText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("weather_widget_condition")
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IraqFlagBadge(
                                    width = 21.dp,
                                    height = 14.dp,
                                    cornerRadius = 4.dp,
                                    modifier = Modifier.testTag("weather_iraq_flag")
                                )
                                Text(
                                    text = bayanString(R.string.weather_widget_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bayanColors.mutedText
                                )
                            }
                        }
                    }

                    Text(
                        text = if (weather != null) "${weather.temperatureCelsius}°C" else "--°",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("weather_widget_temp")
                    )
                }

                // Live telemetry chips (Feels Like, Humidity, Wind) when available
                if (weather != null && (weather.apparentTempCelsius != null || weather.humidityPercent != null || weather.windSpeedKmh != null)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weather.apparentTempCelsius?.let { apparent ->
                            WeatherStatChip(
                                icon = Icons.Rounded.Thermostat,
                                label = bayanString(R.string.weather_feels_like, apparent),
                                tint = bayanColors.accentEmber
                            )
                        }
                        weather.humidityPercent?.let { humidity ->
                            WeatherStatChip(
                                icon = Icons.Rounded.WaterDrop,
                                label = bayanString(R.string.weather_humidity_format, humidity),
                                tint = bayanColors.accentBlue
                            )
                        }
                        weather.windSpeedKmh?.let { wind ->
                            WeatherStatChip(
                                icon = Icons.Rounded.Air,
                                label = bayanString(R.string.weather_wind_format, wind),
                                tint = EmeraldLive
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherStatChip(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        shape = CircleShape,
        color = bayanColors.pillBackground.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, bayanColors.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = bayanColors.mutedText
            )
        }
    }
}

private data class WeatherVisual(
    val icon: ImageVector,
    val iconTint: Color,
    val atmosphericTint: Color
)

private fun weatherVisuals(
    code: Int?,
    isDay: Boolean,
    defaultBlue: Color,
    defaultEmber: Color
): WeatherVisual = when (code) {
    0 -> if (isDay) {
        WeatherVisual(Icons.Rounded.WbSunny, defaultEmber, defaultEmber)
    } else {
        WeatherVisual(Icons.Rounded.NightsStay, defaultBlue, defaultBlue)
    }
    1, 2, 3 -> WeatherVisual(Icons.Rounded.Cloud, defaultBlue, defaultBlue)
    45, 48 -> WeatherVisual(Icons.Rounded.Grain, Color(0xFF94A3B8), Color(0xFF64748B))
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherVisual(
        Icons.Rounded.WaterDrop,
        Color(0xFF38BDF8),
        Color(0xFF0284C7)
    )
    95, 96, 99 -> WeatherVisual(
        Icons.Rounded.Thunderstorm,
        Color(0xFFA855F7),
        Color(0xFF7E22CE)
    )
    else -> WeatherVisual(Icons.Rounded.Thermostat, defaultBlue, defaultBlue)
}
