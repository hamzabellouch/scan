package com.tkno.scan.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkno.scan.R
import com.tkno.scan.ui.common.LocalDarkTheme

/**
 * Screen 1 Illustration: 1.svg Hero Vector Graphic
 * Spans full width from far right to far left.
 */
@Composable
fun WelcomeIllustration(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalDarkTheme.current.isDarkTheme()
) {
    val resId = if (isDark) R.drawable.ic_onboarding_welcome_hero_dark else R.drawable.ic_onboarding_welcome_hero_light
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = modifier.fillMaxWidth(),
        contentScale = ContentScale.FillWidth
    )
}

/**
 * Screen 2 Illustration: Document & Verification Vector Graphic
 * Centered illustration above the features headline.
 */
@Composable
fun FeaturesIllustration(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalDarkTheme.current.isDarkTheme()
) {
    val resId = if (isDark) R.drawable.ic_onboarding_features_hero_dark else R.drawable.ic_onboarding_features_hero_light
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Screen 3 Illustration: 3.svg Vector Graphic (Level Up Growth Arrow)
 * Centered illustration above "Level up your experience".
 */
@Composable
fun LevelUpIllustration(
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalDarkTheme.current.isDarkTheme()
) {
    val resId = if (isDark) R.drawable.ic_onboarding_level_up_hero_dark else R.drawable.ic_onboarding_level_up_hero_light
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Scan Official Brand Badge - Exact matching app icon
 */
@Composable
fun ScanBrandBadge(
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_scan_logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(iconSize)
    )
}

