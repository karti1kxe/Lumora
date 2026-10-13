package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentPink
import com.example.ui.theme.AccentSkyBlue
import com.example.util.OnlineMediaLibrary
import com.example.util.OnlineVideoQueue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.screens.VideoItem
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextSecondary
import com.example.util.OnlineChannelResult
import com.example.util.OnlineMusicService
import com.example.util.OnlineTrackResult
import com.example.util.OnlineVideoRecommender
import com.example.util.OnlineVideoService
import com.example.util.cachedBestLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.util.Locale

/**
 * Process-wide state of the Online Video folder. It lives outside composition on purpose: when the user opens a video
 * and comes back, the SAME recommendations (and scroll position) are shown — the feed only changes when the user
 * pulls down to refresh or searches.
 */
object OnlineVideoFeedState {
    var isOpen by mutableStateOf(false)
    var results by mutableStateOf<List<OnlineTrackResult>>(emptyList())
    var channelResults by mutableStateOf<List<OnlineChannelResult>>(emptyList())
    var isSearching by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var refreshRequest by mutableIntStateOf(0)
    /** True once the user changed Sort & View sorting; until then the feed keeps its recommendation order. */
    var sortTouched by mutableStateOf(false)
    /** "Watch later" videos: always pinned at the very top of the feed. */
    var watchLater by mutableStateOf<List<OnlineTrackResult>>(emptyList())
    /** Bumped whenever the "Not interested" list changes so the visible list is recomputed. */
    var hiddenVersion by mutableIntStateOf(0)
    var initialized = false
    var lastQuery: String = ""
    var extractor: SearchExtractor? = null
    var nextPage: Page? = null
    var hasNextPage: Boolean = false
    val usedQueries = mutableSetOf<String>()
    var token = 0
    var listIndex = 0
    var listOffset = 0
    var gridIndex = 0
    var gridOffset = 0
}

private fun formatViews(count: Long): String {
    if (count <= 0L) return ""
    val t = when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
    return "$t views"
}

/** Converts a search/feed result into the app's regular VideoItem so the existing cards and player are reused. */
fun OnlineTrackResult.toOnlineVideoItem(): VideoItem = VideoItem(
    id = -(kotlin.math.abs(videoUrl.hashCode().toLong())) - 1L,
    uri = Uri.parse(videoUrl),
    displayName = title,
    path = "${OnlineVideoService.ONLINE_VIDEO_PATH_PREFIX}$videoUrl",
    sizeBytes = 0L,
    durationMs = durationSec.coerceAtLeast(0L) * 1000L,
    dateModified = uploadDateEpochMs ?: 0L,
    resolution = OnlineVideoService.cachedBestLabel(videoUrl) ?: "",
    isOnline = true,
    thumbnailUrl = thumbnailUrl,
    channelName = artist,
    viewCountText = formatViews(viewCount).ifEmpty { null }
)

/**
 * Online Video browsing panel (inside the "Online Video" folder): recommendation feed when the search box is empty,
 * video + channel search otherwise. Same architecture as [OnlineMusicPanel], but renders the regular video cards.
 */
@Composable
fun OnlineVideoPanel(
    isDark: Boolean,
    searchQuery: String,
    layoutMode: LayoutMode,
    visibleFields: VisibleFields,
    sortField: SortField,
    sortDirection: SortDirection,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onOpenChannel: (OnlineChannelResult) -> Unit,
    modifier: Modifier = Modifier,
    glassBlurTransparency: Float = 85f
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = OnlineVideoFeedState
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    val listState = rememberLazyListState(st.listIndex, st.listOffset)
    val gridState = rememberLazyGridState(st.gridIndex, st.gridOffset)
    var job by remember { mutableStateOf<Job?>(null) }
    val isFeedMode = searchQuery.isBlank()

    // ---- swipe (right -> left) quick actions: nothing is selected, the bar just appears at the bottom ----
    var actionTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    var lastActionTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    if (actionTarget != null) lastActionTarget = actionTarget
    var playlistTarget by remember { mutableStateOf<OnlineTrackResult?>(null) }
    LaunchedEffect(Unit) {
        OnlineMediaLibrary.attach(context)
        st.watchLater = withContext(Dispatchers.IO) { OnlineMediaLibrary.watchLater(context, OnlineMediaLibrary.KIND_VIDEO) }
    }
    val hidden = remember(st.hiddenVersion) {
        OnlineMediaLibrary.notInterestedSet(context, OnlineMediaLibrary.KIND_VIDEO)
    }
    BackHandler(enabled = actionTarget != null) { actionTarget = null }
    LaunchedEffect(listState.isScrollInProgress, gridState.isScrollInProgress) {
        if (listState.isScrollInProgress || gridState.isScrollInProgress) actionTarget = null
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> st.listIndex = i; st.listOffset = o }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> st.gridIndex = i; st.gridOffset = o }
    }
    DisposableEffect(Unit) {
        onDispose {
            // a load that was cut short by leaving the screen must not leave the spinner stuck
            st.isSearching = false
            st.isLoadingMore = false
            st.isRefreshing = false
        }
    }

    fun loadFeed(fromRefresh: Boolean) {
        job?.cancel()
        st.token++
        val token = st.token
        st.isSearching = st.results.isEmpty()
        st.isRefreshing = fromRefresh
        st.isLoadingMore = false
        st.channelResults = emptyList()
        st.extractor = null
        st.nextPage = null
        st.hasNextPage = false
        st.lastQuery = ""
        st.usedQueries.clear()
        job = scope.launch(Dispatchers.IO) {
            val batch = try {
                kotlinx.coroutines.withTimeoutOrNull(20000L) {
                    OnlineVideoRecommender.fetchFeedBatch(context, emptySet(), st.usedQueries)
                } ?: emptyList()
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (token == st.token) {
                    if (batch.isNotEmpty() || st.results.isEmpty()) {
                        st.results = batch
                        st.listIndex = 0; st.listOffset = 0; st.gridIndex = 0; st.gridOffset = 0
                    }
                    st.initialized = batch.isNotEmpty()
                    st.isSearching = false
                    OnlineVideoRecommender.recordShown(context, batch.map { it.videoUrl })
                }
                // the pull-to-refresh animation must always end, whatever happened above
                st.isRefreshing = false
            }
        }
    }

    fun loadMoreFeed() {
        if (st.isLoadingMore || st.isSearching) return
        val token = st.token
        val shown = st.results.map { it.videoUrl }.toSet()
        st.isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            val batch = try {
                OnlineVideoRecommender.fetchFeedBatch(context, shown, st.usedQueries)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (token == st.token && batch.isNotEmpty()) {
                    val already = st.results.map { it.videoUrl }.toSet()
                    val fresh = batch.filter { it.videoUrl !in already }
                    st.results = st.results + fresh
                    OnlineVideoRecommender.recordShown(context, fresh.map { it.videoUrl })
                }
                st.isLoadingMore = false
            }
        }
    }

    fun executeSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) {
            loadFeed(false)
            return
        }
        job?.cancel()
        st.token++
        val token = st.token
        st.isSearching = true
        job = scope.launch(Dispatchers.IO) {
            try {
                val (extractor, page) = OnlineMusicService.searchMusicPage(trimmed)
                withContext(Dispatchers.Main) {
                    if (token == st.token) {
                        st.extractor = extractor
                        st.nextPage = page.nextPage
                        st.hasNextPage = page.hasNextPage
                        st.results = page.items
                        st.channelResults = page.channelResults
                        st.lastQuery = trimmed
                        st.initialized = true
                        st.isSearching = false
                    }
                    st.isRefreshing = false
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    if (token == st.token) { st.results = emptyList(); st.channelResults = emptyList(); st.isSearching = false }
                    st.isRefreshing = false
                }
            }
        }
    }

    fun loadMoreSearch() {
        val ex = st.extractor ?: return
        val next = st.nextPage ?: return
        if (!st.hasNextPage || st.isLoadingMore || st.isSearching) return
        val token = st.token
        st.isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            val p = OnlineMusicService.fetchNextMusicPage(ex, next)
            withContext(Dispatchers.Main) {
                if (token == st.token) {
                    val existing = st.results.map { it.videoUrl }.toSet()
                    st.results = st.results + p.items.filter { it.videoUrl !in existing }
                    st.nextPage = p.nextPage
                    st.hasNextPage = p.hasNextPage
                }
                st.isLoadingMore = false
            }
        }
    }

    // First composition after (re)opening: keep what is already loaded (e.g. coming back from the player).
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(searchQuery) {
        if (firstRun) {
            firstRun = false
            if (!st.initialized) executeSearch(searchQuery)
            return@LaunchedEffect
        }
        if (searchQuery.isNotBlank()) delay(350)
        executeSearch(searchQuery)
    }

    // Manual pull-to-refresh (from the Home screen's pull gesture) -> brand-new recommendations.
    var handledRefresh by remember { mutableIntStateOf(st.refreshRequest) }
    LaunchedEffect(st.refreshRequest) {
        if (st.refreshRequest != handledRefresh) {
            handledRefresh = st.refreshRequest
            if (isFeedMode) loadFeed(true) else executeSearch(searchQuery)
        }
    }

    // Safety net: the pull-to-refresh indicator can never stay on screen for more than a few seconds.
    LaunchedEffect(st.isRefreshing) {
        if (st.isRefreshing) {
            delay(25000L)
            st.isRefreshing = false
        }
    }

    // Unlimited scrolling
    val nearEnd by remember(layoutMode) {
        derivedStateOf {
            val total: Int
            val last: Int
            if (layoutMode == LayoutMode.GRID) {
                total = gridState.layoutInfo.totalItemsCount
                last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            } else {
                total = listState.layoutInfo.totalItemsCount
                last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(nearEnd, st.results.size) {
        if (nearEnd && !st.isLoadingMore && !st.isSearching) {
            if (st.lastQuery.isBlank()) loadMoreFeed() else loadMoreSearch()
        }
    }

    val sorted = remember(st.results, sortField, sortDirection, isFeedMode, st.sortTouched) {
        if (isFeedMode && !st.sortTouched) st.results else when (sortField) {
            SortField.TITLE ->
                if (sortDirection == SortDirection.ASCENDING) st.results.sortedBy { it.title.lowercase(Locale.ROOT) }
                else st.results.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SortField.DATE ->
                if (sortDirection == SortDirection.ASCENDING) st.results.sortedBy { it.uploadDateEpochMs ?: 0L }
                else st.results.sortedByDescending { it.uploadDateEpochMs ?: 0L }
            else -> st.results
        }
    }
    // Watch-later videos are pinned above everything else in the recommendation feed; hidden ones never show.
    val shown = remember(sorted, isFeedMode, st.watchLater, hidden) {
        val pinned = if (isFeedMode) st.watchLater.filter { it.videoUrl !in hidden } else emptyList()
        val pinnedUrls = pinned.map { it.videoUrl }.toSet()
        pinned + sorted.filter { it.videoUrl !in pinnedUrls && it.videoUrl !in hidden }
    }
    val cardFields = visibleFields.copy(showExtension = false, showSize = false, showSubtitleIndicator = false)

    fun play(r: OnlineTrackResult) {
        OnlineVideoRecommender.recordOpened(context, r.videoUrl, r.artist)
        val tappedPath = "${OnlineVideoService.ONLINE_VIDEO_PATH_PREFIX}${r.videoUrl}"
        var all = shown.map { it.toOnlineVideoItem() }
        val item = all.firstOrNull { it.path == tappedPath } ?: r.toOnlineVideoItem()
        // Videos queued with "Play next / Play last" are spliced in right after the one that was tapped.
        val queued = OnlineVideoQueue.items.filter { it.videoUrl != r.videoUrl }.map { it.toOnlineVideoItem() }
        if (queued.isNotEmpty()) {
            val queuedPaths = queued.map { it.path }.toSet()
            val rest = all.filter { it.path !in queuedPaths }
            val idx = rest.indexOfFirst { it.path == tappedPath }
            all = if (idx >= 0) rest.take(idx + 1) + queued + rest.drop(idx + 1) else listOf(item) + queued
        }
        OnlineVideoQueue.clear()
        onPlayVideo(item, all)
    }

    fun playQueue() {
        val q = OnlineVideoQueue.items.toList()
        if (q.isEmpty()) return
        OnlineVideoQueue.clear()
        OnlineVideoRecommender.recordOpened(context, q.first().videoUrl, q.first().artist)
        val list = q.map { it.toOnlineVideoItem() }
        onPlayVideo(list.first(), list)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            st.isSearching && st.results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LumoraExpressiveLoadingIndicator(size = 54.dp, isDark = isDark)
                }
            }
            shown.isEmpty() && st.channelResults.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No online videos found for \"$searchQuery\". Check your internet connection or try a different search."
                        else "No online videos found. Check your internet connection and pull down to refresh.",
                        color = secondaryText,
                        modifier = Modifier.padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            layoutMode == LayoutMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(st.channelResults, key = { "ov_channel_${it.channelUrl}" }, span = { GridItemSpan(2) }) { ch ->
                        OnlineChannelResultCard(channel = ch, isDark = isDark, onClick = {
                            OnlineVideoRecommender.recordChannelSearch(context, ch.name)
                            onOpenChannel(ch)
                        })
                    }
                    items(shown, key = { "ov_g_${it.videoUrl}" }) { r ->
                        val v = remember(r) { r.toOnlineVideoItem() }
                        Box(Modifier.swipeLeftForActions { actionTarget = r }) {
                            VideoItemGridCard(video = v, isDark = isDark, visibleFields = cardFields, onClick = { actionTarget = null; play(r) })
                        }
                    }
                    if (st.isLoadingMore) {
                        item(span = { GridItemSpan(2) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
                            }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(st.channelResults, key = { "ov_channel_${it.channelUrl}" }) { ch ->
                        OnlineChannelResultCard(channel = ch, isDark = isDark, onClick = {
                            OnlineVideoRecommender.recordChannelSearch(context, ch.name)
                            onOpenChannel(ch)
                        })
                    }
                    items(shown, key = { "ov_l_${it.videoUrl}" }) { r ->
                        val v = remember(r) { r.toOnlineVideoItem() }
                        Box(Modifier.swipeLeftForActions { actionTarget = r }) {
                            VideoItemCard(video = v, isDark = isDark, visibleFields = cardFields, onClick = { actionTarget = null; play(r) })
                        }
                    }
                    if (st.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
                            }
                        }
                    }
                }
            }
        }

        // "Play queue" button while videos are waiting in the queue
        if (OnlineVideoQueue.items.isNotEmpty() && actionTarget == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp)
                    .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = AccentSkyBlue.copy(alpha = 0.4f), spotColor = AccentPink.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(22.dp))
                    .background(AccentGradient)
                    .clickable { playQueue() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledIcon(drawableRes = R.drawable.lumora_play_solid, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play queue (${OnlineVideoQueue.items.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Swipe toolbar
        AnimatedVisibility(
            visible = actionTarget != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            val t = actionTarget ?: lastActionTarget
            if (t != null) {
                val kind = OnlineMediaLibrary.KIND_VIDEO
                OnlineQuickActionBar(
                    isDark = isDark,
                    alphaRatio = (glassBlurTransparency / 100f).coerceIn(0.15f, 1.0f),
                    isSavedForLater = st.watchLater.any { it.videoUrl == t.videoUrl },
                    onPlayNext = {
                        OnlineMediaLibrary.rememberMeta(context, kind, t)
                        OnlineVideoQueue.addNext(t)
                        Toast.makeText(context, "Will play next: ${t.title}", Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    },
                    onPlayLast = {
                        OnlineMediaLibrary.rememberMeta(context, kind, t)
                        OnlineVideoQueue.addLast(t)
                        Toast.makeText(context, "Added to the end of the queue", Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    },
                    onSaveToPlaylist = {
                        OnlineMediaLibrary.rememberMeta(context, kind, t)
                        playlistTarget = t
                        actionTarget = null
                    },
                    onWatchLater = {
                        val saved = OnlineMediaLibrary.toggleWatchLater(context, kind, t)
                        st.watchLater = OnlineMediaLibrary.watchLater(context, kind)
                        if (saved) { st.listIndex = 0; st.listOffset = 0; st.gridIndex = 0; st.gridOffset = 0 }
                        Toast.makeText(context, if (saved) "Added to Watch later" else "Removed from Watch later", Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    },
                    onNotInterested = {
                        OnlineMediaLibrary.markNotInterested(context, kind, t.videoUrl)
                        OnlineVideoQueue.remove(t.videoUrl)
                        st.watchLater = OnlineMediaLibrary.watchLater(context, kind)
                        st.hiddenVersion++
                        Toast.makeText(context, "Okay, you will see fewer videos like this", Toast.LENGTH_SHORT).show()
                        actionTarget = null
                    },
                    onShare = {
                        shareOnlineLink(context, t.title, t.videoUrl)
                        actionTarget = null
                    }
                )
            }
        }

        playlistTarget?.let { t ->
            AddToPlaylistDialog(
                selectedVideos = listOf(t.toOnlineVideoItem()),
                isDark = isDark,
                glassBlurTransparency = glassBlurTransparency,
                onDismiss = { playlistTarget = null },
                onPlaylistUpdated = { playlistTarget = null }
            )
        }
    }
}
