CrunchyList turns Crunchyroll on a Google TV into a grid of shows a parent picked, and keeps
it that way.

Crunchyroll's own parental control is one blunt axis — "restrict mature content" — which lets
plenty of fan service through while blocking things that are perfectly fine. This replaces the
dial with an explicit list.

### What it does

- **Curated shelves.** Kids see only approved shows, with real poster art — Keep watching
  first, then a shelf per category. Highlight one for a write-up of it; select it for **Play**
  and **More info**. Play opens Crunchyroll straight to the series, resume state intact.
- **A page on every show.** More info gives the full write-up plus a portrait and a paragraph
  on each main character — who they are and what they can do. Hand-written, not scraped.
- **A guard that holds the line.** Crunchyroll can otherwise be opened straight from the TV's
  app menu, one Back press from a show lands in the full catalogue, and Google TV's own
  "Continue watching" row resumes anything. All three are bounced back.
- **Ships with 29 shows** already loaded, so there is nothing to type on a remote.

### Changes in 0.1.3

First version tested on real hardware — a Google TV Streamer, Android 14. All ten guard checks
pass there, not just on the emulator.

- **A parent can now use Crunchyroll.** Found on the first real TV: with the guard running there
  was no way to reach Crunchyroll's sign-in screen, so a fresh device could not be set up at all.
  Settings → **Let a parent use Crunchyroll** opens a 15-minute window and closes it again on
  its own. Time-boxed rather than a switch, and the home screen shows a loud countdown while it
  is open — a guard that is off must never be quiet about it.
- **Fixed: the first play of a session sometimes bounced straight back out.** `UsageStatsManager`
  reports where the TV *was*, not where it is, so a tick immediately after a launch could still
  see CrunchyList in front and throw away the approval it had just granted. The timing rules are
  now a separate, tested unit rather than inline conditions.
- **Content labels removed from the kids' screen.** They are how a parent decides whether a show
  goes on the list; once it is on the list that decision is made. Still carried in the data and
  still printed by `tools/fetch-show.ps1`.
- **New icon and banner** — the family dog, in Crunchyroll orange.
- Deep links now clear Crunchyroll's task rather than reusing it, so nothing left on that stack
  can be restored into an approved session.

### Changes in 0.1.2

- **Keep watching.** A shelf of what has actually been played, newest first, and focus starts
  there. Built from the app's own launch history — no API, no watch-history permission, nothing
  to keep up to date. Coming back from an episode used to reset to the top of the alphabet.
- **Shelves instead of one alphabetical grid.** A shelf per category, in a deliberate order.
  Every move down now means something instead of being the next twelve posters.
- **Surprise me.** Picks a show at random and hands focus to Play.
- Fixed: a long write-up pushed **Play** and **More info** off the bottom of the panel while
  leaving them focusable, so selecting one of those shows appeared to freeze the remote.
- The panel no longer repeats the category or the episode counts — the shelf heading beside it
  already says the first, and More info has the second. That bought enough room for every
  write-up and every title to appear in full, verified show by show.

### Changes in 0.1.1

- **Every show and character write-up expanded**, from a couple of lines to several paragraphs
  and a real bio per character. About 11,000 words of it.
- **Added Mob Psycho 100 and Dr. STONE.**
- **Selecting a tile opens the panel** rather than launching straight into Crunchyroll, so
  **More info** is somewhere you land rather than something you find by accident.
- **Updating the app now refreshes the bundled write-ups.** Which shows are on the list stays
  yours — remove one and it stays gone — but the text attached to them comes from the APK, so
  improving it reaches an installed TV. Previously new copy shipped and was never displayed.
- `tools/fetch-show.ps1` pulls art, facts and cast portraits for a new show.

It needs no AccessibilityService — it reads the foreground activity through `UsageStatsManager`.

### Install

1. Enable **unknown sources** on the Streamer, then sideload the APK.
2. Open CrunchyList → **Settings** → **Grant usage access** and **Grant overlay access**.
   Both open the real Google TV Settings screens.
3. Set a parent PIN.

**Without those two permissions the app filters nothing** — it says so in red on the home
screen rather than pretending otherwise.

### Known limits

- Inside an already-approved session the guard cannot tell *which* show is on screen; it knows
  the session started from CrunchyList and which kind of screen is showing. Per-title
  enforcement would need content identity that Android does not expose to us.
- The `crunchyroll://` deep links and Crunchyroll's activity names are undocumented and could
  change with any Crunchyroll update. The guard fails **closed** if they do — the app becomes
  unusable rather than unfiltered — and **Settings → Re-verify Crunchyroll** re-learns them by
  observation.
- Tested on a Google TV Streamer (Android 14) as well as the emulator. Verified across a cold
  reboot: with nobody opening CrunchyList first, going straight to Crunchyroll still bounced.

Run `bash tools/verify-guard.sh` against a device to confirm the guard is actually enforcing.
It checks behaviour only and never trusts the app's own status text.
