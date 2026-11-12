package com.unityhub.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.unityhub.app.data.ServicePlanItemDto
import com.unityhub.app.domain.chordpro.ChordProParser
import com.unityhub.app.domain.chordpro.ChordTransposer

private val autoScrollSpeeds = listOf(
    "Slow" to 14,
    "Normal" to 24,
    "Fast" to 40
)

private val StageBackground = Color(0xFF080F15)
private val StageSurface = Color(0xFF14232E)
private val StagePrimaryText = Color(0xFFF4F8FB)
private val StageSecondaryText = Color(0xFFB7C8D3)
private val StageAccent = Color(0xFF8FD4C1)
private val StageChord = Color(0xFFFFD27D)
private val StageDivider = Color(0xFF2B414F)

@Composable
fun LiveStageScreen(
    serviceTitle: String,
    items: List<ServicePlanItemDto>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentIndex by rememberSaveable(serviceTitle) { mutableIntStateOf(0) }
    var transpose by rememberSaveable(serviceTitle) { mutableIntStateOf(0) }
    var textScale by rememberSaveable(serviceTitle) { mutableFloatStateOf(1f) }
    var showChords by rememberSaveable(serviceTitle) { mutableStateOf(true) }
    var autoScrollEnabled by rememberSaveable(serviceTitle) { mutableStateOf(false) }
    var autoScrollSpeedIndex by rememberSaveable(serviceTitle) { mutableIntStateOf(1) }
    var lastAutoScrollIndex by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val window = activity?.window
    val scrollState = rememberScrollState()
    val density = LocalDensity.current.density
    val currentItem = items.getOrNull(currentIndex)
    val document = remember(currentItem?.id, currentItem?.chordproSnapshot) {
        currentItem?.chordproSnapshot?.takeIf(String::isNotBlank)?.let(ChordProParser::parse)
    }
    val displayKey = ChordTransposer.transposeKey(document?.key, transpose)

    DisposableEffect(window, view) {
        val originallyKeptAwake = window?.let {
            it.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        } ?: false
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!originallyKeptAwake) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(currentIndex, autoScrollEnabled, autoScrollSpeedIndex) {
        if (lastAutoScrollIndex != currentIndex) {
            scrollState.scrollTo(0)
            transpose = 0
            lastAutoScrollIndex = currentIndex
        }

        if (autoScrollEnabled) {
            // Wait for the updated item to be laid out before reading the scroll range.
            withFrameNanos { }
            val remainingPx = (scrollState.maxValue - scrollState.value).coerceAtLeast(0)
            val durationMillis = LiveStageRules.autoScrollDurationMillis(
                distancePx = remainingPx,
                density = density,
                speedDpPerSecond = autoScrollSpeeds[autoScrollSpeedIndex].second
            )
            if (durationMillis > 0) {
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = StageBackground
    ) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No setlist items available",
                    color = StagePrimaryText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Add songs or service items before starting Live Stage.",
                    color = StageSecondaryText,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Back to service")
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().background(StageBackground)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(
                                modifier = Modifier.size(8.dp).background(StageAccent, RoundedCornerShape(50))
                            )
                            Text(
                                "LIVE STAGE",
                                color = StageAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp
                            )
                        }
                        Text(
                            serviceTitle,
                            color = StageSecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Live Stage", tint = StagePrimaryText)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().height(3.dp).background(StageDivider)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((currentIndex + 1).toFloat() / items.size.toFloat())
                            .height(3.dp)
                            .background(StageAccent)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ITEM ${currentIndex + 1} OF ${items.size}",
                        color = StageSecondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        currentItem?.itemType.orEmpty().replaceFirstChar { it.uppercase() },
                        color = StageAccent,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        currentItem?.title.orEmpty(),
                        color = StagePrimaryText,
                        fontSize = (32f * textScale).sp,
                        lineHeight = (39f * textScale).sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (currentItem?.durationMinutes != null || displayKey != null || document?.artist != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (displayKey != null) StageMetadata("KEY", displayKey)
                            document?.artist?.takeIf(String::isNotBlank)?.let { StageMetadata("ARTIST", it) }
                            currentItem?.durationMinutes?.let { StageMetadata("TIME", "$it min") }
                        }
                    }

                    if (document?.lines.isNullOrEmpty()) {
                        Text(
                            if (currentItem?.itemType == "song") {
                                "No ChordPro chart was saved with this setlist item."
                            } else {
                                currentItem?.notes?.ifBlank { "No additional notes for this item." }
                                    ?: "No additional notes for this item."
                            },
                            color = StageSecondaryText,
                            fontSize = (21f * textScale).sp,
                            lineHeight = (30f * textScale).sp
                        )
                    } else {
                        document.lines.forEach { line ->
                            when {
                                line.section != null && line.lyrics.isBlank() -> {
                                    Text(
                                        line.section.uppercase(),
                                        color = StageAccent,
                                        fontSize = (16f * textScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.4.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
                                    )
                                }
                                line.comment -> {
                                    Text(
                                        line.lyrics,
                                        color = StageSecondaryText,
                                        fontSize = (17f * textScale).sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 5.dp)
                                    )
                                }
                                line.lyrics.isBlank() && line.chords.isEmpty() -> {
                                    Spacer(Modifier.height((12f * textScale).dp))
                                }
                                else -> {
                                    val chordRow = remember(line, transpose) {
                                        buildChordRow(line, transpose)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (showChords && chordRow.isNotBlank()) {
                                            Text(
                                                chordRow,
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                color = StageChord,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (18f * textScale).sp,
                                                lineHeight = (24f * textScale).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            line.lyrics,
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            color = StagePrimaryText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = (24f * textScale).sp,
                                            lineHeight = (34f * textScale).sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    currentItem?.notes?.takeIf { it.isNotBlank() && document?.lines?.isNotEmpty() == true }?.let { notes ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                .background(StageSurface, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("SERVICE NOTES", color = StageAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text(notes, color = StageSecondaryText, fontSize = (16f * textScale).sp, lineHeight = (23f * textScale).sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StageSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { textScale = (textScale - 0.1f).coerceAtLeast(0.8f) },
                                enabled = textScale > 0.8f
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease text size", tint = StagePrimaryText)
                            }
                            Text("TEXT", color = StageSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { textScale = (textScale + 0.1f).coerceAtMost(1.8f) },
                                enabled = textScale < 1.8f
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase text size", tint = StagePrimaryText)
                            }
                            IconButton(
                                onClick = {
                                    textScale = 1f
                                    transpose = 0
                                    showChords = true
                                    autoScrollEnabled = false
                                    autoScrollSpeedIndex = 1
                                },
                                enabled = textScale != 1f || transpose != 0 || !showChords ||
                                    autoScrollEnabled || autoScrollSpeedIndex != 1
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset stage display", tint = StageSecondaryText)
                            }
                        }
                        TextButton(onClick = { showChords = !showChords }) {
                            Icon(
                                if (showChords) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = StageAccent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(if (showChords) "Hide chords" else "Show chords", color = StageAccent)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            enabled = scrollState.maxValue > 0 || autoScrollEnabled
                        ) {
                            Icon(
                                if (autoScrollEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (autoScrollEnabled) "Pause auto-scroll" else "Start auto-scroll",
                                tint = StageAccent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (autoScrollEnabled) "Pause scroll" else "Auto-scroll",
                                color = StageAccent
                            )
                        }
                        TextButton(
                            onClick = { autoScrollSpeedIndex = (autoScrollSpeedIndex + 1) % autoScrollSpeeds.size }
                        ) {
                            Text(
                                "Speed: ${autoScrollSpeeds[autoScrollSpeedIndex].first}",
                                color = StageAccent
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TRANSPOSE", color = StageSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    transpose > 0 -> "+$transpose semitones"
                                    transpose < 0 -> "$transpose semitones"
                                    else -> "Original key"
                                },
                                color = StagePrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { transpose = LiveStageRules.adjustTranspose(transpose, -1) },
                                enabled = transpose > -11
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Transpose down", tint = StagePrimaryText)
                            }
                            Text(displayKey ?: "—", color = StageAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(
                                onClick = { transpose = LiveStageRules.adjustTranspose(transpose, 1) },
                                enabled = transpose < 11
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Transpose up", tint = StagePrimaryText)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentIndex = LiveStageRules.previousIndex(currentIndex, items.size)
                            },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StagePrimaryText,
                                disabledContentColor = StageSecondaryText
                            )
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Previous")
                        }
                        Button(
                            onClick = {
                                if (currentIndex == items.lastIndex) onBack()
                                else currentIndex = LiveStageRules.nextIndex(currentIndex, items.size)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StageAccent,
                                contentColor = StageBackground
                            )
                        ) {
                            Text(if (currentIndex == items.lastIndex) "Finish" else "Next item", fontWeight = FontWeight.Bold)
                            if (currentIndex < items.lastIndex) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageMetadata(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = StageSecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = StagePrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
