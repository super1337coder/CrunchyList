# CrunchyList

A Chrome extension that creates a parent-curated, kid-safe front end for [Crunchyroll](https://www.crunchyroll.com). Parents pick exactly which anime series their kids can watch. Everything else is hidden.

A [Last Gen Labs](https://lastgenlabs.com) project.

![CrunchyList landing page](screenshots/MainMenu.png)

## The Problem

Crunchyroll's built-in parental controls are too blunt. Either too much fan service gets through, or the filters are so tight that age-appropriate shows get blocked. CrunchyList solves this by letting a parent hand-pick exactly which series are allowed.

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
3. Paste a Crunchyroll series URL (e.g., `https://www.crunchyroll.com/series/GEXH3WKP7/spy-x-family`)
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
