package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/GradesScreen.kt
// Upstream authors and contributors. ExamItemUi, ExamItemRow, ExamDetailRow
// adapted only to local card/theme dependencies.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class ExamItemUi(
    val courseName: String,
    val examTime: String,
    val location: String,
    val seatNumber: String,
    val examName: String,
    val teacher: String,
    val sourceId: String = "",
    val recordId: String = "",
    val manual: Boolean = false,
    val needsRelink: Boolean = false
)

@Composable
internal fun ExamItemRow(
    exam: ExamItemUi
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = exam.courseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(exam.examName.ifBlank { "考试" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        ExamDetailRow(
            icon = Icons.Default.Schedule,
            text = exam.examTime.ifBlank { "未提供考试时间" }
        )
        ExamDetailRow(
            icon = Icons.Default.LocationOn,
            text = buildString {
                append(exam.location.ifBlank { "未提供地点" })
                if (exam.seatNumber.isNotBlank()) {
                    append(" · 座位 ${exam.seatNumber}")
                }
            }
        )
        ExamDetailRow(
            icon = Icons.Default.Person,
            text = exam.teacher.ifBlank { "未提供教师信息" }
        )
        }
    }
}

@Composable
private fun ExamDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
