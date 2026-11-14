package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.bayanString
import com.example.ui.theme.LocalBayanColors

@Composable
fun IraqFlagBadge(
    modifier: Modifier = Modifier,
    width: Dp = 34.dp,
    height: Dp = 23.dp,
    cornerRadius: Dp = 6.dp
) {
    val bayanColors = LocalBayanColors.current
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        border = BorderStroke(1.dp, bayanColors.glassBorder.copy(alpha = 0.9f)),
        shadowElevation = 3.dp,
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.ic_iraq_flag),
                contentDescription = bayanString(R.string.cd_iraq_flag),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle diagonal satin highlight for a polished glass-crest finish
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f)
                            )
                        )
                    )
            )
        }
    }
}
