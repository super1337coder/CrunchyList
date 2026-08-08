package com.lastgenlabs.crunchylist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import com.lastgenlabs.crunchylist.data.Show
import com.lastgenlabs.crunchylist.data.WhitelistStore
import com.lastgenlabs.crunchylist.guard.GuardPermissions
import com.lastgenlabs.crunchylist.guard.GuardService
import com.lastgenlabs.crunchylist.guard.LaunchGrace
import com.lastgenlabs.crunchylist.guard.SessionOrigin
import com.lastgenlabs.crunchylist.settings.SettingsActivity
import com.lastgenlabs.crunchylist.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: WhitelistStore

    /**
     * Set when the guard brought us back rather than the kid navigating here.
     * Hoisted out of Compose so [onNewIntent] can update it — this activity is
     * singleTask, so a bounce arrives as a new intent, not a fresh onCreate.
     */
    private val bounced: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WhitelistStore.get(this)
        bounced.value = intent?.getBooleanExtra(EXTRA_BOUNCED, false) == true

        setContent {
            val shows by store.shows.collectAsState()
            var guardActive by remember { mutableStateOf(GuardPermissions.allGranted(this)) }

            HomeScreen(
                shows = shows,
                guardActive = guardActive,
                wasBounced = bounced.value,
                onBounceMessageShown = { bounced.value = false },
                onShowClick = ::openShow,
                onSettings = {
                    guardActive = GuardPermissions.allGranted(this)
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_BOUNCED, false)) bounced.value = true
    }

    private fun openShow(show: Show) {
        if (!Crunchyroll.isInstalled(this)) {
            Toast.makeText(this, "Crunchyroll isn't installed", Toast.LENGTH_LONG).show()
            return
        }
        // Both markers must be set before the intent.
        //  - LaunchGrace: a legitimate deep link transits Crunchyroll's Startup and
        //    Main activities, both bounce triggers, so without it the guard cancels
        //    its own navigation.
        //  - SessionOrigin: tells the guard this Crunchyroll session came from an
        //    approved tile. Without it the guard bounces Crunchyroll on sight,
        //    which is what blocks Google TV's own "Continue watching" shortcuts.
        LaunchGrace.begin()
        SessionOrigin.beginApprovedSession()
        startActivity(Crunchyroll.seriesIntent(show.seriesId))
    }

    override fun onResume() {
        super.onResume()
        if (GuardPermissions.allGranted(this)) {
            GuardService.start(this)
        }
    }

    companion object {
        const val EXTRA_BOUNCED = "bounced"
    }
}
