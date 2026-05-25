package com.karnadigital.omnisuite.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.ui.theme.OmniColors

/**
 * Supported root navigation destinations within the 5-tab cockpit shell.
 */
enum class HomeTab {
    Home,
    Tools,
    Files,
    History,
    Settings
}

private data class NavItemData(
    val tab: HomeTab,
    val icon: ImageVector,
    val label: String
)

/**
 * Premium 5-tab custom navigation bar conforming exactly to the redesign visual specs.
 * Implements AccentGlow pill indicators and deep slate-navy backgrounds.
 */
@Composable
fun OmniBottomNav(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItemData(HomeTab.Home, Icons.Rounded.Home, "Home"),
        NavItemData(HomeTab.Tools, Icons.Rounded.Build, "Tools"),
        NavItemData(HomeTab.Files, Icons.Rounded.Folder, "Files"),
        NavItemData(HomeTab.History, Icons.Rounded.AccessTime, "History")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OmniColors.Surface)
            .border(width = 1.dp, color = OmniColors.Border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { item ->
            val isActive = selectedTab == item.tab
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTabSelected(item.tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) OmniColors.AccentGlow else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isActive) OmniColors.Accent else OmniColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) OmniColors.Accent else OmniColors.TextMuted
                )
            }
        }
    }
}
