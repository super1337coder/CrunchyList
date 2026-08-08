package com.lastgenlabs.crunchylist.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.lastgenlabs.crunchylist.data.Show

private val Orange = Color(0xFFF47521)

/**
 * One show tile.
 *
 * TV focus rules: the focused tile must be unmistakable from across a room, so it
 * scales up and takes a bright border. Subtle hover styling that works on a phone
 * is not readable at three metres.
 */
@Composable
fun ShowTile(
    show: Show,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tileScale")

    // Drives the detail panel. Keyed on `focused` so it fires on the transition
    // rather than on every recomposition.
    LaunchedEffect(focused) { if (focused) onFocused() }

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Crunchyroll's poster_tall art is 2:3.
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF23232B))
                .border(
                    BorderStroke(if (focused) 4.dp else 0.dp, if (focused) Orange else Color.Transparent),
                    RoundedCornerShape(10.dp)
                )
        ) {
            if (show.imageUrl.isNullOrBlank()) {
                PlaceholderArt(show.title)
            } else {
                SubcomposeAsyncImage(
                    model = show.imageUrl,
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { PlaceholderArt(show.title) },
                    error = { PlaceholderArt(show.title) }
                )
            }
        }

        Text(
            text = show.title,
            color = if (focused) Color.White else Color(0xFFB8B8C0),
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            // Always reserve two lines. Without this, a one-line title sits at a
            // different height from a wrapped one and the whole row looks broken —
            // and the mismatch shifts as soon as the parent adds a long title.
            minLines = 2,
            maxLines = 2,
            lineHeight = 20.sp,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

/** Shown while art loads, when it fails, or when a show has none. */
@Composable
private fun PlaceholderArt(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E2E38)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            color = Orange,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
