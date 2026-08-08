package com.lastgenlabs.crunchylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.lastgenlabs.crunchylist.data.CastMember
import com.lastgenlabs.crunchylist.data.Show
import kotlinx.coroutines.launch

private val Scrim = Color(0xE6000000)
private val Card = Color(0xFF1B1B24)
private val Orange = Color(0xFFF47521)
private val Body = Color(0xFFD2D2DA)
private val Dim = Color(0xFF8E8E9C)

/**
 * The "tell me about this show before I start it" screen.
 *
 * Built for a kid who likes knowing who everyone is first: a proper write-up of
 * the show, then a portrait and a paragraph on each main character — who they
 * are, what they can do, what they are like — plus the factual bits and
 * Crunchyroll's content labels.
 *
 * This is the one screen with room to be long. The side panel has to fit on
 * screen, so it stays short; everything that wants space lands here.
 *
 * Scrollable, unlike the side panel — this one takes focus, so the D-pad can
 * actually move through it.
 */
@Composable
fun MoreInfoDialog(show: Show, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    // Blank-line-separated in the data, one Text per paragraph here. A single
    // Text with embedded newlines would render, but paragraph spacing you can
    // actually see is most of what makes a wall of text readable across a room.
    val paragraphs = remember(show.seriesId, show.about, show.description) {
        show.longRead.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    // Nothing in here is clickable, so there is nothing for D-pad focus traversal
    // to move to — and without focus the list will not scroll at all, leaving the
    // cast permanently below the fold. So the dialog takes focus itself and turns
    // up/down into a scroll. Deliberately not making the rows focusable: they are
    // not actionable, and focus rings on plain text read as broken.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .padding(horizontal = 56.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Card)
                    .padding(36.dp)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        // Roughly a third of a screen per press. Holding the
                        // D-pad repeats the key, so a long write-up is a hold
                        // rather than thirty presses.
                        val step = 340f
                        when (event.key) {
                            Key.DirectionDown -> {
                                scope.launch { listState.animateScrollBy(step) }; true
                            }
                            Key.DirectionUp -> {
                                scope.launch { listState.animateScrollBy(-step) }; true
                            }
                            else -> false
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f)
                ) {

                    // The opening paragraph sits beside the poster rather than
                    // under it. A portrait poster is taller than a title and a
                    // couple of fact lines, so keeping the text out of that
                    // column left a band of empty card across the top of the
                    // screen with the most-read paragraph pushed below it.
                    item { Header(show, lead = paragraphs.firstOrNull()) }

                    items(paragraphs.drop(1)) { para ->
                        Text(para, color = Body, fontSize = 17.sp, lineHeight = 26.sp)
                    }

                    if (show.cast.isNotEmpty()) {
                        item { SectionLabel("WHO'S IN IT") }
                        items(show.cast, key = { it.name }) { member -> CastRow(member) }
                    }

                    if (show.advisories.isNotBlank()) {
                        item { Advisories(show) }
                    }
                }

                // Pinned rather than the last item in the list. These write-ups
                // run to several screens, and a scroll hint you can only see
                // once you have finished scrolling is no hint at all.
                Text(
                    "Up / down to scroll   ·   Back to close",
                    color = Dim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun Header(show: Show, lead: String?) {
    Row(verticalAlignment = Alignment.Top) {
        if (!show.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = show.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Small enough that the facts beside it roughly fill its height —
                // a taller poster leaves a band of empty card above the write-up,
                // which is wasted space on a screen that now has a lot to read.
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2E2E38))
            )
            Spacer(Modifier.width(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (show.category.isNotBlank()) {
                Text(
                    show.category.uppercase(),
                    color = Orange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            Text(
                show.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )
            if (show.hook.isNotBlank()) {
                Text(
                    show.hook,
                    color = Color(0xFFFFC79A),
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 24.sp
                )
            }
            val facts = listOfNotNull(
                show.facts.takeIf { it.isNotBlank() },
                show.rating.takeIf { it.isNotBlank() }
            ).joinToString("   ")
            if (facts.isNotBlank()) {
                Text(facts, color = Dim, fontSize = 15.sp)
            }
            if (show.meta.isNotBlank()) {
                Text(show.meta, color = Dim, fontSize = 15.sp, lineHeight = 21.sp)
            }
            if (!lead.isNullOrBlank()) {
                Text(
                    lead,
                    color = Body,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Orange,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun CastRow(member: CastMember) {
    // Top-aligned rather than centred: with a paragraph of bio the text column is
    // several times the height of the portrait, and centring leaves the face
    // floating in the middle of the block.
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E2E38))
                .border(2.dp, Color(0xFF3A3A46), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (member.image.isNullOrBlank()) {
                Text(
                    member.name.firstOrNull()?.uppercase() ?: "?",
                    color = Orange,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                SubcomposeAsyncImage(
                    model = member.image,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    loading = {
                        Text(
                            member.name.firstOrNull()?.uppercase() ?: "?",
                            color = Orange,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    error = {
                        Text(
                            member.name.firstOrNull()?.uppercase() ?: "?",
                            color = Orange,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
        Spacer(Modifier.width(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(member.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (member.role.isNotBlank()) {
                Text(
                    member.role,
                    color = Color(0xFFFFC79A),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
            if (member.bio.isNotBlank()) {
                Text(member.bio, color = Body, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun Advisories(show: Show) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF241F1A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "THINGS TO KNOW",
            color = Orange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(show.advisories, color = Body, fontSize = 16.sp, lineHeight = 22.sp)
        // Stated because these labels are a presence flag, not a severity scale —
        // "Violence" covers both a slapstick bonk and a decapitation.
        Text(
            "Crunchyroll's own labels. They say what appears, not how much.",
            color = Dim,
            fontSize = 13.sp
        )
    }
}
