package com.example.ui.components

import com.example.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch
import com.example.ui.state.TabNavigationStyle
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
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
    PLAYLISTS("playlists", R.drawable.lumora_playlist_video, R.drawable.lumora_playlist_video),
    NETWORK("network", R.drawable.lumora_network, R.drawable.lumora_network);

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
        NETWORK -> when (lang) {
            AppLanguage.ENGLISH -> "Network"
            AppLanguage.HINGLISH -> "Network"
            AppLanguage.HINDI -> "नेटवर्क"
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
    showNetworkTab: Boolean = false,
    isDark: Boolean,
    glassBlurTransparency: Float = 75f,
    tabNavigationStyle: TabNavigationStyle = TabNavigationStyle.DEFAULT,
    lang: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier
) {
    val activeTabs = remember(showHomeTab, showMusicTab, showRecentsTab, showPlaylistsTab, showNetworkTab) {
        val list = mutableListOf<NavigationTab>()
        if (showHomeTab) list.add(NavigationTab.HOME)
        if (showMusicTab) list.add(NavigationTab.MUSIC)
        if (showRecentsTab) list.add(NavigationTab.RECENTS)
        if (showPlaylistsTab) list.add(NavigationTab.PLAYLISTS)
        if (showNetworkTab) list.add(NavigationTab.NETWORK)
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
        if (alphaFactor >= 0.99f) LightGlassSurface
        else LightGlassSurface.copy(alpha = (0.60f + 0.35f * alphaFactor).coerceIn(0.40f, 0.96f))
    }

    val borderColor = if (isDark) {
        DarkGlassBorder.copy(alpha = (0.30f + 0.40f * alphaFactor).coerceIn(0.25f, 0.85f))
    } else {
        LightGlassBorder
    }

    val pillShape = RoundedCornerShape(28.dp)

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
                    .height(56.dp)
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
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentGradient)
                        .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = AccentSkyBlue.copy(alpha = 0.4f))
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
                    .height(56.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = pillShape,
                        ambientColor = AccentSkyBlue.copy(alpha = 0.25f),
                        spotColor = AccentPink.copy(alpha = 0.30f)
                    )
                    .clip(pillShape)
                    .background(navBgColor)
                    .border(1.2.dp, borderColor, pillShape)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    // The selected tab is wider (icon + label); every other tab is icon-only and
                    // centred in its own slot. The indicator is computed from the same weights so
                    // it is always exactly centred behind the selected tab.
                    val selectedWeight = 2.25f
                    val widthDp = maxWidth.value
                    val totalWeight = (activeTabs.size - 1) + selectedWeight
                    val selectedIndex = activeTabs.indexOf(selectedTab).coerceAtLeast(0)
                    val targetLeft = widthDp * selectedIndex / totalWeight
                    val targetRight = widthDp * (selectedIndex + selectedWeight) / totalWeight
                    val restWidth = widthDp * selectedWeight / totalWeight

                    // Liquid motion: the two edges of the blob are animated independently. The
                    // leading edge snaps ahead (slightly overshooting), the trailing edge lags and
                    // catches up, so the gradient stretches while travelling and then settles.
                    val leftEdge = remember { Animatable(targetLeft) }
                    val rightEdge = remember { Animatable(targetRight) }
                    LaunchedEffect(targetLeft, targetRight, tabNavigationStyle) {
                        if (tabNavigationStyle == TabNavigationStyle.NONE) {
                            leftEdge.snapTo(targetLeft)
                            rightEdge.snapTo(targetRight)
                        } else {
                            val movingRight = targetRight >= rightEdge.value
                            val leadSpec = spring<Float>(
                                dampingRatio = if (tabNavigationStyle == TabNavigationStyle.ELASTIC_SLIDE) 0.55f else 0.72f,
                                stiffness = 520f
                            )
                            val lagSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 170f)
                            launch { leftEdge.animateTo(targetLeft, if (movingRight) lagSpec else leadSpec) }
                            launch { rightEdge.animateTo(targetRight, if (movingRight) leadSpec else lagSpec) }
                        }
                    }

                    // Slowly drifting gradient phase gives the surface a floating, liquid shimmer.
                    val gradientTransition = rememberInfiniteTransition(label = "liquidGradient")
                    val gradientPhase by gradientTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(3200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "liquidGradientPhase"
                    )

                    val indicatorLeft = leftEdge.value
                    val indicatorRight = rightEdge.value
                    val stretch = ((indicatorRight - indicatorLeft) - restWidth).coerceAtLeast(0f)
                    val squash = (stretch / widthDp).coerceIn(0f, 0.25f)
                    val indicatorShape = RoundedCornerShape(20.dp)

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (indicatorLeft + 2f).dp)
                            .width((indicatorRight - indicatorLeft - 4f).coerceAtLeast(0f).dp)
                            .height(40.dp)
                            .graphicsLayer { scaleY = 1f - squash }
                            .shadow(
                                elevation = 10.dp,
                                shape = indicatorShape,
                                ambientColor = AccentSkyBlue.copy(alpha = 0.30f),
                                spotColor = AccentPink.copy(alpha = 0.42f)
                            )
                            .clip(indicatorShape)
                            .drawBehind {
                                val w = size.width
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AccentSkyBlue, AccentPink, AccentSkyBlue),
                                        start = Offset(-w * gradientPhase, 0f),
                                        end = Offset(w * (2f - gradientPhase), size.height)
                                    )
                                )
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                                        endY = size.height * 0.65f
                                    )
                                )
                            }
                            .border(1.dp, Color.White.copy(alpha = if (isDark) 0.18f else 0.34f), indicatorShape)
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        activeTabs.forEach { tab ->
                            key(tab) {
                                val isSelected = selectedTab == tab
                                val animatedWeight by animateFloatAsState(
                                    targetValue = if (isSelected) selectedWeight else 1f,
                                    animationSpec = if (tabNavigationStyle == TabNavigationStyle.NONE) {
                                        tween(0)
                                    } else {
                                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                                    },
                                    label = "tabWeight"
                                )
                                val contentColor by animateColorAsState(
                                    targetValue = if (isSelected) Color.White else if (isDark) DarkTextSecondary else LightTextSecondary,
                                    animationSpec = tween(if (tabNavigationStyle == TabNavigationStyle.NONE) 0 else 200),
                                    label = "tabContentColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(animatedWeight.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(23.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onTabSelected(tab) }
                                        )
                                        .testTag("bottom_nav_tab_${tab.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        StyledIcon(
                                            drawableRes = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                                            contentDescription = tab.getTitle(lang),
                                            tint = contentColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        // The label grows out of the icon to its right.
                                        AnimatedVisibility(
                                            visible = isSelected,
                                            enter = fadeIn(tween(220, delayMillis = 60)) +
                                                expandHorizontally(
                                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                                    expandFrom = Alignment.Start
                                                ),
                                            exit = fadeOut(tween(120)) +
                                                shrinkHorizontally(
                                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                                    shrinkTowards = Alignment.Start
                                                )
                                        ) {
                                            Text(
                                                text = tab.getTitle(lang),
                                                modifier = Modifier.padding(start = 6.dp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
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
    }
}
