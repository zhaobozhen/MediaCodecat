package com.absinthe.mediacodecat.ui.view

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.absinthe.mediacodecat.BuildConfig
import com.absinthe.mediacodecat.R
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onOpenSourceNoticesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsHomeScreen(
        onOpenSourceNoticesClick = onOpenSourceNoticesClick,
        modifier = modifier
    )
}

@Composable
private fun SettingsHomeScreen(
    onOpenSourceNoticesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = settingsScreenColors()
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = safeDrawingPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = safeDrawingPadding.calculateTopPadding() + 24.dp,
                end = safeDrawingPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = safeDrawingPadding.calculateBottomPadding() + 112.dp
            ),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            modifier = Modifier.padding(start = 20.dp),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.title,
            fontWeight = FontWeight.SemiBold
        )

        AppInfoCard(
            colors = colors,
            onSourceCodeClick = {
                runCatching { uriHandler.openUri(SourceCodeUrl) }
            },
            onOpenSourceNoticesClick = onOpenSourceNoticesClick
        )
    }
}

@Composable
private fun AppInfoCard(
    colors: SettingsScreenColors,
    onSourceCodeClick: () -> Unit,
    onOpenSourceNoticesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            SmokeCardBackground(
                baseColor = colors.cardBackground,
                smokeColor = colors.cardSmoke,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.cardTitle,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_version_format,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.cardSubtitle,
                        textAlign = TextAlign.Center
                    )
                }

                AppInfoActionButtons(
                    colors = colors,
                    onSourceCodeClick = onSourceCodeClick,
                    onOpenSourceNoticesClick = onOpenSourceNoticesClick
                )
            }
        }
    }
}

@Composable
private fun SmokeCardBackground(
    baseColor: Color,
    smokeColor: Color,
    modifier: Modifier = Modifier
) {
    val timeSeconds by produceState(initialValue = 0f) {
        val startNanos = withFrameNanos { it }
        while (isActive) {
            value = (withFrameNanos { it } - startNanos) / NanosPerSecond
        }
    }
    val shader = rememberRuntimeShader(AppInfoSmokeShader)

    Canvas(modifier = modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", timeSeconds)
        shader.setFloatUniform("pointer", 0.5f, 0.5f)
        shader.setColorUniform("baseColor", baseColor.toArgb())
        shader.setColorUniform("smokeColor", smokeColor.toArgb())
        drawRect(brush = ShaderBrush(shader))
    }
}

@Composable
private fun rememberRuntimeShader(shaderSource: String): RuntimeShader {
    return androidx.compose.runtime.remember(shaderSource) {
        RuntimeShader(shaderSource)
    }
}

@Composable
private fun AppIcon(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(BuildConfig.APPLICATION_ID)
                    .toBitmap(
                        width = AppIconBitmapSizePx,
                        height = AppIconBitmapSizePx,
                        config = Bitmap.Config.ARGB_8888
                    )
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        icon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun AppInfoActionButtons(
    colors: SettingsScreenColors,
    onSourceCodeClick: () -> Unit,
    onOpenSourceNoticesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppInfoActionButton(
            text = stringResource(R.string.settings_source_code),
            colors = colors,
            onClick = onSourceCodeClick,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_github_24),
                    contentDescription = null,
                    tint = colors.cardActionContent,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        AppInfoActionButton(
            text = stringResource(R.string.settings_open_source),
            colors = colors,
            onClick = onOpenSourceNoticesClick,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.cardActionContent,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun AppInfoActionButton(
    text: String,
    colors: SettingsScreenColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, colors.cardActionBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.cardActionContent
        ),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun settingsScreenColors(): SettingsScreenColors {
    val colorScheme = MaterialTheme.colorScheme
    return SettingsScreenColors(
        background = colorScheme.background,
        title = colorScheme.onBackground,
        cardTitle = colorScheme.onPrimaryContainer,
        cardSubtitle = colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        cardActionContent = colorScheme.onPrimaryContainer,
        cardActionBorder = colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
        cardBackground = colorScheme.primaryContainer,
        cardSmoke = colorScheme.onPrimaryContainer
    )
}

private data class SettingsScreenColors(
    val background: Color,
    val title: Color,
    val cardTitle: Color,
    val cardSubtitle: Color,
    val cardActionContent: Color,
    val cardActionBorder: Color,
    val cardBackground: Color,
    val cardSmoke: Color
)

@Composable
fun OpenSourceNoticesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = settingsScreenColors()
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val context = LocalContext.current
    val libraries by produceLibraries {
        context.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { it.readText() }
    }

    LibrariesContainer(
        libraries = libraries,
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(
            start = safeDrawingPadding.calculateStartPadding(layoutDirection),
            end = safeDrawingPadding.calculateEndPadding(layoutDirection),
            bottom = navigationBarsPadding.calculateBottomPadding() + 16.dp
        ),
        colors = LibraryDefaults.libraryColors(
            libraryBackgroundColor = colors.background,
            libraryContentColor = colors.title,
            dialogBackgroundColor = colors.background,
            dialogContentColor = colors.title
        ),
        header = {
            item(key = OpenSourceNoticesHeaderKey) {
                OpenSourceNoticesHeader(onBack = onBack)
            }
        },
        divider = {
            HorizontalDivider()
        }
    )
}

@Composable
private fun OpenSourceNoticesHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = settingsScreenColors()
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = safeDrawingPadding.calculateStartPadding(layoutDirection) + 4.dp,
                top = safeDrawingPadding.calculateTopPadding() + 8.dp,
                end = safeDrawingPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = colors.title
            )
        }
        Text(
            text = stringResource(R.string.settings_open_source_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.title,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private const val OpenSourceNoticesHeaderKey = "open_source_notices_header"

private const val AppIconBitmapSizePx = 192
private const val SourceCodeUrl = "https://github.com/zhaobozhen/MediaCodecat"
private const val NanosPerSecond = 1_000_000_000f

private val AppInfoSmokeShader = """
    uniform float2 resolution;
    uniform float2 pointer;
    uniform float time;
    layout(color) uniform half4 baseColor;
    layout(color) uniform half4 smokeColor;

    float rand(float2 n) {
        return fract(sin(dot(n, float2(12.9898, 4.1414))) * 43758.5453);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);

        float a = rand(i);
        float b = rand(i + float2(1.0, 0.0));
        float c = rand(i + float2(0.0, 1.0));
        float d = rand(i + float2(1.0, 1.0));

        return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    }

    float fbm(float2 p) {
        float value = 0.0;
        float amplitude = 0.5;

        for(int i = 0; i < 6; i++) {
            value += amplitude * noise(p);
            p *= 2.0;
            amplitude *= 0.5;
        }

        return value;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution.xy;
        float2 aspect = float2(resolution.x / resolution.y, 1.0);
        uv = uv * aspect;

        float2 pointerInfluence = pointer * aspect;
        float pointerDist = length(uv - pointerInfluence);
        float pointerFactor = smoothstep(0.78, 0.0, pointerDist);

        float2 movement = float2(time * 0.014, time * 0.06);
        float turbulence = fbm(uv * 2.05 + movement);
        turbulence += fbm((uv + float2(turbulence)) * 1.35 - movement);

        float smokeMask = fbm(uv * 0.86 + turbulence + movement);
        smokeMask = smoothstep(0.06, 0.82, smokeMask + pointerFactor * 0.28);

        float vignette = smoothstep(1.25, 0.05, length((uv / aspect - 0.5) * float2(1.08, 1.52)));
        float3 finalColor = mix(baseColor.rgb, smokeColor.rgb, smokeMask * vignette * 0.74);
        finalColor += pointerFactor * 0.035;

        return half4(finalColor, 1.0);
    }
""".trimIndent()
