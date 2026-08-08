CrunchyList turns Crunchyroll on a Google TV into a grid of shows a parent picked, and keeps
it that way.

Crunchyroll's own parental control is one blunt axis — "restrict mature content" — which lets
plenty of fan service through while blocking things that are perfectly fine. This replaces the
dial with an explicit list.

### What it does

- **A curated grid.** Kids see only approved shows, with real poster art. Pick one and
  Crunchyroll opens straight to it, resume state intact.
- **A guard that holds the line.** Crunchyroll can otherwise be opened straight from the TV's
  app menu, one Back press from a show lands in the full catalogue, and Google TV's own
  "Continue watching" row resumes anything. All three are bounced back.
- **Ships with 27 shows** already loaded, so there is nothing to type on a remote.

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
- Tested on a Google TV emulator (Android 16 / API 36). Not yet tested on physical hardware.

Run `bash tools/verify-guard.sh` against a device to confirm the guard is actually enforcing.
It checks behaviour only and never trusts the app's own status text.
