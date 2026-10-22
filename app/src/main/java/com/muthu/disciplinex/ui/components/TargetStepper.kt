package com.muthu.disciplinex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary

/** Big number with -big / -1 / +1 / +big buttons (big = 5 by default), used for the reps or minutes target. */
@Composable
fun TargetStepper(
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bigStep: Int = 5
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StepButton("\u2212$bigStep", enabled && value > min) { onValueChange((value - bigStep).coerceAtLeast(min)) }
        StepButton("\u22121", enabled && value > min) { onValueChange((value - 1).coerceAtLeast(min)) }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 32.sp, color = TextPrimary)
            Text(unit, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        StepButton("+1", enabled && value < max) { onValueChange((value + 1).coerceAtMost(max)) }
        StepButton("+$bigStep", enabled && value < max) { onValueChange((value + bigStep).coerceAtMost(max)) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(width = 46.dp, height = 44.dp)
    ) {
        Text(label, color = if (enabled) OrangeEnd else TextMuted, fontSize = 13.sp)
    }
}
