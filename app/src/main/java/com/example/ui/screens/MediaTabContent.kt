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
    modifier: Modifier = Modifier
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(manifest, key = { it.value }) { plugin ->
                    val installedVersion = installed.firstOrNull { it.value == plugin.value }?.version
                    val isBusy = busy.contains(plugin.value)
                    PluginRow(
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
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
// One downloaded plugin: category rows (thumbnails only for now — opening a title comes later).
// --------------------------------------------------------------------------------------------------------------------

@Composable
fun MediaPluginPage(
    plugin: MediaPluginInfo,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

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

    BackHandler(enabled = seeAll != null) { seeAll = null }

    val current = seeAll
    if (current != null) {
        MediaSeeAllGrid(
            plugin = plugin,
            entry = current,
            isDark = isDark,
            onBack = { seeAll = null },
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
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(cat.categories, key = { "c|" + it.title + "|" + it.filter }) { entry ->
                    MediaCategoryRow(plugin, entry, isDark, primaryText, secondaryText, surfaceColor, borderColor) { seeAll = entry }
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
                        MediaCategoryRow(plugin, entry, isDark, primaryText, secondaryText, surfaceColor, borderColor) { seeAll = entry }
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

/** Title + "See all" pill + a horizontal strip of thumbnails; the posts load when the row scrolls into view. */
@Composable
private fun MediaCategoryRow(
    plugin: MediaPluginInfo,
    entry: MediaCatalogEntry,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    surfaceColor: Color,
    borderColor: Color,
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

    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSeeAll)
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "See all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
                Spacer(modifier = Modifier.width(4.dp))
                StyledIcon(
                    drawableRes = R.drawable.lumora_arrow_right_solid,
                    contentDescription = null,
                    tint = primaryText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val list = posts
        when {
            list != null && list.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.link + "|" + it.title }) { post ->
                        MediaPostCard(post, isDark, primaryText, borderColor, Modifier.width(118.dp))
                    }
                }
            }
            list != null -> {
                Text(text = "Nothing found", fontSize = 12.5.sp, color = secondaryText, modifier = Modifier.padding(start = 18.dp, bottom = 8.dp))
            }
            error != null -> {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
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
                Box(modifier = Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSkyBlue, strokeWidth = 2.4.dp)
                }
            }
        }
    }
}

/** Poster-style thumbnail card. Tapping it does nothing yet (detail page comes in a later step). */
@Composable
private fun MediaPostCard(
    post: MediaPost,
    isDark: Boolean,
    primaryText: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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

/** "See all": the whole category as a grid of thumbnails, next pages load while scrolling. */
@Composable
private fun MediaSeeAllGrid(
    plugin: MediaPluginInfo,
    entry: MediaCatalogEntry,
    isDark: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val surfaceColor = if (isDark) DarkGlassSurface else LightGlassSurface
    val borderColor = if (isDark) DarkGlassBorder else LightGlassBorder

    val items = remember(plugin.value, entry.filter) { mutableStateListOf<MediaPost>() }
    var page by remember(plugin.value, entry.filter) { mutableStateOf(1) }
    var loading by remember(plugin.value, entry.filter) { mutableStateOf(false) }
    var ended by remember(plugin.value, entry.filter) { mutableStateOf(false) }
    var error by remember(plugin.value, entry.filter) { mutableStateOf<String?>(null) }

    suspend fun loadNext() {
        if (loading || ended) return
        loading = true
        error = null
        try {
            val fresh = MediaProviderRepository.loadPosts(context, plugin.value, entry.filter, page)
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
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PluginIconButton(R.drawable.lumora_arrow_left_solid, "Back", primaryText, onBack)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = entry.title,
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
                MediaPostCard(post, isDark, primaryText, borderColor)
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
