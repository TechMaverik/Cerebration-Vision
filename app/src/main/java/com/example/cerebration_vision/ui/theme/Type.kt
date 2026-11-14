package com.example.cerebration_vision.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.cerebration_vision.R

// The web app uses Ubuntu for display text and Ubuntu Mono for labels, buttons and data.
val Ubuntu = FontFamily(
    Font(R.font.ubuntu_regular, FontWeight.Normal),
    Font(R.font.ubuntu_medium, FontWeight.Medium),
    Font(R.font.ubuntu_bold, FontWeight.Bold),
)

val UbuntuMono = FontFamily(
    Font(R.font.ubuntu_mono_regular, FontWeight.Normal),
    Font(R.font.ubuntu_mono_medium, FontWeight.Medium),
    Font(R.font.ubuntu_mono_bold, FontWeight.Bold),
)

/** `.brand-Cerebration`: the header wordmark. */
val WordmarkStyle = TextStyle(
    fontFamily = Ubuntu,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    letterSpacing = (-0.02).em,
)

/** `.section-title` */
val SectionTitleStyle = TextStyle(
    fontFamily = Ubuntu,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.01).em,
)

/** `.section-desc`, `.endpoint-desc` */
val DescStyle = TextStyle(
    fontFamily = Ubuntu,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.5.sp,
)

/** `.endpoint-path`: card titles. */
val CardTitleStyle = TextStyle(
    fontFamily = Ubuntu,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
)

/** `.kicker`, `.brand-tag`, `.method-badge`: tiny tracked uppercase mono. */
val KickerStyle = TextStyle(
    fontFamily = UbuntuMono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.16.em,
)

/** `.btn`: mono, tracked uppercase. */
val ButtonTextStyle = TextStyle(
    fontFamily = UbuntuMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.12.em,
)

/** `.status-pill`, `.api-version`, `.card-meta`: mono data text. */
val MonoDataStyle = TextStyle(
    fontFamily = UbuntuMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.5.sp,
    letterSpacing = 0.02.em,
)

val Typography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = Ubuntu, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Ubuntu),
        bodyMedium = bodyMedium.copy(fontFamily = Ubuntu),
        bodySmall = bodySmall.copy(fontFamily = Ubuntu),
        labelLarge = labelLarge.copy(fontFamily = UbuntuMono),
        labelMedium = labelMedium.copy(fontFamily = UbuntuMono),
        labelSmall = labelSmall.copy(fontFamily = UbuntuMono),
    )
}
