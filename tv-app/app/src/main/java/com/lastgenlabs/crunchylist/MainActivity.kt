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
import androidx.lifecycle.lifecycleScope
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import com.lastgenlabs.crunchylist.data.WhitelistStore
import com.lastgenlabs.crunchylist.guard.GuardPermissions
import com.lastgenlabs.crunchylist.guard.GuardService
import com.lastgenlabs.crunchylist.guard.LaunchGrace
import com.lastgenlabs.crunchylist.settings.SettingsActivity
import com.lastgenlabs.crunchylist.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private lateinit var store: WhitelistStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WhitelistStore(this)

        setContent {
            val shows by store.shows.collectAsState()
            var guardActive by remember { mutableStateOf(GuardPermissions.allGranted(this)) }

            HomeScreen(
                shows = shows,
                guardActive = guardActive,
                onShowClick = ::openShow,
                onSettings = {
                    guardActive = GuardPermissions.allGranted(this)
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            )
        }
    }

    private fun openShow(show: com.lastgenlabs.crunchylist.data.Show) {
        if (!Crunchyroll.isInstalled(this)) {
            Toast.makeText(this, "Crunchyroll isn't installed", Toast.LENGTH_LONG).show()
            return
        }
        // Grace MUST begin before the intent. A legitimate deep link transits
        // Crunchyroll's Startup and Main activities, both of which are bounce
        // triggers — without this the guard cancels its own navigation.
        LaunchGrace.begin()
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
