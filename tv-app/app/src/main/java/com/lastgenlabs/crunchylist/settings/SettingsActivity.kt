package com.lastgenlabs.crunchylist.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import com.lastgenlabs.crunchylist.crunchyroll.CrunchyrollApi
import com.lastgenlabs.crunchylist.crunchyroll.TokenDiagnostics
import com.lastgenlabs.crunchylist.data.Show
import com.lastgenlabs.crunchylist.data.WhitelistStore
import com.lastgenlabs.crunchylist.guard.GuardCalibrator
import com.lastgenlabs.crunchylist.guard.GuardPause
import com.lastgenlabs.crunchylist.guard.GuardPermissions
import com.lastgenlabs.crunchylist.guard.ScreenPolicy
import com.lastgenlabs.crunchylist.guard.GuardService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Bg = Color(0xFF101014)
private val Orange = Color(0xFFF47521)
private val Dim = Color(0xFF9A9AA6)

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = WhitelistStore.get(this)
        val pins = PinStore(this)

        setContent {
            var unlocked by remember { mutableStateOf(false) }
            if (unlocked) {
                SettingsScreen(
                    store = store,
                    pins = pins,
                    onOpenUsageSettings = { openSpecialAccess(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    onOpenOverlaySettings = { openSpecialAccess(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
                )
            } else {
                PinGate(pins = pins, onUnlocked = { unlocked = true })
            }
        }
    }

    private fun openSpecialAccess(action: String) {
        // These screens may not exist on Google TV. Fall back gracefully rather
        // than crashing — adb is the documented path anyway (audit §4.2).
        runCatching { startActivity(Intent(action)) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
    }
}

@Composable
private fun PinGate(pins: PinStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val creating = !pins.isSet

    fun submit() {
        if (pin.length != 4) {
            error = "PIN must be 4 digits."
            return
        }
        if (creating) {
            pins.set(pin); onUnlocked()
        } else if (pins.verify(pin)) {
            onUnlocked()
        } else {
            error = "Incorrect PIN."; pin = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(64.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            if (creating) "Create a PIN" else "Parent PIN",
            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (creating) "Set a 4-digit PIN to protect these settings. Press ✓ on the keyboard when done."
            else "Enter your 4-digit PIN, then press ✓ on the keyboard.",
            color = Dim, fontSize = 16.sp
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN") },
            // ImeAction.Done lets the on-screen keyboard's ✓ submit directly.
            // On TV that matters a lot: a focused TextField does not reliably
            // release D-pad focus to a button below it, so requiring the user to
            // navigate to "Set PIN" can strand them in the field.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
            modifier = Modifier.width(280.dp)
        )

        if (error.isNotEmpty()) Text(error, color = Color(0xFFE06C6C))

        TvButton(if (creating) "Set PIN" else "Unlock") { submit() }
    }
}

@Composable
private fun SettingsScreen(
    store: WhitelistStore,
    pins: PinStore,
    onOpenUsageSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val shows by store.shows.collectAsState()

    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Bg).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        // --- Parent access to Crunchyroll ---------------------------------
        item {
            var pausedFor by remember { mutableStateOf(GuardPause.remainingMs(context)) }
            // Recomputed on a tick so the countdown is live and so the section
            // flips back to its normal state the moment the pause expires.
            LaunchedEffect(Unit) {
                while (true) {
                    pausedFor = GuardPause.remainingMs(context)
                    delay(1_000)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Parent access to Crunchyroll",
                    color = Orange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (pausedFor > 0L) {
                    Text(
                        "Guard paused — ${GuardPause.format(pausedFor)} left. " +
                            "Nothing is being filtered.",
                        color = Color(0xFFE06C6C)
                    )
                    TvButton("Resume the guard now") {
                        GuardPause.cancel(context)
                        pausedFor = 0L
                        GuardService.start(context)
                    }
                } else {
                    Text(
                        "Signing in to Crunchyroll, changing the account or picking a " +
                            "profile all happen on screens the guard bounces. This opens a " +
                            "${GuardPause.DEFAULT_MINUTES}-minute window and closes it again on its own.",
                        color = Dim
                    )
                    TvButton("Let a parent use Crunchyroll") {
                        GuardPause.begin(context)
                        pausedFor = GuardPause.remainingMs(context)
                        Crunchyroll.launchIntent(context)?.let { context.startActivity(it) }
                    }
                }
            }
        }

        // --- Guard health -------------------------------------------------
        item {
            val missing = GuardPermissions.missing(context)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Guard", color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (missing.isEmpty()) {
                    Text("Active — only approved shows can be opened.", color = Color(0xFF7FD97F))
                } else {
                    Text("DISABLED. Without these, CrunchyList filters nothing:", color = Color(0xFFE06C6C))
                    missing.forEach { Text("  • $it", color = Color(0xFFE06C6C)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Grant usage access", onOpenUsageSettings)
                        TvButton("Grant overlay access", onOpenOverlaySettings)
                    }
                }
                TvButton(if (busy) "Working…" else "Re-verify Crunchyroll") {
                    if (busy) return@TvButton
                    val reference = store.anySeriesId()
                    if (reference == null) {
                        status = "Add a show first — calibration needs one to test with."
                        return@TvButton
                    }
                    busy = true
                    status = "Calibrating…"
                    scope.launch {
                        val result = GuardCalibrator(context).calibrate(reference)
                        status = result.message
                        busy = false
                    }
                }
            }
        }

        // --- Add a show ---------------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a show", color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Paste a Crunchyroll series URL, or just the series ID.",
                    color = Dim, fontSize = 14.sp
                )
                fun addShow() {
                    if (busy) return
                    val id = CrunchyrollApi.parseSeriesId(input)
                    if (id == null) {
                        status = if (CrunchyrollApi.looksLikeTypo(input)) {
                            "\"${input.trim()}\" isn't a valid series ID — they look like " +
                                "G4PH0WXVJ (a G and 8 more characters). Check for a typo, " +
                                "or paste the full crunchyroll.com/series/… URL."
                        } else {
                            "Couldn't find a series ID in that. Paste a crunchyroll.com/series/… URL."
                        }
                        return
                    }
                    busy = true
                    status = "Looking up $id…"
                    scope.launch {
                        val info = CrunchyrollApi.fetchSeries(id)
                        val show = Show(
                            seriesId = id,
                            title = info?.title ?: id,
                            imageUrl = info?.imageUrl,
                            dateAdded = LocalDate.now().toString()
                        )
                        val added = store.add(show)
                        status = if (added) {
                            input = ""
                            if (info == null) {
                                "Added $id, but couldn't reach Crunchyroll for its title and art."
                            } else {
                                "Added ${show.title}."
                            }
                        } else {
                            "${show.title} is already on the list."
                        }

                        // Calibrate as soon as there is something to calibrate with,
                        // while the parent is still here to see the result. Doing it
                        // later — on app launch — would yank a kid into Crunchyroll
                        // with no explanation.
                        if (added && ScreenPolicy(context).needsCalibration(context)) {
                            status = "${status}\nVerifying Crunchyroll…"
                            val result = GuardCalibrator(context).calibrate(show.seriesId)
                            status = "${show.title} added.\n${result.message}"
                        }
                        busy = false
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("crunchyroll.com/series/…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addShow() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TvButton(if (busy) "Working…" else "Add show") { addShow() }
                if (status.isNotEmpty()) Text(status, color = Color(0xFF8FB8FF))
            }
        }

        // --- Current list -------------------------------------------------
        item {
            Text(
                "Approved shows (${shows.size})",
                color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        if (shows.isEmpty()) {
            item { Text("Nothing yet.", color = Dim) }
        } else {
            items(shows, key = { it.seriesId }) { show ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(show.title, color = Color.White, fontSize = 18.sp)
                        Text(show.seriesId, color = Dim, fontSize = 13.sp)
                    }
                    TvButton("Remove") { store.remove(show.seriesId) }
                }
            }
        }

        // --- PIN ------------------------------------------------------------
        item {
            var newPin by remember { mutableStateOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change PIN", color = Orange, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("New 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.width(280.dp)
                )
                TvButton("Update PIN") {
                    if (newPin.length == 4) {
                        pins.set(newPin); newPin = ""; status = "PIN updated."
                    } else {
                        status = "PIN must be 4 digits."
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Test Crunchyroll connection") {
                    if (busy) return@TvButton
                    busy = true
                    status = "Testing…"
                    scope.launch {
                        status = TokenDiagnostics.run()
                        busy = false
                    }
                }
                TvButton("Restart guard") { GuardService.start(context) }
            }
        }
    }
}

/**
 * A button that is obviously focused from across a room. Material's default focus
 * treatment is far too subtle for a ten-foot interface.
 */
@Composable
private fun TvButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Text(
        text = label,
        color = if (focused) Color.Black else Color.White,
        fontSize = 16.sp,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Orange else Color(0xFF2A2A33))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
