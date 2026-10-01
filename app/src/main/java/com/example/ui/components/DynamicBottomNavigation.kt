package com.example.ui.components

import com.example.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.state.AppLanguage
import com.example.ui.state.TabNavigationStyle
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary

enum class NavigationTab(
    val id: String,
    val activeIcon: Int,
    val inactiveIcon: Int
) {
    HOME("home", R.drawable.lumora_video_library, R.drawable.lumora_video_library),
    MUSIC("music", R.drawable.lumora_music_library, R.drawable.lumora_music_library),
    RECENTS("recents", R.drawable.lumora_recent, R.drawable.lumora_recent),
    PLAYLISTS("playlists", R.drawable.lumora_playlist_video, R.drawable.lumora_playlist_video);

    fun getTitle(lang: AppLanguage = AppLanguage.ENGLISH): String = when (this) {
        HOME -> when (lang) {
            AppLanguage.ENGLISH -> "Videos"
            AppLanguage.HINGLISH -> "Videos"
            AppLanguage.HINDI -> "वीडियो"
        }
        MUSIC -> when (lang) {
            AppLanguage.ENGLISH -> "Audio"
            AppLanguage.HINGLISH -> "Music"
            AppLanguage.HINDI -> "संगीत"
        }
        RECENTS -> when (lang) {
            AppLanguage.ENGLISH -> "Recents"
            AppLanguage.HINGLISH -> "Recents"
            AppLanguage.HINDI -> "हाल ही में"
        }
        PLAYLISTS -> when (lang) {
            AppLanguage.ENGLISH -> "Playlists"
            AppLanguage.HINGLISH -> "Playlists"
            AppLanguage.HINDI -> "प्लेलिस्ट"
        }
    }
}

/**
 * Dynamic Floating Pill-Shaped Bottom Navigation Bar.
 * Controlled by the 4 visibility toggles in Settings.
 * - All 4 OFF: Hides navigation completely.
 * - 1 tab enabled: Shows compact single-tab floating indicator.
 * - 2+ tabs enabled: Redistributes width evenly across the pill container with liquid-glass styling.
 */
@Composable
fun DynamicBottomNavigation(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    showHomeTab: Boolean,
    showMusicTab: Boolean,
    showRecentsTab: Boolean,
    showPlaylistsTab: Boolean,
    isDark: Boolean,
    glassBlurTransparency: Float = 75f,
    tabNavigationStyle: TabNavigationStyle = TabNavigationStyle.DEFAULT,
    lang: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier
) {
    val activeTabs = remember(showHomeTab, showMusicTab, showRecentsTab, showPlaylistsTab) {
        val list = mutableListOf<NavigationTab>()
        if (showHomeTab) list.add(NavigationTab.HOME)
        if (showMusicTab) list.add(NavigationTab.MUSIC)
        if (showRecentsTab) list.add(NavigationTab.RECENTS)
        if (showPlaylistsTab) list.add(NavigationTab.PLAYLISTS)
        list
    }

    // If all toggles are OFF, hide navigation completely
    if (activeTabs.isEmpty()) {
        return
    }

    val alphaFactor = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f)

    // Glass Background and Border Colors matching Liquid Glass design language
    val navBgColor = if (isDark) {
        if (alphaFactor >= 0.99f) Color(0xFF0B1120)
        else Color(0xFF0B1120).copy(alpha = (0.55f + 0.40f * alphaFactor).coerceIn(0.35f, 0.95f))
    } else {
        if (alphaFactor >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.60f + 0.35f * alphaFactor).coerceIn(0.40f, 0.96f))
    }

    val borderColor = if (isDark) {
        DarkGlassBorder.copy(alpha = (0.30f + 0.40f * alphaFactor).coerceIn(0.25f, 0.85f))
    } else {
        LightGlassBorder.copy(alpha = (0.35f + 0.45f * alphaFactor).coerceIn(0.30f, 0.90f))
    }

    val pillShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (activeTabs.size == 1) {
            // Full-Width Single-Tab Bar with centered icon matching the 4-tab capsule width
            val singleTab = activeTabs.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(64.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = pillShape,
                        ambientColor = AccentSkyBlue.copy(alpha = 0.25f),
                        spotColor = AccentPink.copy(alpha = 0.30f)
                    )
                    .clip(pillShape)
                    .background(navBgColor)
                    .border(1.2.dp, borderColor, pillShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(AccentGradient)
                        .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = AccentSkyBlue.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(singleTab) }
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("bottom_nav_tab_${singleTab.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        StyledIcon(
                            drawableRes = singleTab.activeIcon,
                            contentDescription = singleTab.getTitle(lang),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = singleTab.getTitle(lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // Multi-Tab Liquid-Glass Floating Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(64.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = pillShape,
                        ambientColor = AccentSkyBlue.copy(alpha = 0.25f),
                        spotColor = AccentPink.copy(alpha = 0.30f)
                    )
                    .clip(pillShape)
                    .background(navBgColor)
                    .border(1.2.dp, borderColor, pillShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // A single shared liquid-glass indicator travels between tabs. Rendering one
                // surface (instead of four independently fading backgrounds) makes the gradient
                // visibly flow to the next tab when the user swipes/taps.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabWidth = maxWidth / activeTabs.size
                    val selectedIndex = activeTabs.indexOf(selectedTab).coerceAtLeast(0)
                    val indicatorWidth = tabWidth * 0.92f
                    val indicatorX = tabWidth * selectedIndex + (tabWidth - indicatorWidth) / 2f
                    val animatedIndicatorX by animateDpAsState(
                        targetValue = indicatorX,
                        animationSpec = when (tabNavigationStyle) {
                            TabNavigationStyle.NONE -> tween(0)
                            TabNavigationStyle.ELASTIC_SLIDE -> tween(320, easing = FastOutSlowInEasing)
                            TabNavigationStyle.DEPTH_ZOOM -> tween(300, easing = FastOutSlowInEasing)
                            TabNavigationStyle.FLIP_FADE -> tween(270, easing = FastOutSlowInEasing)
                            TabNavigationStyle.MINIMAL_FADE, TabNavigationStyle.DEFAULT -> tween(300, easing = FastOutSlowInEasing)
                        },
                        label = "liquidIndicatorX"
                    )
                    val indicatorScale by animateFloatAsState(
                        targetValue = if (tabNavigationStyle == TabNavigationStyle.ELASTIC_SLIDE) 1.025f else 1f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "liquidIndicatorScale"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = animatedIndicatorX)
                            .width(indicatorWidth)
                            .height(44.dp)
                            .scale(indicatorScale)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AccentGradient)
                            .border(1.dp, Color.White.copy(alpha = if (isDark) 0.18f else 0.34f), RoundedCornerShape(22.dp))
                            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = AccentPink.copy(alpha = 0.42f))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeTabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val itemWeight = 1f / activeTabs.size

                        // The selected-tab motion is deliberately non-bouncy. It stays fluid even
                        // on lower-end devices and honours the user's Appearance > Animation style.
                        val targetScale = when (tabNavigationStyle) {
                            TabNavigationStyle.ELASTIC_SLIDE -> if (isSelected) 1.035f else 0.975f
                            TabNavigationStyle.DEPTH_ZOOM -> if (isSelected) 1.02f else 0.965f
                            TabNavigationStyle.FLIP_FADE -> if (isSelected) 1.015f else 0.98f
                            TabNavigationStyle.MINIMAL_FADE, TabNavigationStyle.DEFAULT -> if (isSelected) 1.01f else 0.99f
                            TabNavigationStyle.NONE -> 1f
                        }
                        val scale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = when (tabNavigationStyle) {
                                TabNavigationStyle.ELASTIC_SLIDE -> tween(300, easing = FastOutSlowInEasing)
                                TabNavigationStyle.DEPTH_ZOOM -> tween(240, easing = FastOutSlowInEasing)
                                TabNavigationStyle.FLIP_FADE -> tween(220, easing = FastOutSlowInEasing)
                                TabNavigationStyle.MINIMAL_FADE, TabNavigationStyle.DEFAULT -> tween(200, easing = FastOutSlowInEasing)
                                TabNavigationStyle.NONE -> tween(0)
                            },
                            label = "tabScale"
                        )

                        val itemTranslationY by animateFloatAsState(
                            targetValue = when (tabNavigationStyle) {
                                TabNavigationStyle.DEPTH_ZOOM -> if (isSelected) -1.5f else 0f
                                TabNavigationStyle.FLIP_FADE -> if (isSelected) -1f else 0f
                                else -> 0f
                            },
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "tabTranslation"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .scale(scale)
                                .graphicsLayer {
                                    translationY = itemTranslationY
                                }
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(tab) }
                                )
                                .testTag("bottom_nav_tab_${tab.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            // The shared liquid indicator is rendered behind this row.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                StyledIcon(
                                    drawableRes = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                                    contentDescription = tab.getTitle(lang),
                                    tint = if (isSelected) Color.White else if (isDark) DarkTextSecondary else LightTextSecondary,
                                    modifier = Modifier.size(if (isSelected) 22.dp else 20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.getTitle(lang),
                                    fontSize = if (isSelected) 11.sp else 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else if (isDark) DarkTextSecondary else LightTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
