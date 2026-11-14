package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.ui.theme.LocalBayanColors
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object BayanImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildLoader(appContext: Context): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) BayanIraqNews/1.0")
                    .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("bayan_image_cache"))
                    .maxSizeBytes(40L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(220)
            .build()
    }
}

@Composable
fun BayanNewsImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onLoadFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { BayanImageLoader.get(context) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }

    val request = remember(imageUrl, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    Box(modifier = modifier) {
        if (isLoading) {
            LoadingShimmerBox()
        }

        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = {
                isLoading = false
                onLoadFailed()
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        )
    }
}

@Composable
private fun LoadingShimmerBox() {
    val bayanColors = LocalBayanColors.current
    val transition = rememberInfiniteTransition(label = "news_img_shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "news_img_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(shimmerAlpha)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        bayanColors.pillBackground,
                        bayanColors.accentBlue.copy(alpha = 0.14f),
                        bayanColors.pillBackground
                    )
                )
            )
    )
}
