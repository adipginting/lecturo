package com.adipginting.lecturo.util

import java.text.DateFormat
import java.util.Date

/**
 * "Title · Page N · Sep 10, 3:41 PM". EPUB locators are spine hrefs rather
 * than page numbers, so the page label is shown only when [format] is pdf or,
 * absent a known format, when the locator is numeric.
 */
fun excerptMetadata(
    title: String?,
    locator: String?,
    timestamp: Long,
    format: String? = null,
): String {
    val isPdf = if (format != null) format == "pdf" else locator?.toIntOrNull() != null
    val page = if (isPdf && !locator.isNullOrBlank()) "Page $locator" else null
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(timestamp))
    return listOfNotNull(title?.takeIf { it.isNotBlank() } ?: "Unknown document", page, time)
        .joinToString(" · ")
}
