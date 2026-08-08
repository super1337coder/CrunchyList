package com.lastgenlabs.crunchylist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastgenlabs.crunchylist.data.Show

private val PanelBg = Color(0xFF191922)
private val Orange = Color(0xFFF47521)
private val Body = Color(0xFFD2D2DA)
private val Dim = Color(0xFF8E8E9C)

/**
 * The reason a kid picks one show over another.
 *
 * Shows the parent's own write-up for whatever tile is focused, rather than
 * Crunchyroll's marketing synopsis. Ordered so the useful part comes first: the
 * hook line answers "why would I like this" in one sentence, and the full
 * description is there for whoever wants it.
 *
 * Type sizes assume a ten-foot viewing distance — the body text here is 17sp,
 * which is roughly a paperback held at arm's length once scaled to a TV.
 */
@Composable
fun ShowDetailPanel(
    show: Show?,
    modifier: Modifier = Modifier,
    playFocus: FocusRequester = remember { FocusRequester() },
    onPlay: () -> Unit = {},
    onMoreInfo: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(PanelBg)
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        // Crossfade rather than slide: the panel changes on every focus move, and
        // anything more energetic turns browsing the grid into a strobe.
        //
        // The weight is load-bearing, not spacing. A Column measures unweighted
        // children first, so without it this took the whole panel height and the
        // buttons below were laid out past the bottom edge and clipped away — on
        // exactly the shows with the most to say. Worse, they were still there as
        // far as focus was concerned, so selecting a tile handed focus to an
        // invisible Play button and the remote appeared to stop working.
        AnimatedContent(
            targetState = show,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "detail",
            modifier = Modifier.weight(1f)
        ) { s ->
            if (s == null) {
                Text(
                    "Pick a show to see what it's about.",
                    color = Dim,
                    fontSize = 17.sp
                )
            } else {
                // Everything is sized to FIT, never to scroll. The panel is not
                // focusable — focus stays in the grid so the D-pad keeps moving
                // between shows — so a scrollbar here would be unreachable, and
                // a scroll modifier only hides the overflow somewhere nobody can
                // get to. Anything that does not fit is ellipsized instead, and
                // More info has the whole thing.
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (s.category.isNotBlank()) {
                        Text(
                            s.category.uppercase(),
                            color = Orange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Text(
                        s.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 29.sp,
                        // Capped because one title in this list ("BOFURI: I Don't
                        // Want to Get Hurt, so I'll Max Out My Defense.") ran to
                        // three lines and pushed the description off the panel.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (s.hook.isNotBlank()) {
                        // The one line that actually helps someone choose, so it
                        // sits above the description rather than under it.
                        Text(
                            s.hook,
                            color = Color(0xFFFFC79A),
                            fontSize = 18.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp
                        )
                    }

                    if (s.description.isNotBlank()) {
                        // The one flexible element: it gets whatever is left after
                        // the title, hook and meta have taken theirs, and trails
                        // off rather than being cut through a line. fill = false
                        // so a short blurb does not push meta to the floor.
                        Text(
                            s.description,
                            color = Body,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    if (s.meta.isNotBlank()) {
                        Text(
                            s.meta,
                            color = Dim,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (show != null) {
            // Selecting a tile moves focus here rather than launching straight
            // away. The old design launched on tile-select, which made "More info"
            // reachable only by arrowing off the grid's last column — findable by
            // accident at best.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                PanelButton(
                    label = "▶  Play",
                    primary = true,
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = onPlay
                )
                if (show.hasMoreInfo) {
                    PanelButton(label = "More info", primary = false, onClick = onMoreInfo)
                }
            }
        }
    }
}

@Composable
private fun PanelButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val bg = when {
        focused -> Orange
        primary -> Color(0xFF3A2A1E)
        else -> Color(0xFF262631)
    }

    Text(
        text = label,
        color = if (focused) Color.Black else Body,
        fontSize = 17.sp,
        fontWeight = if (focused || primary) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    )
}

private fun tween(ms: Int) = androidx.compose.animation.core.tween<Float>(durationMillis = ms)
