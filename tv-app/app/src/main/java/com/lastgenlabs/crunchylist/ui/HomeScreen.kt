package com.lastgenlabs.crunchylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastgenlabs.crunchylist.data.Show

private val Bg = Color(0xFF101014)
private val Orange = Color(0xFFF47521)

/**
 * The kid-facing screen: nothing but parent-approved shows.
 *
 * This is the whole point of the product — the entry point is a curated grid
 * instead of Crunchyroll's full catalogue.
 */
@Composable
fun HomeScreen(
    shows: List<Show>,
    guardActive: Boolean,
    onShowClick: (Show) -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Header(guardActive = guardActive, onSettings = onSettings)

        if (shows.isEmpty()) {
            EmptyState()
        } else {
            ShowGrid(shows = shows, onShowClick = onShowClick)
        }
    }
}

@Composable
private fun Header(guardActive: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "CrunchyList",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!guardActive) {
                // Loud on purpose. A silently disabled guard is the failure mode
                // that matters — the grid would otherwise look entirely normal.
                Text(
                    text = "⚠  Guard off",
                    color = Color(0xFFE06C6C),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
            FocusableText(label = "Settings", onClick = onSettings)
        }
    }
}

@Composable
private fun ShowGrid(shows: List<Show>, onShowClick: (Show) -> Unit) {
    val firstTile = remember { FocusRequester() }

    // Nothing is focused by default on TV — without this the D-pad does nothing.
    LaunchedEffect(shows.firstOrNull()?.seriesId) {
        if (shows.isNotEmpty()) runCatching { firstTile.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(shows, key = { _, show -> show.seriesId }) { index, show ->
            ShowTile(
                show = show,
                onClick = { onShowClick(show) },
                modifier = if (index == 0) Modifier.focusRequester(firstTile) else Modifier
            )
        }
    }
}

@Composable
private fun FocusableText(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Text(
        text = label,
        color = if (focused) Color.Black else Color(0xFFB8B8C0),
        fontSize = 18.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Orange else Color.Transparent)
            .border(
                width = if (focused) 0.dp else 1.dp,
                color = if (focused) Color.Transparent else Color(0xFF3A3A45),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No shows yet", color = Color.White, fontSize = 28.sp)
            Text(
                "A parent can add shows from Settings.",
                color = Color(0xFF9A9AA6),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
