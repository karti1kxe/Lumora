package com.example.ui.components

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.player.AudioPlaybackManager
import com.example.ui.screens.AudioTrackGridCard
import com.example.ui.screens.AudioTrackItem
import com.example.ui.screens.AudioTrackListCard
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightTextSecondary
import com.example.util.AudioPlaybackHistoryManager
import com.example.util.LyricsFetcher
import com.example.util.OnlineChannelResult
import com.example.util.OnlineMusicService
import com.example.util.OnlineRecommendationMixer
import com.example.util.OnlineTrackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.util.Locale

/** Path prefix used to tag AudioTrackItem instances that came from Online Music, not disk. */
const val ONLINE_TRACK_PATH_PREFIX = "online://"

fun OnlineTrackResult.toAudioTrackItem(): AudioTrackItem {
    val stableId = -(kotlin.math.abs(videoUrl.hashCode().toLong()))
    val cachedMeta = OnlineMusicService.getCachedAudioMeta(videoUrl)
    val titleFormat = OnlineMusicService.detectFormatFromTitle(title)
    val titleAudioType = OnlineMusicService.detectAudioTypeFromTitle(title)

    val finalFormat = detectedFormat ?: cachedMeta?.first ?: titleFormat ?: "M4A"
    val finalAudioType = detectedAudioType ?: cachedMeta?.second ?: titleAudioType ?: "Stereo"

    return AudioTrackItem(
        id = stableId,
        uri = Uri.parse(videoUrl),
        title = title,
        artist = artist.ifBlank { "YouTube Music" },
        album = "Online Music",
        durationMs = durationSec.coerceAtLeast(0L) * 1000L,
        path = "$ONLINE_TRACK_PATH_PREFIX$videoUrl",
        sizeBytes = 0L,
        format = finalFormat,
        audioType = finalAudioType,
        dateModified = uploadDateEpochMs ?: 0L,
        thumbnailUrl = thumbnailUrl
    )
}

/**
 * Online Music browsing and search panel.
 * Tapping a result resolves the stream URL and auto-queues recommendations via NewPipeExtractor.
 * Visual cards use AudioTrackListCard / AudioTrackGridCard to match offline media 100%.
 */
@Composable
fun OnlineMusicPanel(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    layoutMode: LayoutMode = LayoutMode.LIST,
    visibleFields: VisibleFields = VisibleFields(),
    sortField: SortField = SortField.TITLE,
    sortDirection: SortDirection = SortDirection.ASCENDING,
    onOpenChannel: ((OnlineChannelResult) -> Unit)? = null,
    onOpenSharedPlaylist: ((com.example.util.OnlinePlaylistDetail) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentPlayingTrack by AudioPlaybackManager.currentTrack.collectAsState()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by AudioPlaybackManager.currentPositionMs.collectAsState()

    var metaUpdateTrigger by remember { mutableStateOf(0) }

    var results by remember { mutableStateOf<List<OnlineTrackResult>>(emptyList()) }
    var channelResults by remember { mutableStateOf<List<OnlineChannelResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadingVideoUrl by remember { mutableStateOf<String?>(null) }
    var hasSearchedOnce by remember { mutableStateOf(false) }

    var activeExtractor by remember { mutableStateOf<SearchExtractor?>(null) }
    var nextPage by remember { mutableStateOf<Page?>(null) }
    var hasNextPage by remember { mutableStateOf(false) }
    var searchSuggestion by remember { mutableStateOf<String?>(null) }
    var isCorrectedSearch by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Search query tracking with cancellation
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Blank search box = the "Recommended for you" feed (NOT a search). It is an endless feed: every time the
    // user nears the bottom another mixed batch (65% like their taste / 35% other genres) is appended.
    val isFeedMode = searchQuery.isBlank()
    val feedUsedQueries = remember { mutableSetOf<String>() }
    // Bumped whenever the feed is reset or a real search starts, so a slow feed batch that finishes late is
    // discarded instead of being appended to unrelated results.
    val feedToken = remember { intArrayOf(0) }

    fun loadFeed() {
        searchJob?.cancel()
        feedToken[0]++
        val token = feedToken[0]
        isSearching = true
        isLoadingMore = false
        hasSearchedOnce = true
        // No search happened, so no "Did you mean" / "Showing results for" pill and no channel cards.
        searchSuggestion = null
        isCorrectedSearch = false
        channelResults = emptyList()
        activeExtractor = null
        nextPage = null
        hasNextPage = false
        feedUsedQueries.clear()
        searchJob = scope.launch(Dispatchers.IO) {
            val batch = try {
                OnlineRecommendationMixer.fetchFeedBatch(
                    topArtists = AudioPlaybackHistoryManager.getTopArtists(context, limit = 5),
                    excludeUrls = emptySet(),
                    usedQueries = feedUsedQueries
                )
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
                OnlineRecommendationMixer.fetchFeedBatch(
                    topArtists = AudioPlaybackHistoryManager.getTopArtists(context, limit = 5),
                    excludeUrls = shownUrls,
                    usedQueries = feedUsedQueries
                )
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

        // If user entered or pasted a YouTube Music / Spotify playlist or song link
        if (onOpenSharedPlaylist != null && com.example.util.OnlineMusicService.isMusicPlaylistOrTrackUrl(trimmed)) {
            searchJob = scope.launch(Dispatchers.IO) {
                val pl = com.example.util.OnlineMusicService.fetchPlaylistDetails(trimmed)
                withContext(Dispatchers.Main) {
                    isSearching = false
                    if (pl != null && pl.tracks.isNotEmpty()) {
                        onOpenSharedPlaylist(pl)
                    } else {
                        android.widget.Toast.makeText(context, "Could not load playlist from link", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        searchJob = scope.launch(Dispatchers.IO) {
            val (extractor, page) = OnlineMusicService.searchMusicPage(trimmed)
            val ranked = page.items

            withContext(Dispatchers.Main) {
                activeExtractor = extractor
                nextPage = page.nextPage
                hasNextPage = page.hasNextPage
                searchSuggestion = page.searchSuggestion
                isCorrectedSearch = page.isCorrectedSearch
                results = ranked
                channelResults = page.channelResults
                isSearching = false
            }
        }
    }

    fun loadMore() {
        val extractor = activeExtractor ?: return
        val next = nextPage ?: return
        if (!hasNextPage || isLoadingMore || isSearching) return

        isLoadingMore = true
        scope.launch(Dispatchers.IO) {
            try {
                val nextPageResult = OnlineMusicService.fetchNextMusicPage(extractor, next)
                withContext(Dispatchers.Main) {
                    if (nextPageResult.items.isNotEmpty()) {
                        val existingUrls = results.map { it.videoUrl }.toSet()
                        val newItems = nextPageResult.items.filter { it.videoUrl !in existingUrls }
                        results = results + newItems
                    }
                    nextPage = nextPageResult.nextPage
                    hasNextPage = nextPageResult.hasNextPage
                    isLoadingMore = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    isLoadingMore = false
                }
            }
        }
    }

    // Debounced search reaction to searchQuery
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(350)
            executeSearch(searchQuery)
        } else {
            executeSearch("")
        }
    }

    // Unlimited pagination scroll trigger
    val shouldLoadMore by remember(layoutMode) {
        derivedStateOf {
            if (layoutMode == LayoutMode.GRID) {
                val totalItems = gridState.layoutInfo.totalItemsCount
                val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 4
            } else {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 4
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && !isSearching) {
            if (isFeedMode) loadMoreFeed() else if (hasNextPage) loadMore()
        }
    }

    fun playOnlineTrack(result: OnlineTrackResult) {
        val stableId = -(kotlin.math.abs(result.videoUrl.hashCode().toLong()))
        val selectedTrack = AudioTrackItem(
            id = stableId,
            uri = Uri.EMPTY,
            title = result.title,
            artist = result.artist,
            album = "Online Music",
            durationMs = result.durationSec * 1000L,
            path = "$ONLINE_TRACK_PATH_PREFIX${result.videoUrl}",
            sizeBytes = 0L,
            format = "STREAM",
            audioType = "Stereo",
            hasLyrics = false,
            lyricsText = "",
            thumbnailUrl = result.thumbnailUrl
        )

        // 1. Immediately open full Audio Player Screen
        AudioPlaybackManager.openFullScreen()

        // 2. Immediately stop previous audio and play new track (buffering animation starts immediately)
        AudioPlaybackManager.playTrack(
            context = context,
            track = selectedTrack,
            newQueue = listOf(selectedTrack)
        )

        scope.launch(Dispatchers.IO) {
            // Concurrently fetch lyrics
            launch {
                try {
                    val lyrics = LyricsFetcher.fetch(result.title, result.artist, result.durationSec)
                    val fullLyrics = lyrics?.syncedLyrics ?: lyrics?.plainLyrics
                    if (!fullLyrics.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            AudioPlaybackManager.setLyricsForCurrentTrack(fullLyrics)
                        }
                    }
                } catch (_: Throwable) {}
            }

            // Background queue construction: gather recommendations and pre-resolve streams
            val info = OnlineMusicService.resolveStreamWithRelated(result.videoUrl)
            val candidateResults = mutableListOf<OnlineTrackResult>()
            candidateResults.addAll(info.relatedTracks)

            // If related items from StreamInfo are fewer than 6, supplement from artist/title search or user favorites
            val topFavs = AudioPlaybackHistoryManager.getTopArtists(context, limit = 2)
            if (candidateResults.size < 6) {
                val fallbackArtist = result.artist.ifBlank { topFavs.firstOrNull() ?: result.title }
                val fallbackQuery = "$fallbackArtist song audio"
                val extra = OnlineMusicService.search(fallbackQuery)
                candidateResults.addAll(extra)
            }

            // Strict filter: ensure ONLY genuine audio/music items are recommended (NO gameplay, vlogs, movies, etc.)
            val filteredAudioCandidates = candidateResults.filter {
                OnlineMusicService.isAudioOrMusicContent(it.title, it.artist, it.durationSec)
            }

            // Rank candidate recommendations by user affinity
            val userTopArtists = AudioPlaybackHistoryManager.getTopArtists(context, limit = 5)
            val rankedCandidates = filteredAudioCandidates.sortedWith(Comparator { candA: OnlineTrackResult, candB: OnlineTrackResult ->
                val scoreA: Int = run {
                    val favIdx = userTopArtists.indexOfFirst { fav: String ->
                        candA.artist.contains(fav, ignoreCase = true) || candA.title.contains(fav, ignoreCase = true)
                    }
                    if (favIdx >= 0) 50 - favIdx * 10 else 0
                }
                val scoreB: Int = run {
                    val favIdx = userTopArtists.indexOfFirst { fav: String ->
                        candB.artist.contains(fav, ignoreCase = true) || candB.title.contains(fav, ignoreCase = true)
                    }
                    if (favIdx >= 0) 50 - favIdx * 10 else 0
                }
                scoreB.compareTo(scoreA)
            })

            // Deduplicate and filter out current track: this is the "similar" pool (same artist / related tracks)
            val similarPool = rankedCandidates
                .filter { it.videoUrl != result.videoUrl && it.title.isNotBlank() }
                .distinctBy { it.videoUrl }

            // Discovery pool: tracks from genres unlike the current song / the listener's usual artists, so
            // the queue is never one single category from start to finish.
            val discoveryPool = try {
                OnlineRecommendationMixer.fetchDiscoveryTracks(
                    avoidText = listOf(result.title, result.artist) + userTopArtists,
                    exclude = similarPool.map { it.videoUrl }.toSet() + result.videoUrl,
                    usedQueries = mutableSetOf()
                )
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (_: Throwable) {
                emptyList<OnlineTrackResult>()
            }

            // 65% similar / 35% discovery, discovery spread evenly through the queue
            val uniqueCandidates = OnlineRecommendationMixer.mix(
                similar = similarPool,
                discovery = discoveryPool,
                total = 20
            )

            if (uniqueCandidates.isEmpty()) return@launch

            // Pre-resolve direct stream URLs for the first 5 tracks in parallel with timeout
            val preResolveCount = uniqueCandidates.size.coerceAtMost(5)
            val preResolvedMap = uniqueCandidates.take(preResolveCount).map { item ->
                async {
                    val resolved = withTimeoutOrNull(3500L) {
                        OnlineMusicService.resolveStreamUrl(item.videoUrl)
                    }
                    item.videoUrl to resolved
                }
            }.awaitAll().toMap()

            val recommendedTrackItems = uniqueCandidates.map { item ->
                val recStableId = -(kotlin.math.abs(item.videoUrl.hashCode().toLong()))
                val resolvedUrl = preResolvedMap[item.videoUrl]
                val uri = if (resolvedUrl != null) Uri.parse(resolvedUrl) else Uri.EMPTY
                AudioTrackItem(
                    id = recStableId,
                    uri = uri,
                    title = item.title,
                    artist = item.artist,
                    album = "Online Music",
                    durationMs = item.durationSec * 1000L,
                    path = "$ONLINE_TRACK_PATH_PREFIX${item.videoUrl}",
                    sizeBytes = 0L,
                    format = "STREAM",
                    audioType = "Stereo",
                    thumbnailUrl = item.thumbnailUrl
                )
            }

            withContext(Dispatchers.Main) {
                val currentQ = AudioPlaybackManager.queue.value
                val existingIds = currentQ.map { it.id }.toSet()
                val itemsToAdd = recommendedTrackItems.filter { it.id !in existingIds }
                if (itemsToAdd.isNotEmpty()) {
                    AudioPlaybackManager.updateQueue(currentQ + itemsToAdd)
                }
            }
        }
    }

    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    // Sort tracks according to global sort parameters
    val sortedTracks = remember(results, sortField, sortDirection, isFeedMode) {
        // The recommendation feed keeps its mixed order (and must not reshuffle while new batches append).
        if (isFeedMode) results else when (sortField) {
            SortField.TITLE -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    results.sortedBy { it.title.lowercase(Locale.ROOT) }
                } else {
                    results.sortedByDescending { it.title.lowercase(Locale.ROOT) }
                }
            }
            SortField.DATE -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    results.sortedBy { it.uploadDateEpochMs ?: 0L }
                } else {
                    results.sortedByDescending { it.uploadDateEpochMs ?: 0L }
                }
            }
            SortField.DURATION -> {
                if (sortDirection == SortDirection.DESCENDING) results.sortedByDescending { it.durationSec }
                else results.sortedBy { it.durationSec }
            }
            SortField.SIZE -> results
            SortField.COUNT -> results
        }
    }

    LaunchedEffect(sortedTracks) {
        withContext(Dispatchers.IO) {
            sortedTracks.take(15).forEach { trackResult ->
                if (OnlineMusicService.getCachedAudioMeta(trackResult.videoUrl) == null) {
                    try {
                        OnlineMusicService.probeAudioMetadata(trackResult.videoUrl, trackResult.title)
                        withContext(Dispatchers.Main) {
                            metaUpdateTrigger++
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search suggestion or spell correction pill
        AnimatedVisibility(visible = !searchSuggestion.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable {
                        searchSuggestion?.let { executeSearch(it) }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledIcon(imageVector = Icons.Outlined.Search,
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
                            "No online songs found for \"$searchQuery\". Check your internet connection or try a different search."
                        else
                            "No online songs found. Check your internet connection.",
                        color = secondaryText,
                        modifier = Modifier.padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                if (layoutMode == LayoutMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (channelResults.isNotEmpty()) {
                            items(
                                items = channelResults,
                                key = { "online_audio_channel_${it.channelUrl}" },
                                span = { GridItemSpan(2) }
                            ) { channel ->
                                OnlineChannelResultCard(
                                    channel = channel,
                                    isDark = isDark,
                                    onClick = { onOpenChannel?.invoke(channel) }
                                )
                            }
                        }
                        items(sortedTracks, key = { it.videoUrl }) { result ->
                            val baseTrackItem = remember(result, metaUpdateTrigger) { result.toAudioTrackItem() }
                            val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${result.videoUrl}"
                            val progress = if (isCurrent && baseTrackItem.durationMs > 0) {
                                (currentPositionMs.toFloat() / baseTrackItem.durationMs).coerceIn(0f, 1f)
                            } else {
                                val savedPos = AudioPlaybackHistoryManager.getPosition(context, baseTrackItem)
                                if (baseTrackItem.durationMs > 0) (savedPos.toFloat() / baseTrackItem.durationMs).coerceIn(0f, 1f) else 0f
                            }
                            val trackItem = if (progress != baseTrackItem.playbackProgress) baseTrackItem.copy(playbackProgress = progress) else baseTrackItem

                            AudioTrackGridCard(
                                track = trackItem,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isDark = isDark,
                                visibleFields = visibleFields.copy(showExtension = false),
                                onClick = { playOnlineTrack(result) }
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (channelResults.isNotEmpty()) {
                            items(
                                items = channelResults,
                                key = { "online_audio_channel_${it.channelUrl}" }
                            ) { channel ->
                                OnlineChannelResultCard(
                                    channel = channel,
                                    isDark = isDark,
                                    onClick = { onOpenChannel?.invoke(channel) }
                                )
                            }
                        }
                        items(sortedTracks, key = { it.videoUrl }) { result ->
                            val baseTrackItem = remember(result, metaUpdateTrigger) { result.toAudioTrackItem() }
                            val isCurrent = currentPlayingTrack?.path == "$ONLINE_TRACK_PATH_PREFIX${result.videoUrl}"
                            val progress = if (isCurrent && baseTrackItem.durationMs > 0) {
                                (currentPositionMs.toFloat() / baseTrackItem.durationMs).coerceIn(0f, 1f)
                            } else {
                                val savedPos = AudioPlaybackHistoryManager.getPosition(context, baseTrackItem)
                                if (baseTrackItem.durationMs > 0) (savedPos.toFloat() / baseTrackItem.durationMs).coerceIn(0f, 1f) else 0f
                            }
                            val trackItem = if (progress != baseTrackItem.playbackProgress) baseTrackItem.copy(playbackProgress = progress) else baseTrackItem

                            AudioTrackListCard(
                                track = trackItem,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isDark = isDark,
                                visibleFields = visibleFields.copy(showExtension = false),
                                onClick = { playOnlineTrack(result) }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LumoraExpressiveLoadingIndicator(size = 36.dp, isDark = isDark)
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

