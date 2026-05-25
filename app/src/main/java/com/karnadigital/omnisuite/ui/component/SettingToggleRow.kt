package com.karnadigital.omnisuite.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.ui.theme.OmniColors

/**
 * Reusable Settings switch card row with customized theme support.
 * Compliant with custom switches utilizing the brand Accent Indigo color.
 */
@Composable
fun SettingToggleRow(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = OmniColors.Surface2
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OmniColors.Border, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OmniColors.Border),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OmniColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniColors.TextMuted
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OmniColors.TextPrimary,
                    checkedTrackColor = OmniColors.Accent,
                    uncheckedThumbColor = OmniColors.TextMuted,
                    uncheckedTrackColor = OmniColors.Surface
                )
            )
        }
    }
}
