# Where things stand — 2026-08-08

Pick-up notes. For the *why* behind any decision, see [AUDIT-2026-08.md](AUDIT-2026-08.md);
this file is just current state and what's next.

## State

`tv-app/` is a working Google TV app, running on the family room Streamer (Android 14) and
verified end to end there as well as on the emulator.
`extension/` is legacy and marked as such — it fails open in five places and shares no code.

| | |
|---|---|
| Guard (the thing that makes this a parental control) | ✅ 10 behavioural checks pass |
| Shelves, Keep watching, Surprise me, detail panel, More Info | ✅ |
| 29 shows seeded with art, long write-ups, cast, portraits and bios | ✅ |
| Unit tests | ✅ 85 pass |
| Signed release APK | ✅ `dist/`, tag `v0.1.3` pushed |
| GitHub release published | ❌ **needs your GitHub login** |
| Tested on the physical Streamer | ✅ 2026-08-08 — all 10 checks pass on hardware |

Build with `bash tv-app/build.sh` — **not** `gradlew` directly. It redirects `TEMP` before the
JVM starts, working around AF_UNIX being blocked under `AppData\Local\Temp` on this machine.

```bash
bash tv-app/build.sh assembleDebug     # build
bash tv-app/build.sh testDebugUnitTest # 85 unit tests
bash tools/verify-guard.sh             # 10 behavioural checks, needs a device
bash tools/release.sh v0.1.3           # signed APK + tag + publish
```

## Next, roughly in order

1. **Publish the v0.1.3 release.** Blocked on GitHub auth. Tag is pushed, APK is built.
   Browser: `https://github.com/super1337coder/CrunchyList/releases/new?tag=v0.1.3` — notes
   from [RELEASE-NOTES.md](RELEASE-NOTES.md), attach `dist/crunchylist-tv-0.1.3.apk`. Or
   `winget install GitHub.cli && gh auth login && bash tools/release.sh v0.1.3`.
   **Rebuild the APK first if commits have landed since.**
   (`v0.1.0`–`v0.1.2` are earlier tags that were never published and now point at code
   with the first-play bounce in it. `git push origin :refs/tags/v0.1.0 :refs/tags/v0.1.1
   :refs/tags/v0.1.2` clears them.)

2. **The reboot test.** The one thing hardware has still not answered: does the guard come
   back after the Streamer is power-cycled? On the emulator it does — `GuardService` was alive
   before the app was opened. Check with the TV connected:
   `adb -s <ip> shell dumpsys activity services com.lastgenlabs.crunchylist | grep -c ServiceRecord`
   *before* opening CrunchyList.

3. **Shows on hold** — see below. Demon Slayer is the nearest.

## Shows: decided, held, and unavailable

**On the list:** 29 entries, [WATCHLIST.md](WATCHLIST.md). Mob Psycho 100 `GY190DKQR` and
Dr. STONE `GYEXQKJG6` were added in 0.1.1.

**On hold — do not add without an explicit go-ahead:**

| Show | ID | Why held |
|---|---|---|
| Demon Slayer | `GY5P48XEY` | "will add soon" — nearly ready. CR labels: *Violence, Suicide* |
| My Hero Academia | `G6NQ5DWZ6` | carries a *Sexualized Imagery* label |
| Dandadan | `GG5H5XQ0D` | episode one — which is why CR labels it *Sexual Violence* |

**Declined:** Jujutsu Kaisen `GRDV0019R` — too violent for this age.

**Not on Crunchyroll at all** (checked under English and Japanese titles): Blue Box, Little
Witch Academia, One Punch Man, Silver Spoon. The last two were listed under Crunchyroll in the
source doc, so their licensing has moved.

**Researched and clean, not yet proposed:** Wind Breaker `G3KHEVDPE` (*Violence* only — Furin
High delinquents who protect their town, fists only, no weapons or gore, essentially no romance
or fanservice), Blue Lock `G4PH0WEKE` (*Profanity* only), World Trigger `GR757DMKY`.

## Adding a show

```bash
powershell -File tools/fetch-show.ps1 GY5P48XEY "Kimetsu no Yaiba"
```

Prints poster art, episode counts, rating, Crunchyroll's content labels and main-cast
portraits. **Check the AniList title it prints** — three of the original 27 matched the wrong
entry. Then hand-write `hook`, `description`, `about`, and a `role` and `bio` per character
into `tv-app/app/src/main/assets/default_whitelist.json`. The prose is deliberately not
generated; see the note at the top of that script.

`about` is blank-line-separated paragraphs. Keep `description` short — the side panel is sized
to fit rather than scroll, so length there gets cut off. Length belongs in `about`.

## Connecting to the Streamer

Android 14 wants a pairing step before adb will connect. On the TV:
Settings → System → About → click **Android TV OS build** ×7, then
Settings → System → Developer options → **Wireless debugging** → **Pair device with pairing
code**. That screen shows a 6-digit code and an IP:port — note the port is *different* from the
one on the Wireless debugging screen itself.

```bash
adb pair 192.168.1.29:<pairing-port> <code>
adb connect 192.168.1.29:<connect-port>
```

The connect port changes on reboot; the pairing is one-time. Everything after that takes
`-s <ip>:<port>`, because the emulator is usually attached too.

## Traps that will bite again

Each of these cost real time. They are all in the audit too, repeated here because they are
the things most likely to waste an hour.

- **Nothing scrolls on TV unless something can take focus.** Bit twice — the detail panel and
  the More Info dialog. A `LazyColumn` with no focusable content simply will not move, and the
  content below the fold is unreachable. Panel content is sized to fit; the dialog takes focus
  itself and turns up/down into a scroll.
- **`UsageStatsManager` reports where the TV *was*.** The foreground reading lags, so a tick
  right after a deep link can still see CrunchyList. Acting on that stale reading threw away the
  approval just granted and bounced the kid out of a show they were allowed to watch — on the
  first play only, which made it look random. All of the timing rules now live in
  `GuardDecision`, pure and tested; do not put conditions back inline in `GuardService.tick`.
- **`FLAG_ACTIVITY_NEW_TASK` alone reuses the target's task.** Whatever Crunchyroll had on its
  stack came back with it, and a restored show page classifies as an approved screen inside an
  approved session. Deep links carry `CLEAR_TASK` for that reason, not for tidiness.
- **The guard blocks Crunchyroll's own sign-in screen.** It is not a show page and not the
  player, so it fails closed — which means a fresh device cannot be set up. `GuardPause` is the
  way through; there is no other one.
- **Coming back from Crunchyroll recreates MainActivity.** The task ID changes, Compose state
  is gone, and focus resets to the very first tile. Verified, not assumed. Keep watching is
  what makes that survivable — the show you were just watching is the first tile.
- **A Column measures unweighted children first.** In `ShowDetailPanel` the content had no
  weight, so it took the whole panel and the buttons under it were laid out past the bottom
  edge and clipped — on exactly the shows with the most to say. They were still focusable, so
  selecting one of those tiles handed focus to an invisible Play button and the remote appeared
  to stop responding. The content is weighted now, and the description ellipsizes.
- **Clipping is not truncation.** A `Text` that overflows its parent gets cut through a line of
  type. Give it `weight(1f, fill = false)` and `TextOverflow.Ellipsis` so it trails off instead.
- **An ellipsis has to lead somewhere.** Ellipsizing the panel blurb looked tidy and was worse
  than clipping: the panel shows `description` and More info shows `about`, so the trimmed
  words existed nowhere. Every bundled blurb is now sized to fit the panel whole. If you edit
  one, the budget is roughly 250–300 characters depending on how many lines the title and hook
  take, and the way to check is `tools/`-free: screenshot each panel and look. Anything that
  does still ellipsize is reachable, because `hasMoreInfo` is true for any show with text.
- **`GridCells.Adaptive` sits on a boundary.** `TILE_MIN_WIDTH` and `PANEL_WIDTH` in
  `HomeScreen.kt` are a pair — changing either silently changes the column count.
- **`getLaunchIntentForPackage()` returns null for TV apps.** They declare
  `CATEGORY_LEANBACK_LAUNCHER`, not `CATEGORY_LAUNCHER`. Looks exactly like "not installed".
- **Crunchyroll's API wants an honest User-Agent.** A browser UA gets 403 from Android —
  Cloudflare rejects the mismatch. The opposite of what the extension and the desktop tools
  need.
- **AniList title matching is unreliable.** Three of 27 matched the wrong entry, one of them a
  completely different show. Always print the matched title and check it. `sort:SEARCH_MATCH`
  is required or Frieren returns a spin-off special.
- **PowerShell 5.1 reads a BOM-less `.ps1` as ANSI.** One em-dash in a string literal is a
  *parse error*, not a wrong character. Keep `.ps1` files pure ASCII — this bit again writing
  `fetch-show.ps1`. `<--` in a string is also a parse error; `<` is a reserved operator.
- **`Get-Content -Raw` needs `-Encoding UTF8`** or show titles come back as mojibake.
- **Git Bash rewrites paths starting with `/`.** `MSYS_NO_PATHCONV=1` for adb device paths
  (including `adb shell screencap -p /sdcard/x.png`, not just `adb pull`), but it must not be
  set globally or local file paths break too.
- **Seeding is not the same as displaying.** The bundled list is read on first run only for
  *membership*; text is refreshed every launch by `SeedMerge`. Before that split, shipping a
  better write-up did nothing on an installed device and every step still reported success.

## Standing principle

Every serious bug in this project failed the same way: **the app looked healthy and protected
nothing.** A guard that never armed, a whitelist that never reached the grid, a calibration
that approved the entire catalogue and reported success, new copy that shipped and was never
displayed.

So `ScreenClassifier` fails closed, `SeedMerge` is pure and separately tested, and
`tools/verify-guard.sh` asserts only on observable behaviour and never reads the app's own
status text. Keep those properties.
