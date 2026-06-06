package com.absinthe.mediacodecat.ui.view.record

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.absinthe.mediacodecat.data.VideoCoverStore
import com.absinthe.mediacodecat.model.VideoRecord
import com.absinthe.mediacodecat.ui.component.GlassPanel
import com.absinthe.mediacodecat.ui.view.record.formatter.VideoRecordStrings
import com.absinthe.mediacodecat.ui.view.record.formatter.aspectRatio
import com.absinthe.mediacodecat.ui.view.record.formatter.attributeLabels
import com.absinthe.mediacodecat.ui.view.record.formatter.codecLine
import com.absinthe.mediacodecat.ui.view.record.formatter.coverAspectRatio
import com.absinthe.mediacodecat.ui.view.record.formatter.coverFrame
import com.absinthe.mediacodecat.ui.view.record.formatter.normalizedMime
import com.absinthe.mediacodecat.ui.view.record.formatter.primaryTitle
import com.absinthe.mediacodecat.ui.view.record.formatter.timeRangeTitle
import com.absinthe.mediacodecat.ui.view.record.formatter.toAnnotatedString
import com.absinthe.mediacodecat.ui.view.record.formatter.usesSideCoverLayout
import com.absinthe.mediacodecat.ui.view.record.formatter.videoRecordStrings
import com.kyant.backdrop.Backdrop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun VideoRecordCard(
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
            RecordBitmapCache.getPackageIcon(packageName)?.asImageBitmap() ?: runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(
                        width = PackageIconBitmapSizePx,
                        height = PackageIconBitmapSizePx,
                        config = Bitmap.Config.ARGB_8888
                    )
            }.getOrNull()
                ?.also { RecordBitmapCache.putPackageIcon(packageName, it) }
                ?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier.size(PackageIconSize),
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
                val cacheKey = file.coverCacheKey()
                RecordBitmapCache.getCover(cacheKey)?.asImageBitmap()
                    ?: BitmapFactory.decodeFile(file.absolutePath)
                        ?.also { RecordBitmapCache.putCover(cacheKey, it) }
                        ?.asImageBitmap()
            }
        }
    }
    val shape = RoundedCornerShape(6.dp)
    val coverColors = record.coverColors(MaterialTheme.colorScheme)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(coverColors)),
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
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun VideoRecord.coverColors(colorScheme: ColorScheme): List<Color> {
    return when (sessionId.hashCode().mod(4)) {
        0 -> listOf(colorScheme.primaryContainer, colorScheme.tertiaryContainer)
        1 -> listOf(colorScheme.secondaryContainer, colorScheme.surfaceContainerHighest)
        2 -> listOf(colorScheme.tertiaryContainer, colorScheme.primary)
        else -> listOf(colorScheme.surfaceContainerHigh, colorScheme.secondaryContainer)
    }
}

private object RecordBitmapCache {
    private val packageIcons = sizedBitmapCache(PackageIconCacheMaxKilobytes)
    private val covers = sizedBitmapCache(CoverCacheMaxKilobytes)

    @Synchronized
    fun getPackageIcon(packageName: String): Bitmap? = packageIcons.get(packageName)

    @Synchronized
    fun putPackageIcon(packageName: String, bitmap: Bitmap) {
        packageIcons.put(packageName, bitmap)
    }

    @Synchronized
    fun getCover(cacheKey: String): Bitmap? = covers.get(cacheKey)

    @Synchronized
    fun putCover(cacheKey: String, bitmap: Bitmap) {
        covers.put(cacheKey, bitmap)
    }

    private fun sizedBitmapCache(maxSizeKilobytes: Int) = object : LruCache<String, Bitmap>(maxSizeKilobytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
}

private fun File.coverCacheKey(): String = "$absolutePath:${lastModified()}:${length()}"

private const val PackageIconCacheMaxKilobytes = 4 * 1024
private const val CoverCacheMaxKilobytes = 16 * 1024
