package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.NewsArticle
import com.example.model.NewsCategory
import com.example.model.ReadingFontSize
import com.example.model.SourceTrustLevel
import com.example.ui.LocalAppLanguage
import com.example.ui.bayanString
import com.example.ui.theme.LocalBayanColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailSheet(
    article: NewsArticle,
    fontSize: ReadingFontSize,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    isLoadingFullContent: Boolean = false
) {
    val context = LocalContext.current
    val bayanColors = LocalBayanColors.current
    val language = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoryLabel = NewsCategory.fromId(article.categoryId).label(language)
    val shareChooserTitle = bayanString(R.string.share_chooser_title)

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
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier
                        .size(38.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("close_article_sheet")
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (article.sourceDomain.isNotBlank()) {
                        "${article.source} (${article.sourceDomain})"
                    } else {
                        article.source
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = bayanColors.accentEmber
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = bayanColors.mutedText
                )
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = bayanColors.mutedText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = article.relativeTime(language),
                    style = MaterialTheme.typography.labelMedium,
                    color = bayanColors.mutedText
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = bayanColors.glassBorder
            )

            val displayBody = article.fullContent?.takeIf { it.isNotBlank() } ?: article.summary
            val paragraphs = remember(displayBody) {
                displayBody.split(Regex("\\n+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }

            if (isLoadingFullContent) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("sheet_full_article_loading")
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
                            text = bayanString(R.string.reader_loading_full_article),
                            style = MaterialTheme.typography.labelMedium,
                            color = bayanColors.mutedText
                        )
                    }
                }
            }

            if (paragraphs.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_article_body")
                ) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = fontSize.bodySp.sp,
                                lineHeight = (fontSize.bodySp * 1.85f).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.93f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleSave,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (article.isSaved) bayanColors.accentEmber else bayanColors.glassBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("sheet_save_button")
                ) {
                    Icon(
                        imageVector = if (article.isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = if (article.isSaved) bayanColors.accentEmber else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (article.isSaved) bayanString(R.string.action_saved) else bayanString(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (article.isSaved) bayanColors.accentEmber else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        val shareText = buildString {
                            append(article.title)
                            if (article.link.isNotBlank()) {
                                append("\n\n")
                                append(article.link)
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .background(bayanColors.pillBackground, RoundedCornerShape(16.dp))
                        .testTag("sheet_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = bayanString(R.string.action_share),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (article.link.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bayanColors.pillBackground,
                    border = BorderStroke(1.dp, bayanColors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = bayanString(R.string.reader_source_footer),
                            style = MaterialTheme.typography.labelMedium,
                            color = bayanColors.mutedText
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_open_source_button")
                        ) {
                            Text(
                                text = bayanString(R.string.action_open_source),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
