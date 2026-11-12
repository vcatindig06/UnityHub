package com.unityhub.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unityhub.app.R

/** The warm UnityHub brand gradient: crimson, coral, then amber. */
val UnityHubBrandGradient: Brush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFC80F32),
        Color(0xFFF0443D),
        Color(0xFFFF9B43),
        Color(0xFFFFD06B)
    )
)

@Composable
fun UnityHubLogoMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "UnityHub logo"
) {
    Image(
        painter = painterResource(R.drawable.unityhub_mark),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
fun UnityHubWordmark(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 21.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    letterSpacing: TextUnit = (-0.6).sp
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = baseColor)) { append("Unity") }
            withStyle(SpanStyle(brush = UnityHubBrandGradient)) { append("Hub") }
        },
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing
    )
}

@Composable
fun UnityHubLoadingScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(13.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UnityHubLogoMark(Modifier.size(88.dp))
            UnityHubWordmark(
                baseColor = MaterialTheme.colorScheme.onBackground,
                fontSize = 27.sp
            )
            Text(
                "PLAN · LEAD · WORSHIP · TOGETHER",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.1.sp
            )
            CircularProgressIndicator(
                modifier = Modifier.size(25.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp
            )
        }
    }
}
