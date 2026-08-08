package com.lastgenlabs.crunchylist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import com.lastgenlabs.crunchylist.guard.GuardPermissions
import com.lastgenlabs.crunchylist.guard.GuardService
import com.lastgenlabs.crunchylist.guard.LaunchGrace

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuardHarness() }
    }

    override fun onResume() {
        super.onResume()
        if (GuardPermissions.allGranted(this)) GuardService.start(this)
    }

    companion object {
        const val EXTRA_BOUNCED = "bounced"
    }
}

/**
 * Temporary harness for verifying the guard end to end. The real tile grid
 * replaces this — see task 4.
 */
@Composable
private fun GuardHarness() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val missing = GuardPermissions.missing(context)
    val crInstalled = Crunchyroll.isInstalled(context)
    val crVersion = Crunchyroll.versionCode(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("CrunchyList", color = Color.White, fontSize = 44.sp)

        Text(
            "Crunchyroll installed: $crInstalled  (version $crVersion)",
            color = Color(0xFFBBBBBB)
        )

        if (missing.isEmpty()) {
            Text("Guard: permissions OK, service running", color = Color(0xFF7FD97F))
        } else {
            Text("Guard DISABLED — missing:", color = Color(0xFFE06C6C))
            missing.forEach { Text("  • $it", color = Color(0xFFE06C6C)) }
        }

        // TV apps get no focus for free: without an explicit request nothing is
        // focused on launch, so the D-pad and Enter do nothing at all.
        val firstButton = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstButton.requestFocus() }

        Button(
            modifier = Modifier.focusRequester(firstButton),
            onClick = {
                // Grace must begin BEFORE the intent: a legitimate launch transits
                // Crunchyroll's Startup/Main activities, which are bounce triggers.
                LaunchGrace.begin()
                context.startActivity(Crunchyroll.seriesIntent(SPY_X_FAMILY))
                status = "launched SPY x FAMILY"
            }
        ) {
            Text("Open SPY x FAMILY")
        }

        Button(onClick = {
            LaunchGrace.begin()
            val i = Intent(Intent.ACTION_MAIN).apply { setPackage(Crunchyroll.PACKAGE) }
            context.packageManager.getLaunchIntentForPackage(Crunchyroll.PACKAGE)?.let {
                context.startActivity(it)
                status = "launched CR cold (guard should bounce after grace)"
            } ?: run { status = "no launch intent for CR"; i.let {} }
        }) {
            Text("Open Crunchyroll cold (should be bounced)")
        }

        if (status.isNotEmpty()) Text(status, color = Color(0xFF8FB8FF))
    }
}

private const val SPY_X_FAMILY = "G4PH0WXVJ"
