package com.absinthe.mediacodecat.ui.view

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.data.DataSource
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.model.VideoRecord
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VideoRecordGallery(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<VideoRecord>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }

    fun refreshRecords() {
        scope.launch {
            records = loadVideoRecords(appContext)
            isLoaded = true
        }
    }

    LaunchedEffect(appContext) {
        records = loadVideoRecords(appContext)
        isLoaded = true
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

    Box(modifier = modifier) {
        when {
            !isLoaded -> LoadingState(Modifier.fillMaxSize())
            records.isEmpty() -> EmptyRecordState(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize()
            )
            else -> RecordGrid(
                backdrop = backdrop,
                recordsCount = records.size,
                galleryItems = galleryItems,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RecordGrid(
    backdrop: Backdrop,
    recordsCount: Int,
    galleryItems: List<VideoGalleryItem>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val layoutDirection = LocalLayoutDirection.current
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val horizontalPadding = if (maxWidth < CompactWidthBreakpoint) 16.dp else 24.dp
        val columns =
            if (maxWidth < CompactWidthBreakpoint) GridCells.Fixed(1)
            else GridCells.Adaptive(WideGridMinCellWidth)
        val contentPadding = PaddingValues(
            start = safeDrawingPadding.calculateStartPadding(layoutDirection) + horizontalPadding,
            top = safeDrawingPadding.calculateTopPadding() + 24.dp,
            end = safeDrawingPadding.calculateEndPadding(layoutDirection) + horizontalPadding,
            bottom = safeDrawingPadding.calculateBottomPadding() + 112.dp
        )

        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GalleryTitle(recordsCount = recordsCount)
            }

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
                    is VideoGalleryItem.RecordCell -> VideoRecordCard(
                        record = item.record,
                        backdrop = backdrop
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryTitle(
    recordsCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "视频记录",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "共 $recordsCount 条",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DateHeader(
    item: VideoGalleryItem.Header,
    modifier: Modifier = Modifier
) {
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
            text = "${item.count} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoRecordCard(
    record: VideoRecord,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            VideoCoverPlaceholder(record = record)
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = record.primaryTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.detailLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.timeTitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoCoverPlaceholder(
    record: VideoRecord,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(record.coverBrush()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = record.mime.substringAfter('/', "video").uppercase(Locale.US),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.88f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "加载中",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyRecordState(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(backdrop = backdrop) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
                Text(
                    text = "暂无视频记录",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun GlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)

    Column(
        modifier = modifier
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(6f.dp.toPx(), 10f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                }
            ),
        content = content
    )
}

private suspend fun loadVideoRecords(context: Context): List<VideoRecord> {
    return withContext(Dispatchers.IO) {
        runCatching { DataSource.queryVideoRecords(context) }
            .getOrDefault(emptyList())
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

private fun VideoRecord.primaryTitle(): String {
    return buildString {
        append(resolutionLabel())
        append(" ")
        append(mime.ifBlank { "video" })
    }
}

private fun VideoRecord.detailLine(): String {
    val codec = codecName ?: "unknown codec"
    val bitrate = bitrateKbps?.let { "$it kbps" } ?: "bitrate -"
    return "$codec / $bitrate"
}

private fun VideoRecord.resolutionLabel(): String {
    return if (width != null && height != null) "${width}x$height" else "unknown size"
}

private fun VideoRecord.dateTitle(): String {
    return timestampMs().toZonedDateTime().format(DAY_FORMATTER)
}

private fun VideoRecord.timeTitle(): String {
    return timestampMs().toZonedDateTime().format(TIME_FORMATTER)
}

private fun VideoRecord.timestampMs(): Long {
    return if (lastSeenAtMs > 0) lastSeenAtMs else firstSeenAtMs
}

private fun Long.toZonedDateTime() =
    Instant.ofEpochMilli(coerceAtLeast(0L)).atZone(ZoneId.systemDefault())

private fun VideoRecord.coverBrush(): Brush {
    val palette = when (sessionId.hashCode().mod(4)) {
        0 -> listOf(Color(0xFF213547), Color(0xFF2A9D8F))
        1 -> listOf(Color(0xFF2B2D42), Color(0xFF6C7A89))
        2 -> listOf(Color(0xFF1F2933), Color(0xFF7A9E9F))
        else -> listOf(Color(0xFF243B4A), Color(0xFFB08968))
    }
    return Brush.linearGradient(palette)
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

private val DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")

private val CompactWidthBreakpoint = 600.dp

private val WideGridMinCellWidth = 280.dp
