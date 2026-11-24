package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.ui.theme.ChipBackground
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.Divider
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.OrangeStart
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.SuccessText
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary

/*
 * Small building blocks shared by the Settings screen and its sections
 * (SettingsScreen.kt, BackupCard.kt). Everything is built on the existing
 * DisciplineX theme colors; nothing here talks to settings or permissions.
 */

internal val GroupShape = RoundedCornerShape(20.dp)

/** Start inset of the row dividers: 16dp padding + 40dp icon badge + 14dp gap. */
internal val RowDividerInset = 70.dp

/** Brand orange, darkened in the light theme so small orange TEXT stays readable. */
internal val AccentText: Color
    get() = if (AppSettings.isDark) OrangeStart else Color(0xFFC93A14)

/** Red for TEXT on a light card (the plain Danger red is too pale there). */
internal val DangerText: Color
    get() = if (AppSettings.isDark) Danger else Color(0xFFC62828)

/** A titled block of settings: small muted heading, then its groups. */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        content()
    }
}

/** Smaller muted label used to split a section into two groups. */
@Composable
internal fun SettingsSubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    )
}

/** One rounded surface that holds a list of rows. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(Surface)
            .border(1.dp, Divider, GroupShape),
        content = content
    )
}

/** Thin separator between two rows of a group, inset to line up with the text. */
@Composable
internal fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = RowDividerInset),
        thickness = 1.dp,
        color = Divider
    )
}

/** Small explanation under a group. */
@Composable
internal fun GroupFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
    )
}

@Composable
internal fun IconBadge(icon: ImageVector, tint: Color = OrangeEnd) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * The layout every row shares: icon badge, title, optional supporting text,
 * optional action label and an optional trailing widget. Touch handling comes
 * in through [modifier] (see [SettingsRow] and [SettingsSwitchRow]).
 */
@Composable
internal fun RowLayout(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = OrangeEnd,
    enabled: Boolean = true,
    actionLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, iconTint)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "$actionLabel  \u203A",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentText
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

private val ChevronTrailing: @Composable () -> Unit = {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.size(22.dp)
    )
}

/** A tappable row. Pass [onClick] = null for a row that only shows information. */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = OrangeEnd,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    actionLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    RowLayout(
        icon = icon,
        title = title,
        modifier = modifier.then(interaction),
        subtitle = subtitle,
        iconTint = iconTint,
        enabled = enabled,
        actionLabel = actionLabel,
        trailing = trailing ?: (if (showChevron) ChevronTrailing else null)
    )
}

/** A row with an on/off switch; the whole row toggles it. */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    RowLayout(
        icon = icon,
        title = title,
        modifier = modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ),
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = OrangeEnd,
                    checkedThumbColor = Color.White
                )
            )
        }
    )
}

enum class PillTone { ON, OFF, NEUTRAL }

/** Small status label. It always carries words, so it never relies on color alone. */
@Composable
internal fun StatusPill(text: String, tone: PillTone) {
    val fg: Color
    val bg: Color
    when (tone) {
        PillTone.ON -> { fg = SuccessText; bg = Success.copy(alpha = 0.14f) }
        PillTone.OFF -> { fg = DangerText; bg = Danger.copy(alpha = 0.14f) }
        PillTone.NEUTRAL -> { fg = TextMuted; bg = ChipBackground }
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (tone != PillTone.NEUTRAL) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
