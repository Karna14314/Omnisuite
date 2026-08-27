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

enum class HomeTab {
    Home,
    Tools,
    Settings
}

private data class NavItemData(
    val tab: HomeTab,
    val icon: ImageVector,
    val label: String
)

@Composable
fun OmniBottomNav(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItemData(HomeTab.Home, Icons.Rounded.Home, "Home"),
        NavItemData(HomeTab.Tools, Icons.Rounded.Build, "Tools")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OmniColors.Surface)
            .border(width = 1.dp, color = OmniColors.Border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 24.dp, vertical = 6.dp)
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
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isActive) OmniColors.Accent else OmniColors.TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) OmniColors.Accent else OmniColors.TextMuted
                )
            }
        }
    }
}
