package com.absinthe.mediacodecat.ui.view

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.R
import com.absinthe.mediacodecat.data.DataSource
import com.absinthe.mediacodecat.data.VideoCoverStore
import com.absinthe.mediacodecat.data.VideoRecordContract
import com.absinthe.mediacodecat.model.VideoRecord
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
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
    var isLoaded by remember { mutableStateOf(false) }
    var coverVersion by remember { mutableIntStateOf(0) }

    fun refreshRecords() {
        scope.launch {
            records = loadVideoRecords(appContext)
            coverVersion++
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
    val headerBackdrop = rememberLayerBackdrop {
        drawContent()
    }

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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        GalleryHeader(
            backdrop = headerBackdrop,
            recordsCount = records.size,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecordGrid(
    backdrop: Backdrop,
    galleryItems: List<VideoGalleryItem>,
    coverVersion: Int,
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
            top = safeDrawingPadding.calculateTopPadding() + GalleryHeaderContentHeight,
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
                        coverVersion = coverVersion
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    val strings = videoRecordStrings()

    GlassPanel(
        backdrop = backdrop,
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
            AttributeChip(text = label)
        }
    }
}

@Composable
private fun AttributeChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
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
    val cover by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = record.sessionId,
        key3 = coverVersion
    ) {
        value = withContext(Dispatchers.IO) {
            val file = VideoCoverStore.coverFile(context, record.sessionId)
            if (!file.exists()) {
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
                text = record.mime.substringAfter('/', strings.fallbackVideo).uppercase(Locale.getDefault()),
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

@Composable
private fun recordCountTotalString(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.video_record_count_total, count, count)
}

@Composable
private fun recordCountString(count: Int): String {
    val resources = LocalContext.current.resources
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
        profileLevelFormat = stringResource(R.string.video_record_profile_level_format),
        levelFormat = stringResource(R.string.video_record_level_format),
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

private fun VideoRecord.attributeLabels(strings: VideoRecordStrings): List<String> {
    return listOf(
        strings.bitrateAttributeFormat.formatLocalized(bitrateLabel(strings)),
        strings.frameRateAttributeFormat.formatLocalized(frameRateLabel(strings)),
        strings.rotationAttributeFormat.formatLocalized(rotationLabel(strings)),
        strings.colorAttributeFormat.formatLocalized(colorLabel(strings)),
        strings.profileAttributeFormat.formatLocalized(profileLevelLabel(strings))
    )
}

private fun VideoRecord.bitrateLabel(strings: VideoRecordStrings): String {
    return bitrateKbps?.let { strings.bitrateKbpsFormat.formatLocalized(it) } ?: strings.emptyValue
}

private fun VideoRecord.frameRateLabel(strings: VideoRecordStrings): String {
    val frameRate = frameRate?.takeIf { it.isFinite() && it > 0f } ?: return strings.emptyValue
    return if (frameRate.rem(1f) == 0f) {
        frameRate.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", frameRate)
    }
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

private fun VideoRecord.profileLevelLabel(strings: VideoRecordStrings): String {
    return when {
        profile != null && level != null -> strings.profileLevelFormat.formatLocalized(profile, level)
        profile != null -> profile.toString()
        level != null -> strings.levelFormat.formatLocalized(level)
        else -> strings.emptyValue
    }
}

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
    val profileLevelFormat: String,
    val levelFormat: String,
    val timeRangeFormat: String
)

private data class CoverFrame(
    val width: Dp
)

private fun String.formatLocalized(vararg args: Any): String =
    String.format(Locale.getDefault(), this, *args)

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
