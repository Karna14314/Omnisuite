package com.karnadigital.omnisuite.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.ui.theme.OmniColors

/**
 * Curated horizontal carousel document card representing recently sandboxed items.
 * Compliance targets for 130dp width and color-coded type indicator tags.
 */
@Composable
fun RecentFileChip(
    file: RecentFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (typeLabel, themeColor, categoryBg) = when {
        file.mimeType.contains("pdf", ignoreCase = true) -> Triple("PDF", OmniColors.PdfRed, OmniColors.PdfRedBg)
        file.mimeType.contains("sheet", ignoreCase = true) || file.mimeType.contains("excel", ignoreCase = true) || file.mimeType.contains("csv", ignoreCase = true) -> Triple("XLS", OmniColors.XlsGreen, OmniColors.XlsGreenBg)
        file.mimeType.contains("word", ignoreCase = true) || file.mimeType.contains("document", ignoreCase = true) -> Triple("DOC", OmniColors.DocBlue, OmniColors.DocBlueBg)
        file.mimeType.contains("presentation", ignoreCase = true) || file.mimeType.contains("powerpoint", ignoreCase = true) -> Triple("PPT", Color(0xFFF59E0B), Color(0x1FF59E0B))
        file.mimeType.contains("zip", ignoreCase = true) -> Triple("ZIP", OmniColors.ArcCyan, OmniColors.ArcCyanBg)
        else -> Triple("TXT", OmniColors.TextMuted, Color(0x1F7A8299))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = OmniColors.Surface2
        ),
        modifier = modifier
            .width(130.dp)
            .height(110.dp)
            .border(1.dp, OmniColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(categoryBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = typeLabel,
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
            Column {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OmniColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(file.fileSize),
                    fontSize = 9.sp,
                    color = OmniColors.TextMuted
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
