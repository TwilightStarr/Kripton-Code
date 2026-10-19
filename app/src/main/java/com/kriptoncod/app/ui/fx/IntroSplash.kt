package com.kriptoncod.app.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.components.LogoMood
import com.kriptoncod.app.ui.theme.LocalEffectsEnabled
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Açılış: logo çizilirken arkasında yumuşak bir ışık büyür, altıgen tamamlanınca parıltılar saçılır ve
 * uygulama adı harf aralığı daralarak belirir. Üst katman çıkarken hafifçe büyüyüp solar (bkz. AppRoot).
 */
@Composable
fun IntroSplash(modifier: Modifier = Modifier) {
    val fx = LocalFx.current
    val effects = LocalEffectsEnabled.current
    val tint = MaterialTheme.colorScheme.primary
    val glow = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    var logoCenter by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        launch { glow.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch {
            delay(900)
            word.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
        }
        // Altıgen tamamlanıp nokta yerine oturduğu an (INTRO_MS içinde ~%80).
        delay(1200)
        if (effects) fx.burst(fx.toLocal(logoCenter), FxKind.Sparkle, listOf(tint, tint.copy(alpha = 0.6f)))
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val g = glow.value
            val r = max(size.width, size.height) * (0.15f + 0.35f * g)
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.16f * g), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.onGloballyPositioned { logoCenter = it.boundsInRoot().center }) {
                KriptonCodLogo(logoSize = 96.dp, mood = LogoMood.Intro)
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (2f + 8f * (1f - word.value)).sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 10.dp.toPx()
                },
            )
        }
    }
}
