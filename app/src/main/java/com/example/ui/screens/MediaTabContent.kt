package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.HighResImage
import com.example.util.media.MediaHistoryStore
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.example.ui.components.LinkThumbnail
import com.example.ui.components.StyledIcon
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.DarkGlassBorder
import com.example.ui.theme.DarkGlassSurface
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightGlassBorder
import com.example.ui.theme.LightGlassSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import com.example.util.media.MediaCatalogEntry
import com.example.util.media.MediaLibraryStore
import com.example.util.media.MediaPlayback
import com.example.util.media.MediaSavedVideo
import com.example.util.media.MediaPluginInfo
import com.example.util.media.MediaPluginStore
import com.example.util.media.MediaPost
import com.example.util.media.MediaProviderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Network > "Media": the plugin manager. Paste a plugin repository URL (e.g. https://github.com/Zenda-Cross/vega-providers.git),
 * load its plugin list, then download the plugins you want. Every downloaded plugin gets its own tab next to "Media".
 */
@Composable
fun MediaPluginManager(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onOpenPlugin: (MediaPluginInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder
    val outerBg = if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFF0F172A).copy(alpha = 0.035f)

    val tick by MediaPluginStore.changes.collectAsState()
    val manifest = remember(tick) { MediaPluginStore.getManifest(context) }
    val installed = remember(tick) { MediaPluginStore.getInstalled(context) }

    var repoUrl by rememberSaveable { mutableStateOf(MediaPluginStore.getRepoUrl(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val busy = remember { mutableStateListOf<String>() }
    var bulkLabel by remember { mutableStateOf<String?>(null) }

    // Downloaded plugins are listed first (the one downloaded last on top); the list scrolls up when a download finishes.
    val ordered = remember(manifest, installed) {
        val installedAtById = installed.associate { it.value to it.installedAt }
        val (done, todo) = manifest.partition { installedAtById.containsKey(it.value) }
        done.sortedByDescending { installedAtById[it.value] ?: 0L } + todo
    }
    val listState = rememberLazyListState()
    var lastInstalledCount by remember { mutableStateOf(installed.size) }
    LaunchedEffect(installed.size) {
        if (installed.size > lastInstalledCount) listState.animateScrollToItem(0)
        lastInstalledCount = installed.size
    }

    fun loadRepo() {
        if (repoUrl.isBlank() || isLoading) return
        focusManager.clearFocus()
        isLoading = true
        errorText = null
        scope.launch {
            val result = MediaPluginStore.refreshManifest(context, repoUrl)
            result.onFailure { errorText = it.message ?: "Could not load the repository" }
            isLoading = false
        }
    }

    fun downloadOne(plugin: MediaPluginInfo) {
        if (busy.contains(plugin.value)) return
        busy.add(plugin.value)
        scope.launch {
            val r = MediaPluginStore.download(context, plugin)
            busy.remove(plugin.value)
            r.onFailure {
                Toast.makeText(context, plugin.name + ": " + (it.message ?: "download failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadAll() {
        if (bulkLabel != null) return
        val todo = manifest.filter { MediaPluginStore.getInstalledVersion(context, it.value) != it.version }
        if (todo.isEmpty()) return
        scope.launch {
            var done = 0
            var failed = 0
            val gate = Semaphore(3)
            val jobs = todo.map { plugin ->
                launch {
                    gate.withPermit {
                        busy.add(plugin.value)
                        val r = MediaPluginStore.download(context, plugin)
                        busy.remove(plugin.value)
                        if (r.isFailure) failed++
                        done++
                        bulkLabel = "Downloading $done/${todo.size}"
                    }
                }
            }
            bulkLabel = "Downloading 0/${todo.size}"
            jobs.forEach { it.join() }
            bulkLabel = null
            if (failed > 0) Toast.makeText(context, "$failed plugin(s) could not be downloaded", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // repository URL card (same look as the Local Network link field)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(outerBg)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledIcon(
                    drawableRes = R.drawable.lumora_cloud,
                    contentDescription = null,
                    tint = secondaryText.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (repoUrl.isEmpty()) {
                        Text(
                            text = "Provider plugins URL...",
                            fontSize = 15.sp,
                            color = secondaryText.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        singleLine = true,
                        textStyle = TextStyle(color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(AccentSkyBlue),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { loadRepo() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val text = clipboard.getText()?.text?.trim().orEmpty()
                            if (text.isNotEmpty()) repoUrl = text
                            else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    StyledIcon(
                        drawableRes = R.drawable.lumora_paste,
                        contentDescription = "Paste URL",
                        tint = primaryText.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            val canLoad = repoUrl.isNotBlank() && !isLoading
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFD5D9DD))
                    .then(
                        if (canLoad) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { loadRepo() } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canLoad || isLoading) Box(modifier = Modifier.fillMaxSize().background(AccentGradient))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.6.dp)
                } else {
                    StyledIcon(
                        drawableRes = R.drawable.lumora_download_minimalistic,
                        contentDescription = "Load plugins",
                        tint = if (canLoad) Color.White else secondaryText.copy(alpha = 0.75f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        com.example.ui.components.NetworkBypassCard(isDark = isDark)

        errorText?.let {
            Text(
                text = it,
                fontSize = 12.5.sp,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp)
            )
        }

        if (manifest.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = "Paste a plugin repository link (for example github.com/Zenda-Cross/vega-providers) and tap download to list its websites.",
                    fontSize = 13.sp,
                    color = secondaryText,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plugins (${manifest.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    modifier = Modifier.weight(1f)
                )
                val pending = manifest.count { p -> installed.firstOrNull { it.value == p.value }?.version != p.version }
                if (bulkLabel != null) {
                    Text(text = bulkLabel.orEmpty(), fontSize = 12.5.sp, color = secondaryText)
                } else if (pending > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(surfaceColor)
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { downloadAll() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Download all", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = primaryText)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(ordered, key = { it.value }) { plugin ->
                    val installedVersion = installed.firstOrNull { it.value == plugin.value }?.version
                    val isBusy = busy.contains(plugin.value)
                    PluginRow(
                        modifier = Modifier.animateItem(),
                        onOpen = if (installedVersion != null) { { onOpenPlugin(installed.first { it.value == plugin.value }) } } else null,
                        plugin = plugin,
                        installedVersion = installedVersion,
                        busy = isBusy,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        onDownload = { downloadOne(plugin) },
                        onRemove = { MediaPluginStore.remove(context, plugin.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    plugin: MediaPluginInfo,
    installedVersion: String?,
    busy: Boolean,
    primaryText: Color,
    secondaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    val isInstalled = installedVersion != null
    val hasUpdate = isInstalled && installedVersion != plugin.version
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .then(
                if (onOpen != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen
                ) else Modifier
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkThumbnail(
            thumbnail = plugin.icon.ifBlank { null },
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            fallback = {
                StyledIcon(
                    drawableRes = R.drawable.lumora_cloud,
                    contentDescription = null,
                    tint = secondaryText.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val status = when {
                hasUpdate -> "Update available · v${plugin.version}"
                isInstalled -> "Downloaded · v$installedVersion"
                else -> "v${plugin.version}" + if (plugin.type.isNotBlank()) " · ${plugin.type}" else ""
            }
            Text(
                text = status,
                fontSize = 12.sp,
                color = if (isInstalled && !hasUpdate) AccentSkyBlue else secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (busy) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentSkyBlue, strokeWidth = 2.2.dp)
            }
        } else {
            if (!isInstalled || hasUpdate) {
                PluginIconButton(R.drawable.lumora_download_minimalistic, if (hasUpdate) "Update" else "Download", primaryText.copy(alpha = 0.9f), onDownload)
            }
            if (isInstalled) {
                PluginIconButton(R.drawable.lumora_trash_bin_2, "Remove", secondaryText.copy(alpha = 0.9f), onRemove)
            }
        }
    }
}

@Composable
private fun PluginIconButton(drawableRes: Int, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        StyledIcon(drawableRes = drawableRes, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

// --------------------------------------------------------------------------------------------------------------------
// One downloaded plugin (provider) page: sliding trending banner, "Continue watching", then the provider's categories.
// --------------------------------------------------------------------------------------------------------------------

/** The category that feeds the sliding banner: a trending / popular / latest one when the plugin has it, else the first. */
private fun heroEntry(cat: MediaProviderRepository.Catalog): MediaCatalogEntry? {
    val all = cat.categories
    val keywords = listOf("trending", "popular", "latest", "new", "top", "recent")
    for (k in keywords) {
        all.firstOrNull { it.title.contains(k, ignoreCase = true) }?.let { return it }
    }
    return all.firstOrNull() ?: cat.genres.firstOrNull()
}

@Composable
fun MediaPluginPage(
    plugin: MediaPluginInfo,
    isDark: Boolean,
    onOpenPost: (MediaPost) -> Unit = {},
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val scope = rememberCoroutineScope()
    val savedTick by MediaLibraryStore.changes.collectAsState()
    val savedVideos = remember(savedTick, plugin.value) { MediaLibraryStore.watchLater(context, plugin.value) }
    val historyTick by MediaHistoryStore.changes.collectAsState()
    val watched = remember(historyTick, plugin.value) { MediaHistoryStore.recent(context, plugin.value) }
    var playingSaved by remember { mutableStateOf(false) }

    fun playSaved(v: MediaSavedVideo) {
        if (playingSaved) return
        playingSaved = true
        Toast.makeText(context, "Getting video link…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val result = MediaPlayback.resolve(context, plugin, v.link, v.type)
                val stream = result.getOrNull()
                if (stream == null) {
                    Toast.makeText(
                        context,
                        "Could not get this video: " + (result.exceptionOrNull()?.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val item = MediaPlayback.toVideoItem(context, plugin, v.title, v.poster, stream)
                    onPlayVideo(item, listOf(item))
                }
            } finally {
                playingSaved = false
            }
        }
    }

    var catalog by remember(plugin.value, plugin.version) { mutableStateOf<MediaProviderRepository.Catalog?>(null) }
    var catalogError by remember(plugin.value, plugin.version) { mutableStateOf<String?>(null) }
    var reloadKey by remember(plugin.value) { mutableStateOf(0) }
    var seeAll by remember(plugin.value) { mutableStateOf<MediaCatalogEntry?>(null) }

    LaunchedEffect(plugin.value, plugin.version, reloadKey) {
        catalog = null
        catalogError = null
        try {
            catalog = MediaProviderRepository.loadCatalog(context, plugin.value, force = reloadKey > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            catalogError = t.message ?: "Could not load this plugin"
        }
    }

    // top search bar of the app: results of this provider replace the page while something is typed
    val typed = searchQuery.trim()
    var activeQuery by remember(plugin.value) { mutableStateOf("") }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) delay(500L)
        activeQuery = typed
    }
    if (activeQuery.isNotEmpty() && typed.isNotEmpty()) {
        MediaPostGrid(
            identity = plugin.value + "|search|" + activeQuery,
            title = "Results for \"$activeQuery\"",
            isDark = isDark,
            onBack = null,
            onOpenPost = onOpenPost,
            loader = { page -> MediaProviderRepository.searchPosts(context, plugin.value, activeQuery, page) },
            modifier = modifier
        )
        return
    }

    BackHandler(enabled = seeAll != null) { seeAll = null }

    val current = seeAll
    if (current != null) {
        MediaPostGrid(
            identity = plugin.value + "|" + current.filter,
            title = current.title,
            isDark = isDark,
            onBack = { seeAll = null },
            onOpenPost = onOpenPost,
            loader = { page -> MediaProviderRepository.loadPosts(context, plugin.value, current.filter, page) },
            modifier = modifier
        )
        return
    }

    val cat = catalog
    when {
        catalogError != null -> {
            Column(
                modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = catalogError.orEmpty(), fontSize = 13.sp, color = secondaryText)
                Spacer(modifier = Modifier.height(14.dp))
                RetryPill(primaryText, surfaceColor, borderColor) { reloadKey++ }
            }
        }
        cat == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 40.dp).size(28.dp),
                    color = AccentSkyBlue,
                    strokeWidth = 2.6.dp
                )
            }
        }
        cat.categories.isEmpty() && cat.genres.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(text = "This plugin has no categories", fontSize = 13.sp, color = secondaryText, modifier = Modifier.padding(top = 32.dp))
            }
        }
        else -> {
            val hero = remember(cat) { heroEntry(cat) }
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
            ) {
                if (hero != null) {
                    item(key = "hero") {
                        MediaHeroSlider(plugin, hero, isDark, borderColor, onOpenPost)
                    }
                }
                if (watched.isNotEmpty()) {
                    item(key = "continue-watching") {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            MediaSectionHeader(
                                title = "Continue watching",
                                iconRes = R.drawable.lumora_recent,
                                primaryText = primaryText,
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                onSeeAll = null
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            MediaFocusRow(
                                posts = watched.map { MediaPost(it.title, it.link, it.image, null) },
                                isDark = isDark,
                                primaryText = primaryText,
                                borderColor = borderColor,
                                onOpenPost = onOpenPost,
                                onRemove = { MediaHistoryStore.remove(context, plugin.value, it.link) }
                            )
                        }
                    }
                }
                if (savedVideos.isNotEmpty()) {
                    item(key = "watch-later") {
                        MediaWatchLaterRow(
                            videos = savedVideos,
                            isDark = isDark,
                            primaryText = primaryText,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            onPlay = { playSaved(it) },
                            onRemove = { MediaLibraryStore.remove(context, it.pluginId, it.link) }
                        )
                    }
                }
                items(cat.categories, key = { "c|" + it.title + "|" + it.filter }) { entry ->
                    MediaCategoryRow(plugin, entry, isDark, primaryText, secondaryText, surfaceColor, borderColor, R.drawable.lumora_video_library, onOpenPost) { seeAll = entry }
                }
                if (cat.genres.isNotEmpty()) {
                    item(key = "genres-label") {
                        Text(
                            text = "Genres",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryText,
                            modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 2.dp)
                        )
                    }
                    items(cat.genres, key = { "g|" + it.title + "|" + it.filter }) { entry ->
                        MediaCategoryRow(plugin, entry, isDark, primaryText, secondaryText, surfaceColor, borderColor, R.drawable.lumora_list, onOpenPost) { seeAll = entry }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryPill(primaryText: Color, surfaceColor: Color, borderColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text = "Retry", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = primaryText)
    }
}

/**
 * Sliding banner at the top of a provider page: the trending / latest titles of the provider (at most 6) change by
 * themselves every few seconds; the neighbours peek in and the page in the middle is the biggest.
 */
@Composable
private fun MediaHeroSlider(
    plugin: MediaPluginInfo,
    entry: MediaCatalogEntry,
    isDark: Boolean,
    borderColor: Color,
    onOpenPost: (MediaPost) -> Unit
) {
    val context = LocalContext.current
    var posts by remember(plugin.value, entry.filter) { mutableStateOf<List<MediaPost>?>(null) }
    LaunchedEffect(plugin.value, entry.filter) {
        posts = try {
            MediaProviderRepository.loadPosts(context, plugin.value, entry.filter, 1)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }
    val loaded = posts
    val slides = remember(loaded) {
        val all = loaded.orEmpty()
        all.filter { it.image.isNotBlank() }.ifEmpty { all }.take(6)
    }
    val placeholderBg = if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0)
    if (loaded == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(210.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSkyBlue, strokeWidth = 2.4.dp)
        }
        return
    }
    if (slides.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { slides.size })
    LaunchedEffect(slides.size) {
        if (slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(4500L)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % slides.size
                pagerState.animateScrollToPage(next, animationSpec = tween(700, easing = FastOutSlowInEasing))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 26.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val post = slides[page]
            val distance = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .graphicsLayer {
                        val s = 1f - 0.07f * distance
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.35f * distance
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(placeholderBg)
                    .border(1.dp, borderColor, RoundedCornerShape(26.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenPost(post) }
            ) {
                HighResImage(
                    url = post.image.ifBlank { null },
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    placeholder = {
                        StyledIcon(
                            drawableRes = R.drawable.lumora_video,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.86f)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = entry.title.uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentGradient)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = post.title,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (slides.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.indices.forEach { i ->
                    val active = pagerState.currentPage == i
                    val w by animateFloatAsState(if (active) 22f else 7f, tween(300), label = "heroDot")
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(7.dp)
                            .width(w.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) AccentGradient else Brush.linearGradient(listOf(borderColor, borderColor)))
                    )
                }
            }
        }
    }
}

/** Section title like the reference design: a small rounded icon tile + upper-case title, "See all" with a gradient line below. */
@Composable
private fun MediaSectionHeader(
    title: String,
    iconRes: Int,
    primaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onSeeAll: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(surfaceColor)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            StyledIcon(
                drawableRes = iconRes,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title.uppercase(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            // no round background any more: just the text and a gradient line under it
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSeeAll)
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "See all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Spacer(modifier = Modifier.width(4.dp))
                    StyledIcon(
                        drawableRes = R.drawable.lumora_arrow_right_solid,
                        contentDescription = null,
                        tint = primaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(AccentGradient)
                )
            }
        }
    }
}

/**
 * Horizontal strip of posters. Like the reference design the cards stand a little uneven (every second one is raised),
 * the cards near the middle of the screen are bigger and straight, the ones towards the edges are smaller and tilt
 * outwards, so the strip sways while it is dragged; a touched card bounces a little.
 */
@Composable
private fun MediaFocusRow(
    posts: List<MediaPost>,
    isDark: Boolean,
    primaryText: Color,
    borderColor: Color,
    onOpenPost: (MediaPost) -> Unit,
    onRemove: ((MediaPost) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(posts.size, key = { posts[it].link + "|" + posts[it].title }) { index ->
            val post = posts[index]
            MediaPosterCard(
                post = post,
                index = index,
                listState = listState,
                isDark = isDark,
                primaryText = primaryText,
                borderColor = borderColor,
                onClick = { onOpenPost(post) },
                onRemove = onRemove?.let { remover -> { remover(post) } }
            )
        }
    }
}

@Composable
private fun MediaPosterCard(
    post: MediaPost,
    index: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isDark: Boolean,
    primaryText: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "posterPress"
    )
    Column(
        modifier = Modifier
            .width(126.dp)
            .graphicsLayer {
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                val raise = if (index % 2 == 1) -10.dp.toPx() else 0f
                if (item != null && info.viewportSize.width > 0) {
                    val half = info.viewportSize.width / 2f
                    val frac = (((item.offset + item.size / 2f) - half) / half).coerceIn(-1f, 1f)
                    val near = 1f - abs(frac)
                    val s = (0.9f + 0.1f * near) * press
                    scaleX = s
                    scaleY = s
                    rotationZ = frac * 5f
                    translationY = raise
                } else {
                    scaleX = 0.9f * press
                    scaleY = 0.9f * press
                    translationY = raise
                }
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0))
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
        ) {
            LinkThumbnail(
                thumbnail = post.image.ifBlank { null },
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    StyledIcon(
                        drawableRes = R.drawable.lumora_video,
                        contentDescription = null,
                        tint = primaryText.copy(alpha = 0.35f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 12.sp, color = Color.White)
                }
            }
            post.tag?.let { tag ->
                Text(
                    text = tag,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = post.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Title + "See all" + a strip of posters; the posts load when the row scrolls into view. */
@Composable
private fun MediaCategoryRow(
    plugin: MediaPluginInfo,
    entry: MediaCatalogEntry,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    iconRes: Int,
    onOpenPost: (MediaPost) -> Unit,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    var posts by remember(plugin.value, entry.filter) { mutableStateOf<List<MediaPost>?>(null) }
    var error by remember(plugin.value, entry.filter) { mutableStateOf<String?>(null) }
    var attempt by remember(plugin.value, entry.filter) { mutableStateOf(0) }

    LaunchedEffect(plugin.value, entry.filter, attempt) {
        if (posts != null) return@LaunchedEffect
        error = null
        try {
            posts = MediaProviderRepository.loadPosts(context, plugin.value, entry.filter, 1, force = attempt > 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = t.message ?: "Could not load"
        }
    }

    // a category that fails to load (or has nothing) is not shown at all
    if (error != null || (posts != null && posts?.isEmpty() == true)) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        MediaSectionHeader(
            title = entry.title,
            iconRes = iconRes,
            primaryText = primaryText,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            onSeeAll = onSeeAll
        )
        val list = posts
        when {
            list != null && list.isNotEmpty() -> {
                MediaFocusRow(
                    posts = list,
                    isDark = isDark,
                    primaryText = primaryText,
                    borderColor = borderColor,
                    onOpenPost = onOpenPost
                )
            }
            list != null -> {
                Text(text = "Nothing found", fontSize = 12.5.sp, color = secondaryText, modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 8.dp))
            }
            error != null -> {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error.orEmpty(),
                        fontSize = 12.sp,
                        color = secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    RetryPill(primaryText, surfaceColor, borderColor) { attempt++ }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSkyBlue, strokeWidth = 2.4.dp)
                }
            }
        }
    }
}

/** Plain poster card of the grids ("See all" / search results). */
@Composable
private fun MediaPostCard(
    post: MediaPost,
    isDark: Boolean,
    primaryText: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            } else Modifier
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark) Color(0xFF111827) else Color(0xFFE2E8F0))
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
        ) {
            LinkThumbnail(
                thumbnail = post.image.ifBlank { null },
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    StyledIcon(
                        drawableRes = R.drawable.lumora_video,
                        contentDescription = null,
                        tint = primaryText.copy(alpha = 0.35f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            )
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✕", fontSize = 12.sp, color = Color.White)
                }
            }
            post.tag?.let { tag ->
                Text(
                    text = tag,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = post.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A grid of thumbnails; the next pages load while scrolling. Used for "See all" of a category ([onBack] set) and for
 * the search results of a provider ([onBack] null: the app's top search bar is the way out).
 */
@Composable
private fun MediaPostGrid(
    identity: String,
    title: String,
    isDark: Boolean,
    onBack: (() -> Unit)?,
    onOpenPost: (MediaPost) -> Unit,
    loader: suspend (page: Int) -> List<MediaPost>,
    modifier: Modifier = Modifier
) {
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val items = remember(identity) { mutableStateListOf<MediaPost>() }
    var page by remember(identity) { mutableStateOf(1) }
    var loading by remember(identity) { mutableStateOf(false) }
    var ended by remember(identity) { mutableStateOf(false) }
    var error by remember(identity) { mutableStateOf<String?>(null) }

    suspend fun loadNext() {
        if (loading || ended) return
        loading = true
        error = null
        try {
            val fresh = loader(page)
            val known = items.map { it.link + "|" + it.title }.toHashSet()
            val added = fresh.filter { known.add(it.link + "|" + it.title) }
            if (added.isEmpty()) ended = true else { items.addAll(added); page++ }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = t.message ?: "Could not load"
            ended = items.isNotEmpty()
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 18.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                PluginIconButton(R.drawable.lumora_arrow_left_solid, "Back", primaryText, onBack)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.link + "|" + it.title }) { post ->
                MediaPostCard(post, isDark, primaryText, borderColor, onClick = { onOpenPost(post) })
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                // Composed when the end of the grid is reached -> loads the next page.
                LaunchedEffect(page, ended, error) {
                    if (!ended && error == null) loadNext()
                }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSkyBlue, strokeWidth = 2.4.dp)
                        error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = error.orEmpty(), fontSize = 12.sp, color = secondaryText, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                            Spacer(modifier = Modifier.width(10.dp))
                            RetryPill(primaryText, surfaceColor, borderColor) { error = null; ended = false }
                        }
                        ended && items.isEmpty() -> Text(text = "Nothing found", fontSize = 12.5.sp, color = secondaryText)
                        else -> Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}

/** "Watch later" category: the movies / episodes saved from a detail page. Tap plays, the cross removes. */
@Composable
private fun MediaWatchLaterRow(
    videos: List<MediaSavedVideo>,
    isDark: Boolean,
    primaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
    onPlay: (MediaSavedVideo) -> Unit,
    onRemove: (MediaSavedVideo) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        MediaSectionHeader(
            title = "Watch later",
            iconRes = R.drawable.lumora_watch_later,
            primaryText = primaryText,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            onSeeAll = null
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { "wl|" + it.link }) { v ->
                MediaPostCard(
                    post = MediaPost(v.title, v.link, v.poster, null),
                    isDark = isDark,
                    primaryText = primaryText,
                    borderColor = borderColor,
                    modifier = Modifier.width(118.dp),
                    onClick = { onPlay(v) },
                    onRemove = { onRemove(v) }
                )
            }
        }
    }
}
