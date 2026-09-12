package com.adipginting.lecturo.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The three glyphs this app needs that `material-icons-core` doesn't ship.
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

/** Material Symbols `bookmark`: text kept while reading. */
val Icons.Filled.Saved: ImageVector
    get() = icon("Bookmark", "M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z")

/** Material Symbols `chat_bubble`: conversations. */
val Icons.Filled.Chats: ImageVector
    get() = icon("ChatBubble", "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z")

/** Material Symbols `memory`: the model and provider chooser. */
val Icons.Filled.Model: ImageVector
    get() = icon(
        "Memory",
        "M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3" +
            "v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z",
    )
