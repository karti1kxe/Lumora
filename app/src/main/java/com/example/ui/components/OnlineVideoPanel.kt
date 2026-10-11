package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.VideoItem
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextSecondary
import com.example.util.ONLINE_VIDEO_PATH_PREFIX
import com.example.util.OnlineChannelDetail
import com.example.util.OnlineChannelResult
import com.example.util.OnlineChannelSection
import com.example.util.OnlineMusicService
import com.example.util.OnlineTrackResult
import com.example.util.OnlineVideoRecommender
import com.example.util.OnlineVideoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.util.Locale

private fun formatViewCount(count: Long): String? {
    if (count <= 0L) return null
    val text = when {
        count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
    return "$text views"
}

/** Turns one online search/feed result into the normal VideoItem every video card in the app already understands. */
fun OnlineTrackResult.toOnlineVideoItem(): VideoItem {
    val maxHeight = OnlineVideoService.cachedMaxHeight(videoUrl)
    val badge = if (maxHeight != null && maxHeight > 0) {
        OnlineVideoService.resolutionBadge(maxHeight)
    } else {
        OnlineVideoService.resolutionFromTitle(title)
    }
    return VideoItem(
        id = -(kotlin.math.abs(videoUrl.hashCode().toLong())) - 1L,
        uri = Uri.EMPTY,
        displayName = title,
        path = "$ONLINE_VIDEO_PATH_PREFIX$videoUrl",
        sizeBytes = 0L,
        durationMs = durationSec.coerceAtLeast(0L) * 1000L,
        dateModified = uploadDateEpochMs ?: 0L,
        resolution = badge,
        isOnline = true,
        thumbnailUrl = thumbnailUrl,
        channelName = artist,
        viewCountText = formatViewCount(viewCount)
    )
}

/**
 * Online Video browsing panel.
 * - Empty search box = "Recommended for you": an endless feed built from the channels / topics the user watches.
 * - Typing = YouTube video search (videos and channels).
 * - Tapping a video opens the normal video player with the same controls as an offline video.
 * Cards are the very same VideoItemCard / VideoItemGridCard used for videos on the device.
 */
@Composable
fun OnlineVideoPanel(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    layoutMode: LayoutMode = LayoutMode.LIST,
    visibleFields: VisibleFields = VisibleFields(),
    sortField: SortField = SortField.TITLE,
    sortDirection: SortDirection = SortDirection.ASCENDING,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var results by remember { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<OnlineChannelResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasSearchedOnce by remember { mutableStateOf(false) }
    var metaTick by remember { mutableStateOf(0) }

    var activeExtractor by remember { mutableStateOf<SearchExtractor?>(null) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var hasNextPage by remember { mutableStateOf(false) }
    var searchSuggestion by remember { mutableStateOf<String?>(null) }
    var isCorrectedSearch by remember { mutableStateOf(false) }

    // A channel the user opened from the search results (its videos replace the list until Back).
    var openedChannel by remember { mutableStateOf<OnlineChannelDetail?>(null) }
    var channelSection by remember { mutableStateOf<OnlineChannelSection?>(null) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val isFeedMode = searchQuery.isBlank() && openedChannel == null
    val feedUsedQueries = remember { mutableSetOf<String>() }
    val feedToken = remember { intArrayOf(0) }

    fun loadFeed() {
        searchJob?.cancel()
        feedToken[0]++
        val token = feedToken[0]
        isSearching = true
        isLoadingMore = false
        hasSearchedOnce = true
        searchSuggestion = null
        isCorrectedSearch = false
        channelResults = emptyList()
        activeExtractor = null
        nextPage = null
        hasNextPage = false
        feedUsedQueries.clear()
        searchJob = scope.launch(Dispatchers.IO) {
            val batch = try {
                OnlineVideoRecommender.fetchFeedBatch(context, emptySet(), feedUsedQueries)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                emptyList<OnlineTrackResult>()
            }
            withContext(Dispatchers.Main) {
                if (token == feedToken[0]) {
                    results = batch
                    isSearching = false
                }
            }
        }
    }

    fun loadMoreFeed() {
        if (isLoadingMore || isSearching) return
        val token = feedToken[0]
        val shownUrls = results.map { it.videoUrl }.toSet()
        isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            val batch = try {
                OnlineVideoRecommender.fetchFeedBatch(context, shownUrls, feedUsedQueries)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                emptyList<OnlineTrackResult>()
            }
            withContext(Dispatchers.Main) {
                if (token == feedToken[0] && batch.isNotEmpty()) {
                    val already = results.map { it.videoUrl }.toSet()
                    results = results + batch.filter { it.videoUrl !in already }
                }
                isLoadingMore = false
            }
        }
    }

    fun executeSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) {
            loadFeed()
            return
        }
        feedToken[0]++
        searchJob?.cancel()
        isSearching = true
        hasSearchedOnce = true
        searchJob = scope.launch(Dispatchers.IO) {
            try {
                val (extractor, page) = OnlineMusicService.searchMusicPage(trimmed)
                withContext(Dispatchers.Main) {
                    activeExtractor = extractor
                    nextPage = page.nextPage
                    hasNextPage = page.hasNextPage
                    searchSuggestion = page.searchSuggestion
                    isCorrectedSearch = page.isCorrectedSearch
                    // Only real videos: no live streams / zero-length items.
                    results = page.items.filter { it.durationSec > 0L }
                    channelResults = page.channelResults
                    isSearching = false
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    results = emptyList()
                    isSearching = false
                }
            }
        }
    }

    fun openChannel(channel: OnlineChannelResult) {
        searchJob?.cancel()
        feedToken[0]++
        isSearching = true
        searchJob = scope.launch(Dispatchers.IO) {
            val detail = try { OnlineMusicService.fetchChannelDetail(channel.channelUrl) } catch (_: Throwable) { null }
            withContext(Dispatchers.Main) {
                if (detail != null) {
                    openedChannel = detail
                    channelSection = detail.availableSections.firstOrNull { it.id == "videos" }
                    results = detail.initialTracks.items
                    nextPage = detail.initialTracks.nextPage
                    hasNextPage = detail.initialTracks.hasNextPage
                    channelResults = emptyList()
                    searchSuggestion = null
                    activeExtractor = null
                } else {
                    Toast.makeText(context, "Could not open channel", Toast.LENGTH_SHORT).show()
                }
                isSearching = false
            }
        }
    }

    fun loadMore() {
        val next = nextPage ?: return
        if (!hasNextPage || isLoadingMore || isSearching) return
        val section = channelSection
        val extractor = activeExtractor
        if (openedChannel == null && extractor == null) return
        isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            try {
                if (openedChannel != null && section != null) {
                    val page = OnlineMusicService.fetchNextChannelTrackPage(section, next)
                    withContext(Dispatchers.Main) {
                        val existing = results.map { it.videoUrl }.toSet()
                        results = results + page.items.filter { it.videoUrl !in existing }
                        nextPage = page.nextPage
                        hasNextPage = page.hasNextPage
                        isLoadingMore = false
                    }
                } else if (extractor != null) {
                    val page = OnlineMusicService.fetchNextMusicPage(extractor, next)
                    withContext(Dispatchers.Main) {
                        val existing = results.map { it.videoUrl }.toSet()
                        results = results + page.items.filter { it.durationSec > 0L && it.videoUrl !in existing }
                        nextPage = page.nextPage
                        hasNextPage = page.hasNextPage
                        isLoadingMore = false
                    }
                } else {
                    withContext(Dispatchers.Main) { isLoadingMore = false }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { isLoadingMore = false }
            }
        }
    }

    // Back from an opened channel returns to the previous results.
    BackHandler(enabled = openedChannel != null) {
        openedChannel = null
        channelSection = null
        executeSearch(searchQuery)
    }

    // Debounced search reaction to the search box (same behaviour as Online Audio).
    LaunchedEffect(searchQuery) {
        openedChannel = null
        channelSection = null
        if (searchQuery.isNotBlank()) {
            delay(350)
            executeSearch(searchQuery)
        } else {
            executeSearch("")
        }
    }

    val shouldLoadMore by remember(layoutMode) {
        derivedStateOf {
            if (layoutMode == LayoutMode.GRID) {
                val total = gridState.layoutInfo.totalItemsCount
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 4
            } else {
                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 4
            }
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && !isSearching) {
            if (isFeedMode) loadMoreFeed() else if (hasNextPage) loadMore()
        }
    }

    val sortedResults = remember(results, sortField, sortDirection, isFeedMode) {
        // A video can appear twice in search pages; lazy-list keys must be unique.
        val unique = results.distinctBy { it.videoUrl }
        // The recommendation feed keeps its mixed order (and must not reshuffle while new batches append).
        if (isFeedMode) unique else when (sortField) {
            SortField.TITLE ->
                if (sortDirection == SortDirection.ASCENDING) unique.sortedBy { it.title.lowercase(Locale.ROOT) }
                else unique.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SortField.DATE ->
                if (sortDirection == SortDirection.ASCENDING) unique.sortedBy { it.uploadDateEpochMs ?: 0L }
                else unique.sortedByDescending { it.uploadDateEpochMs ?: 0L }
            SortField.DURATION ->
                if (sortDirection == SortDirection.DESCENDING) unique.sortedByDescending { it.durationSec }
                else unique.sortedBy { it.durationSec }
            else -> unique
        }
    }

    // Look up the highest available quality of the first results in the background (shown as the resolution badge).
    LaunchedEffect(sortedResults) {
        withContext(Dispatchers.IO) {
            sortedResults.take(14)
                .filter { OnlineVideoService.cachedMaxHeight(it.videoUrl) == null }
                .chunked(2)
                .forEach { pair ->
                    try {
                        pair.map { r -> async { OnlineVideoService.probeMaxHeight(r.videoUrl) } }.awaitAll()
                        withContext(Dispatchers.Main) { metaTick++ }
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (_: Throwable) {}
                }
        }
    }

    fun playResult(result: OnlineTrackResult) {
        try {
            val queue = sortedResults.map { it.toOnlineVideoItem() }
            val chosenPath = "$ONLINE_VIDEO_PATH_PREFIX${result.videoUrl}"
            val chosen = queue.firstOrNull { it.path == chosenPath } ?: result.toOnlineVideoItem()
            onPlayVideo(chosen, if (queue.isEmpty()) listOf(chosen) else queue)
        } catch (t: Throwable) {
            Toast.makeText(context, "Could not open this video", Toast.LENGTH_SHORT).show()
        }
    }

    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary
    val cardFields = visibleFields.copy(showExtension = true, showSize = false, showPath = false)

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = !searchSuggestion.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable { searchSuggestion?.let { executeSearch(it) } }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCorrectedSearch) "Showing results for " else "Did you mean: ",
                        fontSize = 12.5.sp,
                        color = secondaryText
                    )
                    Text(
                        text = searchSuggestion ?: "",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isSearching && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LumoraExpressiveLoadingIndicator(size = 54.dp, isDark = isDark)
                    }
                }
                hasSearchedOnce && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank())
                                "No online videos found for \"$searchQuery\". Check your internet connection or try a different search."
                            else
                                "No online videos found. Check your internet connection.",
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
                        openedChannel?.let { detail ->
                            item(span = { GridItemSpan(2) }, key = "online_video_channel_header") {
                                OnlineChannelResultCard(
                                    channel = OnlineChannelResult(
                                        channelUrl = detail.channelUrl,
                                        name = detail.name,
                                        avatarUrl = detail.avatarUrl,
                                        subscriberCountText = detail.subscriberCountText,
                                        videoCountText = null,
                                        description = detail.description
                                    ),
                                    isDark = isDark,
                                    onClick = {}
                                )
                            }
                        }
                        if (channelResults.isNotEmpty()) {
                            items(
                                items = channelResults.distinctBy { it.channelUrl },
                                key = { "online_video_channel_${it.channelUrl}" },
                                span = { GridItemSpan(2) }
                            ) { channel ->
                                OnlineChannelResultCard(channel = channel, isDark = isDark, onClick = { openChannel(channel) })
                            }
                        }
                        items(sortedResults, key = { it.videoUrl }) { result ->
                            val item = remember(result, metaTick) { result.toOnlineVideoItem() }
                            VideoItemGridCard(
                                video = item,
                                isDark = isDark,
                                visibleFields = cardFields,
                                onClick = { playResult(result) }
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) { LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark) }
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
                        openedChannel?.let { detail ->
                            item(key = "online_video_channel_header") {
                                OnlineChannelResultCard(
                                    channel = OnlineChannelResult(
                                        channelUrl = detail.channelUrl,
                                        name = detail.name,
                                        avatarUrl = detail.avatarUrl,
                                        subscriberCountText = detail.subscriberCountText,
                                        videoCountText = null,
                                        description = detail.description
                                    ),
                                    isDark = isDark,
                                    onClick = {}
                                )
                            }
                        }
                        if (channelResults.isNotEmpty()) {
                            items(
                                items = channelResults.distinctBy { it.channelUrl },
                                key = { "online_video_channel_${it.channelUrl}" }
                            ) { channel ->
                                OnlineChannelResultCard(channel = channel, isDark = isDark, onClick = { openChannel(channel) })
                            }
                        }
                        items(sortedResults, key = { it.videoUrl }) { result ->
                            val item = remember(result, metaTick) { result.toOnlineVideoItem() }
                            VideoItemCard(
                                video = item,
                                isDark = isDark,
                                visibleFields = cardFields,
                                onClick = { playResult(result) }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) { LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark) }
                            }
                        }
                    }
                }
            }
        }
    }
}
