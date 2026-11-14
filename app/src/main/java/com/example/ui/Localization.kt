package com.example.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.model.AppLanguage
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ARABIC }
val LocalLocalizedResources = staticCompositionLocalOf<Resources?> { null }

fun Context.resourcesForLanguage(language: AppLanguage): Resources {
    val locale = Locale.forLanguageTag(language.localeTag)
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(config).resources
}

@Composable
@ReadOnlyComposable
fun bayanString(@StringRes id: Int, vararg formatArgs: Any): String {
    val res = LocalLocalizedResources.current ?: LocalContext.current.resources
    return if (formatArgs.isEmpty()) {
        res.getString(id)
    } else {
        res.getString(id, *formatArgs)
    }
}
