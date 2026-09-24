package com.adipginting.lecturo.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The glyphs this app needs that `material-icons-core` doesn't ship.
 *
 * Path data comes from Material Symbols (fonts.google.com/icons), Apache
 * License 2.0. Vendored as paths rather than depending on
 * `material-icons-extended`, which carries every icon for the sake of three.
 */
private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

/** Material Symbols `chat_bubble`: conversations. */
val Icons.Filled.Chats: ImageVector
    get() = icon("ChatBubble", "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z")

/** Material Symbols `auto_awesome`: skills, the prompt packs that run on a book. */
val Icons.Filled.Skills: ImageVector
    get() = icon(
        "AutoAwesome",
        "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm0 14l-1.25-2.75L15 19" +
            "l2.75-1.25L19 15l1.25 2.75L23 19l-2.75 1.25L19 23zM11.5 9.5L9 10.5l2.5 1 1 2.5 1-2.5 2.5-1" +
            "-2.5-1-1-2.5-1 2.5zM11.5 15.5L9 16.5l2.5 1 1 2.5 1-2.5 2.5-1-2.5-1-1-2.5-1 2.5z",
    )

/** Material Symbols `screenshot`: cropping a passage out of the page. */
val Icons.Filled.Screenshot: ImageVector
    get() = icon(
        "Screenshot",
        "M17 1.01 7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 18H7V6h10v12z" +
            "M9.5 8.5H12V7H8v4h1.5V8.5zM12 17h4v-4h-1.5v2.5H12V17z",
    )

/** Material Symbols `download`: saving a document from a URL. */
val Icons.Filled.Download: ImageVector
    get() = icon("Download", "M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z")

/** Material Symbols `memory`: the model and provider chooser. */
val Icons.Filled.Model: ImageVector
    get() = icon(
        "Memory",
        "M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3" +
            "v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z",
    )
