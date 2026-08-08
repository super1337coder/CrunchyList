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
 * Built for a kid who likes knowing who everyone is first: portraits and a
 * one-line "what they do" for each main character, plus the factual bits
 * (episodes, year) and Crunchyroll's content labels.
 *
 * Scrollable, unlike the side panel — this one takes focus, so the D-pad can
 * actually move through it.
 */
@Composable
fun MoreInfoDialog(show: Show, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

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
                .padding(horizontal = 72.dp, vertical = 44.dp),
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
                        val step = 240f
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
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {

                    item { Header(show) }

                    if (show.description.isNotBlank()) {
                        item {
                            Text(
                                show.description,
                                color = Body,
                                fontSize = 17.sp,
                                lineHeight = 25.sp
                            )
                        }
                    }

                    if (show.cast.isNotEmpty()) {
                        item {
                            Text(
                                "WHO'S IN IT",
                                color = Orange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(show.cast) { member -> CastRow(member) }
                    }

                    if (show.advisories.isNotBlank()) {
                        item { Advisories(show) }
                    }

                    item {
                        Text(
                            "Up / down to scroll   ·   Back to close",
                            color = Dim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(show: Show) {
    Row(verticalAlignment = Alignment.Top) {
        if (!show.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = show.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(150.dp)
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
        }
    }
}

@Composable
private fun CastRow(member: CastMember) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
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
        Spacer(Modifier.width(20.dp))
        Column {
            Text(member.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (member.role.isNotBlank()) {
                Text(member.role, color = Body, fontSize = 16.sp, lineHeight = 22.sp)
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
