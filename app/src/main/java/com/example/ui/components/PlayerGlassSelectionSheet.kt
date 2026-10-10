package com.example.ui.components

import com.example.ui.theme.isAppInDarkTheme
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.AccentPink
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.example.player.EqualizerPreset
import com.example.player.TrackAudioConfig
import com.example.player.AudioChannelMode
import com.example.player.VideoFilterPreset
import com.example.player.ManualVideoAdjustments
import com.example.ui.state.PlayerLayoutConfig
import com.example.ui.state.SeekbarStyle
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Environment
import com.example.player.PlayerMediaTrack
import com.example.player.PlayerVideoChapter
import com.example.ai.AiFeaturesSettingsStore
import com.example.ai.TrackTranslationStatus
import com.example.ai.TranslateLanguages
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.example.ui.screens.AspectRatioMode
import com.example.ui.screens.DecoderMode
import com.example.ui.screens.PlayerRepeatMode
import com.example.ui.state.GestureSensitivityMode
import com.example.ui.screens.VideoItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.Context
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Global persistent store for panel custom heights.
 * Remembers resized height per specific panel (Chapters, Subtitles, Audio, etc.)
 * so user changes persist and never reset unless changed by the user.
 */
object PlayerPanelHeightStore {
    private const val PREFS_NAME = "player_panel_heights_pref"
    private val memoryHeights = mutableMapOf<String, Float>()

    fun getHeight(context: Context, panelTitle: String, defaultHeight: Float): Float {
        val key = panelTitle.trim().lowercase(Locale.ROOT)
        memoryHeights[key]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getFloat("height_$key", defaultHeight)
        memoryHeights[key] = saved
        return saved
    }

    fun saveHeight(context: Context, panelTitle: String, height: Float) {
        val key = panelTitle.trim().lowercase(Locale.ROOT)
        memoryHeights[key] = height
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat("height_$key", height).apply()
    }
}

/**
 * Clean Frosted Glass Palette
 */
private val ScrimColor = Color.Transparent

private val ActiveHighlightColor: Color
    get() = AccentSkyBlue
private val ActiveItemBg = Color(0xFFE0F2FE).copy(alpha = 0.90f)

val LocalSheetNormalTextColor = compositionLocalOf { Color(0xFF1E293B) }
val LocalSheetSecondaryTextColor = compositionLocalOf { Color(0xFF64748B) }
val LocalSheetIsLowGlassOpacity = compositionLocalOf { false }

val NormalItemColor: Color
    @Composable get() = LocalSheetNormalTextColor.current

val SecondaryTextColor: Color
    @Composable get() = LocalSheetSecondaryTextColor.current

val SheetButtonBgColor: Color
    @Composable get() = if (LocalSheetIsLowGlassOpacity.current) Color(0x22FFFFFF) else Color(0xFFF1F5F9)

val SheetButtonTextColor: Color
    @Composable get() = Color(0xFF0F172A)

val SheetButtonBorderColor: Color
    @Composable get() = if (LocalSheetIsLowGlassOpacity.current) Color(0x30FFFFFF) else Color(0xFFCBD5E1)

/**
 * Ultra-stable, high-performance modal container for all player panels.
 * Height strictly constrained to at most 60% of the screen with touch isolation.
 * In portrait: reaches full width edge-to-edge with top rounded corners.
 * In landscape: constrained to strictly 60% of screen width.
 * Tapping outside the sheet card cancels and closes the panel.
 * Tapping inside the sheet card is completely consumed and never leaks to video controls underneath.
 */
@Composable
fun PlayerGlassModalSheet(
    panelKey: String = "default_panel",
    title: String? = null,
    icon: ImageVector? = null,
    badgeText: String? = null,
    isLandscape: Boolean = false,
    openOnRightSide: Boolean = false,
    heightFraction: Float? = 0.58f,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit,
    headerStartContent: (@Composable () -> Unit)? = null,
    headerEndContent: (@Composable () -> Unit)? = null,
    headerCustomRow: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismissRequest)

    val context = LocalContext.current
    val effectiveKey = if (panelKey != "default_panel") panelKey else (title ?: "default_panel")
    val defaultFraction = heightFraction ?: 0.58f
    val initialFraction = remember(effectiveKey) {
        PlayerPanelHeightStore.getHeight(context, effectiveKey, defaultFraction)
    }
    var currentHeightFraction by remember(effectiveKey) { mutableFloatStateOf(initialFraction) }
    val haptic = LocalHapticFeedback.current

    val animatedHeightFraction by animateFloatAsState(
        targetValue = currentHeightFraction.coerceIn(0.35f, 0.95f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "SheetHeightFraction"
    )

    val isDark = isAppInDarkTheme()
    val isLowGlassOpacity = glassBlurTransparency < 25f
    val sheetNormalTextColor = if (isDark || isLowGlassOpacity) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val sheetSecondaryTextColor = if (isDark || isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B)

    val alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.10f, 1.0f)
    val dynamicGlassGradient = remember(alphaRatio, isLowGlassOpacity, isDark) {
        if (isDark) {
            if (isLowGlassOpacity) {
                SolidColor(Color(0xFF090D16).copy(alpha = (alphaRatio * 0.45f).coerceIn(0.15f, 0.40f)))
            } else if (alphaRatio >= 0.99f) {
                SolidColor(Color(0xFF090D16))
            } else {
                SolidColor(Color(0xFF090D16).copy(alpha = alphaRatio))
            }
        } else if (isLowGlassOpacity) {
            SolidColor(Color(0xFF0F172A).copy(alpha = (0.50f + alphaRatio * 0.40f).coerceIn(0.50f, 0.75f)))
        } else if (alphaRatio >= 0.99f) {
            SolidColor(Color(0xFFFFFFFF))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = alphaRatio),
                    Color(0xFFF8FAFC).copy(alpha = (alphaRatio * 0.95f).coerceIn(0.10f, 1.0f))
                )
            )
        }
    }
    val dynamicBorderColor = remember(alphaRatio, isLowGlassOpacity, isDark) {
        if (isDark) {
            Color.White.copy(alpha = (0.12f + 0.18f * alphaRatio).coerceIn(0.15f, 0.35f))
        } else if (isLowGlassOpacity) {
            Color.White.copy(alpha = 0.35f)
        } else if (alphaRatio >= 0.98f) {
            Color(0xFFCBD5E1)
        } else {
            Color.White.copy(alpha = (alphaRatio * 0.80f).coerceIn(0.15f, 0.95f))
        }
    }

    // Direct in-tree overlay - backdrop consumes touches and dismisses when tapped outside
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onDismissRequest()
                    }
                )
            },
        contentAlignment = if (openOnRightSide) Alignment.BottomEnd else Alignment.BottomCenter
    ) {
        val sheetShape = if (openOnRightSide) {
            RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 16.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            )
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = if (openOnRightSide) {
                    Modifier
                        .fillMaxWidth(if (isLandscape) 0.45f else 0.82f)
                        .widthIn(max = if (isLandscape) 380.dp else 330.dp)
                        .fillMaxHeight(animatedHeightFraction)
                } else {
                    Modifier
                        .fillMaxWidth(if (isLandscape) 0.60f else 1.0f)
                        .widthIn(max = if (isLandscape) 520.dp else 600.dp)
                        .fillMaxHeight(animatedHeightFraction)
                }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { /* Consume clicks completely inside sheet so they never bleed into backdrop or video controls */ }
                        )
                    }
                    .pointerInput(effectiveKey) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                            var totalDragY = 0f
                            var totalDragX = 0f
                            var isResizing = false
                            val pointerId = down.id

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                if (!change.pressed) {
                                    // Pointer released or cancelled
                                    if (isResizing) {
                                        PlayerPanelHeightStore.saveHeight(context, effectiveKey, currentHeightFraction)
                                    }
                                    break
                                }

                                if (change.isConsumed) {
                                    // Child view consumed the event (e.g. Slider drag, list scrolling, color picker)
                                    if (!isResizing) {
                                        break
                                    }
                                } else {
                                    val dragY = change.position.y - change.previousPosition.y
                                    val dragX = change.position.x - change.previousPosition.x
                                    totalDragY += dragY
                                    totalDragX += dragX

                                    if (!isResizing) {
                                        if (abs(totalDragY) > touchSlop && abs(totalDragY) > abs(totalDragX) * 1.2f) {
                                            isResizing = true
                                            try {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    if (isResizing) {
                                        change.consume()
                                        val deltaFraction = -dragY / 500f
                                        val newFraction = (currentHeightFraction + deltaFraction).coerceIn(0.35f, 0.95f)
                                        currentHeightFraction = newFraction
                                    }
                                }
                            }
                            if (isResizing) {
                                PlayerPanelHeightStore.saveHeight(context, effectiveKey, currentHeightFraction)
                            }
                        }
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = sheetShape,
                        ambientColor = Color.Black.copy(alpha = 0.45f),
                        spotColor = Color(0xFF0284C7).copy(alpha = 0.15f)
                    )
                    .clip(sheetShape)
                    .background(dynamicGlassGradient)
                    .border(1.5.dp, dynamicBorderColor, sheetShape)
            ) {
                CompositionLocalProvider(
                    LocalSheetNormalTextColor provides sheetNormalTextColor,
                    LocalSheetSecondaryTextColor provides sheetSecondaryTextColor,
                    LocalSheetIsLowGlassOpacity provides isLowGlassOpacity
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    ) {
                        // Header Row
                        if (headerCustomRow != null) {
                            headerCustomRow()
                        } else if (headerStartContent != null || headerEndContent != null || title != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left: Title or custom start content
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(end = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (headerStartContent != null) {
                                        headerStartContent()
                                    } else if (title != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (icon != null) {
                                                StyledIcon(
                                imageVector = icon,
                                                    contentDescription = null,
                                                    tint = ActiveHighlightColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = title,
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = NormalItemColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Right: Action buttons / trailing content
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = 4.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (headerEndContent != null) {
                                        headerEndContent()
                                    } else if (!badgeText.isNullOrBlank()) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SecondaryTextColor
                                        )
                                    }
                                }
                            }
                        }

                        // Clean dynamic list content
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Helper to clean video title into searchable keyword query
 */
fun cleanVideoTitleForSearch(title: String): String {
    if (title.isBlank()) return ""
    var clean = title.substringBeforeLast('.')
    clean = clean.replace(Regex("(?i)\\b(1080p|720p|480p|4k|2160p|bluray|bdrip|webrip|web-dl|x264|x265|hevc|aac|dts)\\b"), "")
    clean = clean.replace(Regex("[\\[\\]()_.-]"), " ")
    return clean.trim().replace(Regex("\\s+"), " ")
}

/**
 * Searches local device storage folders for matching subtitle files
 */
suspend fun searchLocalDeviceSubtitles(query: String, videoTitle: String): List<File> = withContext(Dispatchers.IO) {
    data class ScoredFile(val file: File, val score: Int)

    val visited = mutableSetOf<String>()
    val scored = mutableListOf<ScoredFile>()
    val normalizedQuery = cleanVideoTitleForSearch(query).lowercase(Locale.ROOT)
    val normalizedVideo = cleanVideoTitleForSearch(videoTitle).lowercase(Locale.ROOT)

    // Search by meaningful tokens instead of requiring the complete filename to match.
    // This handles common release-name differences such as:
    //   Video: "Bleach - 110 [FHD 1080p]..."
    //   Subtitle: "Bleach_110.ass" / "110.srt" / "Bleach - 110.eng.srt"
    // Episode numbers are intentionally weighted more heavily than generic words.
    val stopWords = setOf(
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with",
        "episode", "ep", "sub", "subs", "subtitle", "subtitles", "eng", "english",
        "jpn", "jap", "japanese", "multi", "dual", "audio", "video"
    )

    fun tokens(text: String): List<String> = text
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 2 && it !in stopWords }
        .distinct()

    val queryTokens = tokens(if (normalizedQuery.isNotBlank()) normalizedQuery else normalizedVideo)
    val videoTokens = tokens(normalizedVideo)
    val effectiveTokens = (queryTokens + videoTokens).distinct()

    if (effectiveTokens.isEmpty()) return@withContext emptyList()

    fun scoreFile(file: File): Int {
        val fileName = file.nameWithoutExtension.lowercase(Locale.ROOT)
        val folderName = file.parentFile?.name?.lowercase(Locale.ROOT).orEmpty()
        val searchable = "$fileName $folderName"
        val fileTokens = tokens(searchable)
        var score = 0
        var matched = 0

        for (token in effectiveTokens) {
            val numeric = token.all { it.isDigit() }
            val exact = fileTokens.any { it == token }
            val contains = searchable.contains(token)
            if (exact || contains) {
                matched++
                score += if (numeric) 6 else 3
                if (exact) score += 2
            }
        }

        // A complete normalized base-name match is a strong signal.
        if (fileName == normalizedQuery || fileName == normalizedVideo) score += 20
        if (normalizedVideo.isNotBlank() && fileName.contains(normalizedVideo)) score += 8

        // If an episode number exists in the query/video title, require it when possible.
        val queryNumbers = effectiveTokens.filter { it.all(Char::isDigit) && it.length >= 1 }
        if (queryNumbers.isNotEmpty()) {
            val hasEpisodeNumber = queryNumbers.any { searchable.contains(it) }
            if (!hasEpisodeNumber) return 0
        }

        // At least one meaningful token must match. For multi-token searches, reward stronger
        // overlap but don't require the subtitle filename to reproduce the entire video name.
        if (matched == 0) return 0
        val minimum = if (queryTokens.size >= 2) 5 else 3
        return if (score >= minimum) score else 0
    }

    val candidateRoots = listOfNotNull(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        File(Environment.getExternalStorageDirectory(), "Videos"),
        File(Environment.getExternalStorageDirectory(), "Subtitles"),
        File(Environment.getExternalStorageDirectory(), "Download"),
        Environment.getExternalStorageDirectory()
    )

    fun scanDir(dir: File, depth: Int) {
        if (depth > 4 || scored.size >= 120 || !dir.exists() || !dir.canRead()) return
        val canonical = try { dir.canonicalPath } catch (_: Throwable) { dir.absolutePath }
        if (!visited.add(canonical)) return

        val files = try { dir.listFiles() } catch (_: Throwable) { null } ?: return
        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".") && !f.name.equals("Android", ignoreCase = true)) {
                    scanDir(f, depth + 1)
                }
            } else if (f.isFile && STRICT_SUBTITLE_EXTENSIONS.contains(f.extension.lowercase(Locale.ROOT))) {
                val score = scoreFile(f)
                if (score > 0) scored.add(ScoredFile(f, score))
            }
            if (scored.size >= 120) return
        }
    }

    for (root in candidateRoots) {
        if (root.exists() && root.canRead()) scanDir(root, 0)
        if (scored.size >= 120) break
    }

    scored
        .distinctBy { it.file.absolutePath }
        .sortedWith(compareByDescending<ScoredFile> { it.score }.thenBy { it.file.name.lowercase(Locale.ROOT) })
        .take(120)
        .map { it.file }
}

/**
 * 1. SUBTITLE TRACK SELECTION PANEL
 * Matches user's exact reference screenshot:
 * - Top minus drag handle centered horizontally.
 * - Header Left: Clickable "+ Add external subtitles" with Add icon.
 * - Header Right: Search icon (auto-fills search), Palette icon, Time Adjust icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubtitleSelectionPanel(
    videoTitle: String = "",
    subtitleTracks: List<PlayerMediaTrack>,
    selectedTrackId: Int,
    secondaryTrackId: Int = 0,
    lastTouchedTrackId: Int = 0,
    isSubtitleVisible: Boolean,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    showMediaInfo: Boolean = true,
    onSelectTrack: (Int) -> Unit,
    onSelectSecondaryTrack: (Int) -> Unit = {},
    onSelectTrackWithCascade: ((Int) -> Unit)? = null,
    onSelectSubtitleTracks: ((primaryId: Int, secondaryId: Int) -> Unit)? = null,
    onLastTouchedTrackIdChange: ((Int) -> Unit)? = null,
    onRemoveTrack: ((Int) -> Unit)? = null,
    onToggleVisibility: () -> Unit,
    onImportSubtitle: () -> Unit,
    onPasteSubtitle: () -> Unit = {},
    onSelectSubtitleFile: ((File) -> Unit)? = null,
    onShowNotification: (String) -> Unit = {},
    onOpenSubtitleDelay: () -> Unit = {},
    onOpenSubtitleStyle: () -> Unit = {},
    translationStatusByTrackId: Map<Int, TrackTranslationStatus> = emptyMap(),
    onStartTranslate: (Int) -> Unit = {},
    onTranslateSubtitleTrack: suspend (Int, (Int) -> Unit) -> Result<Int> = { _, _ ->
        Result.failure(IllegalStateException("Subtitle translation is not connected"))
    },
    onDismissRequest: () -> Unit
) {
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember(videoTitle) { mutableStateOf(cleanVideoTitleForSearch(videoTitle)) }
    var isSearching by remember { mutableStateOf(false) }
    var foundSubtitleFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    // Attached (built-in) subtitles are visible the moment the panel opens, like the uploaded ones.
    var isEmbeddedExpanded by remember { mutableStateOf(true) }
    var isUploadedExpanded by remember { mutableStateOf(true) }
    var isImportantExpanded by remember { mutableStateOf(true) }
    var isFilesExpanded by remember { mutableStateOf(true) }

    // ---- AI Subtitle Translate (real pipeline) ----
    val aiTranslateContext = androidx.compose.ui.platform.LocalContext.current
    var aiSettingsSnapshot by remember { mutableStateOf(AiFeaturesSettingsStore.load(aiTranslateContext)) }
    LaunchedEffect(Unit) { aiSettingsSnapshot = AiFeaturesSettingsStore.load(aiTranslateContext) }
    val aiTranslateReady = aiSettingsSnapshot.aiFeaturesEnabled &&
        aiSettingsSnapshot.subtitleTranslateEnabled &&
        aiSettingsSnapshot.hasUsableModel
    val aiTranslateLanguageLabel = TranslateLanguages.ALL.firstOrNull {
        it.code == aiSettingsSnapshot.translateLanguageCode
    }?.label ?: "Hindi"

    LaunchedEffect(isSearchMode, searchQuery, videoTitle) {
        if (isSearchMode) {
            isSearching = true
            foundSubtitleFiles = searchLocalDeviceSubtitles(searchQuery, videoTitle)
            isSearching = false
        }
    }

    PlayerGlassModalSheet(
        panelKey = "subtitles",
        isLandscape = isLandscape,
        heightFraction = if (isSearchMode) 0.70f else 0.58f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerCustomRow = {
            if (isSearchMode) {
                // Search Header with Back Button and Auto-filled Search Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isSearchMode = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        StyledIcon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to Subtitle List",
                            tint = NormalItemColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SheetButtonBgColor)
                            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = SecondaryTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.5.sp,
                                    color = SheetButtonTextColor,
                                    fontWeight = FontWeight.Medium
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search subtitles across folders...",
                                            color = SecondaryTextColor,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    StyledIcon(
                                imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear search",
                                        tint = SecondaryTextColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Standard Header matching User's Exact Screenshot:
                // Left: "+ Add external subtitles"
                // Right: [Search Icon] [Palette Icon] [Time Adjust Icon]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Clickable + Add external subtitles (Tap opens file tree, Long-press pastes from clipboard)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    onImportSubtitle()
                                    onDismissRequest()
                                },
                                onLongClick = {
                                    onPasteSubtitle()
                                }
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add external subtitles",
                            tint = NormalItemColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add external subtitles",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = NormalItemColor
                        )
                    }

                    // Right: Action Icons (Search, Palette, Time Adjust)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 1. Search button (Auto-fills file name and scans all folders)
                        IconButton(
                            onClick = { isSearchMode = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search Matching Subtitles",
                                tint = NormalItemColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.5 AI Subtitle Translate button: only visible when AI Features are enabled
                        if (aiSettingsSnapshot.aiFeaturesEnabled) {
                            IconButton(
                                onClick = {
                                    val targetTrackId = lastTouchedTrackId.takeIf { it > 0 } ?: selectedTrackId
                                    when {
                                        !aiTranslateReady -> onShowNotification("Configure AI Subtitle Translate in Settings > Advanced first")
                                        targetTrackId <= 0 -> onShowNotification("Select a subtitle track to translate")
                                        else -> onStartTranslate(targetTrackId)
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_aitranslate,
                                    contentDescription = "AI Subtitle Translate",
                                    tint = if (aiTranslateReady) NormalItemColor else NormalItemColor.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 2. Palette / Subtitle Style button. Reuse the existing
                        // right-side subtitle panel host; do not create another sheet here.
                        // Hidden for an image/bitmap-based track (PGS, VobSub, DVB, CEA-608/708,
                        // ARIB B24, ...) since there is no text style to edit on those at all.
                        val styleButtonTargetTrack = subtitleTracks.firstOrNull {
                            it.id == (lastTouchedTrackId.takeIf { id -> id > 0 } ?: selectedTrackId)
                        }
                        if (!isImageBasedSubtitleTrack(styleButtonTargetTrack)) {
                            IconButton(
                                onClick = { onOpenSubtitleStyle() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = "Subtitle Style & Colors",
                                    tint = NormalItemColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 3. Time Adjust / Sync button (Stopwatch Icon)
                        IconButton(
                            onClick = {
                                onOpenSubtitleDelay()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Timelapse,
                                contentDescription = "Subtitle Time Adjust",
                                tint = NormalItemColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        if (isSearchMode) {
            // SEARCH RESULTS VIEW
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = ActiveHighlightColor
                    )
                }
            } else if (foundSubtitleFiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StyledIcon(
                                imageVector = Icons.Outlined.Subtitles,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No subtitle files found on device" else "No subtitle files found for \"$searchQuery\"",
                        color = SecondaryTextColor,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE0F2FE))
                            .clickable {
                                onImportSubtitle()
                                onDismissRequest()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Browse in Full File Explorer",
                            color = Color(0xFF0284C7),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                val deduplicatedSubtitleFiles = remember(foundSubtitleFiles) {
                    val seen = HashSet<String>()
                    foundSubtitleFiles.filter { file ->
                        val key = file.absolutePath
                        seen.add(key)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isFilesExpanded = !isFilesExpanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Subtitles,
                            contentDescription = null,
                            tint = ActiveHighlightColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Matching Subtitle Files (${deduplicatedSubtitleFiles.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NormalItemColor
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Device",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryTextColor
                        )
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isFilesExpanded) 180f else 0f,
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            label = "filesArrowRotation"
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { isFilesExpanded = !isFilesExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            StyledIcon(
                                drawableRes = R.drawable.lumora_list_arrow_down,
                                contentDescription = if (isFilesExpanded) "Collapse files" else "Expand files",
                                tint = SecondaryTextColor,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer { rotationZ = arrowRotation }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = isFilesExpanded,
                            enter = fadeIn(animationSpec = tween(240)) +
                                    slideInVertically(
                                        initialOffsetY = { fullHeight -> fullHeight / 3 },
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(180)) +
                                   slideOutVertically(
                                       targetOffsetY = { fullHeight -> -fullHeight / 4 },
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   ) +
                                   shrinkVertically(
                                       shrinkTowards = Alignment.Top,
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                deduplicatedSubtitleFiles.forEach { file ->
                                    val ext = file.extension.uppercase(Locale.ROOT)
                                    val folderPath = file.parentFile?.absolutePath ?: file.parent ?: ""
                                    val displayFolderPath = if (folderPath.startsWith("/storage/emulated/0")) {
                                        folderPath.removePrefix("/storage/emulated/0")
                                    } else folderPath

                                    val isDark = com.example.ui.theme.isAppInDarkTheme()
                                    val fileItemBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.50f) else Color(0xFFF8FAFC)
                                    val fileItemBorder = if (isDark) Color.White.copy(alpha = 0.18f) else Color(0xFFE2E8F0)
                                    val iconBadgeBg = if (isDark) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFFE0F2FE)
                                    val iconBadgeTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(fileItemBg)
                                            .border(1.dp, fileItemBorder, RoundedCornerShape(12.dp))
                                            .cardBounceClick {
                                                onSelectSubtitleFile?.invoke(file)
                                                onShowNotification("Loaded: ${file.name}")
                                                onDismissRequest()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(iconBadgeBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                StyledIcon(
                                                    imageVector = Icons.Outlined.Subtitles,
                                                    contentDescription = null,
                                                    tint = iconBadgeTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = file.name,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = NormalItemColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(iconBadgeBg)
                                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = ext,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = iconBadgeTint
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = "📁 $displayFolderPath",
                                                    fontSize = 11.sp,
                                                    color = SecondaryTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
        } else {
            // AI-translated tracks move into "Important Subtitle" instead of staying under
            // Embedded/Uploaded — matches spec: translated track is promoted out of "Attach Subtitle".
            val importantTrackIds = translationStatusByTrackId
                .filterValues { it is TrackTranslationStatus.Completed }
                .keys
            val rawImportantTracks = subtitleTracks.filter { it.id in importantTrackIds }
            val rawEmbeddedTracks = subtitleTracks.filter { !it.isExternal && it.id !in importantTrackIds }
            val rawUploadedTracks = subtitleTracks.filter { it.isExternal && it.id !in importantTrackIds }

            // NOTE: selection highlighting reflects the active player state and ensures
            // external/uploaded tracks are reliably highlighted when active.
            val isPrimaryTrack = { trackId: Int ->
                selectedTrackId > 0 && selectedTrackId == trackId
            }
            val isSecondaryTrack = { trackId: Int ->
                secondaryTrackId > 0 && secondaryTrackId == trackId
            }
            val isTrackActive = { trackId: Int -> isPrimaryTrack(trackId) || isSecondaryTrack(trackId) }

            // Retain ALL tracks exposing genuine distinct subtitle streams (never deduplicate by title/name)
            val importantTracks = remember(rawImportantTracks) {
                rawImportantTracks.distinctBy { it.id }
            }
            val embeddedTracks = remember(rawEmbeddedTracks) {
                rawEmbeddedTracks.distinctBy { it.id }
            }
            val uploadedTracks = remember(rawUploadedTracks) {
                rawUploadedTracks.distinctBy { it.id }
            }

            val handleTrackToggle = { track: PlayerMediaTrack, title: String ->
                val trackId = track.id
                val isPrimary = isPrimaryTrack(trackId)
                val isSecondary = isSecondaryTrack(trackId)

                onLastTouchedTrackIdChange?.invoke(trackId)

                val (newPrimary, newSecondary, notificationMsg) = when {
                    isPrimary -> {
                        // CASE: Tapping Primary subtitle unselects it.
                        // If a Secondary subtitle is active, it is immediately promoted to Primary!
                        // Otherwise, subtitles are turned off.
                        if (secondaryTrackId > 0) {
                            val promotedTrack = (rawImportantTracks + rawEmbeddedTracks + rawUploadedTracks).firstOrNull { it.id == secondaryTrackId }
                            val promotedTitle = promotedTrack?.title ?: "Track $secondaryTrackId"
                            Triple(secondaryTrackId, 0, "Primary Subtitle: $promotedTitle")
                        } else {
                            Triple(0, 0, "Subtitles Off")
                        }
                    }
                    isSecondary -> {
                        // CASE: Tapping Secondary subtitle unselects it.
                        // Primary remains completely unchanged.
                        Triple(selectedTrackId, 0, "2nd Subtitle unselected: $title")
                    }
                    else -> {
                        // CASE: Tapping an unselected track:
                        // Tapped track becomes Primary.
                        // Previous Primary (if any) becomes Secondary.
                        val oldPrimary = if (selectedTrackId > 0 && selectedTrackId != trackId) selectedTrackId else 0
                        Triple(trackId, oldPrimary, "Primary Subtitle: $title")
                    }
                }

                if (newPrimary > 0 || newSecondary > 0) {
                    if (!isSubtitleVisible) onToggleVisibility()
                }

                if (onSelectSubtitleTracks != null) {
                    onSelectSubtitleTracks(newPrimary, newSecondary)
                } else {
                    onSelectSecondaryTrack(newSecondary)
                    onSelectTrack(newPrimary)
                }
                if (notificationMsg.isNotBlank()) {
                    onShowNotification(notificationMsg)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (embeddedTracks.isEmpty() && uploadedTracks.isEmpty() && importantTracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Subtitles,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No subtitle tracks available",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NormalItemColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap \"+ Add external subtitles\" above to load .srt, .ass, or .vtt subtitles",
                                fontSize = 12.sp,
                                color = SecondaryTextColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // 1. IMPORTANT SUBTITLE (AI-translated tracks)
                if (importantTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isImportantExpanded = !isImportantExpanded }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_aitranslate,
                                    contentDescription = null,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Important Subtitle (${importantTracks.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NormalItemColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Translate",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SecondaryTextColor
                                )
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isImportantExpanded) 180f else 0f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    label = "importantArrowRotation"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { isImportantExpanded = !isImportantExpanded },
                                    contentAlignment = Alignment.Center
                                ) {
                                    StyledIcon(
                                        drawableRes = R.drawable.lumora_list_arrow_down,
                                        contentDescription = if (isImportantExpanded) "Collapse translate subtitles" else "Expand translate subtitles",
                                        tint = SecondaryTextColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer { rotationZ = arrowRotation }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = isImportantExpanded,
                            enter = fadeIn(animationSpec = tween(240)) +
                                    slideInVertically(
                                        initialOffsetY = { fullHeight -> fullHeight / 3 },
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(180)) +
                                   slideOutVertically(
                                       targetOffsetY = { fullHeight -> -fullHeight / 4 },
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   ) +
                                   shrinkVertically(
                                       shrinkTowards = Alignment.Top,
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                importantTracks.forEachIndexed { idx, track ->
                                    val isPrimary = isPrimaryTrack(track.id)
                                    val isSecondary = isSecondaryTrack(track.id)
                                    val isSelected = isPrimary || isSecondary
                                    val isLastTouched = isSelected && (lastTouchedTrackId == track.id)
                                    val selectedColor = if (isSecondary) AccentSkyBlue else ActiveHighlightColor
                                    val selectedBg = if (isSecondary) AccentSkyBlue.copy(alpha = 0.12f) else ActiveItemBg
                                    val langName = if (showMediaInfo) formatLanguageName(track.language) else ""
                                    val cleanTitle = cleanTrackTitle(track.title, "Track ${idx + 1}")
                                    val displayTitle = if (langName.isNotBlank() && !cleanTitle.contains(langName, ignoreCase = true)) {
                                        "$cleanTitle • $langName"
                                    } else cleanTitle

                                    SelectableTrackItem(
                                        indexNumber = null,
                                        primaryTitle = displayTitle,
                                        badgeTag = if (showMediaInfo) resolveTrackFormatBadge(track) else "",
                                        isSelected = isSelected,
                                        isSecondary = isSecondary,
                                        isLastTouched = isLastTouched,
                                        selectedColor = selectedColor,
                                        selectedBgColor = selectedBg,
                                        selectionTag = if (isSecondary) "S" else if (isPrimary) "P" else null,
                                        translationStatus = translationStatusByTrackId[track.id] ?: TrackTranslationStatus.Idle,
                                        onClick = { handleTrackToggle(track, displayTitle) }
                                    )
                                }
                            }
                        }
                    }

                    if (embeddedTracks.isNotEmpty() || uploadedTracks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                thickness = 1.dp,
                                color = Color(0xFF94A3B8).copy(alpha = 0.35f)
                            )
                        }
                    }
                }

                // 2. EMBEDDED SUBTITLES
                if (embeddedTracks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isEmbeddedExpanded = !isEmbeddedExpanded }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.Subtitles,
                                    contentDescription = null,
                                    tint = ActiveHighlightColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Embedded Subtitles (${embeddedTracks.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NormalItemColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Built-in",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SecondaryTextColor
                                )
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isEmbeddedExpanded) 180f else 0f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    label = "embeddedArrowRotation"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { isEmbeddedExpanded = !isEmbeddedExpanded },
                                    contentAlignment = Alignment.Center
                                ) {
                                    StyledIcon(
                                        drawableRes = R.drawable.lumora_list_arrow_down,
                                        contentDescription = if (isEmbeddedExpanded) "Collapse embedded subtitles" else "Expand embedded subtitles",
                                        tint = SecondaryTextColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer { rotationZ = arrowRotation }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = isEmbeddedExpanded,
                            enter = fadeIn(animationSpec = tween(240)) +
                                    slideInVertically(
                                        initialOffsetY = { fullHeight -> fullHeight / 3 },
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(180)) +
                                   slideOutVertically(
                                       targetOffsetY = { fullHeight -> -fullHeight / 4 },
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   ) +
                                   shrinkVertically(
                                       shrinkTowards = Alignment.Top,
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                embeddedTracks.forEachIndexed { idx, track ->
                                    val isPrimary = isPrimaryTrack(track.id)
                                    val isSecondary = isSecondaryTrack(track.id)
                                    val isSelected = isPrimary || isSecondary
                                    val isLastTouched = isSelected && (lastTouchedTrackId == track.id)
                                    val selectedColor = if (isSecondary) AccentSkyBlue else ActiveHighlightColor
                                    val selectedBg = if (isSecondary) AccentSkyBlue.copy(alpha = 0.12f) else ActiveItemBg

                                    val langName = if (showMediaInfo) formatLanguageName(track.language) else ""
                                    val cleanTitle = cleanTrackTitle(track.title, "Track ${idx + 1}")
                                    val rawBadge = resolveTrackFormatBadge(track)

                                    val displayTitle = if (langName.isNotBlank() && !cleanTitle.contains(langName, ignoreCase = true)) {
                                        "$cleanTitle • $langName"
                                    } else {
                                        cleanTitle
                                    }

                                    SelectableTrackItem(
                                        indexNumber = "${idx + 1}:",
                                        primaryTitle = displayTitle,
                                        badgeTag = rawBadge,
                                        isSelected = isSelected,
                                        isSecondary = isSecondary,
                                        isLastTouched = isLastTouched,
                                        selectedColor = selectedColor,
                                        selectedBgColor = selectedBg,
                                        selectionTag = if (isSecondary) "S" else if (isPrimary) "P" else null,
                                        translationStatus = translationStatusByTrackId[track.id] ?: TrackTranslationStatus.Idle,
                                        onClick = {
                                            handleTrackToggle(track, displayTitle)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. UPLOADED SUBTITLES
                if (uploadedTracks.isNotEmpty()) {
                    if (embeddedTracks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                thickness = 1.dp,
                                color = Color(0xFF94A3B8).copy(alpha = 0.35f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isUploadedExpanded = !isUploadedExpanded }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledIcon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Uploaded Subtitles (${uploadedTracks.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NormalItemColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "External",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SecondaryTextColor
                                )
                                val arrowRotation by animateFloatAsState(
                                    targetValue = if (isUploadedExpanded) 180f else 0f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    label = "uploadedArrowRotation"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { isUploadedExpanded = !isUploadedExpanded },
                                    contentAlignment = Alignment.Center
                                ) {
                                    StyledIcon(
                                        drawableRes = R.drawable.lumora_list_arrow_down,
                                        contentDescription = if (isUploadedExpanded) "Collapse uploaded subtitles" else "Expand uploaded subtitles",
                                        tint = SecondaryTextColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer { rotationZ = arrowRotation }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = isUploadedExpanded,
                            enter = fadeIn(animationSpec = tween(240)) +
                                    slideInVertically(
                                        initialOffsetY = { fullHeight -> fullHeight / 3 },
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ),
                            exit = fadeOut(animationSpec = tween(180)) +
                                   slideOutVertically(
                                       targetOffsetY = { fullHeight -> -fullHeight / 4 },
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   ) +
                                   shrinkVertically(
                                       shrinkTowards = Alignment.Top,
                                       animationSpec = tween(240, easing = FastOutSlowInEasing)
                                   )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                uploadedTracks.forEach { track ->
                                    val isPrimary = isPrimaryTrack(track.id)
                                    val isSecondary = isSecondaryTrack(track.id)
                                    val isSelected = isPrimary || isSecondary
                                    val isLastTouched = isSelected && (lastTouchedTrackId == track.id)
                                    val selectedColor = if (isSecondary) AccentSkyBlue else ActiveHighlightColor
                                    val selectedBg = if (isSecondary) AccentSkyBlue.copy(alpha = 0.12f) else ActiveItemBg

                                    val originalName = track.originalFilename.ifBlank { track.title }
                                    val cleanName = if (originalName.contains('.')) originalName.substringBeforeLast('.') else originalName
                                    val rawBadge = resolveTrackFormatBadge(track)

                                    SwipeableTrackItem(
                                        primaryTitle = cleanName,
                                        badgeTag = rawBadge,
                                        isSelected = isSelected,
                                        isSecondary = isSecondary,
                                        isLastTouched = isLastTouched,
                                        selectedColor = selectedColor,
                                        selectedBgColor = selectedBg,
                                        selectionTag = if (isSecondary) "S" else if (isPrimary) "P" else null,
                                        translationStatus = translationStatusByTrackId[track.id] ?: TrackTranslationStatus.Idle,
                                        onRemove = if (onRemoveTrack != null) { { onRemoveTrack(track.id) } } else null,
                                        swipeLeftToRightToRemove = true,
                                        onClick = {
                                            handleTrackToggle(track, cleanName)
                                        }
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

/**
 * 2. AUDIO TRACK SELECTION PANEL
 * Matches user's exact specification:
 * - Top minus drag handle centered horizontally.
 * - Header Left: Clickable "+ Add external audio track" with Add icon.
 * - Header Right: Time Adjust icon (no Search or Palette icon).
 */
@Composable
fun AudioSelectionPanel(
    audioTracks: List<PlayerMediaTrack>,
    selectedTrackId: Int,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    showMediaInfo: Boolean = true,
    trackAudioConfigs: Map<Int, TrackAudioConfig> = emptyMap(),
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: ((Int) -> Unit)? = null,
    onTrackChannelModeChange: (trackId: Int, mode: AudioChannelMode) -> Unit = { _, _ -> },
    onTrackVolumeNormalizationChange: (trackId: Int, enabled: Boolean) -> Unit = { _, _ -> },
    onTrackDynamicRangeCompressionChange: (trackId: Int, enabled: Boolean) -> Unit = { _, _ -> },
    onImportAudio: () -> Unit = {},
    onShowNotification: (String) -> Unit = {},
    onOpenAudioDelay: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val actualTracks = if (audioTracks.isNotEmpty()) {
        audioTracks
    } else {
        listOf(
            PlayerMediaTrack(
                id = 1,
                type = "audio",
                title = "Main Audio Track",
                language = "en",
                codec = "",
                isSelected = true
            )
        )
    }

    // Dark sheet (dark app theme OR very transparent glass): the sheet text is light, so fields,
    // +/- buttons and separators must use the dark-glass surfaces, not the light ones. This is
    // read from the app theme directly because this code runs OUTSIDE the sheet's own
    // CompositionLocalProvider, where LocalSheetNormalTextColor is still its light-mode default.
    val isLowGlassOpacity = glassBlurTransparency < 25f || isAppInDarkTheme()

    val effectiveSelectedId = if (selectedTrackId > 0) selectedTrackId else actualTracks.firstOrNull()?.id ?: 1
    val currentActiveConfig = trackAudioConfigs[effectiveSelectedId] ?: TrackAudioConfig()

    val embeddedTracks = actualTracks.filter { !it.isExternal }
    val uploadedTracks = actualTracks.filter { it.isExternal }

    PlayerGlassModalSheet(
        panelKey = "audio",
        isLandscape = isLandscape,
        heightFraction = if (isLandscape) 0.85f else 0.65f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerCustomRow = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Clickable + Add external audio track
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onImportAudio()
                            onDismissRequest()
                        }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add external audio track",
                        tint = NormalItemColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add external audio track",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = NormalItemColor
                    )
                }

                // Right: Equalizer and Audio Delay buttons (Audio Delay is at the very end)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Equalizer icon button (Tuning 3 SVG) - before Audio Delay
                    IconButton(
                        onClick = {
                            onOpenEqualizer()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.lumora_equalizer),
                            contentDescription = "Equalizer",
                            tint = NormalItemColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Time Adjust icon button (Audio Delay) - at the very end
                    IconButton(
                        onClick = {
                            onOpenAudioDelay()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Timelapse,
                            contentDescription = "Audio Time Adjust",
                            tint = NormalItemColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) {
        // UNIFIED SCROLLING CONTAINER: Audio Tracks List -> Divider -> Audio Controls
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
        ) {
            // If user has uploaded custom audio, show Embedded Audio Tracks header
            if (uploadedTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StyledIcon(
                                imageVector = Icons.Outlined.Audiotrack,
                                contentDescription = null,
                                tint = ActiveHighlightColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Embedded Audio Tracks (${embeddedTracks.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NormalItemColor
                            )
                        }
                        Text(
                            text = "Built-in",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryTextColor
                        )
                    }
                }
            }

            if (embeddedTracks.isEmpty()) {
                if (uploadedTracks.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "No embedded audio tracks in this video",
                                fontSize = 12.5.sp,
                                color = SecondaryTextColor,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(embeddedTracks) { idx, track ->
                    val isSelected = (track.id == effectiveSelectedId)
                    val langName = if (showMediaInfo) formatLanguageName(track.language) else ""
                    val cleanTitle = cleanTrackTitle(track.title, "Audio Track ${idx + 1}")
                    val codecBadge = if (showMediaInfo) resolveTrackFormatBadge(track) else ""

                    val displayTitle = if (langName.isNotBlank() && !cleanTitle.contains(langName, ignoreCase = true)) {
                        "$cleanTitle • $langName"
                    } else {
                        cleanTitle
                    }

                    SelectableTrackItem(
                        indexNumber = "${idx + 1}:",
                        primaryTitle = displayTitle,
                        badgeTag = codecBadge,
                        isSelected = isSelected,
                        onClick = {
                            onSelectTrack(track.id)
                        }
                    )
                }
            }

            // Only show DIVIDER LINE and UPLOADED AUDIO if at least one audio track has been uploaded
            if (uploadedTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        thickness = 1.dp,
                        color = Color(0xFF94A3B8).copy(alpha = 0.35f)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StyledIcon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Uploaded Audio Tracks (${uploadedTracks.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NormalItemColor
                            )
                        }
                    }
                }

                itemsIndexed(uploadedTracks, key = { _, t -> "uploaded_audio_${t.id}_${t.originalFilename}" }) { _, track ->
                    val isSelected = (track.id == effectiveSelectedId)
                    val originalName = track.originalFilename.ifBlank { track.title }
                    val cleanName = if (originalName.matches(Regex("(?i).*\\.(m4a|mp3|aac|opus|ogg|flac|wav)$"))) {
                        originalName.substringBeforeLast('.')
                    } else {
                        originalName
                    }
                    val codecBadge = resolveTrackFormatBadge(track)

                    SwipeableTrackItem(
                        primaryTitle = cleanName,
                        badgeTag = codecBadge,
                        isSelected = isSelected,
                        onRemove = if (onRemoveTrack != null) { { onRemoveTrack(track.id) } } else null,
                        onClick = {
                            onSelectTrack(track.id)
                        }
                    )
                }
            }

            // 2. Horizontal Line Separator - rendered strictly AFTER all audio tracks
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(if (isLowGlassOpacity) Color(0x20FFFFFF) else Color(0xFFE2E8F0))
                )
            }

            // 3. Audio Channels (Applies to selected audio track)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Audio channels",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalSheetSecondaryTextColor.current
                    )

                    val channelOptions = listOf(
                        AudioChannelMode.AUTO to "Auto",
                        AudioChannelMode.AUTO_SAFE to "Auto Safe",
                        AudioChannelMode.MONO to "Mono",
                        AudioChannelMode.STEREO to "Stereo",
                        AudioChannelMode.REVERSED_STEREO to "Reversed Stereo"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        channelOptions.forEach { (mode, label) ->
                            val isChannelSelected = currentActiveConfig.channelMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (isChannelSelected) Modifier.background(AccentGradient)
                                        else Modifier.background(SheetButtonBgColor)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isChannelSelected) Color(0x60FFFFFF)
                                        else SheetButtonBorderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .cardBounceClick(scaleDown = 0.94f) {
                                        onTrackChannelModeChange(effectiveSelectedId, mode)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isChannelSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isChannelSelected) Color.White else SheetButtonTextColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 4. Audio Processing (Applies to selected audio track)
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Audio processing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalSheetSecondaryTextColor.current
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Volume normalization toggle
                        val isVolNormActive = currentActiveConfig.volumeNormalization
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isVolNormActive) Modifier.background(AccentGradient)
                                    else Modifier.background(SheetButtonBgColor)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isVolNormActive) Color(0x60FFFFFF)
                                    else SheetButtonBorderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .cardBounceClick(scaleDown = 0.94f) {
                                    onTrackVolumeNormalizationChange(effectiveSelectedId, !isVolNormActive)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Volume normalization",
                                fontSize = 13.sp,
                                fontWeight = if (isVolNormActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isVolNormActive) Color.White else SheetButtonTextColor,
                                maxLines = 1
                            )
                        }

                        // Dynamic range compression (DRC) toggle
                        val isDrcActive = currentActiveConfig.dynamicRangeCompression
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isDrcActive) Modifier.background(AccentGradient)
                                    else Modifier.background(SheetButtonBgColor)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isDrcActive) Color(0x60FFFFFF)
                                    else SheetButtonBorderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .cardBounceClick(scaleDown = 0.94f) {
                                    onTrackDynamicRangeCompressionChange(effectiveSelectedId, !isDrcActive)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Dynamic range compression (DRC)",
                                fontSize = 13.sp,
                                fontWeight = if (isDrcActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDrcActive) Color.White else SheetButtonTextColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shows " ms" after the typed digits inside the delay text field. */
private object DelayMsSuffixTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val shown = androidx.compose.ui.text.AnnotatedString(text.text + " ms")
        return androidx.compose.ui.text.input.TransformedText(
            shown,
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = offset
                override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(text.length)
            }
        )
    }
}

/**
 * +/- button for the delay panels: one tap = one step; pressing and HOLDING keeps stepping
 * (slowly at first, then faster) until the finger is lifted.
 */
@Composable
private fun HoldRepeatStepButton(
    modifier: Modifier = Modifier,
    onStep: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val latestOnStep by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (pressed) 0.88f else 1f, label = "HoldStepScale")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    try {
                        pressed = true
                        latestOnStep()
                        var repeats = 0
                        var nextAt = System.currentTimeMillis() + 420L
                        while (true) {
                            val remaining = nextAt - System.currentTimeMillis()
                            val ev = if (remaining > 0L) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null
                            if (ev == null) {
                                latestOnStep()
                                repeats++
                                val gap = when {
                                    repeats > 30 -> 35L
                                    repeats > 15 -> 60L
                                    repeats > 6 -> 90L
                                    else -> 140L
                                }
                                nextAt = System.currentTimeMillis() + gap
                            } else {
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null || !ch.pressed) break
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 2B. AUDIO DELAY & SYNC PANEL
 * Synchronizes audio track with video playback.
 * Opens from bottom in portrait (center) and bottom in landscape (right side, anchored to bottom area).
 * Features Stepper (- / +), direct inline editable field, quick step presets,
 * "Sound heard" / "Sound spotted" interactive sync, "Set as default", and Reset.
 */
@Composable
fun AudioDelayPanel(
    audioDelayMs: Long,
    currentPositionMs: Long,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onAudioDelayChange: (Long) -> Unit,
    onSetAsDefault: (Long) -> Unit = {},
    onShowNotification: (String) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    var soundHeardTimeMs by remember { mutableStateOf<Long?>(null) }
    var soundSpottedTimeMs by remember { mutableStateOf<Long?>(null) }
    
    // Step configuration in milliseconds (Default to 100ms / 0.1m)
    val stepVariants = remember {
        listOf(
            100L to "0.1m",
            300L to "0.3m",
            500L to "0.5m",
            700L to "0.7m",
            1000L to "1s",
            2000L to "2s",
            3000L to "3s",
            10000L to "10s"
        )
    }
    var selectedStepMs by remember { mutableStateOf(100L) }
    
    var isDelayFocused by remember { mutableStateOf(false) }
    var delayInputText by remember { mutableStateOf(audioDelayMs.toString()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Keep the text box in sync with the real delay, but never overwrite what the user is typing.
    LaunchedEffect(audioDelayMs, isDelayFocused) {
        if (!isDelayFocused) delayInputText = audioDelayMs.toString()
    }
    // Running value so a held +/- button never works from a stale delay between recompositions.
    val liveDelay = remember { longArrayOf(audioDelayMs) }
    liveDelay[0] = audioDelayMs

    // Dark sheet (dark app theme OR very transparent glass): the sheet text is light, so fields,
    // +/- buttons and separators must use the dark-glass surfaces, not the light ones. This is
    // read from the app theme directly because this code runs OUTSIDE the sheet's own
    // CompositionLocalProvider, where LocalSheetNormalTextColor is still its light-mode default.
    val isLowGlassOpacity = glassBlurTransparency < 25f || isAppInDarkTheme()

    PlayerGlassModalSheet(
        panelKey = "audio_delay",
        isLandscape = isLandscape,
        openOnRightSide = true,
        heightFraction = 0.52f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = {
            focusManager.clearFocus()
            onDismissRequest()
        },
        headerStartContent = {
            Text(
                text = "Audio delay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LocalSheetNormalTextColor.current
            )
        },
        headerEndContent = {
            // Reset text button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .cardBounceClick(scaleDown = 0.90f) {
                        onAudioDelayChange(0L)
                        delayInputText = "0"
                        onShowNotification("Audio delay reset")
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (audioDelayMs != 0L) ActiveHighlightColor else LocalSheetSecondaryTextColor.current
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Delay Stepper Field (- [ 0 ms ] +) with direct inline editing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minus Button
                HoldRepeatStepButton(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isLowGlassOpacity) Color(0x28FFFFFF) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1), CircleShape),
                    onStep = {
                        val newMs = (liveDelay[0] - selectedStepMs).coerceIn(-60000L, 60000L)
                        liveDelay[0] = newMs
                        onAudioDelayChange(newMs)
                        delayInputText = newMs.toString()
                    }
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Decrease Delay",
                        tint = LocalSheetNormalTextColor.current,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Outlined Direct-Editable Delay Display (Tap to type directly inside without popup)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isLowGlassOpacity) Color(0x18FFFFFF) else Color(0xFFF8FAFC))
                        .border(
                            1.2.dp,
                            if (audioDelayMs != 0L || isDelayFocused) ActiveHighlightColor else (if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1)),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = delayInputText,
                        onValueChange = { newText ->
                            if (newText.isEmpty() || newText == "-" || newText.matches(Regex("^-?\\d*$"))) {
                                delayInputText = newText
                                val parsed = newText.toLongOrNull()
                                if (parsed != null) {
                                    // Applied live: the video shows the shift while the user types.
                                    onAudioDelayChange(parsed.coerceIn(-60000L, 60000L))
                                }
                            }
                        },
                        textStyle = TextStyle(
                            color = if (audioDelayMs != 0L) ActiveHighlightColor else LocalSheetNormalTextColor.current,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        cursorBrush = SolidColor(ActiveHighlightColor),
                        visualTransformation = DelayMsSuffixTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                isDelayFocused = focusState.isFocused
                            }
                    )
                }

                // Plus Button
                HoldRepeatStepButton(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isLowGlassOpacity) Color(0x28FFFFFF) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1), CircleShape),
                    onStep = {
                        val newMs = (liveDelay[0] + selectedStepMs).coerceIn(-60000L, 60000L)
                        liveDelay[0] = newMs
                        onAudioDelayChange(newMs)
                        delayInputText = newMs.toString()
                    }
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase Delay",
                        tint = LocalSheetNormalTextColor.current,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Step Variant Chips (0.1m, 0.3m, 0.5m, 0.7m, 1s, 2s, 3s, 10s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stepVariants.forEach { (stepMs, label) ->
                    val isSelected = selectedStepMs == stepMs
                    GlassPresetChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            selectedStepMs = stepMs
                        }
                    )
                }
            }

            // 3. Sound Heard & Sound Spotted Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sound Heard
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            if (soundHeardTimeMs != null) Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0284C7)))
                            else AccentGradient
                        )
                        .cardBounceClick(scaleDown = 0.94f) {
                            soundHeardTimeMs = currentPositionMs
                            if (soundSpottedTimeMs != null) {
                                val calculatedDelay = soundSpottedTimeMs!! - currentPositionMs
                                onAudioDelayChange(calculatedDelay)
                                delayInputText = calculatedDelay.toString()
                                onShowNotification("Audio Synced: ${calculatedDelay} ms delay")
                            } else {
                                onShowNotification("Sound heard marked. Tap 'Sound spotted' when visual action occurs.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sound heard",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Sound Spotted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            if (soundSpottedTimeMs != null) Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0284C7)))
                            else AccentGradient
                        )
                        .cardBounceClick(scaleDown = 0.94f) {
                            soundSpottedTimeMs = currentPositionMs
                            if (soundHeardTimeMs != null) {
                                val calculatedDelay = currentPositionMs - soundHeardTimeMs!!
                                onAudioDelayChange(calculatedDelay)
                                delayInputText = calculatedDelay.toString()
                                onShowNotification("Audio Synced: ${calculatedDelay} ms delay")
                            } else {
                                onShowNotification("Sound spotted marked. Tap 'Sound heard' when sound is heard.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sound spotted",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 4. Bottom Action Button: [ Set as default ]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AccentGradient)
                    .cardBounceClick(scaleDown = 0.95f) {
                        onSetAsDefault(audioDelayMs)
                        val formatted = if (audioDelayMs == 0L) "0 ms" else "$audioDelayMs ms"
                        onShowNotification("Saved default audio delay ($formatted)")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Set as default",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * 2C. DEDICATED SUBTITLE DELAY PANEL
 * Synchronizes subtitle timing with video playback.
 * Opens from bottom in portrait (center) and bottom in landscape (right side, anchored to bottom area).
 * Features Stepper (- / +), direct inline editable field, quick step presets,
 * "Voice heard" / "Subtitle spotted" interactive sync, "Set as default", and Reset.
 */
@Composable
fun SubtitleDelayPanel(
    subtitleDelayMs: Long,
    currentPositionMs: Long,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    primarySubtitleFileName: String? = null,
    onSubtitleDelayChange: (Long) -> Unit,
    onSetAsDefault: (Long) -> Unit = {},
    onShowNotification: (String) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    var voiceHeardTimeMs by remember { mutableStateOf<Long?>(null) }
    var subtitleSpottedTimeMs by remember { mutableStateOf<Long?>(null) }
    
    // Step configuration in milliseconds (Default to 100ms / 0.1m)
    val stepVariants = remember {
        listOf(
            100L to "0.1m",
            300L to "0.3m",
            500L to "0.5m",
            700L to "0.7m",
            1000L to "1s",
            2000L to "2s",
            3000L to "3s",
            10000L to "10s"
        )
    }
    var selectedStepMs by remember { mutableStateOf(100L) }

    var isDelayFocused by remember { mutableStateOf(false) }
    var delayInputText by remember { mutableStateOf(subtitleDelayMs.toString()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Keep the text box in sync with the real delay, but never overwrite what the user is typing.
    LaunchedEffect(subtitleDelayMs, isDelayFocused) {
        if (!isDelayFocused) delayInputText = subtitleDelayMs.toString()
    }
    // Running value so a held +/- button never works from a stale delay between recompositions.
    val liveDelay = remember { longArrayOf(subtitleDelayMs) }
    liveDelay[0] = subtitleDelayMs

    // Dark sheet (dark app theme OR very transparent glass): the sheet text is light, so fields,
    // +/- buttons and separators must use the dark-glass surfaces, not the light ones. This is
    // read from the app theme directly because this code runs OUTSIDE the sheet's own
    // CompositionLocalProvider, where LocalSheetNormalTextColor is still its light-mode default.
    val isLowGlassOpacity = glassBlurTransparency < 25f || isAppInDarkTheme()

    PlayerGlassModalSheet(
        panelKey = "subtitle_delay",
        isLandscape = isLandscape,
        openOnRightSide = true,
        heightFraction = 0.52f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = {
            focusManager.clearFocus()
            onDismissRequest()
        },
        headerStartContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Subtitle delay",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalSheetNormalTextColor.current
                )
                if (!primarySubtitleFileName.isNullOrBlank()) {
                    Text(
                        text = primarySubtitleFileName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalSheetSecondaryTextColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        headerEndContent = {
            // Reset text button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .cardBounceClick(scaleDown = 0.90f) {
                        onSubtitleDelayChange(0L)
                        delayInputText = "0"
                        onShowNotification("Subtitle delay reset")
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (subtitleDelayMs != 0L) ActiveHighlightColor else LocalSheetSecondaryTextColor.current
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Delay Stepper Field (- [ 0 ms ] +) with direct inline editing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minus Button
                HoldRepeatStepButton(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isLowGlassOpacity) Color(0x28FFFFFF) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1), CircleShape),
                    onStep = {
                        val newMs = (liveDelay[0] - selectedStepMs).coerceIn(-60000L, 60000L)
                        liveDelay[0] = newMs
                        onSubtitleDelayChange(newMs)
                        delayInputText = newMs.toString()
                    }
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Decrease Delay",
                        tint = LocalSheetNormalTextColor.current,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Center Outlined Direct-Editable Delay Display (Tap to type directly inside without popup)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isLowGlassOpacity) Color(0x18FFFFFF) else Color(0xFFF8FAFC))
                        .border(
                            1.2.dp,
                            if (subtitleDelayMs != 0L || isDelayFocused) ActiveHighlightColor else (if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1)),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = delayInputText,
                        onValueChange = { newText ->
                            if (newText.isEmpty() || newText == "-" || newText.matches(Regex("^-?\\d*$"))) {
                                delayInputText = newText
                                val parsed = newText.toLongOrNull()
                                if (parsed != null) {
                                    // Applied live: the video shows the shift while the user types.
                                    onSubtitleDelayChange(parsed.coerceIn(-60000L, 60000L))
                                }
                            }
                        },
                        textStyle = TextStyle(
                            color = if (subtitleDelayMs != 0L) ActiveHighlightColor else LocalSheetNormalTextColor.current,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        cursorBrush = SolidColor(ActiveHighlightColor),
                        visualTransformation = DelayMsSuffixTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                isDelayFocused = focusState.isFocused
                            }
                    )
                }

                // Plus Button
                HoldRepeatStepButton(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isLowGlassOpacity) Color(0x28FFFFFF) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isLowGlassOpacity) Color(0x40FFFFFF) else Color(0xFFCBD5E1), CircleShape),
                    onStep = {
                        val newMs = (liveDelay[0] + selectedStepMs).coerceIn(-60000L, 60000L)
                        liveDelay[0] = newMs
                        onSubtitleDelayChange(newMs)
                        delayInputText = newMs.toString()
                    }
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase Delay",
                        tint = LocalSheetNormalTextColor.current,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Step Variant Chips (0.1m, 0.3m, 0.5m, 0.7m, 1s, 2s, 3s, 10s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                stepVariants.forEach { (stepMs, label) ->
                    val isSelected = selectedStepMs == stepMs
                    GlassPresetChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            selectedStepMs = stepMs
                        }
                    )
                }
            }

            // 3. Voice Heard & Subtitle Spotted Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice Heard
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            if (voiceHeardTimeMs != null) Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0284C7)))
                            else AccentGradient
                        )
                        .cardBounceClick(scaleDown = 0.94f) {
                            voiceHeardTimeMs = currentPositionMs
                            if (subtitleSpottedTimeMs != null) {
                                val calculatedDelay = subtitleSpottedTimeMs!! - currentPositionMs
                                onSubtitleDelayChange(calculatedDelay)
                                delayInputText = calculatedDelay.toString()
                                onShowNotification("Subtitle Synced: ${calculatedDelay} ms delay")
                            } else {
                                onShowNotification("Voice heard marked. Tap 'Subtitle spotted' when subtitle appears.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Voice heard",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Subtitle Spotted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            if (subtitleSpottedTimeMs != null) Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0284C7)))
                            else AccentGradient
                        )
                        .cardBounceClick(scaleDown = 0.94f) {
                            subtitleSpottedTimeMs = currentPositionMs
                            if (voiceHeardTimeMs != null) {
                                val calculatedDelay = currentPositionMs - voiceHeardTimeMs!!
                                onSubtitleDelayChange(calculatedDelay)
                                delayInputText = calculatedDelay.toString()
                                onShowNotification("Subtitle Synced: ${calculatedDelay} ms delay")
                            } else {
                                onShowNotification("Subtitle spotted marked. Tap 'Voice heard' when corresponding voice is heard.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Subtitle spotted",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 4. Bottom Action Button: [ Set as default ]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AccentGradient)
                    .cardBounceClick(scaleDown = 0.95f) {
                        onSetAsDefault(subtitleDelayMs)
                        val formatted = if (subtitleDelayMs == 0L) "0 ms" else "$subtitleDelayMs ms"
                        onShowNotification("Saved default subtitle delay ($formatted)")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Set as default",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * 3. CHAPTER SELECTION PANEL (Matches reference UI exactly)
 */
@Composable
fun ChapterSelectionPanel(
    chapters: List<PlayerVideoChapter>,
    currentPositionMs: Long,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onSeekToChapter: (PlayerVideoChapter) -> Unit,
    onDismissRequest: () -> Unit
) {
    // Identify active chapter based on position
    val currentChapter = remember(chapters, currentPositionMs) {
        chapters.lastOrNull { it.timeMs <= currentPositionMs } ?: chapters.firstOrNull()
    }

    PlayerGlassModalSheet(
        panelKey = "chapters",
        isLandscape = isLandscape,
        heightFraction = 0.58f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = if (chapters.isNotEmpty()) "Chapters • ${chapters.size}" else "Chapters",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        }
    ) {
        if (chapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chapter marks available for this video",
                    color = SecondaryTextColor,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(chapters) { idx, chapter ->
                    val isCurrent = (currentChapter?.index == chapter.index)
                    val cleanTitle = cleanChapterTitle(chapter.title, idx + 1)

                    SelectableChapterItem(
                        indexNumber = "${idx + 1}:",
                        title = cleanTitle,
                        timestamp = chapter.formattedTime,
                        isCurrent = isCurrent,
                        onClick = {
                            onSeekToChapter(chapter)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 4. 3-DOT OVERFLOW MENU PANEL
 */
@Composable
fun MoreMenuSelectionPanel(
    playbackSpeed: Double,
    onOpenSpeedDialog: () -> Unit,
    onOpenVideoZoomDialog: () -> Unit = {},
    onOpenVideoEqDialog: () -> Unit,
    onOpenAbLoopDialog: () -> Unit,
    aspectRatioMode: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    decoderMode: DecoderMode,
    onDecoderModeChange: (DecoderMode) -> Unit,
    isAudioOnly: Boolean,
    onAudioOnlyChange: (Boolean) -> Unit,
    repeatMode: PlayerRepeatMode,
    onRepeatModeChange: (PlayerRepeatMode) -> Unit,
    isShuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    gestureSensitivityMode: GestureSensitivityMode = GestureSensitivityMode.EXPONENTIAL,
    onGestureSensitivityModeChange: (GestureSensitivityMode) -> Unit = {},
    onTakeScreenshot: () -> Unit,
    onTogglePiP: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenPlayerLayoutDialog: (() -> Unit)? = null,
    onOpenVideoQualityDialog: (() -> Unit)? = null,
    selectedOnlineQuality: String? = null,
    onOpenAudioDelay: (() -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    PlayerGlassModalSheet(
        panelKey = "more_menu",
        isLandscape = isLandscape,
        heightFraction = 0.65f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Playback Menu",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // 0. Player Layout (Seekbar style & layout)
            if (onOpenPlayerLayoutDialog != null) {
                item {
                    MenuActionItem(
                        icon = Icons.Outlined.DashboardCustomize,
                        title = "Player Layout",
                        subtitle = "Seekbar style & custom layout",
                        badgeValue = null,
                        onClick = {
                            onOpenPlayerLayoutDialog()
                        }
                    )
                }
            }

            // Stream Video Quality
            if (onOpenVideoQualityDialog != null) {
                item {
                    MenuActionItem(
                        icon = Icons.Outlined.HighQuality,
                        title = "Video Quality",
                        subtitle = "Stream resolution: ${selectedOnlineQuality ?: "Auto"}",
                        badgeValue = selectedOnlineQuality ?: "Auto",
                        onClick = {
                            onOpenVideoQualityDialog()
                        }
                    )
                }
            }

            // Audio Delay & Sync
            if (onOpenAudioDelay != null) {
                item {
                    MenuActionItem(
                        icon = Icons.Outlined.Timelapse,
                        title = "Audio delay",
                        subtitle = "Audio synchronization & delay offset",
                        badgeValue = null,
                        onClick = {
                            onOpenAudioDelay()
                        }
                    )
                }
            }

            // Equalizer
            if (onOpenEqualizer != null) {
                item {
                    MenuActionItem(
                        iconRes = com.example.R.drawable.lumora_equalizer,
                        title = "Equalizer",
                        subtitle = "5-band audio equalizer & volume boost",
                        badgeValue = null,
                        onClick = {
                            onOpenEqualizer()
                        }
                    )
                }
            }

            // 1. Playback Speed
            item {
                MenuActionItem(
                    icon = Icons.Outlined.Speed,
                    title = "Playback Speed",
                    subtitle = "Current: ${playbackSpeed}x",
                    badgeValue = "${playbackSpeed}x",
                    onClick = {
                        onOpenSpeedDialog()
                    }
                )
            }

            // 2. Video Zoom
            item {
                MenuActionItem(
                    icon = Icons.Outlined.ZoomIn,
                    title = "Video Zoom",
                    subtitle = "Scale and pan video frame",
                    badgeValue = null,
                    onClick = {
                        onOpenVideoZoomDialog()
                    }
                )
            }

            // 3. Video Adjustments / Video Setting
            item {
                MenuActionItem(
                    iconRes = com.example.R.drawable.lumora_video_setting,
                    title = "Video Setting",
                    subtitle = "Filters, Color Presets & Video Adjustments",
                    badgeValue = null,
                    onClick = {
                        onOpenVideoEqDialog()
                    }
                )
            }

            // 3. A-B Repeat Loop
            item {
                MenuActionItem(
                    icon = Icons.Outlined.Repeat,
                    title = "A-B Repeat Loop",
                    subtitle = "Repeat selected section",
                    badgeValue = null,
                    onClick = {
                        onOpenAbLoopDialog()
                    }
                )
            }

            // 4. Aspect Ratio Cycle
            item {
                val nextRatio = aspectRatioMode.next()
                MenuActionItem(
                    iconRes = nextRatio.iconRes,
                    title = "Display Mode",
                    subtitle = "Mode: ${aspectRatioMode.label}",
                    badgeValue = aspectRatioMode.label,
                    onClick = {
                        onAspectRatioChange(nextRatio)
                    }
                )
            }

            // 5. Decoder Mode Cycle
            item {
                val nextDec = when (decoderMode) {
                    DecoderMode.HW_PLUS -> DecoderMode.HW
                    DecoderMode.HW -> DecoderMode.SW
                    DecoderMode.SW -> DecoderMode.HW_PLUS
                }
                MenuActionItem(
                    icon = Icons.Outlined.Memory,
                    title = "Hardware Decoder",
                    subtitle = "Engine: ${decoderMode.label}",
                    badgeValue = decoderMode.label,
                    onClick = {
                        onDecoderModeChange(nextDec)
                    }
                )
            }

            // 6. Audio-Only Mode Toggle
            item {
                MenuActionItem(
                    icon = Icons.Outlined.Headphones,
                    title = "Audio-Only Mode",
                    subtitle = if (isAudioOnly) "Active (Screen savings)" else "Play audio with screen off",
                    badgeValue = if (isAudioOnly) "ON" else "OFF",
                    isActive = isAudioOnly,
                    onClick = {
                        onAudioOnlyChange(!isAudioOnly)
                    }
                )
            }

            // 7. Repeat Mode Cycle
            item {
                val (nextRepeat, repeatLabel) = when (repeatMode) {
                    PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL to "All"
                    PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE to "One"
                    PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF to "Off"
                }
                val repeatIcon = if (repeatMode == PlayerRepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat
                MenuActionItem(
                    icon = repeatIcon,
                    title = "Repeat Mode",
                    subtitle = "Repeat: $repeatLabel",
                    badgeValue = repeatLabel,
                    isActive = repeatMode != PlayerRepeatMode.OFF,
                    onClick = {
                        onRepeatModeChange(nextRepeat)
                    }
                )
            }

            // 8. Shuffle Toggle
            item {
                MenuActionItem(
                    icon = Icons.Outlined.Shuffle,
                    title = "Shuffle Playback",
                    subtitle = if (isShuffle) "Random order enabled" else "Sequential order",
                    badgeValue = if (isShuffle) "ON" else "OFF",
                    isActive = isShuffle,
                    onClick = {
                        onShuffleChange(!isShuffle)
                    }
                )
            }

            // 8.5 Gesture Scaling (Exponential vs Linear)
            item {
                val nextMode = if (gestureSensitivityMode == GestureSensitivityMode.EXPONENTIAL) GestureSensitivityMode.LINEAR else GestureSensitivityMode.EXPONENTIAL
                val gestureIcon = if (gestureSensitivityMode == GestureSensitivityMode.EXPONENTIAL) Icons.Outlined.Speed else Icons.Outlined.Tune
                MenuActionItem(
                    icon = gestureIcon,
                    title = "Gesture Scaling",
                    subtitle = if (gestureSensitivityMode == GestureSensitivityMode.EXPONENTIAL) "Exponential (Perceptual curve)" else "Linear (1:1 Uniform steps)",
                    badgeValue = gestureSensitivityMode.shortLabel,
                    isActive = gestureSensitivityMode == GestureSensitivityMode.EXPONENTIAL,
                    onClick = {
                        onGestureSensitivityModeChange(nextMode)
                    }
                )
            }

            // 9. Take Screenshot
            item {
                MenuActionItem(
                    icon = Icons.Outlined.PhotoCamera,
                    title = "Take Screenshot",
                    subtitle = "Save current video frame to gallery",
                    badgeValue = null,
                    onClick = {
                        onDismissRequest()
                        onTakeScreenshot()
                    }
                )
            }

            // 10. Picture-in-Picture
            item {
                MenuActionItem(
                    icon = Icons.Outlined.PictureInPictureAlt,
                    title = "Picture-in-Picture",
                    subtitle = "Continue playing in floating mini window",
                    badgeValue = null,
                    onClick = {
                        onDismissRequest()
                        onTogglePiP()
                    }
                )
            }

            // 11. Rotate Screen
            item {
                MenuActionItem(
                    icon = Icons.Outlined.ScreenRotation,
                    title = "Rotate Screen",
                    subtitle = if (isLandscape) "Switch to Portrait" else "Switch to Landscape",
                    badgeValue = null,
                    onClick = {
                        onDismissRequest()
                        onToggleOrientation()
                    }
                )
            }
        }
    }
}

/**
 * 5. SPEED SELECTION PANEL (Liquid Glass System UI)
 */
@Composable
fun SpeedSelectionPanel(
    currentSpeed: Double,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    isPitchCorrectionEnabled: Boolean = true,
    onTogglePitchCorrection: (Boolean) -> Unit = {},
    onSpeedChange: (Double) -> Unit,
    onDismissRequest: () -> Unit
) {
    val speeds = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0)
    val context = LocalContext.current
    val formattedSpeed = String.format(Locale.US, "%.2f", currentSpeed).trimEnd('0').trimEnd('.')

    PlayerGlassModalSheet(
        panelKey = "speed",
        isLandscape = isLandscape,
        glassBlurTransparency = glassBlurTransparency,
        heightFraction = 0.62f,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Speed • ${formattedSpeed}x",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        },
        headerEndContent = {
            Text(
                text = "Reset",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ActiveHighlightColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSpeedChange(1.0) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Large Centered Current Speed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${formattedSpeed}x",
                    color = ActiveHighlightColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // 2. Stepper & Progress Slider Row: (-) [======|=======] (+)
            GlassStepperSliderRow(
                value = currentSpeed.toFloat(),
                onValueChange = { spd -> onSpeedChange(spd.toDouble()) },
                valueRange = 0.25f..4.0f,
                step = 0.05f
            )

            // 3. Preset Speed Chips (Horizontal Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                speeds.forEach { spd ->
                    val isSelected = abs(currentSpeed - spd) < 0.01
                    val label = when (spd) {
                        1.0 -> "1.0x"
                        2.0 -> "2.0x"
                        3.0 -> "3.0x"
                        4.0 -> "4.0x"
                        else -> "${spd}x"
                    }
                    GlassPresetChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            onSpeedChange(spd)
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = Color.White.copy(alpha = 0.12f),
                thickness = 1.dp
            )

            // 4. Pitch Correction Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTogglePitchCorrection(!isPitchCorrectionEnabled)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Enable audio pitch correction",
                        color = NormalItemColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Prevents the audio from becoming high-pitched at faster speeds and low-pitched at slower speeds",
                        color = SecondaryTextColor,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = isPitchCorrectionEnabled,
                    onCheckedChange = onTogglePitchCorrection,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveHighlightColor,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFFE2E8F0)
                    )
                )
            }

            // 5. Action Buttons (Make default speed & Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Make Default Speed Button
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentGradient)
                        .cardBounceClick(scaleDown = 0.95f) {
                            Toast.makeText(context, "Default speed set to ${formattedSpeed}x", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Make default speed",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SheetButtonBgColor)
                        .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(14.dp))
                        .cardBounceClick(scaleDown = 0.95f) {
                            onSpeedChange(1.0)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        color = SheetButtonTextColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 5.5 VIDEO ZOOM PANEL (Interactive scaling & panning controls)
 */
@Composable
fun VideoZoomSelectionPanel(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    isPanAndZoomEnabled: Boolean,
    onTogglePanAndZoom: (Boolean) -> Unit,
    onResetZoomAndPan: () -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    val zoomPresets = listOf(1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    val context = LocalContext.current
    val displayZoom = String.format(Locale.US, "%.2fx", zoomScale)

    PlayerGlassModalSheet(
        panelKey = "video_zoom",
        isLandscape = isLandscape,
        glassBlurTransparency = glassBlurTransparency,
        heightFraction = 0.62f,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Video Zoom",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        },
        headerEndContent = {
            Text(
                text = displayZoom,
                color = ActiveHighlightColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Stepper & Real-time Live Zoom Slider: (-) [======|=======] (+)
            GlassStepperSliderRow(
                value = zoomScale,
                onValueChange = onZoomChange,
                valueRange = 1.0f..4.0f,
                step = 0.05f
            )

            // 2. Preset Zoom Chips (Horizontal Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                zoomPresets.forEach { preset ->
                    val isSelected = abs(zoomScale - preset) < 0.02f
                    val label = when (preset) {
                        1.0f -> "1.0x"
                        2.0f -> "2.0f"
                        3.0f -> "3.0f"
                        4.0f -> "4.0f"
                        else -> "${String.format(Locale.US, "%.2f", preset).trimEnd('0').trimEnd('.')}x"
                    }
                    GlassPresetChip(
                        label = label,
                        isSelected = isSelected,
                        onClick = {
                            onZoomChange(preset)
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = Color(0xFFE2E8F0),
                thickness = 1.dp
            )

            // 3. Pan & Zoom Option Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTogglePanAndZoom(!isPanAndZoomEnabled)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Pan & Zoom",
                        color = NormalItemColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Drag with touch to pan across zoomed video frames",
                        color = SecondaryTextColor,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = isPanAndZoomEnabled,
                    onCheckedChange = onTogglePanAndZoom,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveHighlightColor,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFFE2E8F0)
                    )
                )
            }

            // 4. Action Buttons (Set as default & Reset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Set as Default
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentGradient)
                        .cardBounceClick(scaleDown = 0.95f) {
                            Toast.makeText(context, "Default zoom set to ${String.format(Locale.US, "%.2fx", zoomScale)}", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Set as default",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Reset Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SheetButtonBgColor)
                        .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(14.dp))
                        .cardBounceClick(scaleDown = 0.95f) {
                            onResetZoomAndPan()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        color = SheetButtonTextColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 6. VIDEO SETTING (FILTER PRESETS & MANUAL VIDEO ADJUSTMENT) PANEL
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoSettingSelectionPanel(
    selectedPreset: VideoFilterPreset,
    onSelectPreset: (VideoFilterPreset) -> Unit,
    manualAdjustments: ManualVideoAdjustments,
    onManualAdjustmentsChange: (ManualVideoAdjustments) -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onOpenManualAdjustments: () -> Unit,
    onDismissRequest: () -> Unit
) {
    PlayerGlassModalSheet(
        panelKey = "video_setting_filter",
        isLandscape = isLandscape,
        heightFraction = 0.56f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Video Setting",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NormalItemColor
            )
        },
        headerEndContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Reset to None & 0 adjustments
                if (selectedPreset != VideoFilterPreset.NONE || !manualAdjustments.isNeutral()) {
                    Text(
                        text = "Reset",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActiveHighlightColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .cardBounceClick(scaleDown = 0.90f) {
                                onSelectPreset(VideoFilterPreset.NONE)
                                onManualAdjustmentsChange(ManualVideoAdjustments())
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Palette Icon Button -> Opens Manual Video Adjustments (closes Filter UI)
                IconButton(
                    onClick = {
                        onOpenManualAdjustments()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = "Manual Video Adjustments",
                        tint = if (!manualAdjustments.isNeutral()) ActiveHighlightColor else NormalItemColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            val presets = remember { VideoFilterPreset.entries }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset == selectedPreset
                    val isLowGlass = LocalSheetIsLowGlassOpacity.current
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) {
                                    AccentGradient
                                } else if (isLowGlass) {
                                    SolidColor(Color(0x22FFFFFF))
                                } else {
                                    SolidColor(Color(0xFFF1F5F9))
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    Color(0x40FFFFFF)
                                } else if (isLowGlass) {
                                    Color(0x30FFFFFF)
                                } else {
                                    Color(0xFFCBD5E1)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .cardBounceClick(scaleDown = 0.93f) {
                                onSelectPreset(preset)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.displayName,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else NormalItemColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 6B. MANUAL VIDEO ADJUSTMENT RIGHT-SIDE SLIDING PANEL
 */
@Composable
fun ManualVideoAdjustmentPanel(
    manualAdjustments: ManualVideoAdjustments,
    onManualAdjustmentsChange: (ManualVideoAdjustments) -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    PlayerGlassModalSheet(
        panelKey = "manual_video_adjustment",
        isLandscape = isLandscape,
        openOnRightSide = true,
        heightFraction = 0.72f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Manual Video Adjustment",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = NormalItemColor
            )
        },
        headerEndContent = {
            Text(
                text = "Reset",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = ActiveHighlightColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .cardBounceClick(scaleDown = 0.90f) {
                        onManualAdjustmentsChange(ManualVideoAdjustments())
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Brightness (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Brightness",
                value = manualAdjustments.brightness,
                valueRange = -100f..100f,
                valueFormat = { if (it > 0) "+$it" else "$it" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(brightness = it.roundToInt())) }
            )

            // 2. Contrast (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Contrast",
                value = manualAdjustments.contrast,
                valueRange = -100f..100f,
                valueFormat = { if (it > 0) "+$it" else "$it" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(contrast = it.roundToInt())) }
            )

            // 3. Saturation (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Saturation",
                value = manualAdjustments.saturation,
                valueRange = -100f..100f,
                valueFormat = { if (it > 0) "+$it" else "$it" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(saturation = it.roundToInt())) }
            )

            // 4. Gamma (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Gamma",
                value = manualAdjustments.gamma,
                valueRange = -100f..100f,
                valueFormat = { if (it > 0) "+$it" else "$it" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(gamma = it.roundToInt())) }
            )

            // 5. Sharpness (0 to 100)
            ManualAdjustmentSliderItem(
                title = "Sharpness",
                value = manualAdjustments.sharpness,
                valueRange = 0f..100f,
                valueFormat = { "$it" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(sharpness = it.roundToInt())) }
            )

            // 6. Hue (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Hue",
                value = manualAdjustments.hue,
                valueRange = -100f..100f,
                valueFormat = { if (it > 0) "+$it°" else "$it°" },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(hue = it.roundToInt())) }
            )

            // 7. Temperature (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Temperature",
                value = manualAdjustments.temperature,
                valueRange = -100f..100f,
                valueFormat = {
                    if (it > 0) "+$it (Warm)" else if (it < 0) "$it (Cool)" else "0"
                },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(temperature = it.roundToInt())) }
            )

            // 8. Tint (-100 to 100)
            ManualAdjustmentSliderItem(
                title = "Tint",
                value = manualAdjustments.tint,
                valueRange = -100f..100f,
                valueFormat = {
                    if (it > 0) "+$it (Magenta)" else if (it < 0) "$it (Green)" else "0"
                },
                onValueChange = { onManualAdjustmentsChange(manualAdjustments.copy(tint = it.roundToInt())) }
            )

            // 9. Deband (Banding Reduction)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SheetButtonBgColor)
                    .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(12.dp))
                    .clickable {
                        onManualAdjustmentsChange(manualAdjustments.copy(deband = !manualAdjustments.deband))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deband Filter",
                        color = SheetButtonTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Reduces color banding artifacts",
                        color = Color(0xFF475569),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = manualAdjustments.deband,
                    onCheckedChange = {
                        onManualAdjustmentsChange(manualAdjustments.copy(deband = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveHighlightColor
                    )
                )
            }
        }
    }
}

@Composable
private fun ManualAdjustmentSliderItem(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormat: (Int) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = NormalItemColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentGradient)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueFormat(value),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AccentPink,
                activeTrackColor = AccentSkyBlue,
                inactiveTrackColor = if (LocalSheetIsLowGlassOpacity.current) Color(0x33FFFFFF) else AccentSkyBlue.copy(alpha = 0.20f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Backward compatibility wrapper for VideoEqSelectionPanel
 */
@Composable
fun VideoEqSelectionPanel(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    VideoSettingSelectionPanel(
        selectedPreset = VideoFilterPreset.NONE,
        onSelectPreset = {},
        manualAdjustments = ManualVideoAdjustments(
            brightness = ((brightness - 0.5f) * 200f).roundToInt(),
            contrast = ((contrast - 1.0f) * 100f).roundToInt(),
            saturation = ((saturation - 1.0f) * 100f).roundToInt()
        ),
        onManualAdjustmentsChange = { manual ->
            onBrightnessChange((manual.brightness / 200f) + 0.5f)
            onContrastChange((manual.contrast / 100f) + 1.0f)
            onSaturationChange((manual.saturation / 100f) + 1.0f)
        },
        isLandscape = isLandscape,
        glassBlurTransparency = glassBlurTransparency,
        onOpenManualAdjustments = {},
        onDismissRequest = onDismissRequest
    )
}

/**
 * 6B. CHUNKY VERTICAL EQUALIZER SLIDER
 * Chunky pill-track vertical slider matching the high-contrast aesthetic with 0 dB center reference crossbar and thumb dot.
 */
@Composable
fun VerticalEqChunkySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
    trackWidth: androidx.compose.ui.unit.Dp = 14.dp,
    centerNotchWidth: androidx.compose.ui.unit.Dp = 32.dp,
    centerNotchHeight: androidx.compose.ui.unit.Dp = 3.5.dp,
    isLowGlassOpacity: Boolean = false
) {
    val trackWidthPx = with(LocalDensity.current) { trackWidth.toPx() }
    val centerNotchWidthPx = with(LocalDensity.current) { centerNotchWidth.toPx() }
    val centerNotchHeightPx = with(LocalDensity.current) { centerNotchHeight.toPx() }
    val thumbDotRadiusPx = with(LocalDensity.current) { 3.2.dp.toPx() }

    val baseTrackColor = if (isLowGlassOpacity) Color(0xFF334155) else Color(0xFFCBD5E1)
    val activeTrackColor = if (isLowGlassOpacity) Color(0xFF64748B) else Color(0xFF94A3B8)
    val centerNotchColor = if (isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B)
    val thumbDotColor = if (isLowGlassOpacity) Color(0xFFF1F5F9) else Color(0xFF475569)

    val gestureModifier = if (enabled) {
        Modifier
            .pointerInput(valueRange) {
                fun updateFromY(y: Float, totalHeight: Float) {
                    val halfTrack = trackWidthPx / 2f
                    val usableHeight = (totalHeight - 2 * halfTrack).coerceAtLeast(1f)
                    val clampedY = y.coerceIn(halfTrack, totalHeight - halfTrack)
                    val fraction = 1f - ((clampedY - halfTrack) / usableHeight)
                    val rangeSpan = valueRange.endInclusive - valueRange.start
                    val rawVal = valueRange.start + fraction * rangeSpan
                    val snappedVal = ((rawVal * 2).roundToInt() / 2f).coerceIn(valueRange)
                    onValueChange(snappedVal)
                }

                // Tap-to-set and drag-to-adjust are combined in ONE pointerInput
                // block (both launched as sibling coroutines sharing the same
                // pointer event stream) instead of two separate stacked
                // pointerInput modifiers. Two independent modifiers here used to
                // race for the very first touch down, which is why a single tap
                // or the start of a drag on this slider sometimes needed to be
                // repeated before it registered.
                kotlinx.coroutines.coroutineScope {
                    launch {
                        detectTapGestures { offset ->
                            updateFromY(offset.y, size.height.toFloat())
                        }
                    }
                    launch {
                        detectVerticalDragGestures { change, _ ->
                            change.consume()
                            updateFromY(change.position.y, size.height.toFloat())
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .width(44.dp)
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val halfTrackPx = trackWidthPx / 2f
        val usableHeightPx = (totalHeightPx - 2 * halfTrackPx).coerceAtLeast(1f)
        val rangeSpan = valueRange.endInclusive - valueRange.start
        val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        val thumbY = totalHeightPx - halfTrackPx - (fraction * usableHeightPx)
        val zeroFraction = ((0f - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        val zeroY = totalHeightPx - halfTrackPx - (zeroFraction * usableHeightPx)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f

            // 1. Thick base capsule track
            drawRoundRect(
                color = baseTrackColor,
                topLeft = Offset(centerX - halfTrackPx, 0f),
                size = androidx.compose.ui.geometry.Size(trackWidthPx, totalHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfTrackPx, halfTrackPx)
            )

            // 2. Active highlight between 0 dB and current thumb
            if (kotlin.math.abs(thumbY - zeroY) > 1.5f) {
                val topActiveY = kotlin.math.min(thumbY, zeroY)
                val activeHeight = kotlin.math.abs(thumbY - zeroY)
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(centerX - halfTrackPx, topActiveY),
                    size = androidx.compose.ui.geometry.Size(trackWidthPx, activeHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfTrackPx, halfTrackPx)
                )
            }

            // 3. Center 0 dB prominent crossbar line
            drawRoundRect(
                color = centerNotchColor,
                topLeft = Offset(centerX - centerNotchWidthPx / 2f, zeroY - centerNotchHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(centerNotchWidthPx, centerNotchHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(centerNotchHeightPx / 2f, centerNotchHeightPx / 2f)
            )

            // 4. Thumb small dot indicator
            drawCircle(
                color = thumbDotColor,
                radius = thumbDotRadiusPx,
                center = Offset(centerX, thumbY.coerceIn(halfTrackPx, totalHeightPx - halfTrackPx))
            )
        }
    }
}

/**
 * 6C. CHUNKY HORIZONTAL VOLUME BOOST SLIDER
 * Chunky pill slider with start indicator notch bar and thumb dot indicator.
 */
@Composable
fun HorizontalBoostChunkySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isLowGlassOpacity: Boolean = false
) {
    val trackHeightPx = with(LocalDensity.current) { 14.dp.toPx() }
    val startNotchWidthPx = with(LocalDensity.current) { 3.5.dp.toPx() }
    val startNotchHeightPx = with(LocalDensity.current) { 24.dp.toPx() }
    val thumbDotRadiusPx = with(LocalDensity.current) { 3.2.dp.toPx() }
    val spacingPx = with(LocalDensity.current) { 6.dp.toPx() }

    val baseTrackColor = if (isLowGlassOpacity) Color(0xFF334155) else Color(0xFFCBD5E1)
    val activeTrackColor = if (isLowGlassOpacity) Color(0xFF64748B) else Color(0xFF94A3B8)
    val startNotchColor = if (isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B)
    val thumbDotColor = if (isLowGlassOpacity) Color(0xFFF1F5F9) else Color(0xFF475569)

    val gestureModifier = if (enabled) {
        Modifier
            .pointerInput(valueRange) {
                fun updateFromX(x: Float, totalWidth: Float) {
                    val trackStartX = startNotchWidthPx + spacingPx
                    val trackEndX = totalWidth
                    val usableWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val clampedX = x.coerceIn(trackStartX, trackEndX)
                    val fraction = (clampedX - trackStartX) / usableWidth
                    val rangeSpan = valueRange.endInclusive - valueRange.start
                    val rawVal = valueRange.start + fraction * rangeSpan
                    val snappedVal = rawVal.roundToInt().toFloat().coerceIn(valueRange)
                    onValueChange(snappedVal)
                }

                // Combined tap + drag detection in one pointerInput block (see
                // VerticalEqChunkySlider above for why this matters for touch
                // reliability).
                kotlinx.coroutines.coroutineScope {
                    launch {
                        detectTapGestures { offset ->
                            updateFromX(offset.x, size.width.toFloat())
                        }
                    }
                    launch {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            updateFromX(change.position.x, size.width.toFloat())
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val centerY = with(LocalDensity.current) { maxHeight.toPx() / 2f }
        val trackStartX = startNotchWidthPx + spacingPx
        val trackEndX = totalWidthPx
        val usableWidthPx = (trackEndX - trackStartX).coerceAtLeast(1f)
        val rangeSpan = valueRange.endInclusive - valueRange.start
        val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        val thumbX = trackStartX + (fraction * usableWidthPx)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Start vertical reference notch bar
            drawRoundRect(
                color = startNotchColor,
                topLeft = Offset(0f, centerY - startNotchHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(startNotchWidthPx, startNotchHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(startNotchWidthPx / 2f, startNotchWidthPx / 2f)
            )

            // 2. Base thick horizontal capsule track
            drawRoundRect(
                color = baseTrackColor,
                topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(usableWidthPx, trackHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
            )

            // 3. Active highlight
            if (fraction > 0.01f) {
                val activeWidth = (thumbX - trackStartX).coerceAtLeast(trackHeightPx)
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(trackStartX, centerY - trackHeightPx / 2f),
                    size = androidx.compose.ui.geometry.Size(activeWidth, trackHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
                )
            }

            // 4. Thumb dot indicator
            drawCircle(
                color = thumbDotColor,
                radius = thumbDotRadiusPx,
                center = Offset(thumbX.coerceIn(trackStartX + trackHeightPx / 2f, trackEndX - trackHeightPx / 2f), centerY)
            )
        }
    }
}

@Composable
private fun ChunkyEqBandColumn(
    freqLabel: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    enabled: Boolean = true,
    isLowGlassOpacity: Boolean = false,
    sliderHeight: androidx.compose.ui.unit.Dp = 140.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f)
    ) {
        // Gain value number on top (e.g. 0, +4, -2)
        val gainText = if (gain > 0f) "+${if (gain % 1f == 0f) gain.toInt().toString() else "%.1f".format(java.util.Locale.US, gain)}"
        else if (gain < 0f) "${if (gain % 1f == 0f) gain.toInt().toString() else "%.1f".format(java.util.Locale.US, gain)}"
        else "0"

        Text(
            text = gainText,
            color = if (isLowGlassOpacity) Color(0xFFF8FAFC) else Color(0xFF1E293B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        // Chunky Vertical Slider
        VerticalEqChunkySlider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = -12f..12f,
            enabled = enabled,
            height = sliderHeight,
            isLowGlassOpacity = isLowGlassOpacity
        )

        // Frequency label at bottom
        Text(
            text = freqLabel,
            color = if (isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 6D. AUDIO EQUALIZER PANEL
 * Real-time 5-band audio equalizer (-12 dB to +12 dB) and Volume Boost (0 to +10 dB).
 * Anchors on the right side in landscape and bottom in portrait (matching Audio Delay / Subtitle Delay panel style).
 */
@Composable
fun AudioEqualizerPanel(
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    equalizerEnabled: Boolean,
    onToggleEqualizer: (Boolean) -> Unit,
    eq60Hz: Float,
    onEq60HzChange: (Float) -> Unit,
    eq230Hz: Float,
    onEq230HzChange: (Float) -> Unit,
    eq910Hz: Float,
    onEq910HzChange: (Float) -> Unit,
    eq3600Hz: Float,
    onEq3600HzChange: (Float) -> Unit,
    eq14000Hz: Float,
    onEq14000HzChange: (Float) -> Unit,
    volumeBoostDb: Float,
    onVolumeBoostDbChange: (Float) -> Unit,
    currentPreset: String,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onReset: () -> Unit,
    onShowNotification: (String) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    // Dark sheet (dark app theme OR very transparent glass): the sheet text is light, so fields,
    // +/- buttons and separators must use the dark-glass surfaces, not the light ones. This is
    // read from the app theme directly because this code runs OUTSIDE the sheet's own
    // CompositionLocalProvider, where LocalSheetNormalTextColor is still its light-mode default.
    val isLowGlassOpacity = glassBlurTransparency < 25f || isAppInDarkTheme()

    PlayerGlassModalSheet(
        panelKey = "audio_equalizer",
        isLandscape = isLandscape,
        openOnRightSide = true,
        heightFraction = if (isLandscape) 0.92f else 0.72f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "EQUALIZER",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = if (isLowGlassOpacity) Color(0xFFF8FAFC) else Color(0xFF1E293B)
            )
        },
        headerEndContent = {
            Switch(
                checked = equalizerEnabled,
                onCheckedChange = { isChecked ->
                    onToggleEqualizer(isChecked)
                    onShowNotification(if (isChecked) "Equalizer Enabled" else "Equalizer Disabled")
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = if (isLowGlassOpacity) Color(0xFF818CF8) else Color(0xFF475569),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = if (isLowGlassOpacity) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Presets Chip Row (Horizontally Scrollable Pill Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (equalizerEnabled) 1f else 0.45f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EqualizerPreset.entries.forEach { preset ->
                    val isSelected = currentPreset.equals(preset.id, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) {
                                    AccentGradient
                                } else {
                                    if (isLowGlassOpacity) SolidColor(Color(0x1AFFFFFF)) else SolidColor(Color(0xFFF1F5F9))
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    Color(0x40FFFFFF)
                                } else {
                                    if (isLowGlassOpacity) Color(0x33FFFFFF) else Color(0xFFCBD5E1)
                                },
                                shape = RoundedCornerShape(50)
                            )
                            .clickable(enabled = equalizerEnabled) {
                                onSelectPreset(preset)
                                onShowNotification("Preset: ${preset.displayName}")
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.displayName,
                            color = if (isSelected) Color.White else (if (isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 2. 5-Band Vertical Equalizer Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Band 1: 60 Hz
                ChunkyEqBandColumn(
                    freqLabel = "60Hz",
                    gain = eq60Hz,
                    onGainChange = onEq60HzChange,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity,
                    sliderHeight = if (isLandscape) 105.dp else 135.dp
                )

                // Band 2: 230 Hz
                ChunkyEqBandColumn(
                    freqLabel = "230Hz",
                    gain = eq230Hz,
                    onGainChange = onEq230HzChange,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity,
                    sliderHeight = if (isLandscape) 105.dp else 135.dp
                )

                // Band 3: 910 Hz
                ChunkyEqBandColumn(
                    freqLabel = "910Hz",
                    gain = eq910Hz,
                    onGainChange = onEq910HzChange,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity,
                    sliderHeight = if (isLandscape) 105.dp else 135.dp
                )

                // Band 4: 3.6 kHz
                ChunkyEqBandColumn(
                    freqLabel = "3.6kHz",
                    gain = eq3600Hz,
                    onGainChange = onEq3600HzChange,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity,
                    sliderHeight = if (isLandscape) 105.dp else 135.dp
                )

                // Band 5: 14 kHz
                ChunkyEqBandColumn(
                    freqLabel = "14kHz",
                    gain = eq14000Hz,
                    onGainChange = onEq14000HzChange,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity,
                    sliderHeight = if (isLandscape) 105.dp else 135.dp
                )
            }

            // Subtle Divider
            HorizontalDivider(
                color = if (isLowGlassOpacity) Color(0x26FFFFFF) else Color(0xFFE2E8F0),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // 3. Volume Boost Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (equalizerEnabled) 1f else 0.45f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOLUME BOOST",
                        color = if (isLowGlassOpacity) Color(0xFF94A3B8) else Color(0xFF64748B),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (volumeBoostDb <= 0.05f) "Off" else "+${volumeBoostDb.roundToInt()} dB",
                        color = if (volumeBoostDb > 0.05f) {
                            if (isLowGlassOpacity) Color(0xFF818CF8) else Color(0xFF2D1F3D)
                        } else {
                            if (isLowGlassOpacity) Color(0xFF64748B) else Color(0xFF94A3B8)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Chunky Horizontal Slider
                HorizontalBoostChunkySlider(
                    value = volumeBoostDb,
                    onValueChange = onVolumeBoostDbChange,
                    valueRange = 0f..10f,
                    enabled = equalizerEnabled,
                    isLowGlassOpacity = isLowGlassOpacity
                )

                // Range Labels (0 dB and +10 dB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0 dB",
                        color = if (isLowGlassOpacity) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "+10 dB",
                        color = if (isLowGlassOpacity) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 7. A-B REPEAT LOOP PANEL
 */
@Composable
fun AbLoopSelectionPanel(
    currentPositionMs: Long,
    loopPointA: Long?,
    loopPointB: Long?,
    onSetPointA: (Long) -> Unit,
    onSetPointB: (Long) -> Unit,
    onClearLoop: () -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    PlayerGlassModalSheet(
        panelKey = "ab_loop",
        isLandscape = isLandscape,
        heightFraction = 0.52f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "A-B Repeat Loop",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isLowGlass = LocalSheetIsLowGlassOpacity.current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (loopPointA != null) (if (isLowGlass) Color(0x3538BDF8) else Color(0xFFF3E8FF)) else SheetButtonBgColor)
                        .border(1.dp, if (loopPointA != null) ActiveHighlightColor else SheetButtonBorderColor, RoundedCornerShape(14.dp))
                        .cardBounceClick(scaleDown = 0.95f) { onSetPointA(currentPositionMs) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (loopPointA != null) "Point A: ${formatPlaybackMs(loopPointA)}" else "Set Point A",
                            color = if (loopPointA != null) ActiveHighlightColor else SheetButtonTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Start marker",
                            color = if (loopPointA != null) ActiveHighlightColor.copy(alpha = 0.8f) else Color(0xFF475569),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (loopPointB != null) (if (isLowGlass) Color(0x3538BDF8) else Color(0xFFF3E8FF)) else SheetButtonBgColor)
                        .border(1.dp, if (loopPointB != null) ActiveHighlightColor else SheetButtonBorderColor, RoundedCornerShape(14.dp))
                        .cardBounceClick(scaleDown = 0.95f) { onSetPointB(currentPositionMs) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (loopPointB != null) "Point B: ${formatPlaybackMs(loopPointB)}" else "Set Point B",
                            color = if (loopPointB != null) ActiveHighlightColor else SheetButtonTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "End marker",
                            color = if (loopPointB != null) ActiveHighlightColor.copy(alpha = 0.8f) else Color(0xFF475569),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            if (loopPointA != null || loopPointB != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFEE2E2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(14.dp))
                        .cardBounceClick(scaleDown = 0.96f) {
                            onClearLoop()
                            onDismissRequest()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clear A-B Repeat Loop",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

/**
 * 8. PLAYLIST / EPISODE SELECTION PANEL
 * Matches the requested screenshot layout:
 * - Header: "Now Playing • X items" with Grid view toggle icon
 * - Episode Cards with:
 *   - 16:9 Thumbnail preview with dark episode index badge ("1", "2", "3", ...)
 *   - Episode Name in bold
 *   - Metadata row with clean pills for Duration ("23:59") and Quality ("1920x1080")
 *   - Active "Playing" badge for currently playing episode
 *   - Two-line drag handle with touch-and-hold vertical drag to reorder episodes up/down
 */
@Composable
fun PlaylistSelectionPanel(
    playlistVideos: List<String> = emptyList(),
    playlistItems: List<VideoItem> = emptyList(),
    currentIndex: Int,
    totalEpisodes: Int = playlistVideos.size.coerceAtLeast(playlistItems.size),
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    thumbnailStrategy: com.example.ui.state.ThumbnailStrategy = com.example.ui.state.ThumbnailStrategy.SMART,
    thumbnailQuality: com.example.ui.state.ThumbnailQuality = com.example.ui.state.ThumbnailQuality.HIGH,
    thumbnailFallbackSecond: Int = 1,
    onSelectPlaylistItem: (Int) -> Unit,
    onReorderPlaylist: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onDismissRequest: () -> Unit
) {
    val effectiveCount = if (playlistItems.isNotEmpty()) playlistItems.size else playlistVideos.size
    var isGridView by remember { mutableStateOf(false) }

    PlayerGlassModalSheet(
        panelKey = "playlist",
        isLandscape = isLandscape,
        glassBlurTransparency = glassBlurTransparency,
        heightFraction = 0.58f,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Now Playing • $effectiveCount ${if (effectiveCount == 1) "item" else "items"}",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        },
        headerEndContent = {
            // 4-box Grid View / List View Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isGridView) ActiveHighlightColor.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { isGridView = !isGridView }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(
                                imageVector = if (isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                    tint = if (isGridView) ActiveHighlightColor else NormalItemColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        if (isGridView) {
            // GRID VIEW MODE (4-boxes grid layout)
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isLandscape) 3 else 2),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (playlistItems.isNotEmpty()) {
                    itemsIndexed(playlistItems, key = { idx, item -> "${item.id}_${item.path}_$idx" }) { idx, item ->
                        val isCurrent = (idx + 1) == currentIndex
                        val title = item.displayName.substringBeforeLast(".")
                        val durationText = formatDuration(item.durationMs)
                        val qualityText = if (item.resolution.isNotBlank()) item.resolution else "1920x1080"

                        PlaylistEpisodeGridCard(
                            index = idx + 1,
                            title = title,
                            durationText = durationText,
                            qualityText = qualityText,
                            isCurrent = isCurrent,
                            videoItem = item,
                            thumbnailStrategy = thumbnailStrategy,
                            thumbnailQuality = thumbnailQuality,
                            thumbnailFallbackSecond = thumbnailFallbackSecond,
                            onClick = {
                                onSelectPlaylistItem(idx + 1)
                                onDismissRequest()
                            }
                        )
                    }
                } else {
                    itemsIndexed(playlistVideos, key = { idx, name -> "${name}_$idx" }) { idx, name ->
                        val isCurrent = (idx + 1) == currentIndex
                        val title = name.substringBeforeLast(".")

                        PlaylistEpisodeGridCard(
                            index = idx + 1,
                            title = title,
                            durationText = "23:59",
                            qualityText = "1920x1080",
                            isCurrent = isCurrent,
                            videoItem = null,
                            thumbnailStrategy = thumbnailStrategy,
                            thumbnailQuality = thumbnailQuality,
                            thumbnailFallbackSecond = thumbnailFallbackSecond,
                            onClick = {
                                onSelectPlaylistItem(idx + 1)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        } else {
            // LIST VIEW MODE with Smooth Multi-Item Continuous Drag-to-Reorder Support matching ControlLayoutEditor
            val playlistListState = rememberLazyListState()
            var playlistCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var playlistHeightPx by remember { mutableFloatStateOf(0f) }
            var dragPointerYInList by remember { mutableFloatStateOf(-1f) }
            var dragGrabOffsetY by remember { mutableFloatStateOf(0f) }
            var dragPointerId by remember { mutableStateOf<PointerId?>(null) }

            val itemsCount = if (playlistItems.isNotEmpty()) playlistItems.size else playlistVideos.size
            var draggingIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            var measuredItemHeightPx by remember { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current

            LaunchedEffect(draggingIndex) {
                if (draggingIndex == null) return@LaunchedEffect
                val edgeZone = with(density) { 86.dp.toPx() }
                val maxScrollPerTick = with(density) { 7.dp.toPx() }

                while (draggingIndex != null) {
                    val pointerY = dragPointerYInList
                    val listH = playlistHeightPx
                    if (pointerY >= 0f && listH > 0f) {
                        val scrollDelta = when {
                            pointerY < edgeZone -> {
                                val ratio = ((edgeZone - pointerY) / edgeZone).coerceIn(0f, 1f)
                                -maxScrollPerTick * ratio
                            }
                            pointerY > (listH - edgeZone) -> {
                                val ratio = ((pointerY - (listH - edgeZone)) / edgeZone).coerceIn(0f, 1f)
                                maxScrollPerTick * ratio
                            }
                            else -> 0f
                        }

                        if (scrollDelta != 0f) {
                            val consumed = playlistListState.scrollBy(scrollDelta)
                            if (consumed != 0f) dragOffsetY += consumed
                        }
                    }
                    kotlinx.coroutines.delay(24L)
                }
            }

            val effectiveHeight = if (measuredItemHeightPx > 0f) measuredItemHeightPx else with(density) { 70.dp.toPx() }
            val fromIndex = draggingIndex ?: -1
            val hoverIndex = if (fromIndex != -1) {
                val pointerY = dragPointerYInList
                playlistListState.layoutInfo.visibleItemsInfo
                    .filter { it.index in 0 until itemsCount }
                    .minByOrNull { item -> kotlin.math.abs(pointerY - (item.offset + item.size / 2f)) }
                    ?.index ?: fromIndex
            } else -1

            val currentDragPointerId by rememberUpdatedState(dragPointerId)
            val currentDraggingIndex by rememberUpdatedState(draggingIndex)
            val currentHoverIndex by rememberUpdatedState(hoverIndex)

            // The outer Box owns the active pointer lifecycle. This prevents a LazyColumn item
            // disappearing during auto-scroll from cancelling the drag.
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        playlistCoordinates = coordinates
                        playlistHeightPx = coordinates.size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val activeId = currentDragPointerId
                                if (activeId != null) {
                                    val change = event.changes.firstOrNull { it.id == activeId }
                                    if (change != null) {
                                        if (change.pressed) {
                                            dragPointerYInList = change.position.y
                                        } else {
                                            val f = currentDraggingIndex ?: -1
                                            val t = currentHoverIndex
                                            if (f != -1 && t != -1 && f != t) onReorderPlaylist(f, t)
                                            dragPointerId = null
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                            dragPointerYInList = -1f
                                            dragGrabOffsetY = 0f
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = playlistListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                if (playlistItems.isNotEmpty()) {
                    itemsIndexed(playlistItems, key = { _, item -> item.id }) { idx, item ->
                        val isCurrent = (idx + 1) == currentIndex
                        val title = item.displayName.substringBeforeLast(".")
                        val durationText = formatDuration(item.durationMs)
                        val qualityText = if (item.resolution.isNotBlank()) item.resolution else "1920x1080"
                        val isBeingDragged = (draggingIndex == idx)

                        val targetTranslationY = when {
                            isBeingDragged -> 0f
                            draggingIndex != null && fromIndex != -1 && hoverIndex != -1 -> {
                                when {
                                    fromIndex < hoverIndex && idx in (fromIndex + 1)..hoverIndex -> -effectiveHeight
                                    fromIndex > hoverIndex && idx in hoverIndex until fromIndex -> effectiveHeight
                                    else -> 0f
                                }
                            }
                            else -> 0f
                        }

                        val animatedTranslationY by animateFloatAsState(
                            targetValue = targetTranslationY,
                            animationSpec = spring(
                                dampingRatio = 0.90f,
                                stiffness = 800f
                            ),
                            label = "EpisodeTranslationY_${item.id}"
                        )

                        val visualTranslationY = if (isBeingDragged) dragOffsetY else animatedTranslationY

                        PlaylistEpisodeRow(
                            index = idx + 1,
                            title = title,
                            durationText = durationText,
                            qualityText = qualityText,
                            isCurrent = isCurrent,
                            videoItem = item,
                            thumbnailStrategy = thumbnailStrategy,
                            thumbnailQuality = thumbnailQuality,
                            thumbnailFallbackSecond = thumbnailFallbackSecond,
                            isBeingDragged = isBeingDragged,
                            visualTranslationY = visualTranslationY,
                            modifier = if (isBeingDragged) Modifier.alpha(0f) else Modifier,
                            onMeasureHeight = { h ->
                                if (draggingIndex == null && h > 0f) {
                                    measuredItemHeightPx = h + with(density) { 8.dp.toPx() }
                                }
                            },
                            onDragStart = { pointerId, offset, itemCoords, handleCoords ->
                                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                                draggingIndex = idx
                                dragOffsetY = 0f
                                dragPointerId = pointerId
                                handleCoords?.let { handle ->
                                    playlistCoordinates?.let { lst -> dragPointerYInList = lst.localPositionOf(handle, offset).y }
                                    itemCoords?.let { itm -> dragGrabOffsetY = itm.localPositionOf(handle, offset).y }
                                }
                            },
                            onDragDelta = { deltaY, pos, itemCoords ->
                                if (draggingIndex == idx) {
                                    dragOffsetY += deltaY
                                    itemCoords?.let { itm -> playlistCoordinates?.let { lst -> dragPointerYInList = lst.localPositionOf(itm, pos).y } }
                                }
                            },
                            onDragFinish = {},
                            onClick = {
                                if (draggingIndex == null) {
                                    onSelectPlaylistItem(idx + 1)
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                } else {
                    itemsIndexed(playlistVideos, key = { idx, name -> "${name}_$idx" }) { idx, name ->
                        val isCurrent = (idx + 1) == currentIndex
                        val title = name.substringBeforeLast(".")
                        val isBeingDragged = (draggingIndex == idx)

                        val targetTranslationY = when {
                            isBeingDragged -> 0f
                            draggingIndex != null && fromIndex != -1 && hoverIndex != -1 -> {
                                when {
                                    fromIndex < hoverIndex && idx in (fromIndex + 1)..hoverIndex -> -effectiveHeight
                                    fromIndex > hoverIndex && idx in hoverIndex until fromIndex -> effectiveHeight
                                    else -> 0f
                                }
                            }
                            else -> 0f
                        }

                        val animatedTranslationY by animateFloatAsState(
                            targetValue = targetTranslationY,
                            animationSpec = spring(
                                dampingRatio = 0.90f,
                                stiffness = 800f
                            ),
                            label = "EpisodeTranslationY_${name}_$idx"
                        )

                        val visualTranslationY = if (isBeingDragged) dragOffsetY else animatedTranslationY

                        PlaylistEpisodeRow(
                            index = idx + 1,
                            title = title,
                            durationText = "23:59",
                            qualityText = "1920x1080",
                            isCurrent = isCurrent,
                            videoItem = null,
                            thumbnailStrategy = thumbnailStrategy,
                            thumbnailQuality = thumbnailQuality,
                            thumbnailFallbackSecond = thumbnailFallbackSecond,
                            isBeingDragged = isBeingDragged,
                            visualTranslationY = visualTranslationY,
                            modifier = if (isBeingDragged) Modifier.alpha(0f) else Modifier,
                            onMeasureHeight = { h ->
                                if (draggingIndex == null && h > 0f) {
                                    measuredItemHeightPx = h + with(density) { 8.dp.toPx() }
                                }
                            },
                            onDragStart = { pointerId, offset, itemCoords, handleCoords ->
                                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                                draggingIndex = idx
                                dragOffsetY = 0f
                                dragPointerId = pointerId
                                handleCoords?.let { handle ->
                                    playlistCoordinates?.let { lst -> dragPointerYInList = lst.localPositionOf(handle, offset).y }
                                    itemCoords?.let { itm -> dragGrabOffsetY = itm.localPositionOf(handle, offset).y }
                                }
                            },
                            onDragDelta = { deltaY, pos, itemCoords ->
                                if (draggingIndex == idx) {
                                    dragOffsetY += deltaY
                                    itemCoords?.let { itm -> playlistCoordinates?.let { lst -> dragPointerYInList = lst.localPositionOf(itm, pos).y } }
                                }
                            },
                            onDragFinish = {},
                            onClick = {
                                if (draggingIndex == null) {
                                    onSelectPlaylistItem(idx + 1)
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }
            }
                draggingIndex?.let { draggedIndex ->
                    if (draggedIndex in 0 until itemsCount) {
                        val topPx = dragPointerYInList - dragGrabOffsetY
                        if (playlistItems.isNotEmpty()) {
                            val dragged = playlistItems[draggedIndex]
                            PlaylistEpisodeRow(
                                index = draggedIndex + 1,
                                title = dragged.displayName.substringBeforeLast("."),
                                durationText = formatDuration(dragged.durationMs),
                                qualityText = dragged.resolution.ifBlank { "1920x1080" },
                                isCurrent = (draggedIndex + 1) == currentIndex,
                                videoItem = dragged,
                                thumbnailStrategy = thumbnailStrategy,
                                thumbnailQuality = thumbnailQuality,
                                thumbnailFallbackSecond = thumbnailFallbackSecond,
                                isBeingDragged = true,
                                visualTranslationY = 0f,
                                enableDrag = false,
                                modifier = Modifier.offset { IntOffset(0, topPx.roundToInt()) },
                                onMeasureHeight = {},
                                onDragStart = { _, _, _, _ -> },
                                onDragDelta = { _, _, _ -> },
                                onDragFinish = {},
                                onClick = {}
                            )
                        } else {
                            val name = playlistVideos[draggedIndex]
                            PlaylistEpisodeRow(
                                index = draggedIndex + 1,
                                title = name.substringBeforeLast("."),
                                durationText = "23:59",
                                qualityText = "1920x1080",
                                isCurrent = (draggedIndex + 1) == currentIndex,
                                videoItem = null,
                                thumbnailStrategy = thumbnailStrategy,
                                thumbnailQuality = thumbnailQuality,
                                thumbnailFallbackSecond = thumbnailFallbackSecond,
                                isBeingDragged = true,
                                visualTranslationY = 0f,
                                enableDrag = false,
                                modifier = Modifier.offset { IntOffset(0, topPx.roundToInt()) },
                                onMeasureHeight = {},
                                onDragStart = { _, _, _, _ -> },
                                onDragDelta = { _, _, _ -> },
                                onDragFinish = {},
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grid View Card matching modern 4-boxes grid layout
 */
@Composable
private fun PlaylistEpisodeGridCard(
    index: Int,
    title: String,
    durationText: String,
    qualityText: String,
    isCurrent: Boolean,
    videoItem: VideoItem?,
    thumbnailStrategy: com.example.ui.state.ThumbnailStrategy,
    thumbnailQuality: com.example.ui.state.ThumbnailQuality,
    thumbnailFallbackSecond: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(videoItem) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(
        videoItem?.id,
        videoItem?.uri,
        videoItem?.path,
        thumbnailStrategy,
        thumbnailQuality,
        thumbnailFallbackSecond
    ) {
        thumbnailBitmap = null
        if (videoItem != null) {
            repeat(3) { attempt ->
                if (thumbnailBitmap == null) {
                    thumbnailBitmap = loadVideoThumbnail(
                        context, videoItem.id, videoItem.uri, videoItem.path,
                        strategy = thumbnailStrategy,
                        quality = thumbnailQuality,
                        fallbackSecond = thumbnailFallbackSecond
                    )
                    if (thumbnailBitmap == null && attempt < 2) {
                        kotlinx.coroutines.delay(90L * (attempt + 1))
                    }
                }
            }
        }
    }

    // Slightly see-through so the video/background still shows behind the playing item.
    val activeCardBg = Color(0xFFE0F2FE).copy(alpha = 0.78f)
    val activeBorderColor = Color(0xFF0284C7)
    val normalCardBg = Color(0x0A000000)
    val normalBorderColor = Color(0x14000000)

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) activeCardBg else normalCardBg)
            .border(
                width = if (isCurrent) 1.5.dp else 1.dp,
                color = if (isCurrent) activeBorderColor else normalBorderColor,
                shape = shape
            )
            .cardBounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Thumbnail Box with Episode Badge & Duration Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF334155), Color(0xFF1E293B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                                imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Top-Left Episode Number Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bottom-Right Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Playing badge overlay if current
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0284C7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Playing",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Title
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) Color(0xFF0F172A) else NormalItemColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Quality & Episode Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isCurrent) Color(0x220284C7) else Color(0x18000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = qualityText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) Color(0xFF0284C7) else NormalItemColor.copy(alpha = 0.85f)
                    )
                }

                Text(
                    text = "Ep $index",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) Color(0xFF0F172A) else SecondaryTextColor
                )
            }
        }
    }
}

/**
 * Single Playlist Episode Row with smooth continuous Drag-to-Reorder matching ControlLayoutEditor
 */
@Composable
private fun PlaylistEpisodeRow(
    index: Int,
    title: String,
    durationText: String,
    qualityText: String,
    isCurrent: Boolean,
    videoItem: VideoItem?,
    thumbnailStrategy: com.example.ui.state.ThumbnailStrategy,
    thumbnailQuality: com.example.ui.state.ThumbnailQuality,
    thumbnailFallbackSecond: Int,
    isBeingDragged: Boolean,
    visualTranslationY: Float,
    enableDrag: Boolean = true,
    modifier: Modifier = Modifier,
    onMeasureHeight: (Float) -> Unit,
    onDragStart: (PointerId, Offset, LayoutCoordinates?, LayoutCoordinates?) -> Unit,
    onDragDelta: (Float, Offset, LayoutCoordinates?) -> Unit,
    onDragFinish: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(videoItem) { mutableStateOf<Bitmap?>(null) }
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(
        videoItem?.id,
        videoItem?.uri,
        videoItem?.path,
        thumbnailStrategy,
        thumbnailQuality,
        thumbnailFallbackSecond
    ) {
        thumbnailBitmap = null
        if (videoItem != null) {
            repeat(3) { attempt ->
                if (thumbnailBitmap == null) {
                    thumbnailBitmap = loadVideoThumbnail(
                        context, videoItem.id, videoItem.uri, videoItem.path,
                        strategy = thumbnailStrategy,
                        quality = thumbnailQuality,
                        fallbackSecond = thumbnailFallbackSecond
                    )
                    if (thumbnailBitmap == null && attempt < 2) {
                        kotlinx.coroutines.delay(90L * (attempt + 1))
                    }
                }
            }
        }
    }

    // Slightly see-through so the video/background still shows behind the playing item.
    val activeCardBg = Color(0xFFE0F2FE).copy(alpha = 0.78f)
    val activeBorderColor = Color(0xFF0284C7)
    val normalCardBg = Color(0x0A000000)
    val normalBorderColor = Color(0x14000000)
    val draggedBorderGradient = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))

    val shape = RoundedCornerShape(16.dp)
    val itemKey = videoItem?.id ?: "$title$index"

    val dragBorderModifier = if (isBeingDragged) {
        Modifier.border(2.dp, draggedBorderGradient, shape)
    } else {
        Modifier.border(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = if (isCurrent) activeBorderColor else normalBorderColor,
            shape = shape
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isBeingDragged) 99f else 1f)
            .graphicsLayer {
                translationY = visualTranslationY
                if (isBeingDragged) {
                    scaleX = 1.028f
                    scaleY = 1.028f
                    shadowElevation = 18.dp.toPx()
                    this.shape = shape
                    clip = false
                }
            }
            .then(if (isBeingDragged) Modifier.shadow(18.dp, shape) else Modifier)
            .clip(shape)
            .background(if (isBeingDragged || isCurrent) activeCardBg else normalCardBg)
            .then(dragBorderModifier)
            .onGloballyPositioned { coordinates ->
                itemCoordinates = coordinates
                if (coordinates.size.height > 0) {
                    onMeasureHeight(coordinates.size.height.toFloat())
                }
            }
            .pointerInput(itemKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) return@awaitEachGesture

                    onDragStart(down.id, longPress.position, itemCoordinates, handleCoordinates)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val delta = change.positionChange().y
                        if (delta != 0f) {
                            change.consume()
                            onDragDelta(delta, change.position, itemCoordinates)
                        }
                    }
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Thumbnail Box with Episode Number Badge
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF334155), Color(0xFF1E293B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        StyledIcon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Top-Left Episode Number Badge (Black rounded square with bold number)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.5.dp, vertical = 1.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            val isHighlighted = isBeingDragged || isCurrent
            // 2. Middle Content: Title and Metadata Pills (Time and Quality)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) Color(0xFF0F172A) else NormalItemColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1200,
                            initialDelayMillis = 1000
                        )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHighlighted) Color(0x220284C7) else Color(0x18000000))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHighlighted) Color(0xFF0284C7) else NormalItemColor.copy(alpha = 0.85f)
                        )
                    }

                    // Quality Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHighlighted) Color(0x220284C7) else Color(0x18000000))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qualityText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isHighlighted) Color(0xFF0284C7) else NormalItemColor.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Playing Badge (if active)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE0F2FE))
                        .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Playing",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // 4. Two-line Drag Handle (=) that also immediately accepts vertical drag
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBeingDragged) ActiveHighlightColor.copy(alpha = 0.15f) else Color.Transparent)
                    .onGloballyPositioned { handleCoordinates = it }
                    .then(
                        if (enableDrag) {
                            Modifier.pointerInput(itemKey) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val longPress = awaitLongPressOrCancellation(down.id)
                                    if (longPress != null) {
                                        onDragStart(down.id, longPress.position, itemCoordinates, handleCoordinates)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val delta = change.positionChange().y
                                            if (delta != 0f) {
                                                change.consume()
                                                onDragDelta(delta, change.position, itemCoordinates)
                                            }
                                        }
                                    }
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 2.5.dp)
                            .clip(RoundedCornerShape(1.2.dp))
                            .background(if (isBeingDragged) ActiveHighlightColor else Color(0xFF64748B))
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 20.dp, height = 2.5.dp)
                            .clip(RoundedCornerShape(1.2.dp))
                            .background(if (isBeingDragged) ActiveHighlightColor else Color(0xFF64748B))
                    )
                }
            }
        }
    }
}

// =========================================================================
// REUSABLE SUB-COMPONENTS & ROWS (MATCHING SCREENSHOT AESTHETICS)
// =========================================================================

@Composable
private fun SwipeableTrackItem(
    primaryTitle: String,
    badgeTag: String?,
    isSelected: Boolean,
    isSecondary: Boolean = false,
    isLastTouched: Boolean = false,
    selectedColor: Color = ActiveHighlightColor,
    selectedBgColor: Color = ActiveItemBg,
    selectionTag: String? = null,
    translationStatus: TrackTranslationStatus = TrackTranslationStatus.Idle,
    onRemove: (() -> Unit)? = null,
    // false (default): swipe RIGHT -> LEFT removes the row (audio tracks).
    // true: swipe LEFT -> RIGHT removes the row (uploaded subtitles).
    swipeLeftToRightToRemove: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (onRemove == null) {
        SelectableTrackItem(
            indexNumber = null,
            primaryTitle = primaryTitle,
            badgeTag = badgeTag,
            isSelected = isSelected,
            isSecondary = isSecondary,
            isLastTouched = isLastTouched,
            selectedColor = selectedColor,
            selectedBgColor = selectedBgColor,
            selectionTag = selectionTag,
            translationStatus = translationStatus,
            onClick = onClick
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }
    var rowWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 85.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }
    // +1 = the row travels to the right, -1 = to the left while it is being removed.
    val dir = if (swipeLeftToRightToRemove) 1f else -1f
    val removeRed = Color(0xFFEF4444)

    AnimatedVisibility(
        visible = !isDismissed,
        exit = shrinkVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(160)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
                .clip(RoundedCornerShape(12.dp))
                // Red "remove" surface: drawn behind the row, only in the strip the row has
                // already slid away from, so the whole card visibly slides (not just its text).
                .drawBehind {
                    val revealed = kotlin.math.abs(offsetX.value)
                    if (revealed > 1f) {
                        val progress = (revealed / dismissThresholdPx).coerceIn(0f, 1f)
                        val w = revealed.coerceAtMost(size.width)
                        val left = if (dir > 0f) 0f else size.width - w
                        drawRoundRect(
                            color = removeRed.copy(alpha = 0.18f + 0.42f * progress),
                            topLeft = Offset(left, 0f),
                            size = androidx.compose.ui.geometry.Size(w, size.height),
                            cornerRadius = CornerRadius(cornerPx, cornerPx)
                        )
                    }
                }
        ) {
            // Icon + label of the remove action, sitting on the red surface.
            Row(
                modifier = Modifier
                    .align(if (dir > 0f) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 14.dp)
                    .graphicsLayer {
                        val revealed = kotlin.math.abs(offsetX.value)
                        val p = ((revealed - dismissThresholdPx * 0.35f) /
                            (dismissThresholdPx * 0.65f)).coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.8f + 0.2f * p
                        scaleY = 0.8f + 0.2f * p
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StyledIcon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = removeRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Remove",
                    color = removeRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Draggable row (the complete card slides as one piece)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        val progress = (kotlin.math.abs(offsetX.value) / (rowWidthPx.takeIf { it > 0f } ?: 900f))
                            .coerceIn(0f, 1f)
                        alpha = 1f - 0.5f * progress
                    }
                    .pointerInput(swipeLeftToRightToRemove) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDrag = 0f
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (kotlin.math.abs(offsetX.value) > dismissThresholdPx) {
                                        val exitTarget = dir * ((rowWidthPx.takeIf { it > 0f } ?: 900f) + 40f)
                                        offsetX.animateTo(
                                            targetValue = exitTarget,
                                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                                        )
                                        isDismissed = true
                                        // Let the row finish collapsing before it leaves the list.
                                        delay(230)
                                        onRemove()
                                    } else {
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, animationSpec = tween(150))
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                // Only the removal direction moves the row; the opposite direction
                                // is ignored so the row can never be pushed the wrong way.
                                val inRemoveDirection = totalDrag * dir > 0f
                                if (inRemoveDirection) {
                                    change.consume()
                                    coroutineScope.launch {
                                        val next = offsetX.value + dragAmount
                                        offsetX.snapTo(if (dir > 0f) next.coerceAtLeast(0f) else next.coerceAtMost(0f))
                                    }
                                }
                            }
                        )
                    }
            ) {
                SelectableTrackItem(
                    indexNumber = null,
                    primaryTitle = primaryTitle,
                    badgeTag = badgeTag,
                    isSelected = isSelected,
                    isSecondary = isSecondary,
                    isLastTouched = isLastTouched,
                    selectedColor = selectedColor,
                    selectedBgColor = selectedBgColor,
                    selectionTag = selectionTag,
                    translationStatus = translationStatus,
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun SelectableTrackItem(
    indexNumber: String?,
    primaryTitle: String,
    badgeTag: String?,
    isSelected: Boolean,
    isSecondary: Boolean = false,
    isLastTouched: Boolean = false,
    selectedColor: Color = ActiveHighlightColor,
    selectedBgColor: Color = ActiveItemBg,
    selectionTag: String? = null,
    translationStatus: TrackTranslationStatus = TrackTranslationStatus.Idle,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) selectedBgColor else Color.Transparent)
            .cardBounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Radio circle indicator — gradient ring while selected (per AI
                    // Translate UI spec), plain ring otherwise.
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    if (isSecondary) {
                                        Modifier.border(width = 1.8.dp, color = AccentSkyBlue, shape = CircleShape)
                                    } else {
                                        Modifier.border(width = 1.8.dp, brush = AccentGradient, shape = CircleShape)
                                    }
                                } else {
                                    Modifier.border(
                                        width = 1.8.dp,
                                        color = LocalSheetSecondaryTextColor.current.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSecondary) {
                                            Modifier.background(AccentSkyBlue)
                                        } else {
                                            Modifier.background(AccentGradient)
                                        }
                                    )
                            )
                        }
                    }

                    val fullText = if (!indexNumber.isNullOrBlank()) "$indexNumber $primaryTitle" else primaryTitle
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = fullText,
                            color = if (isSelected) Color(0xFF0F172A) else NormalItemColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Completed AI translation: translate icon + [Language] tag under the title.
                        if (translationStatus is TrackTranslationStatus.Completed) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                StyledIcon(
                                    drawableRes = R.drawable.lumora_aitranslate,
                                    contentDescription = null,
                                    tint = AccentSkyBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "[${translationStatus.languageLabel}]",
                                    color = AccentSkyBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!selectionTag.isNullOrBlank()) {
                        val isSecTag = isSecondary || selectionTag == "S"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSecTag) AccentSkyBlue.copy(alpha = 0.15f) else Color(0x250284C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectionTag,
                                color = if (isSecTag) AccentSkyBlue else Color(0xFF0369A1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!badgeTag.isNullOrBlank()) {
                        Text(
                            text = badgeTag,
                            color = if (isSelected) Color(0xFF0369A1) else SecondaryTextColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Thin progress line while AI translation is running on this track.
            if (translationStatus is TrackTranslationStatus.Translating) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { translationStatus.progressPercent / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentSkyBlue,
                        trackColor = AccentSkyBlue.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (translationStatus.etaSeconds != null) {
                            "${translationStatus.progressPercent}% • ~${translationStatus.etaSeconds}s"
                        } else {
                            "${translationStatus.progressPercent}%"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSkyBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableChapterItem(
    indexNumber: String,
    title: String,
    timestamp: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) ActiveItemBg else Color.Transparent)
            .cardBounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$indexNumber $title",
                color = if (isCurrent) Color(0xFF0F172A) else NormalItemColor,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = timestamp,
                color = if (isCurrent) Color(0xFF0284C7) else NormalItemColor.copy(alpha = 0.9f),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.5.sp
            )
        }
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    badgeValue: String?,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) ActiveItemBg else Color.Transparent)
            .cardBounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(iconRes),
                        contentDescription = null,
                        tint = if (isActive) ActiveHighlightColor else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                } else if (icon != null) {
                    StyledIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) ActiveHighlightColor else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = title,
                    color = if (isActive) ActiveHighlightColor else NormalItemColor,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!badgeValue.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = badgeValue,
                    color = if (isActive) ActiveHighlightColor else SecondaryTextColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun GlassPresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLowGlass = LocalSheetIsLowGlassOpacity.current
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    AccentGradient
                } else if (isLowGlass) {
                    SolidColor(Color(0x22FFFFFF))
                } else {
                    SolidColor(Color(0x18FFFFFF))
                }
            )
            .border(
                1.dp,
                if (isSelected) Color(0x40FFFFFF) else if (isLowGlass) Color(0x30FFFFFF) else Color(0x25FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .cardBounceClick(scaleDown = 0.90f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else NormalItemColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun GlassStepperSliderRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0.05f,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Decrement Button (-)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, CircleShape)
                .cardBounceClick(scaleDown = 0.88f) {
                    val next = ((value - step) * 100).roundToInt() / 100f
                    onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
                },
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = "Decrease",
                tint = SheetButtonTextColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Slider Track with Thumb indicator
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { v ->
                val rounded = ((v * 20).roundToInt() / 20f).coerceIn(valueRange.start, valueRange.endInclusive)
                onValueChange(rounded)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AccentPink,
                activeTrackColor = AccentSkyBlue,
                inactiveTrackColor = if (LocalSheetIsLowGlassOpacity.current) Color(0x33FFFFFF) else AccentSkyBlue.copy(alpha = 0.20f)
            )
        )

        // Increment Button (+)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, CircleShape)
                .cardBounceClick(scaleDown = 0.88f) {
                    val next = ((value + step) * 100).roundToInt() / 100f
                    onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
                },
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Increase",
                tint = SheetButtonTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =========================================================================
// METADATA FORMATTERS & SANITIZERS
// =========================================================================

fun formatLanguageName(langCode: String): String {
    if (langCode.isBlank()) return ""
    val clean = langCode.trim().lowercase()
    return when (clean) {
        "en", "eng", "english" -> "English"
        "ja", "jpn", "jp", "japanese" -> "Japanese"
        "hi", "hin", "hindi" -> "Hindi"
        "es", "spa", "spanish" -> "Spanish"
        "fr", "fra", "fre", "french" -> "French"
        "de", "deu", "ger", "german" -> "German"
        "it", "ita", "italian" -> "Italian"
        "pt", "por", "portuguese" -> "Portuguese"
        "ru", "rus", "russian" -> "Russian"
        "zh", "zho", "chi", "chinese" -> "Chinese"
        "ko", "kor", "korean" -> "Korean"
        "ar", "ara", "arabic" -> "Arabic"
        "id", "ind", "indonesian" -> "Indonesian"
        "vi", "vie", "vietnamese" -> "Vietnamese"
        "th", "tha", "thai" -> "Thai"
        "tr", "tur", "turkish" -> "Turkish"
        "bn", "ben", "bengali" -> "Bengali"
        "ta", "tam", "tamil" -> "Tamil"
        "te", "tel", "telugu" -> "Telugu"
        "mr", "mar", "marathi" -> "Marathi"
        "ur", "urd", "urdu" -> "Urdu"
        else -> {
            try {
                val loc = Locale.forLanguageTag(clean)
                val display = loc.displayLanguage
                if (!display.isNullOrBlank() && !display.equals(clean, ignoreCase = true)) {
                    display
                } else {
                    Locale(clean).displayLanguage.ifBlank { clean.uppercase() }
                }
            } catch (_: Throwable) {
                clean.uppercase()
            }
        }
    }
}

/**
 * Resolves the real original format/codec badge for a track (audio or subtitle).
 * Never invents a value: if the actual codec cannot be determined, returns null so the
 * badge is simply hidden instead of showing a misleading default (e.g. always "AAC"/"SUB").
 * 1. Prefers the codec reported directly by the decoder/demuxer (track.codec).
 * 2. Falls back to the file extension of the original filename for external/uploaded tracks.
 */
fun resolveTrackFormatBadge(track: PlayerMediaTrack): String? {
    // External files can be transcoded for MPV playback (e.g. SRT -> temporary ASS). The
    // decoder codec then describes the playback representation, not the user's original file.
    // Prefer the original filename extension for uploaded/external subtitles.
    val originalName = track.originalFilename.trim()
    if (track.isExternal && originalName.isNotBlank()) {
        val originalExt = originalName.substringAfterLast('.', "").trim()
        if (originalExt.isNotBlank() && originalExt.length in 2..6 && originalExt.all { it.isLetterOrDigit() }) {
            return normalizeCodecLabel(originalExt)
        }
    }

    val rawCodec = track.codec.trim()
    if (rawCodec.isNotBlank()) return normalizeCodecLabel(rawCodec)

    val nameToCheck = track.originalFilename.ifBlank { track.title }
    val ext = nameToCheck.substringAfterLast('.', "").trim()
    if (ext.isNotBlank() && ext.length in 2..6 && ext.all { it.isLetterOrDigit() }) {
        return normalizeCodecLabel(ext)
    }
    return null
}

private fun normalizeCodecLabel(rawCodec: String): String {
    val c = rawCodec.trim().lowercase()
    return when (c) {
        "subrip", "srt" -> "SRT"
        "ass" -> "ASS"
        "ssa" -> "SSA"
        "webvtt", "vtt" -> "VTT"
        "dvd_subtitle", "dvdsub" -> "SUB"
        "dvb_subtitle" -> "DVB"
        "hdmv_pgs_subtitle", "pgs" -> "PGS"
        "mov_text" -> "MOV_TEXT"
        "aac", "aac_latm" -> "AAC"
        "ac3", "ac-3", "a52" -> "AC3"
        "eac3", "e-ac-3", "ec3" -> "EAC3"
        "dts", "dca" -> "DTS"
        "dts-hd", "dtshd" -> "DTS-HD"
        "truehd" -> "TRUEHD"
        "mp3", "mp3float", "mpeg" -> "MP3"
        "vorbis" -> "VORBIS"
        "opus" -> "OPUS"
        "flac" -> "FLAC"
        "wav", "wave" -> "WAV"
        "m4a" -> "M4A"
        "ogg" -> "OGG"
        "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_u8", "pcm" -> "PCM"
        else -> c.uppercase()
    }
}

/**
 * Sanitizes track titles: Strips "#", dummy internal codes like "#1: A90Sec"
 */
fun cleanTrackTitle(rawTitle: String, fallback: String): String {
    if (rawTitle.isBlank()) return fallback

    var cleaned = rawTitle.trim()

    // Strip leading hash symbols like "#1:", "#2 -", "#"
    cleaned = cleaned.replace(Regex("^#\\s*\\d*\\s*[:\\-.]?\\s*"), "")

    // If it is just an internal debug string like A90Sec, B10Sec, Track1, etc.
    if (cleaned.matches(Regex("(?i)^[A-Z]?\\d+(sec|s)?$"))) {
        return fallback
    }

    if (cleaned.isBlank()) return fallback
    return cleaned
}

/**
 * Sanitizes chapter titles: Strips "#", dummy internal codes like "A90Sec"
 */
fun cleanChapterTitle(rawTitle: String, chapterNumber: Int): String {
    if (rawTitle.isBlank()) return "Chapter $chapterNumber"

    var cleaned = rawTitle.trim()

    // Strip leading hash symbols like "#1:", "#2 -", "#"
    cleaned = cleaned.replace(Regex("^#\\s*\\d*\\s*[:\\-.]?\\s*"), "")

    // Check if it matches raw internal token like "A90Sec", "A10Sec", "ch01"
    if (cleaned.matches(Regex("(?i)^[A-Z]?\\d+(sec|s)?$")) || cleaned.matches(Regex("(?i)^ch(apter)?_?\\d+$"))) {
        return "Chapter $chapterNumber"
    }

    if (cleaned.isBlank()) return "Chapter $chapterNumber"
    return cleaned
}

private fun formatPlaybackMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Renders high-fidelity interactive seekbar visual previews matching all 5 styles.
 */
@Composable
fun SeekbarPreviewTrack(
    style: SeekbarStyle,
    progressFraction: Float = 0.48f,
    activeColor: Color = Color(0xFF0284C7),
    inactiveColor: Color = Color(0xFFBAE6FD),
    thumbColor: Color = Color(0xFF0284C7),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val activeW = (w * progressFraction).coerceIn(0f, w)

        when (style) {
            SeekbarStyle.NORMAL -> {
                val trackThickness = 3.dp.toPx()
                // Inactive track line
                drawLine(
                    color = inactiveColor,
                    start = Offset(0f, centerY),
                    end = Offset(w, centerY),
                    strokeWidth = trackThickness
                )
                // Active track line
                if (activeW > 0) {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeW, centerY),
                        strokeWidth = trackThickness
                    )
                }
                // Thumb circle
                val thumbRadius = 6.5.dp.toPx()
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(activeW.coerceIn(thumbRadius, w - thumbRadius), centerY)
                )
            }
            SeekbarStyle.STANDARD -> {
                val barHeight = 6.dp.toPx()
                val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
                val topY = centerY - barHeight / 2f

                // Inactive rounded track
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, topY),
                    size = Size(w, barHeight),
                    cornerRadius = corner
                )
                // Active rounded track
                if (activeW > 0) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, topY),
                        size = Size(activeW, barHeight),
                        cornerRadius = corner
                    )
                }
                // Vertical dividing pin
                val pinW = 3.dp.toPx()
                val pinH = 15.dp.toPx()
                val pinX = (activeW - pinW / 2f).coerceIn(0f, w - pinW)
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(pinX, centerY - pinH / 2f),
                    size = Size(pinW, pinH),
                    cornerRadius = CornerRadius(pinW / 2f, pinW / 2f)
                )
            }
            SeekbarStyle.WAVY -> {
                // Inactive straight line
                val inactiveHeight = 4.dp.toPx()
                val topY = centerY - inactiveHeight / 2f
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(activeW, topY),
                    size = Size((w - activeW).coerceAtLeast(0f), inactiveHeight),
                    cornerRadius = CornerRadius(inactiveHeight / 2f, inactiveHeight / 2f)
                )

                // Active wavy sine line with dental teeth oscillating back and forth with progress
                if (activeW > 0) {
                    val wavePath = Path()
                    val waveLength = 26.dp.toPx()
                    val waveAmplitude = 4.dp.toPx()
                    // Dental wave teeth move back and forth directly in sync with progress
                    val wavePhaseShift = progressFraction * (4f * Math.PI.toFloat())
                    wavePath.moveTo(0f, centerY)
                    var x = 0f
                    val step = 1.5.dp.toPx()
                    while (x <= activeW) {
                        val angle = (x / waveLength) * (2f * Math.PI.toFloat()) - wavePhaseShift
                        val y = centerY + sin(angle) * waveAmplitude
                        wavePath.lineTo(x, y)
                        x += step
                    }
                    wavePath.lineTo(activeW, centerY)
                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Vertical dividing pin
                val pinW = 3.5.dp.toPx()
                val pinH = 16.dp.toPx()
                val pinX = (activeW - pinW / 2f).coerceIn(0f, w - pinW)
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(pinX, centerY - pinH / 2f),
                    size = Size(pinW, pinH),
                    cornerRadius = CornerRadius(pinW / 2f, pinW / 2f)
                )
            }
            SeekbarStyle.THICK -> {
                val barHeight = 13.dp.toPx()
                val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
                val topY = centerY - barHeight / 2f

                // Inactive thick pill
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, topY),
                    size = Size(w, barHeight),
                    cornerRadius = corner
                )
                // Active thick pill
                if (activeW > 0) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, topY),
                        size = Size(activeW, barHeight),
                        cornerRadius = corner
                    )
                }
                // Vertical dividing pin
                val pinW = 3.5.dp.toPx()
                val pinH = 18.dp.toPx()
                val pinX = (activeW - pinW / 2f).coerceIn(0f, w - pinW)
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(pinX, centerY - pinH / 2f),
                    size = Size(pinW, pinH),
                    cornerRadius = CornerRadius(pinW / 2f, pinW / 2f)
                )
            }
            SeekbarStyle.SLIM -> {
                val barHeight = 7.dp.toPx()
                val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
                val topY = centerY - barHeight / 2f

                // Inactive rounded pill
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, topY),
                    size = Size(w, barHeight),
                    cornerRadius = corner
                )
                // Active rounded pill
                if (activeW > 0) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, topY),
                        size = Size(activeW, barHeight),
                        cornerRadius = corner
                    )
                }
                // Vertical dividing pin
                val pinW = 3.dp.toPx()
                val pinH = 14.dp.toPx()
                val pinX = (activeW - pinW / 2f).coerceIn(0f, w - pinW)
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(pinX, centerY - pinH / 2f),
                    size = Size(pinW, pinH),
                    cornerRadius = CornerRadius(pinW / 2f, pinW / 2f)
                )
            }
        }
    }
}

/**
 * Reusable Card containing all 5 Seekbar Style selections with previews and radio buttons.
 */
@Composable
fun SeekbarStyleSelectionCard(
    currentStyle: SeekbarStyle,
    onSelectStyle: (SeekbarStyle) -> Unit,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) Color(0x3338BDF8) else Color(0xFFE2E8F0)
    val dividerColor = if (isDark) Color(0x26FFFFFF) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Continuous smooth back-and-forth seeking animation simulating active playback
    val infiniteTransition = rememberInfiniteTransition(label = "SeekbarPreviewProgressTransition")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SeekbarPreviewProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SeekbarStyle.entries.forEachIndexed { index, style ->
                val isSelected = currentStyle == style
                val isFirst = index == 0
                val isLast = index == SeekbarStyle.entries.size - 1

                val itemCornerShape = when {
                    isFirst && isLast -> RoundedCornerShape(16.dp)
                    isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    isLast -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RoundedCornerShape(0.dp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(itemCornerShape)
                        .clickable { onSelectStyle(style) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Style Label & Visual Seekbar Preview
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = style.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) ActiveHighlightColor else textPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Visual Preview Track animating back and forth
                        SeekbarPreviewTrack(
                            style = style,
                            progressFraction = animatedProgress,
                            activeColor = if (isSelected) ActiveHighlightColor else Color(0xFF0284C7),
                            inactiveColor = if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD),
                            thumbColor = if (isSelected) ActiveHighlightColor else Color(0xFF0284C7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }

                    // Right: Custom Radio Button Indicator matching user's design
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 2.2.dp else 1.8.dp,
                                color = if (isSelected) ActiveHighlightColor else textSecondary.copy(alpha = 0.6f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(ActiveHighlightColor)
                            )
                        }
                    }
                }

                if (index < SeekbarStyle.entries.size - 1) {
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Player Layout Selection Modal Sheet in the Video Player.
 * Matches user's exact design specifications:
 * - Header: Back arrow + "Player Layout"
 * - Sub-tabs: "Control Layout" and "Seekbar Style"
 * - Reorderable, configurable Control Layout editor
 * - 5 styled seekbar selections with crisp previews and radio buttons
 */
@Composable
fun PlayerLayoutSelectionPanel(
    currentSeekbarStyle: SeekbarStyle,
    playerLayoutConfig: PlayerLayoutConfig = PlayerLayoutConfig.default,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onSelectSeekbarStyle: (SeekbarStyle) -> Unit,
    onConfigChange: (PlayerLayoutConfig) -> Unit = {},
    onResetToDefault: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Control Layout, 1: Seekbar Style
    val isDark = glassBlurTransparency < 25f

    PlayerGlassModalSheet(
        panelKey = "player_layout",
        isLandscape = isLandscape,
        heightFraction = if (isLandscape) 0.88f else 0.78f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerCustomRow = {
            // Header Row: Left Back Arrow + Title "Player Layout"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    StyledIcon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to Player",
                        tint = NormalItemColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Player Layout",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NormalItemColor
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            // Sub-tabs: "Control Layout" & "Seekbar Style"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tab 0: Control Layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedSubTab = 0 }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Control Layout",
                        fontSize = 14.sp,
                        fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSubTab == 0) ActiveHighlightColor else NormalItemColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (selectedSubTab == 0) ActiveHighlightColor else Color.Transparent)
                    )
                }

                // Tab 1: Seekbar Style
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedSubTab = 1 }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Seekbar Style",
                        fontSize = 14.sp,
                        fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSubTab == 1) ActiveHighlightColor else NormalItemColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (selectedSubTab == 1) ActiveHighlightColor else Color.Transparent)
                    )
                }
            }

            if (selectedSubTab == 0) {
                // Tab 0: Control Layout Editor
                ControlLayoutEditor(
                    config = playerLayoutConfig,
                    onConfigChange = onConfigChange,
                    onResetToDefault = onResetToDefault,
                    isDark = isDark,
                    transparencyPercent = glassBlurTransparency
                )
            } else {
                // Tab 1: 5 Seekbar Style Cards
                SeekbarStyleSelectionCard(
                    currentStyle = currentSeekbarStyle,
                    onSelectStyle = { style ->
                        onSelectSeekbarStyle(style)
                    },
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
fun VideoQualitySelectionPanel(
    qualities: List<String>,
    selectedQuality: String?,
    onSelectQuality: (String) -> Unit,
    isLandscape: Boolean,
    glassBlurTransparency: Float = 85f,
    onDismissRequest: () -> Unit
) {
    PlayerGlassModalSheet(
        panelKey = "video_quality",
        isLandscape = isLandscape,
        heightFraction = 0.55f,
        glassBlurTransparency = glassBlurTransparency,
        onDismissRequest = onDismissRequest,
        headerStartContent = {
            Text(
                text = "Video Quality",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = NormalItemColor
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(qualities) { quality ->
                val isSelected = quality == selectedQuality
                MenuActionItem(
                    icon = Icons.Outlined.HighQuality,
                    title = quality,
                    subtitle = if (isSelected) "Active stream quality" else "",
                    badgeValue = if (isSelected) "✓" else null,
                    onClick = {
                        onSelectQuality(quality)
                        onDismissRequest()
                    }
                )
            }
        }
    }
}
