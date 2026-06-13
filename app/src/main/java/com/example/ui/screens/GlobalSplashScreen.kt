package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.async

@Composable
fun GlobalSplashScreen(
    modifier: Modifier = Modifier
) {
    // Elegant fade and zoom animations
    val scaleAnim = remember { Animatable(0.85f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val scaleJob = async {
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(1200, easing = EaseOutBack)
            )
        }
        val alphaJob = async {
            alphaAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(900)
            )
        }
        scaleJob.await()
        alphaJob.await()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0C0E),
                        Color(0xFF040506)
                    )
                )
            )
            .testTag("global_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
        ) {
            CardLogo(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(2.dp, Color(0xFF4B7399).copy(alpha = 0.4f), RoundedCornerShape(32.dp))
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "FOCUS+",
                fontSize = 42.sp,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("splash_title_text")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "COACHING IA DE JEU D’ÉCHECS",
                fontSize = 11.sp,
                color = Color(0xFFA5C3E6),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF4B7399),
                strokeWidth = 3.2.dp,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("splash_progress_indicator")
            )
        }
    }
}

@Composable
fun CardLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.focus_plus_logo),
            contentDescription = "FOCUS+ Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
