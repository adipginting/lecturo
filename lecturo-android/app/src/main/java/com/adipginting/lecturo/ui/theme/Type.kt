package com.adipginting.lecturo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.adipginting.lecturo.R

/** The app's typeface, as drawn in Penpot. Regular/Medium/SemiBold/Bold. */
private val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

private val Base = Typography()

// Every Material style carries the app's family, so no screen falls back to the
// platform default.
val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = PlusJakartaSans),
    displayMedium = Base.displayMedium.copy(fontFamily = PlusJakartaSans),
    displaySmall = Base.displaySmall.copy(fontFamily = PlusJakartaSans),
    headlineLarge = Base.headlineLarge.copy(fontFamily = PlusJakartaSans),
    headlineMedium = Base.headlineMedium.copy(fontFamily = PlusJakartaSans),
    headlineSmall = Base.headlineSmall.copy(fontFamily = PlusJakartaSans),
    titleLarge = Base.titleLarge.copy(fontFamily = PlusJakartaSans),
    titleMedium = Base.titleMedium.copy(fontFamily = PlusJakartaSans),
    titleSmall = Base.titleSmall.copy(fontFamily = PlusJakartaSans),
    bodyLarge = Base.bodyLarge.copy(fontFamily = PlusJakartaSans),
    bodyMedium = Base.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = Base.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = Base.labelLarge.copy(fontFamily = PlusJakartaSans),
    labelMedium = Base.labelMedium.copy(fontFamily = PlusJakartaSans),
    labelSmall = Base.labelSmall.copy(fontFamily = PlusJakartaSans),
)
