package com.absinthe.mediacodecat.ui.view.record

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.R
import com.absinthe.mediacodecat.data.DataSource
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.model.VideoRecord
import com.absinthe.mediacodecat.ui.view.record.formatter.dateTitle
import com.absinthe.mediacodecat.utils.rememberUiSensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoRecordGallery(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<VideoRecord>>(emptyList()) }
    var totalRecordsCount by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(false) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var coverVersion by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.video_record_deleted)
    val undoLabel = stringResource(R.string.undo)

    fun refreshRecords() {
        val generation = loadGeneration + 1
        loadGeneration = generation
        scope.launch {
            isLoadingPage = true
            val page = loadVideoRecordPage(
                context = appContext,
                offset = 0,
                knownTotalCount = null
            )
            if (generation != loadGeneration) return@launch

            records = page.records
            totalRecordsCount = page.totalCount
            canLoadMore = page.hasMore
            coverVersion++
            isLoaded = true
            isLoadingPage = false
        }
    }

    fun loadNextPage() {
        if (!isLoaded || isLoadingPage || !canLoadMore) return

        val generation = loadGeneration
        val offset = records.size
        scope.launch {
            isLoadingPage = true
            val page = loadVideoRecordPage(
                context = appContext,
                offset = offset,
                knownTotalCount = totalRecordsCount
            )
            if (generation != loadGeneration) return@launch

            val mergedRecords = (records + page.records).distinctBy { it.sessionId }
            records = mergedRecords
            totalRecordsCount = page.totalCount
            canLoadMore = page.hasMore && mergedRecords.size < page.totalCount
            isLoadingPage = false
        }
    }

    fun deleteRecord(record: VideoRecord) {
        records = records.filterNot { it.sessionId == record.sessionId }
        totalRecordsCount = (totalRecordsCount - 1).coerceAtLeast(0)

        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                DataSource.deleteVideoRecord(appContext, record.sessionId)
            }
            if (!deleted) {
                refreshRecords()
                return@launch
            }

            val snackbarResult = withTimeoutOrNull(DeleteUndoDurationMillis.milliseconds) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Indefinite
                )
            }

            if (snackbarResult == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) {
                    DataSource.restoreVideoRecord(appContext, record)
                }
            }
        }
    }

    LaunchedEffect(appContext) {
        refreshRecords()
    }

    DisposableEffect(appContext) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshRecords()
            }
        }
        appContext.contentResolver.registerContentObserver(
            VideoRecordContract.Records.CONTENT_URI,
            true,
            observer
        )
        onDispose {
            appContext.contentResolver.unregisterContentObserver(observer)
        }
    }

    val galleryItems = remember(records) { records.toGalleryItems() }
    val headerBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    val uiSensor = rememberUiSensor()
    val highlightAngle = remember(uiSensor) { { uiSensor.gravityAngle } }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(headerBackdrop)
        ) {
            when {
                !isLoaded -> LoadingState(Modifier.fillMaxSize())
                records.isEmpty() -> EmptyRecordState(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> RecordGrid(
                    backdrop = backdrop,
                    galleryItems = galleryItems,
                    coverVersion = coverVersion,
                    isLoadingMore = isLoadingPage && isLoaded,
                    canLoadMore = canLoadMore,
                    onLoadMore = ::loadNextPage,
                    onDeleteRecord = ::deleteRecord,
                    highlightAngle = highlightAngle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        GalleryHeader(
            backdrop = headerBackdrop,
            recordsCount = totalRecordsCount,
            modifier = Modifier.fillMaxWidth()
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = GallerySnackbarBottomPadding
                )
        )
    }
}

@Composable
private fun RecordGrid(
    backdrop: Backdrop,
    galleryItems: List<VideoGalleryItem>,
    coverVersion: Int,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onDeleteRecord: (VideoRecord) -> Unit,
    highlightAngle: () -> Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val gridState = rememberLazyGridState()
        val layoutDirection = LocalLayoutDirection.current
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val horizontalPadding = if (maxWidth < CompactWidthBreakpoint) 16.dp else 24.dp
        val columns =
            if (maxWidth < CompactWidthBreakpoint) GridCells.Fixed(1)
            else GridCells.Adaptive(WideGridMinCellWidth)
        val contentPadding = PaddingValues(
            start = safeDrawingPadding.calculateStartPadding(layoutDirection) + horizontalPadding,
            top = safeDrawingPadding.calculateTopPadding() + GalleryHeaderContentHeight,
            end = safeDrawingPadding.calculateEndPadding(layoutDirection) + horizontalPadding,
            bottom = safeDrawingPadding.calculateBottomPadding() + 112.dp
        )
        val shouldLoadMore by rememberShouldLoadMore(gridState)

        LaunchedEffect(shouldLoadMore, canLoadMore, isLoadingMore) {
            if (shouldLoadMore && canLoadMore && !isLoadingMore) {
                onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = columns,
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = galleryItems,
                key = { it.key },
                span = { item ->
                    if (item is VideoGalleryItem.Header) GridItemSpan(maxLineSpan)
                    else GridItemSpan(1)
                }
            ) { item ->
                when (item) {
                    is VideoGalleryItem.Header -> DateHeader(item)
                    is VideoGalleryItem.RecordCell -> DismissibleVideoRecordCard(
                        record = item.record,
                        backdrop = backdrop,
                        coverVersion = coverVersion,
                        onDelete = onDeleteRecord,
                        highlightAngle = highlightAngle
                    )
                }
            }

            if (isLoadingMore) {
                item(
                    key = LoadingMoreItemKey,
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    LoadingMoreItem(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun rememberShouldLoadMore(gridState: LazyGridState) = remember(gridState) {
    derivedStateOf {
        val layoutInfo = gridState.layoutInfo
        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
        lastVisibleIndex >= layoutInfo.totalItemsCount - LoadMorePrefetchItemThreshold
    }
}

@Composable
private fun DismissibleVideoRecordCard(
    record: VideoRecord,
    backdrop: Backdrop,
    coverVersion: Int,
    onDelete: (VideoRecord) -> Unit,
    highlightAngle: () -> Float,
    modifier: Modifier = Modifier
) {
    val dismissState = remember(record.sessionId) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { distance ->
            distance * SwipeDismissThresholdFraction
        }
    }
    var deleteRequested by remember(record.sessionId) { mutableStateOf(false) }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            DeleteRecordSwipeBackground(
                direction = dismissState.dismissDirection,
                modifier = Modifier.fillMaxSize()
            )
        },
        modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        onDismiss = {
            if (!deleteRequested) {
                deleteRequested = true
                onDelete(record)
            }
        }
    ) {
        VideoRecordCard(
            record = record,
            backdrop = backdrop,
            coverVersion = coverVersion,
            highlightAngle = highlightAngle
        )
    }
}

@Composable
private fun DeleteRecordSwipeBackground(
    direction: SwipeToDismissBoxValue,
    modifier: Modifier = Modifier
) {
    val alignment =
        if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart
        else Alignment.CenterEnd

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .size(24.dp)
        )
    }
}

@Composable
private fun GalleryHeader(
    backdrop: Backdrop,
    recordsCount: Int,
    modifier: Modifier = Modifier
) {
    val recordCountText = recordCountTotalString(recordsCount)

    BoxWithConstraints(modifier = modifier) {
        val layoutDirection = LocalLayoutDirection.current
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val horizontalPadding = if (maxWidth < CompactWidthBreakpoint) 16.dp else 24.dp
        val topInset = safeDrawingPadding.calculateTopPadding()
        val tintColor = MaterialTheme.colorScheme.background

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + GalleryHeaderMaskHeight)
                .progressiveHeaderBackdrop(
                    backdrop = backdrop,
                    tintColor = tintColor
                )
        )

        Column(
            modifier = Modifier.padding(
                start = safeDrawingPadding.calculateStartPadding(layoutDirection) + horizontalPadding,
                top = topInset + 24.dp,
                end = safeDrawingPadding.calculateEndPadding(layoutDirection) + horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.video_records_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = recordCountText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DateHeader(
    item: VideoGalleryItem.Header,
    modifier: Modifier = Modifier
) {
    val countText = recordCountString(item.count)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.date,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Modifier.progressiveHeaderBackdrop(
    backdrop: Backdrop,
    tintColor: Color
): Modifier {
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            blur(18f.dp.toPx())
            runtimeShaderEffect(
                key = "VideoRecordGalleryHeaderMask",
                shaderString = """
                    uniform shader content;
                    uniform float2 size;
                    layout(color) uniform half4 tint;
                    uniform float tintIntensity;

                    half4 main(float2 coord) {
                        float blurAlpha = smoothstep(size.y, size.y * 0.45, coord.y);
                        float tintAlpha = smoothstep(size.y, size.y * 0.55, coord.y);
                        return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
                    }
                """.trimIndent(),
                uniformShaderName = "content"
            ) {
                setFloatUniform("size", size.width, size.height)
                setColorUniform("tint", tintColor)
                setFloatUniform("tintIntensity", 0.82f)
            }
        }
    )
}

private suspend fun loadVideoRecordPage(
    context: Context,
    offset: Int,
    knownTotalCount: Int?
): VideoRecordPage {
    return withContext(Dispatchers.IO) {
        runCatching {
            val records = DataSource.queryVideoRecordsPage(
                context = context,
                limit = VideoRecordsPageSize + 1,
                offset = offset
            )
            val pageRecords = records.take(VideoRecordsPageSize)
            val loadedCount = offset + pageRecords.size
            val totalCount = (knownTotalCount
                ?: DataSource.queryVideoRecordCount(context)).coerceAtLeast(loadedCount)

            VideoRecordPage(
                records = pageRecords,
                totalCount = totalCount,
                hasMore = records.size > VideoRecordsPageSize || loadedCount < totalCount
            )
        }.getOrElse {
            VideoRecordPage(
                records = emptyList(),
                totalCount = offset,
                hasMore = false
            )
        }
    }
}

private fun List<VideoRecord>.toGalleryItems(): List<VideoGalleryItem> {
    val source = this
    return buildList {
        source.groupBy { it.dateTitle() }.forEach { (date, dayRecords) ->
            add(VideoGalleryItem.Header(date = date, count = dayRecords.size))
            dayRecords.forEach { record ->
                add(VideoGalleryItem.RecordCell(record))
            }
        }
    }
}

@Composable
private fun recordCountTotalString(count: Int): String {
    val resources = LocalResources.current
    return resources.getQuantityString(R.plurals.video_record_count_total, count, count)
}

@Composable
private fun recordCountString(count: Int): String {
    val resources = LocalResources.current
    return resources.getQuantityString(R.plurals.video_record_count, count, count)
}

private sealed interface VideoGalleryItem {
    val key: String

    data class Header(
        val date: String,
        val count: Int
    ) : VideoGalleryItem {
        override val key: String = "header:$date"
    }

    data class RecordCell(
        val record: VideoRecord
    ) : VideoGalleryItem {
        override val key: String = record.sessionId
    }
}

private data class VideoRecordPage(
    val records: List<VideoRecord>,
    val totalCount: Int,
    val hasMore: Boolean
)

private const val VideoRecordsPageSize = 30

private const val LoadMorePrefetchItemThreshold = 6

private const val LoadingMoreItemKey = "loading_more"

private const val DeleteUndoDurationMillis = 5_000L

private const val SwipeDismissThresholdFraction = 0.38f

private val GalleryHeaderContentHeight = 132.dp

private val GalleryHeaderMaskHeight = 156.dp

private val GallerySnackbarBottomPadding = 104.dp

private val CompactWidthBreakpoint = 600.dp

private val WideGridMinCellWidth = 280.dp
