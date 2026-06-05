package com.absinthe.mediacodecat.ui.view

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.R
import com.absinthe.mediacodecat.data.DataSource
import com.absinthe.mediacodecat.data.VideoCoverStore
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.model.VideoRecord
import com.absinthe.mediacodecat.utils.rememberUiSensor
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import androidx.core.graphics.drawable.toBitmap
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
    var totalRecordsCount by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(false) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var coverVersion by remember { mutableIntStateOf(0) }

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
                    is VideoGalleryItem.RecordCell -> VideoRecordCard(
                        record = item.record,
                        backdrop = backdrop,
                        coverVersion = coverVersion,
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
        val tintColor =
            if (isSystemInDarkTheme()) Color(0xFF101214)
            else Color(0xFFF6F7F9)

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

@Composable
private fun VideoRecordCard(
    record: VideoRecord,
    backdrop: Backdrop,
    coverVersion: Int,
    highlightAngle: () -> Float,
    modifier: Modifier = Modifier
) {
    val strings = videoRecordStrings()

    GlassPanel(
        backdrop = backdrop,
        highlightAngle = highlightAngle,
        modifier = modifier.fillMaxWidth()
    ) {
        if (record.usesSideCoverLayout()) {
            SideCoverRecordCardContent(
                record = record,
                strings = strings,
                coverVersion = coverVersion
            )
        } else {
            StackedRecordCardContent(
                record = record,
                strings = strings,
                coverVersion = coverVersion
            )
        }
    }
}

@Composable
private fun StackedRecordCardContent(
    record: VideoRecord,
    strings: VideoRecordStrings,
    coverVersion: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        VideoCoverPlaceholder(
            record = record,
            strings = strings,
            coverVersion = coverVersion,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(record.coverAspectRatio())
        )
        VideoRecordInfoColumn(
            record = record,
            strings = strings,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun SideCoverRecordCardContent(
    record: VideoRecord,
    strings: VideoRecordStrings,
    coverVersion: Int,
    modifier: Modifier = Modifier
) {
    val coverFrame = record.coverFrame()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PortraitCoverHeight)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        VideoCoverPlaceholder(
            record = record,
            strings = strings,
            coverVersion = coverVersion,
            modifier = Modifier
                .width(coverFrame.width)
                .fillMaxHeight()
        )
        VideoRecordInfoColumn(
            record = record,
            strings = strings,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun VideoRecordInfoColumn(
    record: VideoRecord,
    strings: VideoRecordStrings,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResolutionLine(
            record = record,
            strings = strings
        )
        PackageLine(record = record)
        Text(
            text = record.codecLine(strings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        RecordAttributeChips(
            record = record,
            strings = strings
        )
        Text(
            text = record.timeRangeTitle(strings),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResolutionLine(
    record: VideoRecord,
    strings: VideoRecordStrings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InfoLeadingGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(InfoLeadingSlotWidth),
            contentAlignment = Alignment.Center
        ) {
            ResolutionRatioBadge(record = record)
        }
        Text(
            text = record.primaryTitle(strings),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResolutionRatioBadge(
    record: VideoRecord,
    modifier: Modifier = Modifier
) {
    val aspectRatio = record.aspectRatio() ?: ResolutionBadgeDefaultAspectRatio
    val (rectWidth, rectHeight) = resolutionBadgeSize(aspectRatio)
    val outlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)

    Canvas(
        modifier = modifier.size(ResolutionBadgeWidth, ResolutionBadgeHeight)
    ) {
        val rectWidthPx = rectWidth.toPx()
        val rectHeightPx = rectHeight.toPx()
        val cornerRadius = minOf(
            ResolutionBadgeCornerRadius.toPx(),
            rectWidthPx / 2f,
            rectHeightPx / 2f
        )

        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(
                x = (size.width - rectWidthPx) / 2f,
                y = (size.height - rectHeightPx) / 2f
            ),
            size = Size(rectWidthPx, rectHeightPx),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = ResolutionBadgeStrokeWidth.toPx())
        )
    }
}

@Composable
private fun PackageLine(
    record: VideoRecord,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InfoLeadingGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(InfoLeadingSlotWidth),
            contentAlignment = Alignment.Center
        ) {
            PackageIcon(packageName = record.packageName)
        }
        Text(
            text = record.packageName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PackageIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = packageName
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(
                        width = PackageIconBitmapSizePx,
                        height = PackageIconBitmapSizePx,
                        config = Bitmap.Config.ARGB_8888
                    )
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .size(PackageIconSize)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = requireNotNull(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f))
            )
        }
    }
}

@Composable
private fun RecordAttributeChips(
    record: VideoRecord,
    strings: VideoRecordStrings,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        record.attributeLabels(strings).forEach { label ->
            AttributeChip(text = label.toAnnotatedString())
        }
    }
}

@Composable
private fun AttributeChip(
    text: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun VideoCoverPlaceholder(
    record: VideoRecord,
    strings: VideoRecordStrings,
    coverVersion: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val locale = LocalLocale.current.platformLocale
    val cover by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = record.sessionId,
        key3 = coverVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val file = VideoCoverStore.existingCoverFile(context, record.sessionId)
            if (file == null) {
                null
            } else {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
        }
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(record.coverBrush()),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(
                bitmap = requireNotNull(cover),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = record.mime.substringAfter('/', strings.fallbackVideo).uppercase(locale),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingMoreItem(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
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
                    text = stringResource(R.string.empty_video_records),
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
    highlightAngle: () -> Float = { 45f },
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
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            angle = highlightAngle(),
                            falloff = 2f
                        )
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                }
            ),
        content = content
    )
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

@Composable
private fun videoRecordStrings(): VideoRecordStrings {
    return VideoRecordStrings(
        fallbackVideo = stringResource(R.string.video_record_fallback_video),
        unknownSize = stringResource(R.string.video_record_unknown_size),
        emptyValue = stringResource(R.string.video_record_empty_value),
        codecFormat = stringResource(R.string.video_record_codec_format),
        bitrateAttributeFormat = stringResource(R.string.video_record_attribute_bitrate_format),
        frameRateAttributeFormat = stringResource(R.string.video_record_attribute_frame_rate_format),
        rotationAttributeFormat = stringResource(R.string.video_record_attribute_rotation_format),
        colorAttributeFormat = stringResource(R.string.video_record_attribute_color_format),
        profileAttributeFormat = stringResource(R.string.video_record_attribute_profile_format),
        levelAttributeFormat = stringResource(R.string.video_record_attribute_level_format),
        bitrateKbpsFormat = stringResource(R.string.video_record_bitrate_kbps_format),
        colorStandardBt709 = stringResource(R.string.video_record_color_standard_bt709),
        colorStandardBt601Pal = stringResource(R.string.video_record_color_standard_bt601_pal),
        colorStandardBt601Ntsc = stringResource(R.string.video_record_color_standard_bt601_ntsc),
        colorStandardBt2020 = stringResource(R.string.video_record_color_standard_bt2020),
        colorStandardUnknownFormat = stringResource(R.string.video_record_color_standard_unknown_format),
        colorRangeFull = stringResource(R.string.video_record_color_range_full),
        colorRangeLimited = stringResource(R.string.video_record_color_range_limited),
        colorRangeUnknownFormat = stringResource(R.string.video_record_color_range_unknown_format),
        colorTransferLinear = stringResource(R.string.video_record_color_transfer_linear),
        colorTransferSdr = stringResource(R.string.video_record_color_transfer_sdr),
        colorTransferPq = stringResource(R.string.video_record_color_transfer_pq),
        colorTransferHlg = stringResource(R.string.video_record_color_transfer_hlg),
        colorTransferUnknownFormat = stringResource(R.string.video_record_color_transfer_unknown_format),
        profileUnknownFormat = stringResource(R.string.video_record_profile_unknown_format),
        levelUnknownFormat = stringResource(R.string.video_record_level_unknown_format),
        timeRangeFormat = stringResource(R.string.video_record_time_range_format)
    )
}

private fun VideoRecord.primaryTitle(strings: VideoRecordStrings): String {
    return buildString {
        append(resolutionLabel(strings))
        append(" ")
        append(mime.ifBlank { strings.fallbackVideo })
    }
}

private fun VideoRecord.codecLine(strings: VideoRecordStrings): String {
    return strings.codecFormat.formatLocalized(codecName ?: strings.emptyValue)
}

private fun VideoRecord.attributeLabels(strings: VideoRecordStrings): List<AttributeLabel> {
    return listOfNotNull(
        bitrateAttributeLabel(strings),
        frameRateAttributeLabel(strings),
        AttributeLabel(strings.rotationAttributeFormat, rotationLabel(strings)),
        AttributeLabel(strings.colorAttributeFormat, colorLabel(strings)),
        profileAttributeLabel(strings),
        levelAttributeLabel(strings)
    )
}

private fun VideoRecord.bitrateAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val bitrate = bitrateKbps?.takeIf { it > 0 } ?: return null
    return AttributeLabel(
        format = strings.bitrateAttributeFormat,
        value = strings.bitrateKbpsFormat.formatLocalized(bitrate)
    )
}

private fun VideoRecord.frameRateAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val frameRate = frameRate?.takeIf { it.isFinite() && it > 0f } ?: return null
    val label = if (frameRate.rem(1f) == 0f) {
        frameRate.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", frameRate)
    }
    return AttributeLabel(strings.frameRateAttributeFormat, label)
}

private fun VideoRecord.rotationLabel(strings: VideoRecordStrings): String {
    return rotationDegrees?.let { "${it}°" } ?: strings.emptyValue
}

private fun VideoRecord.colorLabel(strings: VideoRecordStrings): String {
    val standard = colorStandard?.let { colorStandardLabel(it, strings) }
    val range = colorRange?.let { colorRangeLabel(it, strings) }
    val transfer = colorTransfer?.let { colorTransferLabel(it, strings) }
    return listOfNotNull(standard, range, transfer)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("/")
        ?: strings.emptyValue
}

private fun colorStandardLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_STANDARD_BT709 -> strings.colorStandardBt709
        MediaFormat.COLOR_STANDARD_BT601_PAL -> strings.colorStandardBt601Pal
        MediaFormat.COLOR_STANDARD_BT601_NTSC -> strings.colorStandardBt601Ntsc
        MediaFormat.COLOR_STANDARD_BT2020 -> strings.colorStandardBt2020
        else -> strings.colorStandardUnknownFormat.formatLocalized(value)
    }
}

private fun colorRangeLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_RANGE_FULL -> strings.colorRangeFull
        MediaFormat.COLOR_RANGE_LIMITED -> strings.colorRangeLimited
        else -> strings.colorRangeUnknownFormat.formatLocalized(value)
    }
}

private fun colorTransferLabel(value: Int, strings: VideoRecordStrings): String {
    return when (value) {
        MediaFormat.COLOR_TRANSFER_LINEAR -> strings.colorTransferLinear
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> strings.colorTransferSdr
        MediaFormat.COLOR_TRANSFER_ST2084 -> strings.colorTransferPq
        MediaFormat.COLOR_TRANSFER_HLG -> strings.colorTransferHlg
        else -> strings.colorTransferUnknownFormat.formatLocalized(value)
    }
}

private fun VideoRecord.profileAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val profileValue = profile ?: return null
    val label = codecProfileLabel(mime, profileValue, strings) ?: return null
    return AttributeLabel(strings.profileAttributeFormat, label)
}

private fun VideoRecord.levelAttributeLabel(strings: VideoRecordStrings): AttributeLabel? {
    val levelValue = level ?: return null
    val label = codecLevelLabel(mime, levelValue, strings) ?: return null
    return AttributeLabel(strings.levelAttributeFormat, label)
}

private fun codecProfileLabel(mime: String, value: Int, strings: VideoRecordStrings): String? {
    return when (mime.normalizedMime()) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> avcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_HEVC -> hevcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP8 -> vp8ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_AV1 -> av1ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> dolbyVisionProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_H263 -> h263ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> mpeg2ProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> mpeg4ProfileLabel(value)
        MIME_VIDEO_VVC -> vvcProfileLabel(value)
        MediaFormat.MIMETYPE_VIDEO_RAW -> null
        else -> return null
    } ?: strings.profileUnknownFormat.formatLocalized(value)
}

private fun codecLevelLabel(mime: String, value: Int, strings: VideoRecordStrings): String? {
    return when (mime.normalizedMime()) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> avcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_HEVC -> hevcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP8 -> vp8LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_VP9 -> vp9LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_AV1 -> av1LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> dolbyVisionLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_H263 -> h263LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG2 -> mpeg2LevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_MPEG4 -> mpeg4LevelLabel(value)
        MIME_VIDEO_VVC -> vvcLevelLabel(value)
        MediaFormat.MIMETYPE_VIDEO_RAW -> null
        else -> return null
    } ?: strings.levelUnknownFormat.formatLocalized(value)
}

private fun avcProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AVCProfileBaseline -> "Baseline"
    CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
    CodecProfileLevel.AVCProfileMain -> "Main"
    CodecProfileLevel.AVCProfileExtended -> "Extended"
    CodecProfileLevel.AVCProfileHigh -> "High"
    CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
    CodecProfileLevel.AVCProfileHigh10 -> "High 10"
    CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
    CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
    else -> null
}

private fun avcLevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AVCLevel1 -> "L1"
    CodecProfileLevel.AVCLevel1b -> "L1b"
    CodecProfileLevel.AVCLevel11 -> "L1.1"
    CodecProfileLevel.AVCLevel12 -> "L1.2"
    CodecProfileLevel.AVCLevel13 -> "L1.3"
    CodecProfileLevel.AVCLevel2 -> "L2"
    CodecProfileLevel.AVCLevel21 -> "L2.1"
    CodecProfileLevel.AVCLevel22 -> "L2.2"
    CodecProfileLevel.AVCLevel3 -> "L3"
    CodecProfileLevel.AVCLevel31 -> "L3.1"
    CodecProfileLevel.AVCLevel32 -> "L3.2"
    CodecProfileLevel.AVCLevel4 -> "L4"
    CodecProfileLevel.AVCLevel41 -> "L4.1"
    CodecProfileLevel.AVCLevel42 -> "L4.2"
    CodecProfileLevel.AVCLevel5 -> "L5"
    CodecProfileLevel.AVCLevel51 -> "L5.1"
    CodecProfileLevel.AVCLevel52 -> "L5.2"
    CodecProfileLevel.AVCLevel6 -> "L6"
    CodecProfileLevel.AVCLevel61 -> "L6.1"
    CodecProfileLevel.AVCLevel62 -> "L6.2"
    else -> null
}

private fun hevcProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.HEVCProfileMain -> "Main"
    CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
    CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
    CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
    CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR10+"
    HEVC_PROFILE_MAIN_400 -> "Main 4:0:0"
    HEVC_PROFILE_MAIN_444 -> "Main 4:4:4"
    else -> null
}

private fun hevcLevelLabel(value: Int): String? {
    return hevcTierLevelLabel(value, tier = "Main", levels = HevcMainTierLevels)
        ?: hevcTierLevelLabel(value, tier = "High", levels = HevcHighTierLevels)
}

private fun hevcTierLevelLabel(
    value: Int,
    tier: String,
    levels: List<Pair<Int, String>>
): String? {
    val label = levels.firstOrNull { it.first == value }?.second ?: return null
    return "$tier $label"
}

private val HevcMainTierLevels = listOf(
    CodecProfileLevel.HEVCMainTierLevel1 to "L1",
    CodecProfileLevel.HEVCMainTierLevel2 to "L2",
    CodecProfileLevel.HEVCMainTierLevel21 to "L2.1",
    CodecProfileLevel.HEVCMainTierLevel3 to "L3",
    CodecProfileLevel.HEVCMainTierLevel31 to "L3.1",
    CodecProfileLevel.HEVCMainTierLevel4 to "L4",
    CodecProfileLevel.HEVCMainTierLevel41 to "L4.1",
    CodecProfileLevel.HEVCMainTierLevel5 to "L5",
    CodecProfileLevel.HEVCMainTierLevel51 to "L5.1",
    CodecProfileLevel.HEVCMainTierLevel52 to "L5.2",
    CodecProfileLevel.HEVCMainTierLevel6 to "L6",
    CodecProfileLevel.HEVCMainTierLevel61 to "L6.1",
    CodecProfileLevel.HEVCMainTierLevel62 to "L6.2"
)

private val HevcHighTierLevels = listOf(
    CodecProfileLevel.HEVCHighTierLevel1 to "L1",
    CodecProfileLevel.HEVCHighTierLevel2 to "L2",
    CodecProfileLevel.HEVCHighTierLevel21 to "L2.1",
    CodecProfileLevel.HEVCHighTierLevel3 to "L3",
    CodecProfileLevel.HEVCHighTierLevel31 to "L3.1",
    CodecProfileLevel.HEVCHighTierLevel4 to "L4",
    CodecProfileLevel.HEVCHighTierLevel41 to "L4.1",
    CodecProfileLevel.HEVCHighTierLevel5 to "L5",
    CodecProfileLevel.HEVCHighTierLevel51 to "L5.1",
    CodecProfileLevel.HEVCHighTierLevel52 to "L5.2",
    CodecProfileLevel.HEVCHighTierLevel6 to "L6",
    CodecProfileLevel.HEVCHighTierLevel61 to "L6.1",
    CodecProfileLevel.HEVCHighTierLevel62 to "L6.2"
)

private fun vp8ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP8ProfileMain -> "Main"
    else -> null
}

private fun vp8LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP8Level_Version0 -> "Version 0"
    CodecProfileLevel.VP8Level_Version1 -> "Version 1"
    CodecProfileLevel.VP8Level_Version2 -> "Version 2"
    CodecProfileLevel.VP8Level_Version3 -> "Version 3"
    else -> null
}

private fun vp9ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP9Profile0 -> "Profile 0"
    CodecProfileLevel.VP9Profile1 -> "Profile 1"
    CodecProfileLevel.VP9Profile2 -> "Profile 2"
    CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
    CodecProfileLevel.VP9Profile2HDR10Plus -> "Profile 2 HDR10+"
    CodecProfileLevel.VP9Profile3 -> "Profile 3"
    CodecProfileLevel.VP9Profile3HDR -> "Profile 3 HDR"
    CodecProfileLevel.VP9Profile3HDR10Plus -> "Profile 3 HDR10+"
    else -> null
}

private fun vp9LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.VP9Level1 -> "L1"
    CodecProfileLevel.VP9Level11 -> "L1.1"
    CodecProfileLevel.VP9Level2 -> "L2"
    CodecProfileLevel.VP9Level21 -> "L2.1"
    CodecProfileLevel.VP9Level3 -> "L3"
    CodecProfileLevel.VP9Level31 -> "L3.1"
    CodecProfileLevel.VP9Level4 -> "L4"
    CodecProfileLevel.VP9Level41 -> "L4.1"
    CodecProfileLevel.VP9Level5 -> "L5"
    CodecProfileLevel.VP9Level51 -> "L5.1"
    CodecProfileLevel.VP9Level52 -> "L5.2"
    CodecProfileLevel.VP9Level6 -> "L6"
    CodecProfileLevel.VP9Level61 -> "L6.1"
    CodecProfileLevel.VP9Level62 -> "L6.2"
    else -> null
}

private fun av1ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AV1ProfileMain8 -> "Main 8"
    CodecProfileLevel.AV1ProfileMain10 -> "Main 10"
    CodecProfileLevel.AV1ProfileMain10HDR10 -> "Main 10 HDR10"
    CodecProfileLevel.AV1ProfileMain10HDR10Plus -> "Main 10 HDR10+"
    else -> null
}

private fun av1LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.AV1Level2 -> "L2"
    CodecProfileLevel.AV1Level21 -> "L2.1"
    CodecProfileLevel.AV1Level22 -> "L2.2"
    CodecProfileLevel.AV1Level23 -> "L2.3"
    CodecProfileLevel.AV1Level3 -> "L3"
    CodecProfileLevel.AV1Level31 -> "L3.1"
    CodecProfileLevel.AV1Level32 -> "L3.2"
    CodecProfileLevel.AV1Level33 -> "L3.3"
    CodecProfileLevel.AV1Level4 -> "L4"
    CodecProfileLevel.AV1Level41 -> "L4.1"
    CodecProfileLevel.AV1Level42 -> "L4.2"
    CodecProfileLevel.AV1Level43 -> "L4.3"
    CodecProfileLevel.AV1Level5 -> "L5"
    CodecProfileLevel.AV1Level51 -> "L5.1"
    CodecProfileLevel.AV1Level52 -> "L5.2"
    CodecProfileLevel.AV1Level53 -> "L5.3"
    CodecProfileLevel.AV1Level6 -> "L6"
    CodecProfileLevel.AV1Level61 -> "L6.1"
    CodecProfileLevel.AV1Level62 -> "L6.2"
    CodecProfileLevel.AV1Level63 -> "L6.3"
    CodecProfileLevel.AV1Level7 -> "L7"
    CodecProfileLevel.AV1Level71 -> "L7.1"
    CodecProfileLevel.AV1Level72 -> "L7.2"
    CodecProfileLevel.AV1Level73 -> "L7.3"
    else -> null
}

private fun dolbyVisionProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.DolbyVisionProfileDvavPer -> "dvav.01"
    CodecProfileLevel.DolbyVisionProfileDvavPen -> "dvav.02"
    CodecProfileLevel.DolbyVisionProfileDvheDer -> "dvhe.03"
    CodecProfileLevel.DolbyVisionProfileDvheDen -> "dvhe.04"
    CodecProfileLevel.DolbyVisionProfileDvheDtr -> "dvhe.05"
    CodecProfileLevel.DolbyVisionProfileDvheStn -> "dvhe.06"
    CodecProfileLevel.DolbyVisionProfileDvheDth -> "dvhe.07"
    CodecProfileLevel.DolbyVisionProfileDvheDtb -> "dvhe.08"
    CodecProfileLevel.DolbyVisionProfileDvheSt -> "dvhe.09"
    CodecProfileLevel.DolbyVisionProfileDvav110 -> "dvav.10"
    CodecProfileLevel.DolbyVisionProfileDvavSe -> "dvav.11"
    else -> null
}

private fun dolbyVisionLevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.DolbyVisionLevelHd24 -> "HD 24"
    CodecProfileLevel.DolbyVisionLevelHd30 -> "HD 30"
    CodecProfileLevel.DolbyVisionLevelFhd24 -> "FHD 24"
    CodecProfileLevel.DolbyVisionLevelFhd30 -> "FHD 30"
    CodecProfileLevel.DolbyVisionLevelFhd60 -> "FHD 60"
    CodecProfileLevel.DolbyVisionLevelUhd24 -> "UHD 24"
    CodecProfileLevel.DolbyVisionLevelUhd30 -> "UHD 30"
    CodecProfileLevel.DolbyVisionLevelUhd48 -> "UHD 48"
    CodecProfileLevel.DolbyVisionLevelUhd60 -> "UHD 60"
    CodecProfileLevel.DolbyVisionLevelUhd120 -> "UHD 120"
    CodecProfileLevel.DolbyVisionLevel8k30 -> "8K 30"
    CodecProfileLevel.DolbyVisionLevel8k60 -> "8K 60"
    else -> null
}

private fun h263ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.H263ProfileBaseline -> "Baseline"
    CodecProfileLevel.H263ProfileH320Coding -> "H.320"
    CodecProfileLevel.H263ProfileBackwardCompatible -> "Backward Compatible"
    CodecProfileLevel.H263ProfileISWV2 -> "ISW V2"
    CodecProfileLevel.H263ProfileISWV3 -> "ISW V3"
    CodecProfileLevel.H263ProfileHighCompression -> "High Compression"
    CodecProfileLevel.H263ProfileInternet -> "Internet"
    CodecProfileLevel.H263ProfileInterlace -> "Interlace"
    CodecProfileLevel.H263ProfileHighLatency -> "High Latency"
    else -> null
}

private fun h263LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.H263Level10 -> "L10"
    CodecProfileLevel.H263Level20 -> "L20"
    CodecProfileLevel.H263Level30 -> "L30"
    CodecProfileLevel.H263Level40 -> "L40"
    CodecProfileLevel.H263Level45 -> "L45"
    CodecProfileLevel.H263Level50 -> "L50"
    CodecProfileLevel.H263Level60 -> "L60"
    CodecProfileLevel.H263Level70 -> "L70"
    else -> null
}

private fun mpeg2ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG2ProfileSimple -> "Simple"
    CodecProfileLevel.MPEG2ProfileMain -> "Main"
    CodecProfileLevel.MPEG2ProfileSNR -> "SNR"
    CodecProfileLevel.MPEG2ProfileSpatial -> "Spatial"
    CodecProfileLevel.MPEG2ProfileHigh -> "High"
    CodecProfileLevel.MPEG2Profile422 -> "4:2:2"
    else -> null
}

private fun mpeg2LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG2LevelLL -> "Low"
    CodecProfileLevel.MPEG2LevelML -> "Main"
    CodecProfileLevel.MPEG2LevelH14 -> "High 1440"
    CodecProfileLevel.MPEG2LevelHL -> "High"
    CodecProfileLevel.MPEG2LevelHP -> "HighP"
    else -> null
}

private fun mpeg4ProfileLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG4ProfileSimple -> "Simple"
    CodecProfileLevel.MPEG4ProfileSimpleScalable -> "Simple Scalable"
    CodecProfileLevel.MPEG4ProfileCore -> "Core"
    CodecProfileLevel.MPEG4ProfileMain -> "Main"
    CodecProfileLevel.MPEG4ProfileNbit -> "N-bit"
    CodecProfileLevel.MPEG4ProfileScalableTexture -> "Scalable Texture"
    CodecProfileLevel.MPEG4ProfileSimpleFace -> "Simple Face"
    CodecProfileLevel.MPEG4ProfileSimpleFBA -> "Simple FBA"
    CodecProfileLevel.MPEG4ProfileBasicAnimated -> "Basic Animated"
    CodecProfileLevel.MPEG4ProfileHybrid -> "Hybrid"
    CodecProfileLevel.MPEG4ProfileAdvancedRealTime -> "Advanced Real Time"
    CodecProfileLevel.MPEG4ProfileCoreScalable -> "Core Scalable"
    CodecProfileLevel.MPEG4ProfileAdvancedCoding -> "Advanced Coding"
    CodecProfileLevel.MPEG4ProfileAdvancedCore -> "Advanced Core"
    CodecProfileLevel.MPEG4ProfileAdvancedScalable -> "Advanced Scalable"
    CodecProfileLevel.MPEG4ProfileAdvancedSimple -> "Advanced Simple"
    else -> null
}

private fun mpeg4LevelLabel(value: Int): String? = when (value) {
    CodecProfileLevel.MPEG4Level0 -> "L0"
    CodecProfileLevel.MPEG4Level0b -> "L0b"
    CodecProfileLevel.MPEG4Level1 -> "L1"
    CodecProfileLevel.MPEG4Level2 -> "L2"
    CodecProfileLevel.MPEG4Level3 -> "L3"
    CodecProfileLevel.MPEG4Level3b -> "L3b"
    CodecProfileLevel.MPEG4Level4 -> "L4"
    CodecProfileLevel.MPEG4Level4a -> "L4a"
    CodecProfileLevel.MPEG4Level5 -> "L5"
    CodecProfileLevel.MPEG4Level6 -> "L6"
    else -> null
}

private fun vvcProfileLabel(value: Int): String? = when (value) {
    VVC_PROFILE_MAIN_8 -> "Main 8"
    VVC_PROFILE_MAIN_10 -> "Main 10"
    VVC_PROFILE_MAIN_10_HDR10 -> "Main 10 HDR10"
    VVC_PROFILE_MAIN_10_HDR10_PLUS -> "Main 10 HDR10+"
    VVC_PROFILE_MAIN_10_STILL -> "Main 10 Still"
    else -> null
}

private fun vvcLevelLabel(value: Int): String? {
    return vvcTierLevelLabel(value, tier = "Main", levels = VvcMainTierLevels)
        ?: vvcTierLevelLabel(value, tier = "High", levels = VvcHighTierLevels)
}

private fun vvcTierLevelLabel(
    value: Int,
    tier: String,
    levels: List<Pair<Int, String>>
): String? {
    val label = levels.firstOrNull { it.first == value }?.second ?: return null
    return "$tier $label"
}

private val VvcMainTierLevels = listOf(
    VVC_MAIN_TIER_LEVEL_10 to "L1",
    VVC_MAIN_TIER_LEVEL_20 to "L2",
    VVC_MAIN_TIER_LEVEL_21 to "L2.1",
    VVC_MAIN_TIER_LEVEL_30 to "L3",
    VVC_MAIN_TIER_LEVEL_31 to "L3.1",
    VVC_MAIN_TIER_LEVEL_40 to "L4",
    VVC_MAIN_TIER_LEVEL_41 to "L4.1",
    VVC_MAIN_TIER_LEVEL_50 to "L5",
    VVC_MAIN_TIER_LEVEL_51 to "L5.1",
    VVC_MAIN_TIER_LEVEL_52 to "L5.2",
    VVC_MAIN_TIER_LEVEL_60 to "L6",
    VVC_MAIN_TIER_LEVEL_61 to "L6.1",
    VVC_MAIN_TIER_LEVEL_62 to "L6.2",
    VVC_MAIN_TIER_LEVEL_63 to "L6.3"
)

private val VvcHighTierLevels = listOf(
    VVC_HIGH_TIER_LEVEL_40 to "L4",
    VVC_HIGH_TIER_LEVEL_41 to "L4.1",
    VVC_HIGH_TIER_LEVEL_50 to "L5",
    VVC_HIGH_TIER_LEVEL_51 to "L5.1",
    VVC_HIGH_TIER_LEVEL_52 to "L5.2",
    VVC_HIGH_TIER_LEVEL_60 to "L6",
    VVC_HIGH_TIER_LEVEL_61 to "L6.1",
    VVC_HIGH_TIER_LEVEL_62 to "L6.2",
    VVC_HIGH_TIER_LEVEL_63 to "L6.3"
)

private fun VideoRecord.resolutionLabel(strings: VideoRecordStrings): String {
    return if (width != null && height != null) "${width}x$height" else strings.unknownSize
}

private fun VideoRecord.aspectRatio(): Float? {
    val width = width?.takeIf { it > 0 } ?: return null
    val height = height?.takeIf { it > 0 } ?: return null
    return width.toFloat() / height.toFloat()
}

private fun VideoRecord.coverAspectRatio(): Float {
    return aspectRatio()?.coerceIn(
        CoverMinAspectRatio,
        CoverMaxAspectRatio
    ) ?: CoverDefaultAspectRatio
}

private fun VideoRecord.usesSideCoverLayout(): Boolean {
    return (aspectRatio() ?: return false) < PortraitCoverAspectThreshold
}

private fun VideoRecord.coverFrame(): CoverFrame {
    val aspectRatio = coverAspectRatio()
    val width = (PortraitCoverHeight * aspectRatio).coerceIn(
        PortraitCoverMinWidth,
        PortraitCoverMaxWidth
    )
    return CoverFrame(width = width)
}

private fun resolutionBadgeSize(aspectRatio: Float): Pair<Dp, Dp> {
    val safeAspectRatio = aspectRatio.coerceIn(
        ResolutionBadgeMinAspectRatio,
        ResolutionBadgeMaxAspectRatio
    )
    val frameAspectRatio = ResolutionBadgeWidth.value / ResolutionBadgeHeight.value

    return if (safeAspectRatio >= frameAspectRatio) {
        ResolutionBadgeWidth to ResolutionBadgeWidth / safeAspectRatio
    } else {
        ResolutionBadgeHeight * safeAspectRatio to ResolutionBadgeHeight
    }
}

private fun VideoRecord.dateTitle(): String {
    return timestampMs().toZonedDateTime().format(DAY_FORMATTER)
}

private fun VideoRecord.timeRangeTitle(strings: VideoRecordStrings): String {
    val firstSeen = firstSeenAtMs.toZonedDateTime().format(TIME_FORMATTER)
    val lastSeen = lastSeenAtMs.toZonedDateTime().format(TIME_FORMATTER)
    return if (firstSeen == lastSeen) {
        firstSeen
    } else {
        strings.timeRangeFormat.formatLocalized(firstSeen, lastSeen)
    }
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

private const val MIME_VIDEO_VVC = "video/vvc"

private const val HEVC_PROFILE_MAIN_400 = 8
private const val HEVC_PROFILE_MAIN_444 = 16

private const val VVC_PROFILE_MAIN_8 = 1
private const val VVC_PROFILE_MAIN_10 = 2
private const val VVC_PROFILE_MAIN_10_STILL = 4
private const val VVC_PROFILE_MAIN_10_HDR10 = 4096
private const val VVC_PROFILE_MAIN_10_HDR10_PLUS = 8192

private const val VVC_MAIN_TIER_LEVEL_10 = 1
private const val VVC_MAIN_TIER_LEVEL_20 = 2
private const val VVC_MAIN_TIER_LEVEL_21 = 4
private const val VVC_MAIN_TIER_LEVEL_30 = 8
private const val VVC_MAIN_TIER_LEVEL_31 = 16
private const val VVC_MAIN_TIER_LEVEL_40 = 32
private const val VVC_MAIN_TIER_LEVEL_41 = 128
private const val VVC_MAIN_TIER_LEVEL_50 = 512
private const val VVC_MAIN_TIER_LEVEL_51 = 2048
private const val VVC_MAIN_TIER_LEVEL_52 = 8192
private const val VVC_MAIN_TIER_LEVEL_60 = 32768
private const val VVC_MAIN_TIER_LEVEL_61 = 131072
private const val VVC_MAIN_TIER_LEVEL_62 = 524288
private const val VVC_MAIN_TIER_LEVEL_63 = 2097152

private const val VVC_HIGH_TIER_LEVEL_40 = 64
private const val VVC_HIGH_TIER_LEVEL_41 = 256
private const val VVC_HIGH_TIER_LEVEL_50 = 1024
private const val VVC_HIGH_TIER_LEVEL_51 = 4096
private const val VVC_HIGH_TIER_LEVEL_52 = 16384
private const val VVC_HIGH_TIER_LEVEL_60 = 65536
private const val VVC_HIGH_TIER_LEVEL_61 = 262144
private const val VVC_HIGH_TIER_LEVEL_62 = 1048576
private const val VVC_HIGH_TIER_LEVEL_63 = 4194304

private data class VideoRecordStrings(
    val fallbackVideo: String,
    val unknownSize: String,
    val emptyValue: String,
    val codecFormat: String,
    val bitrateAttributeFormat: String,
    val frameRateAttributeFormat: String,
    val rotationAttributeFormat: String,
    val colorAttributeFormat: String,
    val profileAttributeFormat: String,
    val levelAttributeFormat: String,
    val bitrateKbpsFormat: String,
    val colorStandardBt709: String,
    val colorStandardBt601Pal: String,
    val colorStandardBt601Ntsc: String,
    val colorStandardBt2020: String,
    val colorStandardUnknownFormat: String,
    val colorRangeFull: String,
    val colorRangeLimited: String,
    val colorRangeUnknownFormat: String,
    val colorTransferLinear: String,
    val colorTransferSdr: String,
    val colorTransferPq: String,
    val colorTransferHlg: String,
    val colorTransferUnknownFormat: String,
    val profileUnknownFormat: String,
    val levelUnknownFormat: String,
    val timeRangeFormat: String
)

private data class CoverFrame(
    val width: Dp
)

private data class VideoRecordPage(
    val records: List<VideoRecord>,
    val totalCount: Int,
    val hasMore: Boolean
)

private data class AttributeLabel(
    val format: String,
    val value: String
)

private fun AttributeLabel.toAnnotatedString(): AnnotatedString {
    val markedText = format.formatLocalized(AttributeValueMarker)
    val markerStart = markedText.indexOf(AttributeValueMarker)
    if (markerStart < 0) {
        return AnnotatedString(format.formatLocalized(value))
    }

    val markerEnd = markerStart + AttributeValueMarker.length
    val titlePrefix = markedText.substring(0, markerStart)
    val titleSuffix = markedText.substring(markerEnd)
    return buildAnnotatedString {
        appendChipTitle(titlePrefix)
        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
            append(value)
        }
        appendChipTitle(titleSuffix)
    }
}

private fun AnnotatedString.Builder.appendChipTitle(text: String) {
    if (text.isEmpty()) return
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(text)
    }
}

private fun String.normalizedMime(): String = substringBefore(';').trim().lowercase(Locale.ROOT)

private fun String.formatLocalized(vararg args: Any): String =
    String.format(Locale.getDefault(), this, *args)

private const val AttributeValueMarker = "__MEDIA_CODECAT_ATTRIBUTE_VALUE__"

private const val VideoRecordsPageSize = 30

private const val LoadMorePrefetchItemThreshold = 6

private const val LoadingMoreItemKey = "loading_more"

private val GalleryHeaderContentHeight = 132.dp

private val GalleryHeaderMaskHeight = 156.dp

private val ResolutionBadgeWidth = 22.dp

private val ResolutionBadgeHeight = 14.dp

private val ResolutionBadgeCornerRadius = 3.dp

private val ResolutionBadgeStrokeWidth = 1.4.dp

private val InfoLeadingSlotWidth = 22.dp

private val InfoLeadingGap = 6.dp

private const val ResolutionBadgeDefaultAspectRatio = 16f / 9f

private const val ResolutionBadgeMinAspectRatio = 0.5f

private const val ResolutionBadgeMaxAspectRatio = 2.75f

private val PortraitCoverHeight = 132.dp

private val PortraitCoverMinWidth = 68.dp

private val PortraitCoverMaxWidth = 96.dp

private const val CoverDefaultAspectRatio = 16f / 9f

private const val CoverMinAspectRatio = 0.42f

private const val CoverMaxAspectRatio = 2.6f

private const val PortraitCoverAspectThreshold = 0.92f

private val PackageIconSize = 16.dp

private const val PackageIconBitmapSizePx = 96

private val CompactWidthBreakpoint = 600.dp

private val WideGridMinCellWidth = 280.dp
