package com.example.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GoldGlow
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onSplashComplete: () -> Unit
) {
    val fullText = "RECEIPT MAKER PRO"
    var typedCharCount by remember { mutableIntStateOf(0) }
    var cursorVisible by remember { mutableStateOf(true) }
    var typingFinished by remember { mutableStateOf(false) }

    // Blinking cursor loop
    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = !cursorVisible
            delay(400)
        }
    }

    // Realistic keyboard typing loop
    LaunchedEffect(Unit) {
        // Initial brief dramatic pause
        delay(400)
        for (i in 1..fullText.length) {
            typedCharCount = i
            // Realistic keyboard cadence with slight variation
            val typingDelay = 75L + Random.nextLong(20, 50)
            delay(typingDelay)
        }
        typingFinished = true
        // Allow user to admire the completed glowing title before entering
        delay(900)
        onSplashComplete()
    }

    // Gentle pulse animation for the logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip intro immediately
                onSplashComplete()
            }
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Unaltered user splash image with original colors & aspect ratio
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .scale(pulseScale)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = "Receipt Maker Pro Splash Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("splash_logo_image")
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Realistic keyboard typing animation with blinking cursor & glowing text
            val displayedText = fullText.take(typedCharCount)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.widthIn(min = 280.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayedText,
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 2.5.sp,
                            color = GoldPrimary,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(
                                color = GoldGlow.copy(alpha = glowAlpha),
                                offset = Offset(0f, 0f),
                                blurRadius = 24f
                            )
                        ),
                        modifier = Modifier.testTag("typing_animated_text")
                    )

                    // Blinking Cursor
                    Text(
                        text = if (cursorVisible) "❙" else " ",
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            shadow = Shadow(
                                color = ElectricBlue,
                                offset = Offset(0f, 0f),
                                blurRadius = 14f
                            )
                        ),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle that fades in once typing finishes
            AnimatedVisibility(
                visible = typingFinished,
                enter = fadeIn(animationSpec = tween(500))
            ) {
                Text(
                    text = "Professional Digital Receipts & Templates",
                    fontSize = 12.sp,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Tap to skip hint at bottom
        Text(
            text = "Tap anywhere to skip",
            fontSize = 11.sp,
            color = TextMuted.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
