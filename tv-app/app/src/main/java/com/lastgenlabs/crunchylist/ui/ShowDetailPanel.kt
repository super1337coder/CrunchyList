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
        AnimatedContent(
            targetState = show,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "detail"
        ) { s ->
            if (s == null) {
                Text(
                    "Pick a show to see what it's about.",
                    color = Dim,
                    fontSize = 17.sp
                )
            } else {
                // Everything is sized to FIT rather than to scroll. The panel is
                // not focusable — focus stays in the grid so the D-pad keeps
                // moving between shows — which means a scrollbar here would be
                // unreachable and the tail of a long write-up simply unreadable.
                // The scroll modifier stays only as a safety net for a longer
                // blurb than any of these.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
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
                        Text(
                            s.description,
                            color = Body,
                            fontSize = 16.sp,
                            lineHeight = 23.sp
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

        if (show?.hasMoreInfo == true) {
            Spacer(Modifier.weight(1f))
            // Reachable by pressing right from the grid's last column. Kept at the
            // bottom so it doesn't shift as blurbs change length.
            MoreInfoButton(onClick = onMoreInfo)
        }
    }
}

@Composable
private fun MoreInfoButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Text(
        text = if (focused) "More info  ▸" else "More info",
        color = if (focused) Color.Black else Body,
        fontSize = 17.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Orange else Color(0xFF262631))
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
