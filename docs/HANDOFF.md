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
| 29 shows seeded with art, long write-ups, cast, portraits and bios | ✅ |
| Unit tests | ✅ 51 pass |
| Signed release APK | ✅ `dist/`, tag `v0.1.1` pushed |
| GitHub release published | ❌ **needs your GitHub login** |
| Tested on the physical Streamer | ❌ never |

Build with `bash tv-app/build.sh` — **not** `gradlew` directly. It redirects `TEMP` before the
JVM starts, working around AF_UNIX being blocked under `AppData\Local\Temp` on this machine.

```bash
bash tv-app/build.sh assembleDebug     # build
bash tv-app/build.sh testDebugUnitTest # 51 unit tests
bash tools/verify-guard.sh             # 10 behavioural checks, needs a device
bash tools/release.sh v0.1.1           # signed APK + tag + publish
```

## Next, roughly in order

1. **Publish the v0.1.1 release.** Blocked on GitHub auth. Tag is pushed, APK is built.
   Browser: `https://github.com/super1337coder/CrunchyList/releases/new?tag=v0.1.1` — notes
   from [RELEASE-NOTES.md](RELEASE-NOTES.md), attach `dist/crunchylist-tv-0.1.1.apk`. Or
   `winget install GitHub.cli && gh auth login && bash tools/release.sh v0.1.1`.
   **Rebuild the APK first if commits have landed since.**

2. **Test on the Streamer.** The specific unknown is whether the guard survives a real boot.
   Everything to date is emulator-only.

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

## Traps that will bite again

Each of these cost real time. They are all in the audit too, repeated here because they are
the things most likely to waste an hour.

- **Nothing scrolls on TV unless something can take focus.** Bit twice — the detail panel and
  the More Info dialog. A `LazyColumn` with no focusable content simply will not move, and the
  content below the fold is unreachable. Panel content is sized to fit; the dialog takes focus
  itself and turns up/down into a scroll.
- **A Column measures unweighted children first.** In `ShowDetailPanel` the content had no
  weight, so it took the whole panel and the buttons under it were laid out past the bottom
  edge and clipped — on exactly the shows with the most to say. They were still focusable, so
  selecting one of those tiles handed focus to an invisible Play button and the remote appeared
  to stop responding. The content is weighted now, and the description ellipsizes.
- **Clipping is not truncation.** A `Text` that overflows its parent gets cut through a line of
  type. Give it `weight(1f, fill = false)` and `TextOverflow.Ellipsis` so it trails off instead.
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
