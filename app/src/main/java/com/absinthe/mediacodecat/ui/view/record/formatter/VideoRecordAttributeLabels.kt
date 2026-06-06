package com.absinthe.mediacodecat.ui.view.record.formatter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal data class AttributeLabel(
    val format: String,
    val value: String
)

internal fun AttributeLabel.toAnnotatedString(): AnnotatedString {
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

private const val AttributeValueMarker = "__MEDIA_CODECAT_ATTRIBUTE_VALUE__"
