package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.BayanTab
import com.example.ui.theme.LocalBayanColors

@Composable
fun FloatingNavBar(
    activeTab: BayanTab,
    savedCount: Int,
    onTabSelected: (BayanTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = bayanColors.glassSurface,
            shadowElevation = 14.dp,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, bayanColors.accentBlue.copy(alpha = 0.25f)),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBarItem(
                    tab = BayanTab.HOME,
                    label = stringResource(R.string.tab_home),
                    selectedIcon = Icons.Rounded.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = activeTab == BayanTab.HOME,
                    onClick = { onTabSelected(BayanTab.HOME) },
                    modifier = Modifier.weight(1f)
                )
                NavBarItem(
                    tab = BayanTab.BREAKING,
                    label = stringResource(R.string.tab_breaking),
                    selectedIcon = Icons.Rounded.LocalFireDepartment,
                    unselectedIcon = Icons.Outlined.LocalFireDepartment,
                    isSelected = activeTab == BayanTab.BREAKING,
                    onClick = { onTabSelected(BayanTab.BREAKING) },
                    modifier = Modifier.weight(1f)
                )
                NavBarItem(
                    tab = BayanTab.SAVED,
                    label = if (savedCount > 0) {
                        "${stringResource(R.string.tab_saved)} ($savedCount)"
                    } else {
                        stringResource(R.string.tab_saved)
                    },
                    selectedIcon = Icons.Rounded.Bookmark,
                    unselectedIcon = Icons.Outlined.BookmarkBorder,
                    isSelected = activeTab == BayanTab.SAVED,
                    onClick = { onTabSelected(BayanTab.SAVED) },
                    modifier = Modifier.weight(1f)
                )
                NavBarItem(
                    tab = BayanTab.SETTINGS,
                    label = stringResource(R.string.tab_settings),
                    selectedIcon = Icons.Rounded.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    isSelected = activeTab == BayanTab.SETTINGS,
                    onClick = { onTabSelected(BayanTab.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: BayanTab,
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bayanColors = LocalBayanColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("nav_tab_${tab.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = if (isSelected) bayanColors.accentBlue else bayanColors.mutedText,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (isSelected) bayanColors.accentEmber else bayanColors.mutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
