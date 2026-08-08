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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.lastgenlabs.crunchylist.data.Shelves
import com.lastgenlabs.crunchylist.data.Show
import kotlinx.coroutines.delay

private val Bg = Color(0xFF101014)
private val Orange = Color(0xFFF47521)

/** TV-safe margins — roughly 5% of a 1080p panel, the usual overscan allowance. */
private val OVERSCAN_H = 56.dp
private val OVERSCAN_V = 36.dp

/**
 * Tile width.
 *
 * Fixed rather than adaptive now that shows sit in horizontally scrolling
 * shelves: a shelf does not fit a whole number of tiles into its width, and
 * should not try to. A partly visible tile at the right-hand edge is the thing
 * that tells you the row continues.
 */
private val TILE_WIDTH = 104.dp

/**
 * Detail panel width.
 *
 * ~40 characters a line at 17sp, which is about the comfortable limit for reading
 * prose across a room. Narrower and the text starts to feel like a column of
 * confetti; wider and the shelves lose a tile.
 */
private val PANEL_WIDTH = 400.dp

/**
 * The kid-facing screen: nothing but parent-approved shows.
 *
 * This is the whole point of the product — the entry point is a curated set of
 * shelves instead of Crunchyroll's full catalogue.
 */
@Composable
fun HomeScreen(
    shows: List<Show>,
    recentIds: List<String>,
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
        val playFocus = remember { FocusRequester() }
        val shelves = remember(shows, recentIds) { Shelves.build(shows, recentIds) }
        val anyBlurbs = remember(shows) { shows.any { it.hasBlurb } }

        // Whatever the first shelf is, its first tile is where focus starts — so
        // when there is a Keep watching row, coming back from an episode lands on
        // the show you were just watching rather than at the top of the alphabet.
        val opener = shelves.firstOrNull()?.shows?.firstOrNull()

        var focused by remember { mutableStateOf(opener) }
        var showingInfoFor by remember { mutableStateOf<Show?>(null) }

        // If the whitelist changes under us, don't keep describing a show that is
        // no longer on screen.
        LaunchedEffect(shows, opener?.seriesId) {
            if (focused == null || shows.none { it.seriesId == focused?.seriesId }) {
                focused = opener
            }
        }

        Header(
            guardActive = guardActive,
            canSurprise = shows.size > 1,
            onSurprise = {
                // Pick something other than what is already showing, so pressing
                // it twice never looks like it did nothing.
                focused = shows.filterNot { it.seriesId == focused?.seriesId }.randomOrNull()
                    ?: focused
                // Hand focus to Play so the pick can be watched immediately. It
                // also means a second press comes from the panel rather than the
                // header, which is a shorter loop for "no, another one".
                if (anyBlurbs) runCatching { playFocus.requestFocus() }
            },
            onSettings = onSettings
        )

        // Being teleported out of Crunchyroll with no explanation is baffling —
        // especially for a kid, who has no idea an app just did that. Say so, then
        // get out of the way.
        if (wasBounced) {
            BounceNotice(onDismissed = onBounceMessageShown)
        }

        if (shows.isEmpty()) {
            EmptyState()
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                ShelfList(
                    shelves = shelves,
                    // Selecting a tile hands focus to the panel's Play button
                    // instead of launching. One more press to watch, but it puts
                    // the write-up and More info in front of you on the way —
                    // which is the point of having them.
                    //
                    // Unless there is no panel. A whitelist built entirely by
                    // pasting URLs has no write-ups, so nothing renders on the
                    // right and there is no Play button to hand focus to —
                    // selecting a tile would silently do nothing at all and the
                    // app would look broken. Fall back to launching directly.
                    onShowClick = { show ->
                        if (anyBlurbs) runCatching { playFocus.requestFocus() }
                        else onShowClick(show)
                    },
                    onShowFocused = { focused = it },
                    modifier = Modifier.weight(1f)
                )
                // Only spend the space when there is something to put in it. A
                // whitelist built entirely by pasting URLs has no write-ups, and a
                // permanently empty panel would just be narrower shelves for nothing.
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
private fun ShelfList(
    shelves: List<Shelves.Shelf>,
    onShowClick: (Show) -> Unit,
    onShowFocused: (Show) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstTile = remember { FocusRequester() }

    // Nothing is focused by default on TV — without this the D-pad does nothing.
    LaunchedEffect(shelves.firstOrNull()?.shows?.firstOrNull()?.seriesId) {
        if (shelves.isNotEmpty()) runCatching { firstTile.requestFocus() }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        // A lazy list clips to its bounds, and the focused tile scales up — so
        // without room at both ends the top and bottom shelves get shaved.
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        itemsIndexed(shelves, key = { _, shelf -> shelf.title }) { row, shelf ->
            Column {
                Text(
                    shelf.title.uppercase(),
                    color = Orange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    // The focused tile scales up, so it needs somewhere to grow
                    // into at both ends of the row.
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    itemsIndexed(shelf.shows, key = { _, show -> show.seriesId }) { col, show ->
                        ShowTile(
                            show = show,
                            onClick = { onShowClick(show) },
                            onFocused = { onShowFocused(show) },
                            modifier = Modifier
                                .width(TILE_WIDTH)
                                .then(
                                    if (row == 0 && col == 0) Modifier.focusRequester(firstTile)
                                    else Modifier
                                )
                        )
                    }
                }
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
private fun Header(
    guardActive: Boolean,
    canSurprise: Boolean,
    onSurprise: () -> Unit,
    onSettings: () -> Unit
) {
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!guardActive) {
                // Loud on purpose. A silently disabled guard is the failure mode
                // that matters — the shelves would otherwise look entirely normal.
                Text(
                    text = "⚠  Guard off",
                    color = Color(0xFFE06C6C),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            // For when two kids cannot agree, which is most evenings.
            if (canSurprise) {
                FocusableText(label = "🎲  Surprise me", onClick = onSurprise)
            }
            FocusableText(label = "Settings", onClick = onSettings)
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
