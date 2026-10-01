package com.example.ui.components

import com.example.R

import com.example.ui.theme.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioChannelMode
import com.example.player.AudioLanguageMatcher
import com.example.player.SubtitleFontManager
import com.example.ui.state.AppLanguage
import com.example.ui.state.AppThemeVariant
import com.example.ui.state.ControlsAnimationStyle
import com.example.ui.state.GestureSensitivityMode
import com.example.ui.state.HwAccelMode
import com.example.ui.state.PlayerSettings
import com.example.ui.state.ScreenNavigationStyle
import com.example.ui.state.SeekbarStyle
import com.example.ui.state.TabNavigationStyle
import com.example.ui.state.ThemeMode
import com.example.ui.state.ThumbnailQuality
import com.example.ui.state.ThumbnailStrategy
import com.example.ui.state.UiState
import com.example.ui.state.VideoOpeningAnimation
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentPink
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.ui.screens.LocalGlassBlurTransparency
import kotlinx.coroutines.launch

import com.example.ui.state.AppStrings
import java.io.File
import kotlin.math.roundToInt

enum class SettingsCategoryTab(
    val icon: ImageVector,
    val lumoraIconRes: Int
) {
    APPEARANCE(Icons.Outlined.Palette, R.drawable.lumora_pallete_2),
    PLAYER(Icons.Outlined.PlayCircle, R.drawable.lumora_play_circle),
    AUDIO(Icons.Outlined.VolumeUp, R.drawable.lumora_audio_circle),
    SUBTITLES(Icons.Outlined.Subtitles, R.drawable.lumora_subtitles),
    DECODER(Icons.Outlined.Memory, R.drawable.lumora_cpu),
    GESTURES(Icons.Outlined.Tune, R.drawable.lumora_gesture),
    PLAYER_LAYOUT(Icons.Outlined.DashboardCustomize, R.drawable.lumora_widget_5),
    FOLDERS(Icons.Outlined.Folder, R.drawable.lumora_folder),
    ADVANCED(Icons.Outlined.Settings, R.drawable.lumora_settings_minimalistic),
    ABOUT(Icons.Outlined.Info, R.drawable.lumora_user);

    fun getTitle(lang: AppLanguage): String = when (this) {
        APPEARANCE -> when (lang) {
            AppLanguage.ENGLISH -> "Appearance"
            AppLanguage.HINGLISH -> "Appearance"
            AppLanguage.HINDI -> "दिखावट"
        }
        FOLDERS -> when (lang) {
            AppLanguage.ENGLISH -> "Folders"
            AppLanguage.HINGLISH -> "Folders"
            AppLanguage.HINDI -> "फ़ोल्डर"
        }
        PLAYER -> AppStrings.getTabPlayer(lang)
        AUDIO -> AppStrings.getTabAudio(lang)
        DECODER -> AppStrings.getTabDecoder(lang)
        SUBTITLES -> AppStrings.getTabSubtitles(lang)
        GESTURES -> AppStrings.getTabGestures(lang)
        PLAYER_LAYOUT -> AppStrings.getTabPlayerLayout(lang)
        ADVANCED -> AppStrings.getTabAdvanced(lang)
        ABOUT -> AppStrings.getTabAbout(lang)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: UiState,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onScaleChange: (Float) -> Unit = {},
    onGlassBlurTransparencyChange: (Float) -> Unit = {},
    onSideBySideChange: (Boolean) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onFrameStepChange: (Int) -> Unit = {},
    onCopyTimestampChange: (Boolean) -> Unit = {},
    onHwAccelModeChange: (HwAccelMode) -> Unit = {},
    onGestureSensitivityModeChange: (GestureSensitivityMode) -> Unit = {},
    onShowPlayerNotificationsChange: (Boolean) -> Unit = {},
    onSeekbarStyleChange: (SeekbarStyle) -> Unit = {},
    onPlayerLayoutConfigChange: (com.example.ui.state.PlayerLayoutConfig) -> Unit = {},
    onResetPlayerLayoutConfig: () -> Unit = {},
    onPreferredAudioLanguagesChange: (String) -> Unit = {},
    onApplyAudioLanguageToSelectedContentOnlyChange: (Boolean) -> Unit = {},
    onSelectedAudioFoldersChange: (Set<String>) -> Unit = {},
    onSelectedAudioVideosChange: (Set<String>) -> Unit = {},
    onEnableAudioPitchCorrectionChange: (Boolean) -> Unit = {},
    onVolumeNormalizationChange: (Boolean) -> Unit = {},
    onBackgroundPlaybackChange: (Boolean) -> Unit = {},
    onAudioChannelModeChange: (AudioChannelMode) -> Unit = {},
    onVolumeBoostCapChange: (Int) -> Unit = {},
    onRememberSelectedAudioTrackChange: (Boolean) -> Unit = {},
    onPlayerSettingsChange: (PlayerSettings) -> Unit = {},
    onAppThemeChange: (AppThemeVariant) -> Unit = {},
    onAmoledBlackModeChange: (Boolean) -> Unit = {},
    onUseSystemFontChange: (Boolean) -> Unit = {},
    onHapticFeedbackChange: (Boolean) -> Unit = {},
    onShowFullNameChange: (Boolean) -> Unit = {},
    onShowNewVideoLabelChange: (Boolean) -> Unit = {},
    onNewVideoDaysThresholdChange: (Int) -> Unit = {},
    onShowFolderUnplayedBadgeChange: (Boolean) -> Unit = {},
    onAutoScrollToLastPlayedChange: (Boolean) -> Unit = {},
    onTreePathCompressionChange: (Boolean) -> Unit = {},
    onDualPaneViewChange: (Boolean) -> Unit = {},
    onWatchedThresholdPercentChange: (Int) -> Unit = {},
    onShowVideoThumbnailsChange: (Boolean) -> Unit = {},
    onThumbnailStrategyChange: (ThumbnailStrategy) -> Unit = {},
    onThumbnailQualityChange: (ThumbnailQuality) -> Unit = {},
    onTapThumbnailToSelectChange: (Boolean) -> Unit = {},
    onShowNetworkThumbnailsChange: (Boolean) -> Unit = {},
    onShowHomeTabChange: (Boolean) -> Unit = {},
    onShowMusicTabChange: (Boolean) -> Unit = {},
    onShowRecentsTabChange: (Boolean) -> Unit = {},
    onShowPlaylistsTabChange: (Boolean) -> Unit = {},
    onControlsAnimationStyleChange: (ControlsAnimationStyle) -> Unit = {},
    onVideoOpeningAnimationChange: (VideoOpeningAnimation) -> Unit = {},
    onScreenNavigationStyleChange: (ScreenNavigationStyle) -> Unit = {},
    onTabNavigationStyleChange: (TabNavigationStyle) -> Unit = {},
    onAnimationSpeedChange: (Float) -> Unit = {},
    viewModel: com.example.ui.state.AppViewModel? = null
) {
    val configuration = LocalConfiguration.current
    val currentDensity = LocalDensity.current
    val screenHeightPx = with(currentDensity) { (configuration.screenHeightDp + 100).dp.toPx() }

    val coroutineScope = rememberCoroutineScope()
    val sheetDragOffsetY = remember { Animatable(screenHeightPx) }
    val scrimAlpha = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            scrimAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
        sheetDragOffsetY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        )
    }

    val dismissWithAnimation: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            coroutineScope.launch {
                try {
                    launch {
                        scrimAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        )
                    }
                    sheetDragOffsetY.animateTo(
                        targetValue = screenHeightPx,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                } catch (_: Throwable) {
                } finally {
                    onDismiss()
                }
            }
        }
    }

    BackHandler {
        dismissWithAnimation()
    }

    val context = LocalContext.current
    val isDark = uiState.themeMode == ThemeMode.DARK || (uiState.themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
    val lang = uiState.language
    val alphaFactor = (uiState.glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)

    // Consume all residual vertical scrolling & fling from content so it NEVER bubbles up to dismiss the sheet
    val consumeContentScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    // Calculate dynamic scaled density so the entire Settings UI scales with appScale
    val currentAppScale = uiState.appScale.coerceIn(1f, 100f)
    val scaleFactor = (0.85f + 0.15f * ((currentAppScale - 1f) / 99f)).coerceIn(0.85f, 1.00f)
    // Keep density at the requested final value for correct measurement, while animating
    // the visual transform. This prevents the settings sheet from visibly jumping when
    // the user drags the app-size slider.
    val animatedUiScale by animateFloatAsState(
        targetValue = scaleFactor,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "settings_ui_scale"
    )
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scaleFactor) {
        Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )
    }

    val pagerState = rememberPagerState(initialPage = 0) { SettingsCategoryTab.entries.size }
    val tabListState = rememberLazyListState()
    var selectedTabIdx by remember { mutableIntStateOf(pagerState.currentPage) }
    // Non-null for the whole duration of a tab-bar tap's scroll animation. While set, it is the
    // single source of truth for which tab is highlighted, so the passive "follow the pager"
    // effect below can never momentarily reassign the highlight to an in-between page while the
    // animation is still mid-flight (that was the cause of the previously-selected tab briefly
    // flashing colored again right after tapping a different one, before the tapped tab settled).
    var pendingTapTarget by remember { mutableStateOf<Int?>(null) }
    val activeTabIdx = pendingTapTarget
        ?: if (pagerState.isScrollInProgress) pagerState.targetPage else selectedTabIdx

    // Smoothly keep the tab list scrolled into view when pager changes
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIdx = pagerState.currentPage
        if (pendingTapTarget == pagerState.currentPage) pendingTapTarget = null
        tabListState.animateScrollToItem((pagerState.currentPage - 1).coerceAtLeast(0))
    }
    var showAudioFolderSelectorSheet by remember { mutableStateOf(false) }
    var showSubtitleFolderSelectorSheet by remember { mutableStateOf(false) }
    var isPreferredLanguagesExpanded by remember { mutableStateOf(false) }

    // Subtitle font directory picker & individual font files picker.
    var fontListVersion by remember { mutableIntStateOf(0) }
    val fontDirectoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            val count = SubtitleFontManager.importFontsFromTree(context, uri)
            fontListVersion++
            onPlayerSettingsChange(
                uiState.playerSettings.copy(
                    subtitleFontDirectoryUri = uri.toString(),
                    subtitleFontsReloadNonce = System.currentTimeMillis()
                )
            )
            Toast.makeText(
                context,
                if (count > 0) "Loaded $count subtitle font(s)" else "No supported font files found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val fontFilesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            var importedCount = 0
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
                val result = SubtitleFontManager.importFontFromUri(context, uri)
                if (result != null) importedCount++
            }
            fontListVersion++
            onPlayerSettingsChange(
                uiState.playerSettings.copy(
                    subtitleFontsReloadNonce = System.currentTimeMillis()
                )
            )
            Toast.makeText(
                context,
                if (importedCount > 0) "Imported $importedCount font file(s)" else "Could not load font files",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val sheetBg = if (isDark) {
        if (alphaFactor >= 0.99f) Color(0xFF0B1120)
        else Color(0xFF0B1120).copy(alpha = (0.12f + 0.85f * alphaFactor).coerceIn(0.12f, 1.0f))
    } else {
        if (alphaFactor >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.12f + 0.85f * alphaFactor).coerceIn(0.12f, 1.0f))
    }
    val maxContentHeight = (configuration.screenHeightDp - 36).coerceAtLeast(360).dp

    val cardBg = if (isDark) {
        if (alphaFactor >= 0.99f) Color(0xFF1E293B)
        else Color(0xFF1E293B).copy(alpha = (0.08f + 0.50f * alphaFactor).coerceIn(0.08f, 0.90f))
    } else {
        if (alphaFactor >= 0.99f) Color(0xFFFFFFFF)
        else Color(0xFFFFFFFF).copy(alpha = (0.08f + 0.50f * alphaFactor).coerceIn(0.08f, 0.90f))
    }
    val segmentedBg = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val borderColor = if (isDark) Color(0x3338BDF8) else Color(0x2E0284C7)
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    val handleDragModifier = Modifier
        .pointerInput(Unit) {
            // Tap-to-dismiss and drag-to-dismiss are combined inside a SINGLE
            // pointerInput block (instead of two separate stacked pointerInput
            // modifiers) so both gesture detectors observe the exact same
            // pointer event stream. Two independent pointerInput nodes on the
            // same handle used to race for the first touch (the drag detector
            // could "steal" the initial down before the tap detector resolved
            // it), which is what made the handle sometimes need a second tap
            // to register.
            kotlinx.coroutines.coroutineScope {
                launch {
                    detectTapGestures {
                        dismissWithAnimation()
                    }
                }
                launch {
                    detectVerticalDragGestures(
                        onDragStart = { /* Drag started on handle/divider */ },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || sheetDragOffsetY.value > 0f) {
                                change.consume()
                                coroutineScope.launch {
                                    sheetDragOffsetY.snapTo(
                                        (sheetDragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            if (sheetDragOffsetY.value > 50f) {
                                dismissWithAnimation()
                            } else {
                                coroutineScope.launch {
                                    sheetDragOffsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                sheetDragOffsetY.animateTo(0f)
                            }
                        }
                    )
                }
            }
        }

    Dialog(
        onDismissRequest = {
            dismissWithAnimation()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            configureEdgeToEdgeDialogWindow(
                view = view,
                isLightStatusBars = !isDark,
                isLightNavBars = !isDark
            )
        }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalGlassBlurTransparency provides uiState.glassBlurTransparency
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.35f * alphaFactor * scrimAlpha.value).coerceIn(0.0f, 0.40f)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismissWithAnimation() }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .padding(top = 4.dp)
                        .offset { IntOffset(0, sheetDragOffsetY.value.roundToInt()) }
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(sheetBg)
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume taps inside dashboard */ }
                        }
                ) {
                    // HEADER & CATEGORY TABS ZONE:
                    // Only the minus drag handle initiates swipe-down dismiss.
                    // Swiping or touching header, done button, tabs, or content NEVER dismisses the dashboard.
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Minus Drag Handle (ONLY this minus button initiates swipe-down dismiss)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(38.dp)
                                    .then(handleDragModifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (isDark) Color.White.copy(alpha = 0.50f)
                                            else Color.Black.copy(alpha = 0.40f)
                                        )
                                )
                            }
                        }

                    // Top Header: Gear Icon + "Settings Dashboard" + "Done" Gradient Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.getSettingsDashboard(lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .testTag("settings_done_button")
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentGradient)
                                .clickable {
                                    dismissWithAnimation()
                                }
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = AppStrings.getDone(lang),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Category Tabs Row: [Appearance] [Player] [Audio] [Decoder] ...
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(SettingsCategoryTab.entries) { index, tab ->
                            val isSelected = activeTabIdx == index
                            val tabScale by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.97f,
                                animationSpec = tween(120, easing = FastOutSlowInEasing),
                                label = "settings_tab_scale_$index"
                            )
                            // Smooth cross-fade instead of an abrupt background swap: the neutral
                            // gradient is always the base, and the accent gradient fades in/out on
                            // top of it, so the selection colour eases in rather than snapping.
                            val selectionAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "settings_tab_selection_alpha_$index"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else secondaryText,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "settings_tab_content_color_$index"
                            )
                            val tabBorderColor by animateColorAsState(
                                targetValue = if (isSelected) Color.Transparent else borderColor,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "settings_tab_border_color_$index"
                            )
                            val neutralBrush = remember(isDark) {
                                Brush.linearGradient(
                                    listOf(
                                        if (isDark) Color(0x26FFFFFF) else Color(0x14000000),
                                        if (isDark) Color(0x26FFFFFF) else Color(0x14000000)
                                    )
                                )
                            }
                            val tabModifier = Modifier
                                .graphicsLayer { scaleX = tabScale; scaleY = tabScale }
                                .clip(RoundedCornerShape(20.dp))
                                .background(neutralBrush)
                                .border(
                                    width = 1.dp,
                                    color = tabBorderColor,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    val targetIdx = index
                                    pendingTapTarget = targetIdx
                                    selectedTabIdx = targetIdx
                                    coroutineScope.launch {
                                        launch {
                                            tabListState.animateScrollToItem(
                                                (targetIdx - 1).coerceAtLeast(0),
                                                scrollOffset = -30
                                            )
                                        }
                                        pagerState.animateScrollToPage(
                                            page = targetIdx,
                                            animationSpec = tween(
                                                durationMillis = 360,
                                                easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
                                            )
                                        )
                                        // Safety net: always release the pin once the tap-driven
                                        // scroll is done, even if the currentPage settle-check in
                                        // the LaunchedEffect above never runs (e.g. interrupted by
                                        // another tap mid-flight).
                                        if (pendingTapTarget == targetIdx) pendingTapTarget = null
                                    }
                                }

                            Box(
                                modifier = tabModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                // The selection gradient must be sized against the WHOLE tab. The tab's
                                // inner padding therefore lives on the Row below (not on the outer Box):
                                // matchParentSize() excludes the parent's own padding, which previously
                                // made the gradient hug only the icon + label instead of the full pill.
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = selectionAlpha }
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AccentGradient)
                                )
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StyledIcon(
                                        drawableRes = tab.lumoraIconRes,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.getTitle(lang),
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(color = borderColor)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // CONTENT ZONE: Isolated from sheet dismissal, scrolls independently
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(consumeContentScrollConnection)
                        .pointerInput(Unit) {
                            detectTapGestures { /* consumes tap events on empty areas so they never dismiss */ }
                        }
                ) {
                    // Swipable Horizontal Pager across top-level settings categories
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                        pageSpacing = 18.dp,
                        beyondViewportPageCount = 1,
                        key = { SettingsCategoryTab.entries[it].name }
                    ) { pageIndex ->
                        val currentCategory = SettingsCategoryTab.entries[pageIndex]
                        val signedPageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val pageOffset = signedPageOffset.absoluteValue.coerceIn(0f, 1f)
                        val pageAlpha = when (uiState.tabNavigationStyle) {
                            TabNavigationStyle.NONE -> 1f
                            TabNavigationStyle.MINIMAL_FADE -> (1f - pageOffset * 0.25f).coerceIn(0.75f, 1f)
                            TabNavigationStyle.DEPTH_ZOOM -> (1f - pageOffset * 0.30f).coerceIn(0.70f, 1f)
                            TabNavigationStyle.FLIP_FADE -> (1f - pageOffset * 0.35f).coerceIn(0.65f, 1f)
                            TabNavigationStyle.ELASTIC_SLIDE, TabNavigationStyle.DEFAULT -> (1f - pageOffset * 0.28f).coerceIn(0.72f, 1f)
                        }
                        val pageScale = when (uiState.tabNavigationStyle) {
                            TabNavigationStyle.DEPTH_ZOOM -> (1f - pageOffset * 0.035f).coerceIn(0.965f, 1f)
                            TabNavigationStyle.FLIP_FADE -> (1f - pageOffset * 0.03f).coerceIn(0.97f, 1f)
                            else -> (1f - pageOffset * 0.02f).coerceIn(0.98f, 1f)
                        }
                        val pageTranslationX = when (uiState.tabNavigationStyle) {
                            TabNavigationStyle.DEFAULT, TabNavigationStyle.ELASTIC_SLIDE -> signedPageOffset * 28f
                            TabNavigationStyle.DEPTH_ZOOM -> signedPageOffset * 18f
                            TabNavigationStyle.FLIP_FADE -> signedPageOffset * 14f
                            else -> signedPageOffset * 24f
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = pageAlpha
                                    scaleX = pageScale
                                    scaleY = pageScale
                                    translationX = pageTranslationX
                                }
                                .padding(horizontal = 20.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                    when (currentCategory) {
                    SettingsCategoryTab.APPEARANCE -> {
                        // Banner: Appearance & Themes
                        CategoryBanner(
                            icon = Icons.Outlined.Palette,
                            lumoraIconRes = R.drawable.lumora_pallete_2,
                            title = AppStrings.getAppearanceCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 1: APP THEME PALETTES
                        Text(
                            text = "APP THEME",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(AppThemeVariant.entries) { _, variant ->
                                val isSelected = uiState.appTheme == variant
                                Column(
                                    modifier = Modifier
                                        .width(190.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) variant.primaryColor else borderColor,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .background(cardBg)
                                        .clickable { onAppThemeChange(variant) }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(112.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(variant.gradient)
                                            .padding(8.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.58f)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Color.White.copy(alpha = 0.92f))
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.78f))
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(16.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(variant.primaryColor)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(variant.secondaryColor)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.72f)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.White.copy(alpha = 0.78f))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = variant.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = primaryText,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = variant.description,
                                        fontSize = 10.sp,
                                        color = secondaryText,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 1.1: THEME MODE & TYPOGRAPHY
                        Text(
                            text = AppStrings.getSectionAppearance(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(segmentedBg)
                                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                                .padding(4.dp)
                        ) {
                            val modes = listOf(
                                 Triple(ThemeMode.LIGHT, AppStrings.getLightMode(lang), R.drawable.lumora_sun),
                                 Triple(ThemeMode.DARK, AppStrings.getDarkMode(lang), R.drawable.lumora_moon),
                                 Triple(ThemeMode.SYSTEM, "System", R.drawable.lumora_device)
                             )

                            modes.forEach { (mode, label, icon) ->
                                val isSelected = uiState.themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isSelected) AccentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                        .clickable { onThemeChange(mode) }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        StyledIcon(
                                            drawableRes = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else secondaryText,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else secondaryText
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                val isAmoledAvailable = uiState.themeMode != ThemeMode.LIGHT
                                PlayerSwitchRow(
                                    title = "AMOLED Black Mode",
                                    description = if (isAmoledAvailable) "Pure pitch-black (#000000) background for OLED power saving" else "Available in Dark or System mode",
                                    checked = uiState.amoledBlackMode,
                                    enabled = isAmoledAvailable,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onAmoledBlackModeChange
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))
                                PlayerSwitchRow(
                                    title = "Use System Font",
                                    description = "Use device native typography across the interface",
                                    checked = uiState.useSystemFont,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onUseSystemFontChange
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))
                                PlayerSwitchRow(
                                    title = "Haptic Feedback",
                                    description = "Use subtle vibration feedback for key actions and interactions",
                                    checked = uiState.hapticFeedback,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onHapticFeedbackChange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 1.2: DISPLAY ZOOM & SCALE
                        Text(
                            text = AppStrings.getSectionScaling(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = AppStrings.getAppScaleZoom(lang),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentGradient)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${uiState.appScale.roundToInt()}%",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Slider(
                                    value = uiState.appScale.coerceIn(1f, 100f),
                                    onValueChange = { newScale ->
                                        onScaleChange(newScale)
                                    },
                                    valueRange = 1f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentPink,
                                        activeTrackColor = AccentSkyBlue,
                                        inactiveTrackColor = if (isDark) AccentSkyBlue.copy(alpha = 0.22f) else AccentSkyBlue.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "1%",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Text(
                                        text = "100%",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 1.3: UI BLUR & GLASS TRANSPARENCY
                        Text(
                            text = AppStrings.getSectionGlassBlur(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = AppStrings.getGlassBlurTitle(lang),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                        Text(
                                            text = AppStrings.getGlassBlurSubtitle(lang),
                                            fontSize = 11.5.sp,
                                            color = secondaryText
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentGradient)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${uiState.glassBlurTransparency.roundToInt()}%",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Slider(
                                    value = uiState.glassBlurTransparency.coerceIn(10f, 100f),
                                    onValueChange = { newTrans ->
                                        onGlassBlurTransparencyChange(newTrans)
                                    },
                                    valueRange = 10f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentPink,
                                        activeTrackColor = AccentSkyBlue,
                                        inactiveTrackColor = if (isDark) AccentSkyBlue.copy(alpha = 0.22f) else AccentSkyBlue.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = AppStrings.getGlassBlurLow(lang),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Text(
                                        text = AppStrings.getGlassBlurHigh(lang),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 2: FILE BROWSER
                        Text(
                            text = "FILE DISPLAY & BADGES",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerSwitchRow(
                                    title = "Show Full Filename",
                                    description = "Display full video file extension and unaltered filenames in library",
                                    checked = uiState.showFullName,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowFullNameChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Show 'NEW' Video Badge",
                                    description = "Highlight recently discovered videos with a vibrant NEW badge",
                                    checked = uiState.showNewVideoLabel,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowNewVideoLabelChange
                                )

                                AnimatedVisibility(visible = uiState.showNewVideoLabel) {
                                    Column {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = borderColor)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        PlayerSliderRow(
                                            title = "'NEW' Badge Threshold",
                                            description = "Maximum age (in days) for a video to be marked as NEW",
                                            value = uiState.newVideoDaysThreshold,
                                            min = 1,
                                            max = 30,
                                            unit = " days",
                                            isDark = isDark,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            onChange = onNewVideoDaysThresholdChange
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Show Unplayed Badge on Folders",
                                    description = "Display number of unplayed videos on top of video folders",
                                    checked = uiState.showFolderUnplayedBadge,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowFolderUnplayedBadgeChange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "DIRECTORY NAVIGATION & LAYOUT",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerSwitchRow(
                                    title = "Auto-scroll to Last Played",
                                    description = "Automatically focus and center on the last played video when opening folders",
                                    checked = uiState.autoScrollToLastPlayed,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onAutoScrollToLastPlayedChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Path Compression in Tree",
                                    description = "Collapse single-child nested folders into compact combined nodes",
                                    checked = uiState.treePathCompression,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onTreePathCompressionChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Dual-Pane View on Wide Displays",
                                    description = "Show folder list and media items side-by-side on tablets and foldables",
                                    checked = uiState.dualPaneView,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onDualPaneViewChange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "WATCHED PROGRESS THRESHOLD",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            PlayerSliderRow(
                                title = "Watched Status Completion",
                                description = "Percentage of video length after which it is marked as completed/watched",
                                value = uiState.watchedThresholdPercent,
                                min = 50,
                                max = 100,
                                unit = "%",
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                onChange = onWatchedThresholdPercentChange
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 3: THUMBNAILS
                        Text(
                            text = "THUMBNAIL EXTRACTION",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerSwitchRow(
                                    title = "Show Video Thumbnails",
                                    description = "Generate visual frame previews for videos in lists and grids",
                                    checked = uiState.showVideoThumbnails,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowVideoThumbnailsChange
                                )

                                AnimatedVisibility(visible = uiState.showVideoThumbnails) {
                                    Column {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = borderColor)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        PlayerChoiceRow(
                                            title = "Extraction Strategy",
                                            description = "Choose how thumbnail artwork is selected from the video",
                                            value = uiState.thumbnailStrategy.name,
                                            options = ThumbnailStrategy.entries.map { it.name to it.displayName },
                                            isDark = isDark,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            borderColor = borderColor,
                                            alphaFactor = alphaFactor,
                                            onChange = { sel ->
                                                onThumbnailStrategyChange(ThumbnailStrategy.entries.firstOrNull { it.name == sel } ?: ThumbnailStrategy.SMART)
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = borderColor)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        PlayerChoiceRow(
                                            title = "Thumbnail Quality",
                                            description = "Resolution and cache density for image generation",
                                            value = uiState.thumbnailQuality.name,
                                            options = ThumbnailQuality.entries.map { it.name to it.displayName },
                                            isDark = isDark,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText,
                                            borderColor = borderColor,
                                            alphaFactor = alphaFactor,
                                            onChange = { sel ->
                                                onThumbnailQualityChange(ThumbnailQuality.entries.firstOrNull { it.name == sel } ?: ThumbnailQuality.HIGH)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "THUMBNAIL INTERACTION",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerSwitchRow(
                                    title = "Tap Thumbnail to Multi-Select",
                                    description = "Tapping the thumbnail preview directly engages selection mode",
                                    checked = uiState.tapThumbnailToSelect,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onTapThumbnailToSelectChange
                                )

                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 4: NAVIGATION
                        Text(
                            text = "BOTTOM BAR TAB VISIBILITY",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerSwitchRow(
                                    title = "Videos / Home Tab",
                                    description = "Display primary video library and folders screen",
                                    checked = uiState.showHomeTab,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowHomeTabChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Audio / Music Tab",
                                    description = "Display dedicated music player and audio files tab",
                                    checked = uiState.showMusicTab,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowMusicTabChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Recently Played Tab",
                                    description = "Display playback history and quick resume section",
                                    checked = uiState.showRecentsTab,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowRecentsTabChange
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerSwitchRow(
                                    title = "Custom Playlists Tab",
                                    description = "Display custom playlists and categorized series collections",
                                    checked = uiState.showPlaylistsTab,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onChange = onShowPlaylistsTabChange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 5: ANIMATIONS
                        Text(
                            text = "TRANSITION STYLES",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column {
                                PlayerChoiceRow(
                                    title = "Player Controls Animation",
                                    description = "Style of on-screen controls overlay entrance and exit",
                                    value = uiState.controlsAnimationStyle.name,
                                    options = ControlsAnimationStyle.entries.map { it.name to it.displayName },
                                    isDark = isDark,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    borderColor = borderColor,
                                    alphaFactor = alphaFactor,
                                    onChange = { sel ->
                                        onControlsAnimationStyleChange(ControlsAnimationStyle.entries.firstOrNull { it.name == sel } ?: ControlsAnimationStyle.DEFAULT)
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerChoiceRow(
                                    title = "Video Player Enter Transition",
                                    description = "Visual motion when launching video playback from library",
                                    value = uiState.videoOpeningAnimation.name,
                                    options = VideoOpeningAnimation.entries.map { it.name to it.displayName },
                                    isDark = isDark,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    borderColor = borderColor,
                                    alphaFactor = alphaFactor,
                                    onChange = { sel ->
                                        onVideoOpeningAnimationChange(VideoOpeningAnimation.entries.firstOrNull { it.name == sel } ?: VideoOpeningAnimation.DEFAULT)
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerChoiceRow(
                                    title = "Screen Transition Style",
                                    description = "Visual page transition when navigating between application views",
                                    value = uiState.screenNavigationStyle.name,
                                    options = ScreenNavigationStyle.entries.map { it.name to it.displayName },
                                    isDark = isDark,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    borderColor = borderColor,
                                    alphaFactor = alphaFactor,
                                    onChange = { sel ->
                                        onScreenNavigationStyleChange(ScreenNavigationStyle.entries.firstOrNull { it.name == sel } ?: ScreenNavigationStyle.DEFAULT)
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(10.dp))

                                PlayerChoiceRow(
                                    title = "Tab Switch Animation",
                                    description = "Motion when switching between bottom or category tabs",
                                    value = uiState.tabNavigationStyle.name,
                                    options = TabNavigationStyle.entries.map { it.name to it.displayName },
                                    isDark = isDark,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    borderColor = borderColor,
                                    alphaFactor = alphaFactor,
                                    onChange = { sel ->
                                        onTabNavigationStyleChange(TabNavigationStyle.entries.firstOrNull { it.name == sel } ?: TabNavigationStyle.DEFAULT)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "ANIMATION SPEED MULTIPLIER",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = "Global Motion Scale",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                        Text(
                                            text = "Scale duration of all UI animations across the app",
                                            fontSize = 11.5.sp,
                                            color = secondaryText
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentGradient)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.2fx", uiState.animationSpeed),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Slider(
                                    value = uiState.animationSpeed.coerceIn(0.25f, 2.0f),
                                    onValueChange = { newSpeed ->
                                        onAnimationSpeedChange(newSpeed)
                                    },
                                    valueRange = 0.25f..2.0f,
                                    steps = 6,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentPink,
                                        activeTrackColor = AccentSkyBlue,
                                        inactiveTrackColor = if (isDark) AccentSkyBlue.copy(alpha = 0.22f) else AccentSkyBlue.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "0.25x (Slower)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Text(
                                        text = "1.00x (Normal)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Text(
                                        text = "2.00x (Faster)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 6: INTERFACE LANGUAGE
                        Text(
                            text = AppStrings.getSectionLanguage(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(segmentedBg)
                                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                                .padding(4.dp)
                        ) {
                            AppLanguage.entries.forEach { appLang ->
                                val isLangSelected = uiState.language == appLang
                                val label = when (appLang) {
                                    AppLanguage.ENGLISH -> "English"
                                    AppLanguage.HINGLISH -> "Hinglish"
                                    AppLanguage.HINDI -> "हिन्दी"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isLangSelected) AccentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                        .clickable { onLanguageChange(appLang) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isLangSelected) Color.White else secondaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.FOLDERS -> {
                        // Banner: Folders Management
                        CategoryBanner(
                            icon = Icons.Outlined.Folder,
                            lumoraIconRes = R.drawable.lumora_folder,
                            title = AppStrings.getFoldersCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        FoldersSettingsTabContent(
                            isDark = isDark,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            glassBlurTransparency = uiState.glassBlurTransparency
                        )
                    }

                    SettingsCategoryTab.PLAYER -> {
                        // Banner: Player & Controls
                        CategoryBanner(
                            icon = Icons.Outlined.PlayCircle,
                            lumoraIconRes = R.drawable.lumora_play_circle,
                            title = AppStrings.getPlayerCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // TITLE NAME PLAYER — extended native player settings.
                        PlayerSettingsPanel(
                            settings = uiState.playerSettings,
                            onSettingsChange = onPlayerSettingsChange,
                            isDark = isDark,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            alphaFactor = alphaFactor
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = AppStrings.getSectionPlayback(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(AppStrings.getCopyTimestamp(lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Text(AppStrings.getCopyTimestampDesc(lang), fontSize = 12.sp, color = secondaryText)
                                    }
                                    Switch(
                                        checked = uiState.copyTimestampOnDoubleTap,
                                        onCheckedChange = onCopyTimestampChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentSkyBlue
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = borderColor)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(AppStrings.getForceSideBySide(lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                                        Text(AppStrings.getForceSideBySideDesc(lang), fontSize = 12.sp, color = secondaryText)
                                    }
                                    Switch(
                                        checked = uiState.forceSideBySide,
                                        onCheckedChange = onSideBySideChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentSkyBlue
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = AppStrings.getSectionFastSeek(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(segmentedBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(4.dp)
                        ) {
                            listOf(1, 5, 10, 15).forEach { sec ->
                                val isSelected = uiState.frameStepAmount == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) AccentGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                        .clickable { onFrameStepChange(sec) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else secondaryText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = AppStrings.getSectionGestureSensitivity(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GestureSensitivityMode.entries.forEach { mode ->
                                val isSelected = uiState.gestureSensitivityMode == mode
                                val icon = when (mode) {
                                    GestureSensitivityMode.EXPONENTIAL -> Icons.Outlined.Speed
                                    GestureSensitivityMode.LINEAR -> Icons.Outlined.Tune
                                }
                                val cardShape = RoundedCornerShape(20.dp)
                                val cardBackground = if (isSelected) {
                                    if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE)
                                } else {
                                    if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                }
                                val cardBorder = if (isSelected) {
                                    BorderStroke(1.5.dp, AccentSkyBlue)
                                } else {
                                    BorderStroke(1.dp, borderColor)
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .clickable { onGestureSensitivityModeChange(mode) },
                                    shape = cardShape,
                                    color = cardBackground,
                                    border = cardBorder
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) AccentSkyBlue.copy(alpha = 0.2f) else Color(0x15FFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            StyledIcon(
                                imageVector = icon,
                                                contentDescription = mode.getDisplayName(lang),
                                                tint = if (isSelected) AccentSkyBlue else secondaryText,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = mode.getDisplayName(lang),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryText
                                                )
                                                if (mode == GestureSensitivityMode.EXPONENTIAL) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AccentSkyBlue.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = AppStrings.getRecommended(lang),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = AccentSkyBlue,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = mode.getDescription(lang),
                                                fontSize = 12.sp,
                                                color = secondaryText,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onGestureSensitivityModeChange(mode) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = AccentSkyBlue,
                                                unselectedColor = secondaryText.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION: PLAYER NOTIFICATIONS
                        Text(
                            text = AppStrings.getSectionPlayerNotification(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppStrings.getPlayerNotificationTitle(lang),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = AppStrings.getPlayerNotificationDesc(lang),
                                        fontSize = 12.sp,
                                        color = secondaryText,
                                        lineHeight = 16.sp
                                    )
                                }
                                Switch(
                                    checked = uiState.showPlayerNotifications,
                                    onCheckedChange = onShowPlayerNotificationsChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentSkyBlue
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.AUDIO -> {
                        // Banner: Audio & Sound Settings
                        CategoryBanner(
                            icon = Icons.Outlined.VolumeUp,
                            lumoraIconRes = R.drawable.lumora_audio_circle,
                            title = AppStrings.getAudioCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 1: PREFERRED AUDIO LANGUAGES
                        Text(
                            text = AppStrings.getSectionPreferredLanguages(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Header Row (Always visible, tap to expand/collapse)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isPreferredLanguagesExpanded = !isPreferredLanguagesExpanded }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(AccentSkyBlue.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            StyledIcon(
                                imageVector = Icons.Outlined.Translate,
                                                contentDescription = null,
                                                tint = AccentSkyBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = AppStrings.getPreferredLanguagesTitle(lang),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryText
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val currentLangs = uiState.preferredAudioLanguages.trim()
                                            val summaryText = if (currentLangs.isNotBlank()) {
                                                val targetInfo = if (uiState.applyAudioLanguageToSelectedContentOnly) {
                                                    val total = uiState.selectedAudioFolders.size + uiState.selectedAudioVideos.size
                                                    if (total > 0) " • $total items" else " • 0 items"
                                                } else {
                                                    " • All media"
                                                }
                                                "$currentLangs$targetInfo"
                                            } else {
                                                "Tap to configure"
                                            }
                                            Text(
                                                text = summaryText,
                                                fontSize = 11.5.sp,
                                                color = if (currentLangs.isNotBlank()) AccentSkyBlue else secondaryText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Expand / Collapse Chevron Button
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPreferredLanguagesExpanded) AccentSkyBlue.copy(alpha = 0.18f)
                                                else if (isDark) Color.White.copy(alpha = 0.08f)
                                                else Color.Black.copy(alpha = 0.05f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        StyledIcon(
                                imageVector = if (isPreferredLanguagesExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = if (isPreferredLanguagesExpanded) "Collapse" else "Expand",
                                            tint = if (isPreferredLanguagesExpanded) AccentSkyBlue else secondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Collapsible Content
                                AnimatedVisibility(
                                    visible = isPreferredLanguagesExpanded,
                                    enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                                    exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                    ) {
                                        HorizontalDivider(color = borderColor)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = AppStrings.getPreferredLanguagesDesc(lang),
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            lineHeight = 16.sp
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        val audioLangContainerColor = if (isDark) {
                                            Color(0xFF0F172A).copy(alpha = (0.25f + 0.45f * alphaFactor).coerceIn(0.15f, 0.85f))
                                        } else {
                                            Color(0xFFFFFFFF).copy(alpha = (0.30f + 0.50f * alphaFactor).coerceIn(0.20f, 0.85f))
                                        }
                                        OutlinedTextField(
                                            value = uiState.preferredAudioLanguages,
                                            onValueChange = onPreferredAudioLanguagesChange,
                                            placeholder = {
                                                Text(
                                                    text = "e.g. hin, eng, jpn, spa",
                                                    fontSize = 13.sp,
                                                    color = secondaryText.copy(alpha = 0.6f)
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentSkyBlue,
                                                unfocusedBorderColor = borderColor,
                                                focusedTextColor = primaryText,
                                                unfocusedTextColor = primaryText,
                                                focusedContainerColor = audioLangContainerColor,
                                                unfocusedContainerColor = audioLangContainerColor,
                                                cursorColor = AccentSkyBlue
                                            ),
                                            trailingIcon = {
                                                if (uiState.preferredAudioLanguages.isNotBlank()) {
                                                    IconButton(onClick = { onPreferredAudioLanguagesChange("") }) {
                                                        StyledIcon(
                                                            imageVector = Icons.Outlined.Close,
                                                            contentDescription = "Clear",
                                                            tint = secondaryText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Quick-Add Language Chips
                                        Text(
                                            text = "Quick Presets:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = secondaryText
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        val currentTokens = remember(uiState.preferredAudioLanguages) {
                                            AudioLanguageMatcher.normalizePreferredLanguages(uiState.preferredAudioLanguages)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AudioLanguageMatcher.POPULAR_LANGUAGES.take(10).forEach { (code, label) ->
                                                val isPresent = currentTokens.contains(code)
                                                val chipBg = if (isPresent) {
                                                    if (isDark) Color(0xFF0284C7).copy(alpha = 0.40f) else Color(0xFFE0F2FE)
                                                } else {
                                                    if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                                }
                                                val chipBorder = if (isPresent) AccentSkyBlue else borderColor

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(chipBg)
                                                        .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            val tokens = AudioLanguageMatcher.normalizePreferredLanguages(uiState.preferredAudioLanguages).toMutableList()
                                                            if (tokens.contains(code)) {
                                                                tokens.remove(code)
                                                            } else {
                                                                tokens.add(code)
                                                            }
                                                            onPreferredAudioLanguagesChange(tokens.joinToString(", "))
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (isPresent) {
                                                            StyledIcon(
                                imageVector = Icons.Outlined.Check,
                                                                contentDescription = null,
                                                                tint = AccentSkyBlue,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = label,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isPresent) AccentSkyBlue else primaryText
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = borderColor)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Sub-option: Apply to selected folder/videos only
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = AppStrings.getApplyToSelectedContentTitle(lang),
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = primaryText
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = AppStrings.getApplyToSelectedContentDesc(lang),
                                                    fontSize = 11.5.sp,
                                                    color = secondaryText,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = uiState.applyAudioLanguageToSelectedContentOnly,
                                                onCheckedChange = onApplyAudioLanguageToSelectedContentOnlyChange,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = AccentSkyBlue
                                                )
                                            )
                                        }

                                        // Rounded Rectangular Box for Selected Folders & Videos
                                        AnimatedVisibility(
                                            visible = uiState.applyAudioLanguageToSelectedContentOnly,
                                            enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                                            exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(
                                                            if (isDark) Color(0xFF0F172A).copy(alpha = 0.85f)
                                                            else Color(0xFFF1F5F9).copy(alpha = 0.95f)
                                                        )
                                                        .border(
                                                            1.5.dp,
                                                            if (isDark) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.35f),
                                                            RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(14.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        // Header Row
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(CircleShape)
                                                                        .background(AccentSkyBlue.copy(alpha = 0.18f)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    StyledIcon(
                                imageVector = Icons.Outlined.FolderSpecial,
                                                                        contentDescription = null,
                                                                        tint = AccentSkyBlue,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = "Target Folders & Videos",
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = primaryText
                                                                )
                                                            }

                                                            val totalCount = uiState.selectedAudioFolders.size + uiState.selectedAudioVideos.size
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        if (totalCount > 0) AccentSkyBlue.copy(alpha = 0.20f)
                                                                        else if (isDark) Color.White.copy(alpha = 0.10f)
                                                                        else Color.Black.copy(alpha = 0.06f)
                                                                    )
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "$totalCount selected",
                                                                    fontSize = 10.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (totalCount > 0) AccentSkyBlue else secondaryText
                                                                )
                                                            }
                                                        }

                                                        // List of selected tags/chips
                                                        val hasItems = uiState.selectedAudioFolders.isNotEmpty() || uiState.selectedAudioVideos.isNotEmpty()
                                                        if (hasItems) {
                                                            @OptIn(ExperimentalLayoutApi::class)
                                                            FlowRow(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                uiState.selectedAudioFolders.forEach { folderPath ->
                                                                    val folderName = File(folderPath).name.ifBlank { folderPath }
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(10.dp))
                                                                            .background(if (isDark) Color(0xFF1E293B) else Color(0x1A0284C7))
                                                                            .border(
                                                                                1.dp,
                                                                                AccentSkyBlue.copy(alpha = 0.40f),
                                                                                RoundedCornerShape(10.dp)
                                                                            )
                                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            StyledIcon(
                                imageVector = Icons.Outlined.Folder,
                                                                                contentDescription = null,
                                                                                tint = AccentSkyBlue,
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                            Text(
                                                                                text = folderName,
                                                                                fontSize = 11.5.sp,
                                                                                fontWeight = FontWeight.SemiBold,
                                                                                color = primaryText,
                                                                                maxLines = 1
                                                                            )
                                                                            StyledIcon(
                                imageVector = Icons.Outlined.Close,
                                                                                contentDescription = "Remove",
                                                                                tint = secondaryText,
                                                                                modifier = Modifier
                                                                                    .size(14.dp)
                                                                                    .clickable {
                                                                                        val newSet = uiState.selectedAudioFolders.toMutableSet()
                                                                                        newSet.remove(folderPath)
                                                                                        onSelectedAudioFoldersChange(newSet)
                                                                                    }
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                uiState.selectedAudioVideos.forEach { videoPath ->
                                                                    val videoName = File(videoPath).name.ifBlank { videoPath }
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(10.dp))
                                                                            .background(if (isDark) Color(0xFF1E293B) else Color(0x1A6366F1))
                                                                            .border(
                                                                                1.dp,
                                                                                Color(0xFF818CF8).copy(alpha = 0.40f),
                                                                                RoundedCornerShape(10.dp)
                                                                            )
                                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            StyledIcon(
                                imageVector = Icons.Outlined.Movie,
                                                                                contentDescription = null,
                                                                                tint = Color(0xFF818CF8),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                            Text(
                                                                                text = videoName,
                                                                                fontSize = 11.5.sp,
                                                                                fontWeight = FontWeight.Medium,
                                                                                color = primaryText,
                                                                                maxLines = 1
                                                                            )
                                                                            StyledIcon(
                                imageVector = Icons.Outlined.Close,
                                                                                contentDescription = "Remove",
                                                                                tint = secondaryText,
                                                                                modifier = Modifier
                                                                                    .size(14.dp)
                                                                                    .clickable {
                                                                                        val newSet = uiState.selectedAudioVideos.toMutableSet()
                                                                                        newSet.remove(videoPath)
                                                                                        onSelectedAudioVideosChange(newSet)
                                                                                    }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Text(
                                                                text = "No folders or videos selected yet. Tap '+' below to select directories or videos where auto-selection rule applies.",
                                                                fontSize = 11.5.sp,
                                                                color = secondaryText,
                                                                lineHeight = 15.sp
                                                            )
                                                        }

                                                        // Plus Add Button
                                                        Button(
                                                            onClick = { showAudioFolderSelectorSheet = true },
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color.Transparent
                                                            ),
                                                            contentPadding = PaddingValues(0.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(38.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(AccentGradient)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                StyledIcon(
                                imageVector = Icons.Outlined.Add,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "+ Add Folders or Videos",
                                                                    fontSize = 12.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White
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

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 2: PLAYBACK & SOUND PROCESSING
                        Text(
                            text = AppStrings.getSectionSoundProcessing(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. Audio Pitch Correction
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppStrings.getAudioPitchCorrectionTitle(lang),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = AppStrings.getAudioPitchCorrectionDesc(lang),
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = uiState.enableAudioPitchCorrection,
                                        onCheckedChange = onEnableAudioPitchCorrectionChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentSkyBlue
                                        )
                                    )
                                }

                                HorizontalDivider(color = borderColor)

                                // 2. Volume Normalization
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppStrings.getVolumeNormalizationTitle(lang),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = AppStrings.getVolumeNormalizationDesc(lang),
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = uiState.volumeNormalization,
                                        onCheckedChange = onVolumeNormalizationChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentSkyBlue
                                        )
                                    )
                                }

                                HorizontalDivider(color = borderColor)

                                // 3. Remember Selected Audio Track
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppStrings.getRememberAudioTrackTitle(lang),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = AppStrings.getRememberAudioTrackDesc(lang),
                                            fontSize = 12.sp,
                                            color = secondaryText,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = uiState.rememberSelectedAudioTrack,
                                        onCheckedChange = onRememberSelectedAudioTrackChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentSkyBlue
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 3: BACKGROUND PLAYBACK
                        Text(
                            text = AppStrings.getSectionBackgroundPlayback(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppStrings.getBackgroundPlaybackTitle(lang),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = AppStrings.getBackgroundPlaybackDesc(lang),
                                        fontSize = 12.sp,
                                        color = secondaryText,
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = uiState.backgroundPlayback,
                                    onCheckedChange = onBackgroundPlaybackChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentSkyBlue
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 4: AUDIO CHANNELS
                        Text(
                            text = AppStrings.getSectionAudioChannels(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AudioChannelMode.entries.forEach { mode ->
                                val isSelected = uiState.audioChannelMode == mode
                                val icon = when (mode) {
                                    AudioChannelMode.AUTO, AudioChannelMode.AUTO_SAFE -> Icons.Outlined.GraphicEq
                                    AudioChannelMode.STEREO, AudioChannelMode.REVERSED_STEREO -> Icons.Outlined.Headphones
                                    AudioChannelMode.MONO -> Icons.Outlined.VolumeUp
                                    AudioChannelMode.LEFT, AudioChannelMode.RIGHT -> Icons.Outlined.SurroundSound
                                }
                                val cardShape = RoundedCornerShape(20.dp)
                                val itemBg = if (isSelected) {
                                    if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE)
                                } else {
                                    if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                }
                                val itemBorder = if (isSelected) {
                                    if (isDark) Color(0xFF0284C7) else Color(0xFF38BDF8)
                                } else {
                                    if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
                                }
                                val iconTint = if (isSelected) {
                                    if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                } else {
                                    secondaryText
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(itemBg)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = itemBorder,
                                            shape = cardShape
                                        )
                                        .clickable { onAudioChannelModeChange(mode) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StyledIcon(
                                imageVector = icon,
                                            contentDescription = mode.getDisplayName(lang),
                                            tint = iconTint,
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.getDisplayName(lang),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryText
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = mode.getDescription(lang),
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                color = secondaryText
                                            )
                                        }

                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDark) Color(0xFF0284C7) else Color(0xFF38BDF8)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                StyledIcon(
                                imageVector = Icons.Outlined.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // SECTION 5: VOLUME AMPLIFICATION & BOOST
                        Text(
                            text = AppStrings.getSectionVolumeBoost(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = AppStrings.getVolumeBoostTitle(lang),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentGradient)
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${uiState.volumeBoostCap}%",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = AppStrings.getVolumeBoostDesc(lang),
                                    fontSize = 12.sp,
                                    color = secondaryText,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Slider(
                                    value = uiState.volumeBoostCap.toFloat(),
                                    onValueChange = { onVolumeBoostCapChange(it.roundToInt()) },
                                    valueRange = 100f..200f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentPink,
                                        activeTrackColor = AccentSkyBlue,
                                        inactiveTrackColor = if (isDark) AccentSkyBlue.copy(alpha = 0.22f) else AccentSkyBlue.copy(alpha = 0.15f)
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("100% (1.0x)", fontSize = 11.sp, color = secondaryText)
                                    Text("150% (1.5x)", fontSize = 11.sp, color = secondaryText)
                                    Text("200% (2.0x)", fontSize = 11.sp, color = secondaryText)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.SUBTITLES -> {
                        SubtitlesTab(
                            settings = uiState.playerSettings,
                            isDark = isDark,
                            context = context,
                            fontListVersion = fontListVersion,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            alphaFactor = alphaFactor,
                            onSettingsChange = onPlayerSettingsChange,
                            onChooseFontDirectory = { fontDirectoryPicker.launch(null) },
                            onPickFontFiles = {
                                fontFilesPicker.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "*/*"))
                            },
                            onReloadFonts = {
                                val uriString = uiState.playerSettings.subtitleFontDirectoryUri
                                if (uriString.isNotBlank()) {
                                    try {
                                        val count = SubtitleFontManager.importFontsFromTree(context, Uri.parse(uriString))
                                        fontListVersion++
                                        onPlayerSettingsChange(uiState.playerSettings.copy(subtitleFontsReloadNonce = System.currentTimeMillis()))
                                        Toast.makeText(context, "Reloaded $count subtitle font(s)", Toast.LENGTH_SHORT).show()
                                    } catch (_: Throwable) {
                                        Toast.makeText(context, "Unable to reload fonts", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    fontListVersion++
                                    SubtitleFontManager.syncFonts(context)
                                    onPlayerSettingsChange(uiState.playerSettings.copy(subtitleFontsReloadNonce = System.currentTimeMillis()))
                                    Toast.makeText(context, "Fonts refreshed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteFont = { fontName ->
                                val deleted = SubtitleFontManager.deleteCustomFont(context, fontName)
                                if (deleted) {
                                    fontListVersion++
                                    val newSelected = if (uiState.playerSettings.selectedSubtitleFont.equals(fontName, ignoreCase = true) ||
                                        uiState.playerSettings.selectedSubtitleFont.equals(fontName.substringBeforeLast('.'), ignoreCase = true)) {
                                        ""
                                    } else uiState.playerSettings.selectedSubtitleFont
                                    onPlayerSettingsChange(
                                        uiState.playerSettings.copy(
                                            selectedSubtitleFont = newSelected,
                                            subtitleFontsReloadNonce = System.currentTimeMillis()
                                        )
                                    )
                                    Toast.makeText(context, "Removed font: $fontName", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteAllFonts = {
                                val count = SubtitleFontManager.deleteAllCustomFonts(context)
                                fontListVersion++
                                onPlayerSettingsChange(
                                    uiState.playerSettings.copy(
                                        selectedSubtitleFont = "",
                                        subtitleFontsReloadNonce = System.currentTimeMillis()
                                    )
                                )
                                Toast.makeText(context, "Removed all $count custom font(s)", Toast.LENGTH_SHORT).show()
                            },
                            lang = lang,
                            onOpenFolderVideoSelector = { showSubtitleFolderSelectorSheet = true }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.DECODER -> {
                        // Banner: Decoder & Hardware
                        CategoryBanner(
                            icon = Icons.Outlined.Memory,
                            lumoraIconRes = R.drawable.lumora_cpu,
                            title = AppStrings.getDecoderCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = AppStrings.getSectionHwAccel(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HwAccelMode.entries.forEach { mode ->
                                val isSelected = uiState.hwAccelMode == mode
                                val icon = when (mode) {
                                    HwAccelMode.FORCE -> Icons.Outlined.Speed
                                    HwAccelMode.PREFER -> Icons.Outlined.Memory
                                    HwAccelMode.DISABLE -> Icons.Outlined.Tune
                                }
                                val cardShape = RoundedCornerShape(20.dp)
                                val cardBackground = if (isSelected) {
                                    if (isDark) Color(0xFF0C4A6E).copy(alpha = 0.50f) else Color(0xFFE0F2FE)
                                } else {
                                    if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                }
                                val cardBorder = if (isSelected) {
                                    if (isDark) Color(0xFF0284C7) else Color(0xFF38BDF8)
                                } else {
                                    if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
                                }
                                val iconTint = if (isSelected) {
                                    if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                } else {
                                    secondaryText
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(cardShape)
                                        .background(cardBackground)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = cardBorder,
                                            shape = cardShape
                                        )
                                        .clickable { onHwAccelModeChange(mode) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StyledIcon(
                                            imageVector = icon,
                                            contentDescription = mode.getDisplayName(lang),
                                            tint = iconTint,
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = mode.getDisplayName(lang),
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = primaryText
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = mode.getDescription(lang),
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                color = secondaryText
                                            )
                                        }

                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDark) Color(0xFF0284C7) else Color(0xFF38BDF8)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                StyledIcon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = AppStrings.getSectionRenderEngine(lang),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                EngineFeatureRow(
                                    title = AppStrings.getRenderLibassTitle(lang),
                                    subtitle = AppStrings.getRenderLibassDesc(lang),
                                    activeBadgeText = AppStrings.getActiveBadge(lang),
                                    active = true
                                )
                                HorizontalDivider(color = borderColor)
                                EngineFeatureRow(
                                    title = AppStrings.getRenderEmbeddedFontsTitle(lang),
                                    subtitle = AppStrings.getRenderEmbeddedFontsDesc(lang),
                                    activeBadgeText = AppStrings.getActiveBadge(lang),
                                    active = true
                                )
                                HorizontalDivider(color = borderColor)
                                EngineFeatureRow(
                                    title = AppStrings.getRenderHwEqTitle(lang),
                                    subtitle = AppStrings.getRenderHwEqDesc(lang),
                                    activeBadgeText = AppStrings.getActiveBadge(lang),
                                    active = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.GESTURES -> {
                        CategoryBanner(
                            icon = Icons.Outlined.Tune,
                            lumoraIconRes = R.drawable.lumora_gesture,
                            title = AppStrings.getTabGestures(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GestureSettingsPanel(
                            settings = uiState.playerSettings,
                            isDark = isDark,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            alphaFactor = alphaFactor,
                            onSettingsChange = onPlayerSettingsChange
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.PLAYER_LAYOUT -> {
                        // Banner: Player Layout & Controls
                        CategoryBanner(
                            icon = Icons.Outlined.DashboardCustomize,
                            lumoraIconRes = R.drawable.lumora_widget_5,
                            title = AppStrings.getPlayerLayoutTitle(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // SECTION 1: CONTROL LAYOUT CUSTOMIZER
                        Text(
                            text = "CONTROL LAYOUT CUSTOMIZER",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ControlLayoutEditor(
                            config = uiState.playerLayoutConfig,
                            onConfigChange = onPlayerLayoutConfigChange,
                            onResetToDefault = onResetPlayerLayoutConfig,
                            isDark = isDark,
                            transparencyPercent = uiState.glassBlurTransparency
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 2: SEEKBAR STYLE
                        Text(
                            text = AppStrings.getSeekbarStyleTitle(lang).uppercase(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        SeekbarStyleSelectionCard(
                            currentStyle = uiState.seekbarStyle,
                            onSelectStyle = { onSeekbarStyleChange(it) },
                            isDark = isDark
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        PlayerSettingsCategory("Playback Buttons", cardBg, borderColor, secondaryText) {
                            // 1. Portrait playback buttons
                            PlayerChoiceRow(
                                title = "Portrait playback buttons",
                                description = "Position of play/pause and skip buttons in portrait orientation",
                                value = uiState.playerSettings.portraitPlaybackButtonsPosition,
                                options = listOf(
                                    "CENTER" to "Center of screen",
                                    "BOTTOM" to "Between seekbar and controls"
                                ),
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                borderColor = borderColor,
                                alphaFactor = alphaFactor
                            ) { v ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(portraitPlaybackButtonsPosition = v))
                            }

                            HorizontalDivider(color = borderColor)

                            // 2. Landscape playback buttons
                            PlayerChoiceRow(
                                title = "Landscape playback buttons",
                                description = "Position of play/pause and skip buttons in landscape orientation",
                                value = uiState.playerSettings.landscapePlaybackButtonsPosition,
                                options = listOf(
                                    "CENTER" to "Center of screen",
                                    "BOTTOM" to "Between seekbar and controls"
                                ),
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                borderColor = borderColor,
                                alphaFactor = alphaFactor
                            ) { v ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(landscapePlaybackButtonsPosition = v))
                            }

                            HorizontalDivider(color = borderColor)

                            // 3. Hide player buttons background
                            PlayerSwitchRow(
                                title = "Hide player buttons background",
                                description = "Hide the background of all player control buttons",
                                checked = uiState.playerSettings.hidePlayerButtonsBackground,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            ) { checked ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(hidePlayerButtonsBackground = checked))
                            }

                            HorizontalDivider(color = borderColor)

                            // 4. Always use dark player button backgrounds
                            PlayerSwitchRow(
                                title = "Always use dark player button backgrounds",
                                description = "Keep player control button backgrounds dark in light, dark, and automatic themes",
                                checked = uiState.playerSettings.alwaysDarkPlayerButtonBackground,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            ) { checked ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(alwaysDarkPlayerButtonBackground = checked))
                            }

                            HorizontalDivider(color = borderColor)

                            // 5. Hide player control time
                            PlayerSliderRow(
                                title = "Hide player control time",
                                description = "Inactivity timeout before video controls automatically hide",
                                value = uiState.playerSettings.hidePlayerControlsTimeoutMs,
                                min = 500,
                                max = 10000,
                                unit = " ms",
                                presets = listOf(1000, 2000, 3000, 4000, 5000, 7500, 10000),
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText
                            ) { v ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(hidePlayerControlsTimeoutMs = v))
                            }

                            HorizontalDivider(color = borderColor)

                            // 6. Time + Network clock
                            PlayerChoiceRow(
                                title = "Time + Network clock",
                                description = "Format of top header clock and status indicators",
                                value = uiState.playerSettings.timeNetworkClockFormat,
                                options = listOf(
                                    "NONE" to "None",
                                    "SYSTEM" to "System",
                                    "12_HOUR" to "12 hour",
                                    "24_HOUR" to "24 hour"
                                ),
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                borderColor = borderColor,
                                alphaFactor = alphaFactor
                            ) { v ->
                                onPlayerSettingsChange(uiState.playerSettings.copy(timeNetworkClockFormat = v))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.ADVANCED -> {
                        CategoryBanner(
                            icon = Icons.Outlined.Settings,
                            lumoraIconRes = R.drawable.lumora_settings_minimalistic,
                            title = AppStrings.getAdvancedCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AdvancedSettingsPanel(
                            uiState = uiState,
                            viewModel = viewModel,
                            isDark = isDark,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            alphaFactor = alphaFactor,
                            lang = lang
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SettingsCategoryTab.ABOUT -> {
                        CategoryBanner(
                            icon = Icons.Outlined.Info,
                            lumoraIconRes = R.drawable.lumora_user,
                            title = AppStrings.getAboutCategory(lang)

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AboutSettingsPanel(
                            uiState = uiState,
                            isDark = isDark,
                            cardBg = cardBg,
                            borderColor = borderColor,
                            alphaFactor = alphaFactor,
                            lang = lang
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Seamless bottom navigation bar spacing:
                // Ensures sheet background bleeds completely under the gesture pill without visual cut-off
                Spacer(modifier = Modifier.navigationBarsPadding())
                // Extra bottom scroll buffer so the last settings item can fully scroll
                // into view above the nav bar on smaller-height screens instead of
                // getting clipped at the bottom edge of the sheet.
                Spacer(modifier = Modifier.height(48.dp))
            }
            }
        }
    }
}

    // Modal popup for folder and video selection
    if (showAudioFolderSelectorSheet) {
        AudioFolderVideoSelectorSheet(
            initialSelectedFolders = uiState.selectedAudioFolders,
            initialSelectedVideos = uiState.selectedAudioVideos,
            onSaveSelection = { folders, videos ->
                onSelectedAudioFoldersChange(folders)
                onSelectedAudioVideosChange(videos)
            },
            onDismiss = { showAudioFolderSelectorSheet = false },
            isDark = isDark,
            appScale = uiState.appScale,
            glassBlurTransparency = uiState.glassBlurTransparency,
            lang = lang
        )
    }

    if (showSubtitleFolderSelectorSheet) {
        AudioFolderVideoSelectorSheet(
            initialSelectedFolders = uiState.playerSettings.selectedSubtitleFolders,
            initialSelectedVideos = uiState.playerSettings.selectedSubtitleVideos,
            onSaveSelection = { folders, videos ->
                onPlayerSettingsChange(
                    uiState.playerSettings.copy(
                        selectedSubtitleFolders = folders,
                        selectedSubtitleVideos = videos
                    )
                )
            },
            onDismiss = { showSubtitleFolderSelectorSheet = false },
            isDark = isDark,
            appScale = uiState.appScale,
            glassBlurTransparency = uiState.glassBlurTransparency,
            lang = lang,
            sheetTitle = "Subtitle Filter Folders & Videos"
        )
    }
}
}
}

@Composable
private fun CategoryBanner(
    icon: ImageVector,
    title: String,
    lumoraIconRes: Int? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AccentGradient)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (lumoraIconRes != null) {
                    StyledIcon(
                        drawableRes = lumoraIconRes,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    StyledIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun EngineFeatureRow(
    title: String,
    subtitle: String,
    activeBadgeText: String = "Active",
    active: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
            Text(text = subtitle, fontSize = 11.5.sp, color = DarkTextSecondary)
        }
        if (active) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}


@Composable
fun SubtitlesTab(
    settings: PlayerSettings,
    isDark: Boolean,
    context: android.content.Context,
    fontListVersion: Int,
    cardBg: Color,
    borderColor: Color,
    alphaFactor: Float = 1.0f,
    lang: AppLanguage = AppLanguage.ENGLISH,
    onSettingsChange: (PlayerSettings) -> Unit,
    onChooseFontDirectory: () -> Unit,
    onPickFontFiles: () -> Unit,
    onReloadFonts: () -> Unit,
    onDeleteFont: (String) -> Unit,
    onDeleteAllFonts: () -> Unit,
    onOpenFolderVideoSelector: () -> Unit = {}
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val installedFonts = remember(fontListVersion, settings.subtitleFontsReloadNonce, settings.showVideoEmbeddedSubtitleFonts) {
        SubtitleFontManager.getInstalledFonts(context, includeVideoExtracted = settings.showVideoEmbeddedSubtitleFonts)
    }
    var isSubtitlesExpanded by remember { mutableStateOf(false) }
    var isFontsExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // 1. Header: "Subtitles & Custom Fonts"
        CategoryBanner(
            icon = Icons.Outlined.Subtitles,
            lumoraIconRes = R.drawable.lumora_subtitles,
            title = AppStrings.getSubtitlesCategory(lang)

        )

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: SUBTITLE PREFERENCES
        Text(
            text = "SUBTITLE PREFERENCES",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryText,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 2. Main Expandable Card named "Subtitles" (or "Subtitle Preferences")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row (Always visible, tap to expand/collapse)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSubtitlesExpanded = !isSubtitlesExpanded }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AccentSkyBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Subtitles,
                                contentDescription = null,
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preferred Subtitle Languages",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val currentSubtitleLangs = settings.preferredSubtitleLanguages.trim()
                            val subtitleSummaryText = if (currentSubtitleLangs.isNotBlank()) {
                                val targetInfo = if (settings.applySubtitleLanguageToSelectedContentOnly) {
                                    val total = settings.selectedSubtitleFolders.size + settings.selectedSubtitleVideos.size
                                    if (total > 0) " • $total items" else " • 0 items"
                                } else {
                                    " • All media"
                                }
                                "$currentSubtitleLangs$targetInfo"
                            } else {
                                "Tap to configure"
                            }
                            Text(
                                text = subtitleSummaryText,
                                fontSize = 11.5.sp,
                                color = if (currentSubtitleLangs.isNotBlank()) AccentSkyBlue else secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Expand / Collapse Chevron Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSubtitlesExpanded) AccentSkyBlue.copy(alpha = 0.18f)
                                else if (isDark) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = if (isSubtitlesExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (isSubtitlesExpanded) "Collapse" else "Expand",
                            tint = if (isSubtitlesExpanded) AccentSkyBlue else secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Collapsible Content
                AnimatedVisibility(
                    visible = isSubtitlesExpanded,
                    enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        HorizontalDivider(color = borderColor)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Subtitle languages ordered by priority (comma-separated)",
                            fontSize = 12.sp,
                            color = secondaryText,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val subLangContainerColor = if (isDark) {
                            Color(0xFF0F172A).copy(alpha = (0.25f + 0.45f * alphaFactor).coerceIn(0.15f, 0.85f))
                        } else {
                            Color(0xFFFFFFFF).copy(alpha = (0.30f + 0.50f * alphaFactor).coerceIn(0.20f, 0.85f))
                        }
                        OutlinedTextField(
                            value = settings.preferredSubtitleLanguages,
                            onValueChange = { onSettingsChange(settings.copy(preferredSubtitleLanguages = it)) },
                            placeholder = {
                                Text(
                                    text = "e.g. eng, hin, jpn, spa",
                                    fontSize = 13.sp,
                                    color = secondaryText.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSkyBlue,
                                unfocusedBorderColor = borderColor,
                                focusedTextColor = primaryText,
                                unfocusedTextColor = primaryText,
                                focusedContainerColor = subLangContainerColor,
                                unfocusedContainerColor = subLangContainerColor,
                                cursorColor = AccentSkyBlue
                            ),
                            trailingIcon = {
                                if (settings.preferredSubtitleLanguages.isNotBlank()) {
                                    IconButton(onClick = { onSettingsChange(settings.copy(preferredSubtitleLanguages = "")) }) {
                                        StyledIcon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear",
                                            tint = secondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Presets
                        Text(
                            text = "Quick Presets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val currentSubtitleTokens = remember(settings.preferredSubtitleLanguages) {
                            AudioLanguageMatcher.normalizePreferredLanguages(settings.preferredSubtitleLanguages)
                        }

                        val subtitlePresets = listOf(
                            "hin" to "Hindi (हिन्दी)",
                            "eng" to "English",
                            "jpn" to "Japanese (日本語)",
                            "kor" to "Korean (한국어)",
                            "spa" to "Spanish (Español)",
                            "fra" to "French (Français)"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            subtitlePresets.forEach { (code, label) ->
                                val isPresent = currentSubtitleTokens.contains(code)
                                val chipBg = if (isPresent) {
                                    if (isDark) Color(0xFF0284C7).copy(alpha = 0.40f) else Color(0xFFE0F2FE)
                                } else {
                                    if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                }
                                val chipBorder = if (isPresent) AccentSkyBlue else borderColor

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipBg)
                                        .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            val tokens = AudioLanguageMatcher.normalizePreferredLanguages(settings.preferredSubtitleLanguages).toMutableList()
                                            if (tokens.contains(code)) {
                                                tokens.remove(code)
                                            } else {
                                                tokens.add(code)
                                            }
                                            onSettingsChange(settings.copy(preferredSubtitleLanguages = tokens.joinToString(", ")))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPresent) {
                                            StyledIcon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = AccentSkyBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isPresent) AccentSkyBlue else primaryText
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = borderColor)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Apply to selected folder/videos only
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Apply to selected folder/videos only",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Restrict auto-selection rule to chosen directories instead of applying globally to all videos",
                                    fontSize = 12.sp,
                                    color = secondaryText,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = settings.applySubtitleLanguageToSelectedContentOnly,
                                onCheckedChange = { onSettingsChange(settings.copy(applySubtitleLanguageToSelectedContentOnly = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentSkyBlue
                                )
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.applySubtitleLanguageToSelectedContentOnly,
                            enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                            exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isDark) Color(0xFF0F172A).copy(alpha = 0.85f)
                                            else Color(0xFFF1F5F9).copy(alpha = 0.95f)
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isDark) Color(0xFF38BDF8).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.35f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Header Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentSkyBlue.copy(alpha = 0.18f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    StyledIcon(
                                                        imageVector = Icons.Outlined.FolderSpecial,
                                                        contentDescription = null,
                                                        tint = AccentSkyBlue,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Target Folders & Videos",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryText
                                                )
                                            }

                                            val totalCount = settings.selectedSubtitleFolders.size + settings.selectedSubtitleVideos.size
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (totalCount > 0) AccentSkyBlue.copy(alpha = 0.20f)
                                                        else if (isDark) Color.White.copy(alpha = 0.10f)
                                                        else Color.Black.copy(alpha = 0.06f)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$totalCount selected",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (totalCount > 0) AccentSkyBlue else secondaryText
                                                )
                                            }
                                        }

                                        // List of selected tags/chips
                                        val hasItems = settings.selectedSubtitleFolders.isNotEmpty() || settings.selectedSubtitleVideos.isNotEmpty()
                                        if (hasItems) {
                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                settings.selectedSubtitleFolders.forEach { folderPath ->
                                                    val folderName = File(folderPath).name.ifBlank { folderPath }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(if (isDark) Color(0xFF1E293B) else Color(0x1A0284C7))
                                                            .border(
                                                                1.dp,
                                                                AccentSkyBlue.copy(alpha = 0.40f),
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            StyledIcon(
                                                                imageVector = Icons.Outlined.Folder,
                                                                contentDescription = null,
                                                                tint = AccentSkyBlue,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = folderName,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = primaryText,
                                                                maxLines = 1
                                                            )
                                                            StyledIcon(
                                                                imageVector = Icons.Outlined.Close,
                                                                contentDescription = "Remove",
                                                                tint = secondaryText,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable {
                                                                        val newSet = settings.selectedSubtitleFolders.toMutableSet()
                                                                        newSet.remove(folderPath)
                                                                        onSettingsChange(settings.copy(selectedSubtitleFolders = newSet))
                                                                    }
                                                            )
                                                        }
                                                    }
                                                }

                                                settings.selectedSubtitleVideos.forEach { videoPath ->
                                                    val videoName = File(videoPath).name.ifBlank { videoPath }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(if (isDark) Color(0xFF1E293B) else Color(0x1A6366F1))
                                                            .border(
                                                                1.dp,
                                                                Color(0xFF818CF8).copy(alpha = 0.40f),
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            StyledIcon(
                                                                imageVector = Icons.Outlined.Movie,
                                                                contentDescription = null,
                                                                tint = Color(0xFF818CF8),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = videoName,
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = primaryText,
                                                                maxLines = 1
                                                            )
                                                            StyledIcon(
                                                                imageVector = Icons.Outlined.Close,
                                                                contentDescription = "Remove",
                                                                tint = secondaryText,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable {
                                                                        val newSet = settings.selectedSubtitleVideos.toMutableSet()
                                                                        newSet.remove(videoPath)
                                                                        onSettingsChange(settings.copy(selectedSubtitleVideos = newSet))
                                                                    }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "No folders or videos selected yet. Tap '+' below to select directories or videos where auto-selection rule applies.",
                                                fontSize = 11.5.sp,
                                                color = secondaryText,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        // Plus Add Button
                                        Button(
                                            onClick = { onOpenFolderVideoSelector() },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(AccentGradient)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                StyledIcon(
                                                    imageVector = Icons.Outlined.Add,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "+ Add Folders or Videos",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = borderColor)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Dual subtitles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dual subtitles",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Enable displaying primary and secondary subtitles simultaneously",
                                    fontSize = 12.sp,
                                    color = secondaryText,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = settings.dualSubtitlesEnabled,
                                onCheckedChange = { onSettingsChange(settings.copy(dualSubtitlesEnabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentSkyBlue
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = borderColor)
                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Signs & Songs subtitles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signs & Songs subtitles",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryText
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Prioritize signs, songs, and forced narrative subtitle tracks",
                                    fontSize = 12.sp,
                                    color = secondaryText,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = settings.subtitleSignsAndSongs,
                                onCheckedChange = { onSettingsChange(settings.copy(subtitleSignsAndSongs = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentSkyBlue
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = borderColor)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4 Standalone Subtitle Settings outside the expandable card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlayerSwitchRow(
                    title = "Detect subtitles by filename",
                    description = "Auto-detect external subtitle tracks matching video name",
                    checked = settings.detectSubtitlesByFilename,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChange = { onSettingsChange(settings.copy(detectSubtitlesByFilename = it)) }
                )
                HorizontalDivider(color = borderColor)
                PlayerSwitchRow(
                    title = "Automatically load subtitles",
                    description = "Auto-detect and load external subtitle tracks matching video name",
                    checked = settings.autoLoadExternalSubtitles,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChange = { onSettingsChange(settings.copy(autoLoadExternalSubtitles = it)) }
                )
                HorizontalDivider(color = borderColor)
                PlayerSwitchRow(
                    title = "Override ASS/SSA subtitles",
                    description = "Apply custom font styling and sizing to ASS/SSA subtitles",
                    checked = settings.overrideAssSsaSubtitles,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChange = { onSettingsChange(settings.copy(overrideAssSsaSubtitles = it)) }
                )
                HorizontalDivider(color = borderColor)
                PlayerSwitchRow(
                    title = "Advanced ASS/SSA",
                    description = "Edit each style of the current ASS/SSA subtitle individually (Player → Subtitle Style)",
                    checked = settings.advancedAssEnabled,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChange = { onSettingsChange(settings.copy(advancedAssEnabled = it)) }
                )
                HorizontalDivider(color = borderColor)
                PlayerSwitchRow(
                    title = "Scale by window",
                    description = "Scale subtitle font size proportionally with player window",
                    checked = settings.scaleSubtitlesByWindow,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChange = { onSettingsChange(settings.copy(scaleSubtitlesByWindow = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        PlayerSettingsCategory("Fonts", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Show video-embedded fonts",
                description = if (settings.showVideoEmbeddedSubtitleFonts) {
                    "Show fonts extracted from the currently played video in subtitle font pickers"
                } else {
                    "Hide video-extracted fonts; only manually uploaded fonts remain selectable"
                },
                checked = settings.showVideoEmbeddedSubtitleFonts,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onChange = { enabled ->
                    val selectedIsVideoFont = if (!enabled && settings.selectedSubtitleFont.isNotBlank()) {
                        val selected = SubtitleFontManager.getInstalledFonts(context, includeVideoExtracted = true)
                            .firstOrNull { file ->
                                file.name.equals(settings.selectedSubtitleFont, ignoreCase = true) ||
                                    file.nameWithoutExtension.equals(settings.selectedSubtitleFont, ignoreCase = true)
                            }
                        selected?.let { SubtitleFontManager.isVideoExtractedFont(context, it) } == true
                    } else false
                    onSettingsChange(
                        settings.copy(
                            showVideoEmbeddedSubtitleFonts = enabled,
                            selectedSubtitleFont = if (selectedIsVideoFont) "" else settings.selectedSubtitleFont
                        )
                    )
                }
            )

            HorizontalDivider(color = borderColor)

            PlayerActionRow(
                title = "Fonts directory",
                subtitle = if (settings.subtitleFontDirectoryUri.isBlank()) "Choose folder containing .ttf or .otf subtitle fonts" else "Custom font folder configured",
                actionLabel = if (settings.subtitleFontDirectoryUri.isBlank()) "Choose" else "Change",
                icon = R.drawable.lumora_folder_open,
                enabled = true,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                onClick = onChooseFontDirectory
            )

            HorizontalDivider(color = borderColor)

            PlayerActionRow(
                title = "Import font files",
                subtitle = "Select one or more .ttf / .otf files directly",
                actionLabel = "Import",
                icon = Icons.Outlined.Add,
                enabled = true,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                onClick = onPickFontFiles
            )

            HorizontalDivider(color = borderColor)

            // Expandable Loaded Fonts Box UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isFontsExpanded = !isFontsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Loaded fonts library",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentSkyBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${installedFonts.size} loaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentSkyBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isFontsExpanded) "Click to collapse font container" else "Click extend to inspect, apply, or remove loaded font files",
                        fontSize = 12.sp,
                        color = secondaryText,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AccentSkyBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentSkyBlue.copy(alpha = 0.35f)),
                    onClick = { isFontsExpanded = !isFontsExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StyledIcon(
                            imageVector = if (isFontsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (isFontsExpanded) "Collapse" else "Extend",
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isFontsExpanded) "Collapse" else "Extend",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentSkyBlue
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isFontsExpanded,
                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) + shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) {
                        Color(0xFF0F172A).copy(alpha = (0.35f + 0.45f * alphaFactor).coerceIn(0.20f, 0.85f))
                    } else {
                        Color(0xFFF8FAFC).copy(alpha = (0.45f + 0.45f * alphaFactor).coerceIn(0.25f, 0.90f))
                    },
                    border = BorderStroke(1.dp, borderColor.copy(alpha = (0.5f + 0.5f * alphaFactor).coerceIn(0.3f, 0.95f))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Box Header: Title + Badge + Remove All Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.FontDownload,
                                    contentDescription = null,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Loaded Font Repository",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                            }

                            if (installedFonts.any { SubtitleFontManager.isUserUploadedFont(context, it) }) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.40f)),
                                    onClick = onDeleteAllFonts
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        StyledIcon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Remove All",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Remove All",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = borderColor.copy(alpha = 0.4f))

                        if (installedFonts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "No custom fonts loaded yet",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = "Use 'Import font files' or 'Fonts directory' above to add .ttf or .otf fonts.",
                                        fontSize = 11.5.sp,
                                        color = secondaryText,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                installedFonts.forEach { fontFile ->
                                    val isVideoFont = SubtitleFontManager.isVideoExtractedFont(context, fontFile)
                                    val isSelected = settings.selectedSubtitleFont.equals(fontFile.name, ignoreCase = true) ||
                                        settings.selectedSubtitleFont.equals(fontFile.nameWithoutExtension, ignoreCase = true)
                                    val fileSizeKb = (fontFile.length() / 1024).coerceAtLeast(1)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) AccentSkyBlue.copy(alpha = 0.12f)
                                        else if (isDark) Color(0xFF1E293B).copy(alpha = 0.45f)
                                        else Color.White.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, if (isSelected) AccentSkyBlue.copy(alpha = 0.45f) else borderColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) AccentSkyBlue.copy(alpha = 0.20f)
                                                    else if (isDark) Color(0x33FFFFFF)
                                                    else Color(0x1A000000),
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        StyledIcon(
                                                            imageVector = Icons.Outlined.FontDownload,
                                                            contentDescription = null,
                                                            tint = if (isSelected) AccentSkyBlue else primaryText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    val originalFontName = SubtitleFontManager.getOriginalFontName(fontFile)
                                                    Text(
                                                        text = originalFontName,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) AccentSkyBlue else primaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        // Only the font's real (metadata) name is shown - never the file name.
                                                        Text(
                                                            text = "${fileSizeKb} KB",
                                                            fontSize = 11.sp,
                                                            color = secondaryText
                                                        )
                                                        if (isSelected) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = AccentSkyBlue.copy(alpha = 0.20f)
                                                            ) {
                                                                Text(
                                                                    text = "Active",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = AccentSkyBlue,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (isVideoFont) "Video font" else "Tap to apply",
                                                                    fontSize = 10.5.sp,
                                                                    color = if (isVideoFont) secondaryText else AccentSkyBlue,
                                                                    modifier = if (isVideoFont) Modifier else Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .clickable {
                                                                            onSettingsChange(settings.copy(selectedSubtitleFont = fontFile.name))
                                                                        }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Only manually uploaded fonts are removable. Video-extracted
                                            // fonts remain available for the current renderer/session.
                                            if (!isVideoFont) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                                                    onClick = { onDeleteFont(fontFile.name) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        StyledIcon(
                                                            imageVector = Icons.Outlined.Delete,
                                                            contentDescription = "Remove font",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Remove",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFFEF4444)
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

            HorizontalDivider(color = borderColor)

            PlayerActionRow(
                title = "Reload fonts",
                subtitle = "Rescan font directory and refresh loaded font list",
                actionLabel = "Reload",
                icon = Icons.Outlined.Refresh,
                enabled = settings.subtitleFontDirectoryUri.isNotBlank() || installedFonts.isNotEmpty(),
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                onClick = onReloadFonts
            )
        }
    }
}

@Composable
private fun SubtitleSettingsPanel(
    settings: PlayerSettings,
    isDark: Boolean,
    context: android.content.Context,
    fontListVersion: Int,
    cardBg: Color,
    borderColor: Color,
    alphaFactor: Float = 1.0f,
    lang: AppLanguage = AppLanguage.ENGLISH,
    onSettingsChange: (PlayerSettings) -> Unit,
    onChooseFontDirectory: () -> Unit,
    onPickFontFiles: () -> Unit,
    onReloadFonts: () -> Unit,
    onDeleteFont: (String) -> Unit,
    onDeleteAllFonts: () -> Unit,
    onOpenFolderVideoSelector: () -> Unit = {}
) {
    SubtitlesTab(
        settings = settings,
        isDark = isDark,
        context = context,
        fontListVersion = fontListVersion,
        cardBg = cardBg,
        borderColor = borderColor,
        alphaFactor = alphaFactor,
        lang = lang,
        onSettingsChange = onSettingsChange,
        onChooseFontDirectory = onChooseFontDirectory,
        onPickFontFiles = onPickFontFiles,
        onReloadFonts = onReloadFonts,
        onDeleteFont = onDeleteFont,
        onDeleteAllFonts = onDeleteAllFonts,
        onOpenFolderVideoSelector = onOpenFolderVideoSelector
    )
}

@Composable
private fun GestureSettingsPanel(
    settings: PlayerSettings,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color,
    alphaFactor: Float = 1.0f,
    onSettingsChange: (PlayerSettings) -> Unit
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PlayerSettingsCategory("Swipe & Speed", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Brightness gestures",
                description = "Swipe vertically on left side to adjust screen brightness",
                checked = settings.brightnessGestures,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(brightnessGestures = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Volume gestures",
                description = "Swipe vertically on right side to adjust media volume",
                checked = settings.volumeGestures,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(volumeGestures = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Swap volume and brightness slider",
                description = "Swipe left for volume and right for brightness",
                checked = settings.swapVolumeBrightness,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(swapVolumeBrightness = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Pinch to zoom",
                description = "Pinch with two fingers to zoom and scale video frame",
                checked = settings.pinchToZoom,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(pinchToZoom = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Pinch to zoom subtitles",
                description = "Pinch subtitle text with two fingers to resize",
                checked = settings.pinchToZoomSubtitles,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(pinchToZoomSubtitles = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Swipe subtitles to seek dialog",
                description = "Swipe subtitle area to open seek jump dialog",
                checked = settings.swipeSubtitlesToSeekDialog,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(swipeSubtitlesToSeekDialog = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Horizontal swipe to seek",
                description = "Swipe horizontally anywhere to seek forward or backward",
                checked = settings.horizontalSwipeToSeek,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(horizontalSwipeToSeek = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Swipe up in center for playlist",
                description = "Swipe upward from center of player to display playlist",
                checked = settings.swipeUpCenterForPlaylist,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(swipeUpCenterForPlaylist = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSliderRow(
                title = "Horizontal swipe sensitivity",
                description = "Maximum seek jump percentage when swiping across the entire screen",
                value = settings.horizontalSwipeSensitivity,
                min = 1,
                max = 100,
                unit = "%",
                presets = listOf(5, 10, 15, 20, 25, 50, 100),
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(horizontalSwipeSensitivity = it)) }
        }

        PlayerSettingsCategory("Hold & Speed", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Hold for multi-x speed",
                description = "Long-press and hold screen to accelerate playback speed",
                checked = settings.holdMultiSpeed,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(holdMultiSpeed = it)) }

            HorizontalDivider(color = borderColor)

            PlayerChoiceRow(
                title = "Hold speed multiplier",
                description = "Playback rate applied during long-press hold",
                value = String.format(java.util.Locale.US, "%.2f", settings.holdSpeedMultiplier),
                options = listOf(
                    "1.25" to "1.25x",
                    "1.50" to "1.50x",
                    "2.00" to "2.00x",
                    "2.50" to "2.50x",
                    "3.00" to "3.00x",
                    "4.00" to "4.00x"
                ),
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { onSettingsChange(settings.copy(holdSpeedMultiplier = it.toDoubleOrNull() ?: 2.0)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Dynamic Speed Overlay",
                description = "Display animated speed indicator badge during long-press",
                checked = settings.dynamicSpeedOverlay,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(dynamicSpeedOverlay = it)) }
        }

        // Mutual exclusion callbacks between Single Tap and Double Tap
        val onDoubleTapLeftChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(doubleTapLeftAction = newVal)
            if (newVal != "NONE") {
                if (updated.singleTapLeftAction == newVal || updated.singleTapLeftAction != "NONE") {
                    updated = updated.copy(singleTapLeftAction = "NONE")
                }
                if (updated.singleTapCenterAction == newVal) {
                    updated = updated.copy(singleTapCenterAction = "NONE")
                }
                if (updated.singleTapRightAction == newVal) {
                    updated = updated.copy(singleTapRightAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        val onDoubleTapCenterChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(doubleTapCenterAction = newVal)
            if (newVal != "NONE") {
                if (updated.singleTapCenterAction == newVal || updated.singleTapCenterAction != "NONE") {
                    updated = updated.copy(singleTapCenterAction = "NONE")
                }
                if (updated.singleTapLeftAction == newVal) {
                    updated = updated.copy(singleTapLeftAction = "NONE")
                }
                if (updated.singleTapRightAction == newVal) {
                    updated = updated.copy(singleTapRightAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        val onDoubleTapRightChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(doubleTapRightAction = newVal)
            if (newVal != "NONE") {
                if (updated.singleTapRightAction == newVal || updated.singleTapRightAction != "NONE") {
                    updated = updated.copy(singleTapRightAction = "NONE")
                }
                if (updated.singleTapLeftAction == newVal) {
                    updated = updated.copy(singleTapLeftAction = "NONE")
                }
                if (updated.singleTapCenterAction == newVal) {
                    updated = updated.copy(singleTapCenterAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        val onSingleTapLeftChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(singleTapLeftAction = newVal)
            if (newVal != "NONE") {
                if (updated.doubleTapLeftAction == newVal || updated.doubleTapLeftAction != "NONE") {
                    updated = updated.copy(doubleTapLeftAction = "NONE")
                }
                if (updated.doubleTapCenterAction == newVal) {
                    updated = updated.copy(doubleTapCenterAction = "NONE")
                }
                if (updated.doubleTapRightAction == newVal) {
                    updated = updated.copy(doubleTapRightAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        val onSingleTapCenterChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(singleTapCenterAction = newVal)
            if (newVal != "NONE") {
                if (updated.doubleTapCenterAction == newVal || updated.doubleTapCenterAction != "NONE") {
                    updated = updated.copy(doubleTapCenterAction = "NONE")
                }
                if (updated.doubleTapLeftAction == newVal) {
                    updated = updated.copy(doubleTapLeftAction = "NONE")
                }
                if (updated.doubleTapRightAction == newVal) {
                    updated = updated.copy(doubleTapRightAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        val onSingleTapRightChange: (String) -> Unit = { newVal ->
            var updated = settings.copy(singleTapRightAction = newVal)
            if (newVal != "NONE") {
                if (updated.doubleTapRightAction == newVal || updated.doubleTapRightAction != "NONE") {
                    updated = updated.copy(doubleTapRightAction = "NONE")
                }
                if (updated.doubleTapLeftAction == newVal) {
                    updated = updated.copy(doubleTapLeftAction = "NONE")
                }
                if (updated.doubleTapCenterAction == newVal) {
                    updated = updated.copy(doubleTapCenterAction = "NONE")
                }
            }
            onSettingsChange(updated)
        }

        PlayerSettingsCategory("Double Tap", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Enable Double Tap",
                description = "Master toggle for all double tap gestures and shortcut actions",
                checked = settings.enableDoubleTap,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(enableDoubleTap = it)) }

            AnimatedVisibility(
                visible = settings.enableDoubleTap,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(0.dp))
                    HorizontalDivider(color = borderColor)

                    PlayerSliderRow(
                        title = "Double tap seek duration",
                        description = "Seconds to seek per double-tap gesture (1s, 5s, 10s, etc.)",
                        value = settings.doubleTapSeekDuration,
                        min = 1,
                        max = 120,
                        unit = "s",
                        presets = listOf(1, 3, 5, 10, 15, 20, 30, 60),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    ) { onSettingsChange(settings.copy(doubleTapSeekDuration = it)) }

                    HorizontalDivider(color = borderColor)

                    PlayerSliderRow(
                        title = "Double Tap Seek Area Width",
                        description = "Horizontal width percentage of double-tap trigger zones",
                        value = settings.doubleTapSeekAreaWidth,
                        min = 10,
                        max = 50,
                        unit = "%",
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText
                    ) { onSettingsChange(settings.copy(doubleTapSeekAreaWidth = it)) }

                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Double tap (left)",
                        description = "Action triggered by double tapping left side of screen",
                        value = settings.doubleTapLeftAction,
                        options = listOf("SEEK_BACK" to "Seek / Back", "PLAY_PAUSE" to "Play / Pause", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onDoubleTapLeftChange
                    )

                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Double tap (center)",
                        description = "Action triggered by double tapping center of screen",
                        value = settings.doubleTapCenterAction,
                        options = listOf("PLAY_PAUSE" to "Play / Pause", "SEEK_BACK" to "Seek / Back", "SEEK_FORWARD" to "Seek / Forward", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onDoubleTapCenterChange
                    )

                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Double tap (right)",
                        description = "Action triggered by double tapping right side of screen",
                        value = settings.doubleTapRightAction,
                        options = listOf("SEEK_FORWARD" to "Seek / Forward", "PLAY_PAUSE" to "Play / Pause", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onDoubleTapRightChange
                    )
                }
            }
        }

        PlayerSettingsCategory("Single Tap", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Enable Single Tap",
                description = "Master toggle for single tap zone gestures and shortcut actions",
                checked = settings.enableSingleTap,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { enabled ->
                onSettingsChange(
                    settings.copy(
                        enableSingleTap = enabled,
                        singleTapLeftAction = "NONE",
                        singleTapCenterAction = "NONE",
                        singleTapRightAction = "NONE"
                    )
                )
            }

            AnimatedVisibility(
                visible = settings.enableSingleTap,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(0.dp))
                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Single tap (left)",
                        description = "Action triggered by single tapping left side of screen",
                        value = settings.singleTapLeftAction,
                        options = listOf("SEEK_BACK" to "Seek / Back", "PLAY_PAUSE" to "Play / Pause", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onSingleTapLeftChange
                    )

                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Single tap (center)",
                        description = "Action triggered by single tapping center of screen",
                        value = settings.singleTapCenterAction,
                        options = listOf("PLAY_PAUSE" to "Play / Pause", "SEEK_BACK" to "Seek / Back", "SEEK_FORWARD" to "Seek / Forward", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onSingleTapCenterChange
                    )

                    HorizontalDivider(color = borderColor)

                    PlayerChoiceRow(
                        title = "Single tap (right)",
                        description = "Action triggered by single tapping right side of screen",
                        value = settings.singleTapRightAction,
                        options = listOf("SEEK_FORWARD" to "Seek / Forward", "PLAY_PAUSE" to "Play / Pause", "NONE" to "None"),
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        alphaFactor = alphaFactor,
                        onChange = onSingleTapRightChange
                    )
                }
            }
        }

        PlayerSettingsCategory("Subtitle Gestures", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Hold and drag moves subtitles",
                description = "Long press on subtitle text and drag to reposition on screen",
                checked = settings.holdDragMovesSubtitles,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(holdDragMovesSubtitles = it)) }
        }

        PlayerSettingsCategory("Media controls", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Previous",
                description = "Enable gesture or media button for previous item",
                checked = settings.mediaPreviousControl,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(mediaPreviousControl = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Play/Pause",
                description = "Enable gesture or media button for toggle play/pause",
                checked = settings.mediaPlayPauseControl,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(mediaPlayPauseControl = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Next",
                description = "Enable gesture or media button for next item",
                checked = settings.mediaNextControl,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(mediaNextControl = it)) }
        }
    }
}

@Composable
private fun PlayerSettingsPanel(
    settings: PlayerSettings,
    onSettingsChange: (PlayerSettings) -> Unit,
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color,
    alphaFactor: Float = 1.0f
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PlayerSettingsCategory("General", cardBg, borderColor, secondaryText) {
            PlayerChoiceRow(
                title = "Orientation",
                description = "Video playback orientation and screen lock behavior",
                value = settings.orientation,
                options = listOf(
                    "FREE" to "Free",
                    "VIDEO" to "Video",
                    "PORTRAIT" to "Portrait",
                    "REVERSE_PORTRAIT" to "Reverse portrait",
                    "SENSOR_PORTRAIT" to "Sensor portrait",
                    "LANDSCAPE" to "Landscape",
                    "REVERSE_LANDSCAPE" to "Reverse landscape",
                    "SENSOR_LANDSCAPE" to "Sensor landscape"
                ),
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { v -> onSettingsChange(settings.copy(orientation = v)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Save position on quit",
                description = "Resume video playback from last known position",
                checked = settings.savePositionOnQuit,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(savePositionOnQuit = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Close after the end of playback",
                description = "Automatically exit player when playback finishes",
                checked = settings.closeAfterEnd,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(closeAfterEnd = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Autoplay next video",
                description = "Play next video in folder or queue automatically",
                checked = settings.autoplayNext,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(autoplayNext = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Repeat playlist after last episode",
                description = "Restart the playlist from the first episode after the last episode finishes",
                checked = settings.repeatPlaylistAfterLast,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(repeatPlaylistAfterLast = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Enable next/previous navigation",
                description = "Show previous and next track controls on overlay",
                checked = settings.enableNextPrevious,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(enableNextPrevious = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Remember display brightness",
                description = "Restore individual screen brightness level for each session",
                checked = settings.rememberBrightness,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(rememberBrightness = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Auto Picture-in-Picture",
                description = "Switch to PiP mode automatically when minimizing player",
                checked = settings.autoPictureInPicture,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(autoPictureInPicture = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Keep screen on when paused",
                description = "Prevent display timeout while playback is paused",
                checked = settings.keepScreenOnPaused,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(keepScreenOnPaused = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Autoplay after screen unlock",
                description = "Resume playback automatically when device is unlocked",
                checked = settings.autoplayAfterUnlock,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(autoplayAfterUnlock = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show Media Info in chooser",
                description = "Display audio and video technical codecs in track chooser",
                checked = settings.showMediaInfoChooser,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showMediaInfoChooser = it)) }
        }

        PlayerSettingsCategory("Display & Controls", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Show system status bar with controls",
                description = "Display battery and time status bar when controls are visible",
                checked = settings.showStatusBar,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showStatusBar = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show navigation bar with controls",
                description = "Display Android navigation buttons when controls are visible",
                checked = settings.showNavigationBar,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showNavigationBar = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Safe Area Window",
                description = "Pad player margins around notches and curved screen edges",
                checked = settings.safeAreaWindow,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(safeAreaWindow = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Reduce player animation",
                description = "Minimize motion effects for faster UI performance",
                checked = settings.reduceAnimation,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(reduceAnimation = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show loading circle",
                description = "Display circular progress spinner during video loading",
                checked = settings.showLoadingCircle,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showLoadingCircle = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Swap volume and brightness slider",
                description = "Swipe left for volume and right for brightness",
                checked = settings.swapVolumeBrightness,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(swapVolumeBrightness = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show slider on opposite side",
                description = "Swipe on one side and the volume or brightness slider appears on the other side",
                checked = settings.gestureHudOppositeSide,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(gestureHudOppositeSide = it)) }
        }

        PlayerSettingsCategory("Seeking", cardBg, borderColor, secondaryText) {
            PlayerSwitchRow(
                title = "Show ripple when double tap seeking",
                description = "Display animated ripple indicator when double tapping",
                checked = settings.rippleOnDoubleTap,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(rippleOnDoubleTap = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show seek time",
                description = "Display relative seek target timestamp during swipe gestures",
                checked = settings.showSeekTime,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showSeekTime = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Show buffered range on seek bar",
                description = "Display cached playback buffer range on progress bar",
                checked = settings.showBufferedRange,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(showBufferedRange = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Use precise seeking",
                description = "Seek to exact requested frame rather than nearest keyframe",
                checked = settings.preciseSeeking,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(preciseSeeking = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Use ThumbFast seek preview",
                description = "Display rapid visual thumbnail preview while seek scrubbing",
                checked = settings.thumbFastPreview,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(thumbFastPreview = it)) }
        }

        PlayerSettingsCategory("Skip", cardBg, borderColor, secondaryText) {
            PlayerSliderRow(
                title = "Custom skip duration",
                description = "Duration in seconds to jump when pressing skip buttons",
                value = settings.customSkipDuration,
                min = 1,
                max = 600,
                unit = "s",
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(customSkipDuration = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Use online skip markers",
                description = "Query online chapter and intro databases automatically",
                checked = settings.onlineSkipMarkers,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(onlineSkipMarkers = it)) }

            HorizontalDivider(color = borderColor)

            PlayerChoiceRow(
                title = "Online marker provider",
                description = "Preferred service for fetching intro and outro timestamps",
                value = settings.markerProvider,
                options = listOf("INTRODB" to "IntroDB", "TIDB" to "TIDB", "ANISKIP" to "AniSkip"),
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { v -> onSettingsChange(settings.copy(markerProvider = v)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Detect intro/outro from chapter titles",
                description = "Auto-identify openings and endings from embedded chapter titles",
                checked = settings.detectChapterIntroOutro,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(detectChapterIntroOutro = it)) }

            HorizontalDivider(color = borderColor)

            PlayerTextRow(
                title = "Custom keywords for skipping openings",
                description = "Comma-separated keywords used to detect intro markers",
                value = settings.customOpeningKeywords,
                placeholder = "e.g. OP, Opening, Intro",
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { onSettingsChange(settings.copy(customOpeningKeywords = it)) }

            HorizontalDivider(color = borderColor)

            PlayerTextRow(
                title = "Custom keywords for skipping endings",
                description = "Comma-separated keywords used to detect ending markers",
                value = settings.customEndingKeywords,
                placeholder = "e.g. ED, Ending, Outro",
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { onSettingsChange(settings.copy(customEndingKeywords = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Auto-skip intro",
                description = "Automatically jump past detected opening chapter segment",
                checked = settings.autoSkipIntro,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(autoSkipIntro = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Auto-skip outro",
                description = "Automatically jump to next episode when ending segment starts",
                checked = settings.autoSkipOutro,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(autoSkipOutro = it)) }
        }

        PlayerSettingsCategory("Screenshots", cardBg, borderColor, secondaryText) {
            PlayerChoiceRow(
                title = "Image format",
                description = "Image file encoding format for captured frames",
                value = settings.screenshotFormat,
                options = listOf("PNG" to "PNG", "JPG" to "JPG", "WEBP" to "WebP"),
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { v -> onSettingsChange(settings.copy(screenshotFormat = v)) }

            HorizontalDivider(color = borderColor)

            PlayerSwitchRow(
                title = "Include subtitles in screenshots",
                description = "Burn visible subtitle text directly into captured images",
                checked = settings.subtitlesInScreenshots,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(subtitlesInScreenshots = it)) }

            HorizontalDivider(color = borderColor)

            PlayerTextRow(
                title = "Filename template",
                description = "Naming template pattern for saved screenshots",
                value = settings.screenshotFilenameTemplate,
                placeholder = "e.g. %F_%wH.%wM.%wS",
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                alphaFactor = alphaFactor
            ) { onSettingsChange(settings.copy(screenshotFilenameTemplate = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSliderRow(
                title = "JPEG/WebP quality",
                description = "Compression quality level for lossy captures",
                value = settings.jpegWebpQuality,
                min = 1,
                max = 100,
                unit = "%",
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(jpegWebpQuality = it)) }

            HorizontalDivider(color = borderColor)

            PlayerSliderRow(
                title = "PNG compression",
                description = "Compression level from 0 (fastest) to 9 (smallest file)",
                value = settings.pngCompression,
                min = 0,
                max = 9,
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) { onSettingsChange(settings.copy(pngCompression = it)) }
        }
    }
}

@Composable
private fun PlayerSettingsCategory(
    title: String,
    cardBg: Color,
    borderColor: Color,
    secondaryText: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryText,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
private fun PlayerSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.45f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked && enabled,
            onCheckedChange = if (enabled) onChange else { _ -> },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentSkyBlue
            )
        )
    }
}

@Composable
private fun PlayerChoiceRow(
    title: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    isDark: Boolean,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    alphaFactor: Float = 1.0f,
    onChange: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: value

    // Obtain dynamic transparency from slider (10% to 100%)
    val currentGlassTrans = LocalGlassBlurTransparency.current
    val effectiveFactor = if (alphaFactor != 1.0f) {
        alphaFactor.coerceIn(0.10f, 1.0f)
    } else {
        (currentGlassTrans / 100f).coerceIn(0.10f, 1.0f)
    }

    // Dynamic glass opacity smoothly interpolates across slider (10% to 100%)
    // Uses high baseline opacity (86% to 99%) so background controls/text do not show through unevenly,
    // while providing genuine frosted glass translucency and seamlessly reacting to the UI Glass Blur & Opacity slider.
    val dropdownBg = if (isDark) {
        Color(0xFF0F172A).copy(alpha = (0.86f + 0.13f * effectiveFactor).coerceIn(0.86f, 0.99f))
    } else {
        Color(0xFFFFFFFF).copy(alpha = (0.88f + 0.11f * effectiveFactor).coerceIn(0.88f, 0.99f))
    }

    val dropdownBorder = if (isDark) {
        Color(0xFF38BDF8).copy(alpha = (0.35f + 0.35f * effectiveFactor).coerceIn(0.35f, 0.70f))
    } else {
        Color(0xFF0284C7).copy(alpha = (0.30f + 0.35f * effectiveFactor).coerceIn(0.30f, 0.65f))
    }

    val unselectedTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val selectedItemBg = if (isDark) {
        Color(0x3338BDF8).copy(alpha = (0.22f + 0.20f * effectiveFactor).coerceIn(0.22f, 0.45f))
    } else {
        Color(0xFF0284C7).copy(alpha = (0.12f + 0.14f * effectiveFactor).coerceIn(0.12f, 0.26f))
    }

    val menuShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box {
            val pillAccent = accentColor
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (expanded) {
                    if (isDark) Color(0x3338BDF8) else Color(0x220284C7)
                } else if (isDark) {
                    Color(0xFF1E293B).copy(alpha = (0.20f + 0.60f * effectiveFactor).coerceIn(0.15f, 0.85f))
                } else {
                    Color(0xFF0284C7).copy(alpha = (0.07f + 0.15f * effectiveFactor).coerceIn(0.07f, 0.22f))
                },
                border = if (expanded) {
                    BorderStroke(1.2.dp, pillAccent)
                } else {
                    BorderStroke(1.dp, borderColor.copy(alpha = (0.35f + 0.55f * effectiveFactor).coerceIn(0.35f, 0.90f)))
                },
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectedLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pillAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp)
                    )
                    StyledIcon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        tint = pillAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = menuShape,
                containerColor = dropdownBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.2.dp, dropdownBorder),
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 225.dp)
                    .clip(menuShape)
            ) {
                options.forEach { (key, label) ->
                    val isSelected = key == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) selectedItemBg else Color.Transparent
                            )
                            .clickable {
                                expanded = false
                                onChange(key)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentColor else unselectedTextColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StyledIcon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSliderRow(
    title: String,
    description: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String = "",
    presets: List<Int>? = null,
    isDark: Boolean,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = secondaryText,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$value$unit",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (!presets.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { preset ->
                    val isSelected = preset == value
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentSkyBlue else if (isDark) Color(0xFF1E293B) else Color(0x1A0284C7),
                        modifier = Modifier.clickable { onChange(preset) }
                    ) {
                        Text(
                            text = "$preset$unit",
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else primaryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = AccentPink,
                activeTrackColor = AccentSkyBlue,
                inactiveTrackColor = if (isDark) AccentSkyBlue.copy(alpha = 0.22f) else AccentSkyBlue.copy(alpha = 0.15f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerTextRow(
    title: String,
    description: String,
    value: String,
    placeholder: String = "",
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    alphaFactor: Float = 1.0f,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    val isDark = primaryText == DarkTextPrimary
    val textFieldContainerColor = if (isDark) {
        Color(0xFF0F172A).copy(alpha = (0.25f + 0.45f * alphaFactor).coerceIn(0.15f, 0.85f))
    } else {
        Color(0xFFFFFFFF).copy(alpha = (0.30f + 0.50f * alphaFactor).coerceIn(0.20f, 0.85f))
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryText
        )
        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, fontSize = 13.sp, color = secondaryText.copy(alpha = 0.6f)) }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentSkyBlue,
                unfocusedBorderColor = borderColor,
                focusedTextColor = primaryText,
                unfocusedTextColor = primaryText,
                focusedContainerColor = textFieldContainerColor,
                unfocusedContainerColor = textFieldContainerColor,
                cursorColor = AccentSkyBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    icon: ImageVector,
    enabled: Boolean = true,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    onClick: () -> Unit
) {
    PlayerActionRow(
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        iconContent = {
            StyledIcon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) AccentSkyBlue else secondaryText,
                modifier = Modifier.size(16.dp)
            )
        },
        enabled = enabled,
        primaryText = primaryText,
        secondaryText = secondaryText,
        borderColor = borderColor,
        onClick = onClick
    )
}

@Composable
private fun PlayerActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    icon: Int,
    enabled: Boolean = true,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    onClick: () -> Unit
) {
    PlayerActionRow(
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        iconContent = {
            StyledIcon(
                drawableRes = icon,
                contentDescription = null,
                tint = if (enabled) AccentSkyBlue else secondaryText,
                modifier = Modifier.size(16.dp)
            )
        },
        enabled = enabled,
        primaryText = primaryText,
        secondaryText = secondaryText,
        borderColor = borderColor,
        onClick = onClick
    )
}

@Composable
private fun PlayerActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    iconContent: @Composable () -> Unit,
    enabled: Boolean = true,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) primaryText else secondaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (enabled) AccentSkyBlue.copy(alpha = 0.15f) else Color.Transparent,
            border = BorderStroke(1.dp, if (enabled) AccentSkyBlue.copy(alpha = 0.35f) else borderColor),
            onClick = onClick,
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                iconContent()
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) AccentSkyBlue else secondaryText
                )
            }
        }
    }
}