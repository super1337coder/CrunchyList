# Where things stand — 2026-08-08

Pick-up notes. For the *why* behind any decision, see [AUDIT-2026-08.md](AUDIT-2026-08.md);
this file is just current state and what's next.

## State

`tv-app/` is a working Google TV app, verified end to end on the emulator (Google TV, API 36).
`extension/` is legacy and marked as such — it fails open in five places and shares no code.

| | |
|---|---|
| Guard (the thing that makes this a parental control) | ✅ 10 behavioural checks pass |
| Tile grid, detail panel, More Info screen | ✅ |
| 27 shows seeded with art, write-ups, cast and portraits | ✅ |
| Unit tests | ✅ 40 pass |
| Signed release APK | ✅ `dist/`, tag `v0.1.0` pushed |
| GitHub release published | ❌ **needs your GitHub login** |
| Tested on the physical Streamer | ❌ never |

Build with `bash tv-app/build.sh` — **not** `gradlew` directly. It redirects `TEMP` before the
JVM starts, working around AF_UNIX being blocked under `AppData\Local\Temp` on this machine.

```bash
bash tv-app/build.sh assembleDebug     # build
bash tv-app/build.sh testDebugUnitTest # 40 unit tests
bash tools/verify-guard.sh             # 10 behavioural checks, needs a device
bash tools/release.sh v0.1.0           # signed APK + tag + publish
```

## Next, roughly in order

1. **Publish the v0.1.0 release.** Blocked on GitHub auth. Tag is pushed, APK is built.
   Browser: `https://github.com/super1337coder/CrunchyList/releases/new?tag=v0.1.0` — notes
   from [RELEASE-NOTES.md](RELEASE-NOTES.md), attach `dist/crunchylist-tv-0.1.0.apk`. Or
   `winget install GitHub.cli && gh auth login && bash tools/release.sh v0.1.0`.
   **Rebuild the APK first if commits have landed since.**

2. **Add Mob Psycho 100 (`GY190DKQR`) and Dr. STONE (`GYEXQKJG6`).** Approved. Mob Psycho was
   missing from the source list only because they're watching it right now.

3. **Expand every write-up.** The current text is too condensed. Chris's example: Frieren's
   *"An elf mage who outlived the friends she quested with"* should go further into who she is
   and what she can do — same for the show descriptions. The kids are 10 and 13 and read well
   on a big screen, so length is not the constraint. Probably wants a longer field
   (`Show.about`, `CastMember.bio`) plus UI in `MoreInfoDialog`.

4. **Test on the Streamer.** The specific unknown is whether the guard survives a real boot.

## Shows: decided, held, and unavailable

**Approved, not yet added:** Mob Psycho 100 `GY190DKQR`, Dr. STONE `GYEXQKJG6`.

**On hold — do not add without an explicit go-ahead:**

| Show | ID | Why held |
|---|---|---|
| Demon Slayer | `GY5P48XEY` | "will add soon" — nearly ready |
| My Hero Academia | `G6NQ5DWZ6` | carries a *Sexualized Imagery* label |
| Dandadan | `GG5H5XQ0D` | episode one — which is why CR labels it *Sexual Violence* |

**Declined:** Jujutsu Kaisen `GRDV0019R` — too violent for this age.

**Not on Crunchyroll at all** (checked under English and Japanese titles): Blue Box, Little
Witch Academia, One Punch Man, Silver Spoon. The last two were listed under Crunchyroll in the
source doc, so their licensing has moved.

**Researched and clean, not yet proposed:** Wind Breaker `G3KHEVDPE` (*Violence* only),
Blue Lock `G4PH0WEKE` (*Profanity* only), World Trigger `GR757DMKY`.

## Traps that will bite again

Each of these cost real time. They are all in the audit too, repeated here because they are
the things most likely to waste an hour.

- **Nothing scrolls on TV unless something can take focus.** Bit twice — the detail panel and
  the More Info dialog. A `LazyColumn` with no focusable content simply will not move, and the
  content below the fold is unreachable. Panel content is sized to fit; the dialog takes focus
  itself and turns up/down into a scroll.
- **`GridCells.Adaptive` sits on a boundary.** `TILE_MIN_WIDTH` and `PANEL_WIDTH` in
  `HomeScreen.kt` are a pair — changing either silently changes the column count.
- **`getLaunchIntentForPackage()` returns null for TV apps.** They declare
  `CATEGORY_LEANBACK_LAUNCHER`, not `CATEGORY_LAUNCHER`. Looks exactly like "not installed".
- **Crunchyroll's API wants an honest User-Agent.** A browser UA gets 403 from Android —
  Cloudflare rejects the mismatch. The opposite of what the extension needed.
- **AniList title matching is unreliable.** Three of 27 matched the wrong entry, one of them a
  completely different show. Always print the matched title and check it. `sort:SEARCH_MATCH`
  is required or Frieren returns a spin-off special.
- **PowerShell 5.1 reads files as ANSI unless told otherwise.** `Get-Content -Raw -Encoding UTF8`,
  and no non-ASCII literals in `.ps1` files — both produced mojibake in show titles.
- **Git Bash rewrites paths starting with `/`.** `MSYS_NO_PATHCONV=1` for adb device paths, but
  it must not be set globally or local file paths break too.

## Standing principle

Every serious bug in this project failed the same way: **the app looked healthy and protected
nothing.** A guard that never armed, a whitelist that never reached the grid, a calibration
that approved the entire catalogue and reported success.

So `ScreenClassifier` fails closed, and `tools/verify-guard.sh` asserts only on observable
behaviour and never reads the app's own status text. Keep both properties.
