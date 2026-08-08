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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

private val Bg = Color(0xFF101014)
private val Orange = Color(0xFFF47521)

/** TV-safe margins — roughly 5% of a 1080p panel, the usual overscan allowance. */
private val OVERSCAN_H = 56.dp
private val OVERSCAN_V = 36.dp

/**
 * Tile width floor.
 *
 * GridCells.Adaptive fits floor((w + gap) / (min + gap)) columns, so this sits
 * just under a boundary and the two constants below are a pair — changing either
 * silently changes the column count.
 *
 * At 1080p/320dpi with these margins the grid gets ~420dp beside the panel, which
 * this floor turns into three columns. Raising it to 140.dp drops that to two,
 * which is too cramped for 27 shows.
 */
private val TILE_MIN_WIDTH = 120.dp

/**
 * Detail panel width.
 *
 * ~40 characters a line at 17sp, which is about the comfortable limit for reading
 * prose across a room. Narrower than this and the text starts to feel like a
 * column of confetti; wider and the grid loses a column.
 */
private val PANEL_WIDTH = 400.dp

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
    wasBounced: Boolean,
    onBounceMessageShown: () -> Unit,
    onShowClick: (Show) -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            // TV overscan. Many sets crop the outer ~5% of the panel, so anything
            // closer to the edge than this can simply not exist on the wall.
            .padding(horizontal = OVERSCAN_H, vertical = OVERSCAN_V)
    ) {
        Header(guardActive = guardActive, onSettings = onSettings)

        // Being teleported out of Crunchyroll with no explanation is baffling —
        // especially for a kid, who has no idea an app just did that. Say so, then
        // get out of the way.
        if (wasBounced) {
            BounceNotice(onDismissed = onBounceMessageShown)
        }

        if (shows.isEmpty()) {
            EmptyState()
        } else {
            var focused by remember { mutableStateOf(shows.firstOrNull()) }
            var showingInfoFor by remember { mutableStateOf<Show?>(null) }

            // If the whitelist changes under us, don't keep describing a show that
            // is no longer on screen.
            LaunchedEffect(shows) {
                if (focused == null || shows.none { it.seriesId == focused?.seriesId }) {
                    focused = shows.firstOrNull()
                }
            }

            val anyBlurbs = remember(shows) { shows.any { it.hasBlurb } }

            val playFocus = remember { FocusRequester() }

            Row(modifier = Modifier.fillMaxSize()) {
                ShowGrid(
                    shows = shows,
                    // Selecting a tile hands focus to the panel's Play button
                    // instead of launching. One more press to watch, but it puts
                    // the write-up and More info in front of you on the way —
                    // which is the point of having them.
                    onShowClick = { runCatching { playFocus.requestFocus() } },
                    onShowFocused = { focused = it },
                    modifier = Modifier.weight(1f)
                )
                // Only spend the space when there is something to put in it. A
                // whitelist built entirely by pasting URLs has no write-ups, and a
                // permanently empty panel would just be a narrower grid for nothing.
                if (anyBlurbs) {
                    Spacer(Modifier.width(28.dp))
                    ShowDetailPanel(
                        show = focused,
                        modifier = Modifier.width(PANEL_WIDTH),
                        playFocus = playFocus,
                        onPlay = { focused?.let(onShowClick) },
                        onMoreInfo = { showingInfoFor = focused }
                    )
                }
            }

            showingInfoFor?.let { s ->
                MoreInfoDialog(show = s, onDismiss = { showingInfoFor = null })
            }
        }
    }
}

@Composable
private fun BounceNotice(onDismissed: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(4_000)
        onDismissed()
    }
    Text(
        text = "Let's pick a show from your list 👋",
        color = Orange,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )
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
private fun ShowGrid(
    shows: List<Show>,
    onShowClick: (Show) -> Unit,
    onShowFocused: (Show) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstTile = remember { FocusRequester() }

    // Nothing is focused by default on TV — without this the D-pad does nothing.
    LaunchedEffect(shows.firstOrNull()?.seriesId) {
        if (shows.isNotEmpty()) runCatching { firstTile.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TILE_MIN_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        // Room for the focused tile to grow without being clipped by the viewport.
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        itemsIndexed(shows, key = { _, show -> show.seriesId }) { index, show ->
            ShowTile(
                show = show,
                onClick = { onShowClick(show) },
                onFocused = { onShowFocused(show) },
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
