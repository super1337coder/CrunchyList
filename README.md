# CrunchyList

A parent-curated, kid-safe front end for [Crunchyroll](https://www.crunchyroll.com). Parents pick exactly which anime series their kids can watch. Everything else is out of reach.

A [Last Gen Labs](https://lastgenlabs.com) project.

## Two front ends

| | |
|---|---|
| **`tv-app/`** | **Google TV / Android TV app.** The active project. Runs on the TV itself — no computer involved. |
| `extension/` | Chrome extension. Works, but only on the laptop it runs on. See [Chrome extension](#chrome-extension) below. |

The extension came first and was abandoned when casting to the TV turned out to be impossible — Crunchyroll removed Chromecast from its web player, and its Widevine DRM blocks the Remote Playback API. The TV app takes a different route: it deep-links into Crunchyroll's own Android app, and enforces the whitelist with a background guard.

See [docs/AUDIT-2026-08.md](docs/AUDIT-2026-08.md) for the full design, every mechanism verified by test, and the traps found along the way.

## The Problem

Crunchyroll's built-in parental controls are one blunt axis — "restrict mature content". That lets through plenty of fan service while blocking things that are perfectly fine. The objection is orthogonal to the dial they give you, so no setting of it works. CrunchyList replaces the dial with an explicit list.

## The Google TV app

**What the kids see:** a grid of approved shows with real poster art. Pick one, and Crunchyroll opens straight to that series — with its own resume state intact, so "Continue: E7" still works.

**What stops them wandering:** a background guard. Crunchyroll's app can otherwise be opened straight from the TV's app menu, and one Back press from a show lands in the full catalogue. The guard watches which activity is foreground and bounces back to CrunchyList whenever Crunchyroll shows anything that isn't an approved screen.

It needs no AccessibilityService — it reads foreground activity via `UsageStatsManager` and returns to the front using a `SYSTEM_ALERT_WINDOW` background-launch exemption.

**It fails closed.** Anything not positively recognised as an approved screen is bounced. If Crunchyroll renames its activities, CrunchyList becomes unusable rather than permissive — and a **Re-verify** button re-learns the new names by observation.

### Setup

Build and install:

```bash
bash tv-app/build.sh assembleDebug
```

```bash
adb install -r tv-app/app/build/outputs/apk/debug/app-debug.apk
```

Then grant the guard's two permissions. Neither is an install-time permission, so this step is required — without them CrunchyList filters nothing:

```bash
adb shell appops set com.lastgenlabs.crunchylist GET_USAGE_STATS allow
```

```bash
adb shell appops set com.lastgenlabs.crunchylist SYSTEM_ALERT_WINDOW allow
```

Open CrunchyList, set a parent PIN, and add shows by pasting a Crunchyroll series URL. Titles and poster art are fetched automatically.

> **Note:** `tv-app/build.sh` is a wrapper, not decoration — it redirects `TEMP` before starting the JVM. See the Gradle gotcha in the audit if you're curious why.

---

## Chrome extension

The original laptop-only version. Still functional, but it cannot get video to a TV.

## How It Works

- **Kids** open Chrome and see a tile grid of parent-approved shows. Clicking a tile goes to that show's Crunchyroll page, stripped down to just the video player and episode list. Everything else (browse, search, recommendations, etc.) is hidden.
- **Parents** manage the approved show list through a PIN-protected settings page. Just paste a Crunchyroll series URL and the extension pulls in the title and poster art automatically.
- **Navigation is locked down.** Any attempt to browse to a non-approved show redirects back to the CrunchyList landing page.

## Install

CrunchyList isn't on the Chrome Web Store yet. Install it manually in developer mode:

1. **Download**: Clone this repo or [download the ZIP](../../archive/refs/heads/main.zip) and unzip it
2. **Open Chrome Extensions**: Navigate to `chrome://extensions/`
3. **Enable Developer Mode**: Toggle the switch in the top right
4. **Load the extension**: Click "Load unpacked" and select the `extension/` folder (not the repo root)
5. **Log into Crunchyroll**: Make sure the Chrome profile is logged into a Crunchyroll account
6. **Set your PIN**: Open the options page (right-click the CrunchyList icon > **Options**, or go to `chrome://extensions`, click the three-dot menu on CrunchyList, and select **Options**). Create a 4-digit PIN
7. **You're ready**: Open a new tab to see the landing page. SPY x FAMILY is included as a starter show. Add more from the options page.

## Setup

### Adding Shows

1. Open the CrunchyList options page: right-click the extension icon and select **Options**, or go to `chrome://extensions`, click the three-dot menu (⋮) on CrunchyList, and select **Options**
2. Enter your 4-digit PIN
3. Paste a Crunchyroll series URL (e.g., `https://www.crunchyroll.com/series/G4PH0WXVJ/spy-x-family`)
4. The title and poster image are fetched automatically
5. Click **Add Show**

### Fetching Images

If you've added shows and they're missing poster art, click the **Fetch All Images** button on the options page to pull artwork from Crunchyroll for every show in your list.

### Removing Shows

On the options page, click the **Remove** button next to any show.

## What Gets Hidden

On approved show pages, CrunchyList hides:
- Navigation bar, search, browse/discover links
- Recommendations ("More Like This") and carousels
- Comments, social/share buttons
- Footer, sidebar, promotional banners
- Account/profile links

What's preserved:
- Video player
- Episode list and season selector
- Watch progress indicators

## How Navigation Enforcement Works

- **Series pages** (`/series/{ID}`): allowed if the series ID is in the whitelist
- **Watch pages** (`/watch/{ID}`): allowed via lazy whitelisting (if the kid navigated from an approved series page) with a fallback check using page metadata
- **Everything else** on crunchyroll.com: redirects to the CrunchyList landing page

The extension only controls `crunchyroll.com`. It cannot block other websites. For a fully locked-down experience, see [Browser Hardening](#browser-hardening) below.

## Browser Hardening

For a more locked-down kids' Chrome profile, configure these via Chrome enterprise policies:

- Force-install the CrunchyList extension (prevents removal)
- Disable Chrome developer tools
- Disable incognito mode
- Use Chrome's site allowlist to restrict browsing to `crunchyroll.com` only

This is optional and outside the extension itself. See the [Chrome Enterprise policy docs](https://chromeenterprise.google/policies/) for details.

## Technical Details

- **Manifest V3** Chrome extension
- **No build step, no bundler, no dependencies.** Pure vanilla JS/HTML/CSS
- **Storage**: Whitelist metadata in `chrome.storage.sync` (syncs across devices), images and PIN hash in `chrome.storage.local`
- **Content hiding**: CSS injection at `document_start` (no flicker) + DOM cleanup via MutationObserver at `document_idle`
- **Image fetching**: Uses Crunchyroll's public CMS API to pull poster art

## Project Structure

```
CrunchyList/
├── README.md
├── .gitignore
├── docs/
│   └── CRUNCHYLIST-REQUIREMENTS.md
└── extension/              # Load this folder in Chrome
    ├── manifest.json
    ├── background.js       # Navigation interception
    ├── content.css         # CSS hiding (document_start)
    ├── content.js          # DOM cleanup + watch page validation
    ├── landing.html/js/css # Kid-facing tile grid
    ├── options.html/js     # PIN-protected parent settings
    ├── whitelist.json      # Default whitelist (SPY x FAMILY)
    └── icons/
```

## License

MIT
