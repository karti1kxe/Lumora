package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.NetworkBypass

/**
 * Network > Media: the settings that run in the background while provider pages and videos open.
 * AntiDPI Mode (splits the first packet so an ISP cannot block the site by its name) and DNS over HTTPS.
 */
@Composable
fun NetworkBypassCard(isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    var antiDpi by remember { mutableStateOf(NetworkBypass.isAntiDpiEnabled(context)) }
    var doh by remember { mutableStateOf(NetworkBypass.isDohEnabled(context)) }
    var dohProvider by remember { mutableIntStateOf(NetworkBypass.dohProviderIndex(context)) }
    var method by remember { mutableIntStateOf(NetworkBypass.method(context)) }
    var expanded by remember { mutableStateOf(false) }
    val activePort by NetworkBypass.activePort.collectAsState()

    // the proxy follows the saved settings as soon as this card is shown
    androidx.compose.runtime.LaunchedEffect(Unit) { NetworkBypass.refresh(context) }

    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledIcon(
                drawableRes = R.drawable.lumora_shield_check,
                contentDescription = null,
                tint = secondaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Network", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                Text(
                    text = listOfNotNull(
                        if (antiDpi) "AntiDPI" else null,
                        if (doh) "DNS over HTTPS" else null
                    ).joinToString(" · ").ifEmpty { "Bypass is off" } + if (expanded) "" else "  ▾",
                    fontSize = 12.5.sp,
                    color = secondaryText
                )
            }
            if (activePort > 0) PortChip(activePort)
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                title = "AntiDPI Mode",
                subtitle = "Bypass ISP blocking without VPN",
                checked = antiDpi,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {
                antiDpi = it
                NetworkBypass.setAntiDpiEnabled(context, it)
            }
            if (antiDpi) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Method", fontSize = 12.5.sp, color = secondaryText, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("TLS + TCP split", method == NetworkBypass.METHOD_TLS_AND_TCP, primaryText, borderColor) {
                        method = NetworkBypass.METHOD_TLS_AND_TCP
                        NetworkBypass.setMethod(context, method)
                    }
                    ChoiceChip("TCP split", method == NetworkBypass.METHOD_TCP_ONLY, primaryText, borderColor) {
                        method = NetworkBypass.METHOD_TCP_ONLY
                        NetworkBypass.setMethod(context, method)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            SettingRow(
                title = "DNS over HTTPS",
                subtitle = "Site names are looked up through a secure DNS, not the ISP's",
                checked = doh,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {
                doh = it
                NetworkBypass.setDohEnabled(context, it)
            }
            if (doh) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NetworkBypass.dohProviders.forEachIndexed { i, p ->
                        ChoiceChip(p.label, dohProvider == i, primaryText, borderColor) {
                            dohProvider = i
                            NetworkBypass.setDohProvider(context, i)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortChip(port: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentSkyBlue.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = "Active :$port", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentSkyBlue)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryText)
            Text(text = subtitle, fontSize = 12.5.sp, color = secondaryText)
        }
        Spacer(modifier = Modifier.size(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentSkyBlue, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, primaryText: Color, borderColor: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AccentSkyBlue.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) AccentSkyBlue else borderColor, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AccentSkyBlue else primaryText
        )
    }
}
