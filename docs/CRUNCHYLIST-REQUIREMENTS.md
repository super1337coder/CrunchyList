# CrunchyList — Project Scope & Requirements

## Overview

CrunchyList is a Chrome extension that creates a parent-curated, kid-safe front end for Crunchyroll. It replaces the default Crunchyroll browsing experience with a locked-down view: a custom landing page showing only whitelisted shows, and aggressive content hiding on Crunchyroll pages so kids can only see the video player and episode list for approved series.

## Problem

Crunchyroll's built-in parental controls are too blunt — either too much fan service gets through, or the filters are so tight that age-appropriate shows get blocked. CrunchyList solves this by letting a parent hand-pick exactly which series are allowed.

## Expected Workflow

1. Kids open Chrome on the laptop (CrunchyList landing page loads automatically)
2. They pick a show from the tile grid
3. CR series page loads (stripped down to just episodes + player)
4. They hit play, then cast to the Google TV via the native Chromecast button in CR's player
5. Video streams directly from CR to the TV at full quality; laptop acts as a remote
6. CR handles watch progress natively — next time they click that show tile, they resume where they left off

## User Roles

- **Parent (admin):** Manages the whitelist. Single user. Options page is protected by a simple PIN (4-digit numeric code, set on first use). This prevents curious kids from right-clicking the extension icon → Options and modifying the whitelist. PIN is stored hashed in `chrome.storage.local`.
- **Kids:** See only the CrunchyList landing page and whitelisted Crunchyroll content. One shared whitelist for all kids.

## Core Features

### 1. Custom Landing Page (New Tab Override + Extension Popup)

- **New Tab Override** (`chrome_url_overrides.newtab`): Replaces Chrome's new tab page with the CrunchyList landing page. This is the primary entry point — when kids open Chrome or a new tab, they see the show grid automatically.
- **Extension Popup** (`action.default_popup`): Same landing page, accessible by clicking the extension icon. Provides quick access when a new tab isn't open.
- Displays a grid of tiles for each whitelisted series
- Each tile shows the series artwork and title
- Clicking a tile navigates directly to that series page on crunchyroll.com
- Clean, simple UI — big tiles, easy for kids to browse
- Serves as the "home base" kids return to when navigating away from allowed content

### 2. Whitelist Management (Options Page)

- Parent-only interface accessed via the extension's options page
- Add a show by pasting a Crunchyroll series URL (e.g., `https://www.crunchyroll.com/series/GEXH3WKP7/spy-x-family`)
- Extension extracts the series ID and title automatically (fetch from CR page or let parent type the display name)
- Optional: paste or upload a thumbnail image URL for the tile
- Remove shows from the whitelist
- Whitelist metadata (series ID, title, URL) stored in `chrome.storage.sync` so it persists across sessions and syncs if using the same Chrome profile elsewhere
- Image URLs stored in `chrome.storage.local` to avoid hitting the ~100KB `sync` quota (sync is limited to ~8KB per item). If sync storage is near capacity, the extension should degrade gracefully by falling back to local-only storage

### 3. Content Filtering on Crunchyroll Pages

#### On Whitelisted Series Pages (`/series/{ID}/*`)
- **Show:** Episode list, series banner/art, season selector
- **Hide:** Top navigation bar, search, sidebar, footer, recommendations ("More Like This"), news/promotional banners, comments, social sharing, any "browse" or "discover" links

#### On Watch Pages (`/watch/*` belonging to a whitelisted series)
- **Show:** Video player, episode title, episode selector/next-prev navigation within the series
- **Hide:** Everything else — nav, search, recommendations, comments, sidebar, footer, "Up Next" suggestions from other series

#### On Any Non-Whitelisted Page
- Redirect immediately to the CrunchyList landing page
- This includes: CR homepage, browse, search, any non-whitelisted series or watch page, account/settings pages

### 4. Navigation Interception

- Use `chrome.webNavigation` or `chrome.declarativeNetRequest` to intercept all navigation on `*.crunchyroll.com`
- Allow navigation only to URLs matching whitelisted series patterns:
  - `/series/{whitelisted_ID}/*`
  - `/watch/{episode_ID}` — need to verify the episode belongs to a whitelisted series (may require checking the page content or CR's internal data)
- All other crunchyroll.com URLs redirect to the CrunchyList landing page

## URL Pattern Matching

Crunchyroll URL structure (with optional locale prefix):
```
Series page:  https://www.crunchyroll.com/[{locale}/]series/{SERIES_ID}/{slug}
Watch page:   https://www.crunchyroll.com/[{locale}/]watch/{EPISODE_ID}/{slug}

Examples:
  https://www.crunchyroll.com/series/GEXH3WKP7/spy-x-family
  https://www.crunchyroll.com/en-us/series/GEXH3WKP7/spy-x-family
```

### Locale Handling
CR URLs sometimes include locale prefixes (e.g., `/en-us/`, `/fr/`, `/pt-br/`). All URL pattern matching must account for an optional locale segment. The regex pattern should match:
```
/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:series|watch)/...
```
This applies to both the `background.js` navigation interception and the `content.js` page detection logic.

### Series-Level Whitelisting
- The whitelist stores **series IDs** (e.g., `GEXH3WKP7`)
- Series pages are easy to match: check if the URL contains a whitelisted series ID
- Watch pages are harder: the URL contains an episode ID, not a series ID. Strategies:
  1. **Page content inspection:** After the page loads, check for the series ID in the page source / meta tags / structured data
  2. **CR internal API:** Fetch episode metadata to get the parent series ID (may be fragile)
  3. **Lazy whitelisting:** When a kid clicks an episode from an allowed series page, temporarily whitelist that episode URL. This avoids needing to resolve episode → series mapping.

**Recommended approach:** Option 3 (lazy whitelisting) as the primary mechanism. Track the last whitelisted series page visited, and allow any `/watch/` navigation that originated from that page. Option 1 (page content inspection) as a fallback for direct `/watch/` URLs — the content script on watch pages should check for the series ID in CR's page metadata (e.g., `<meta>` tags, JSON-LD structured data, or `__NEXT_DATA__`) and redirect to the landing page if the series is not whitelisted. This two-layer approach handles both the common case (clicking from a series page) and edge cases (bookmarked or shared episode links).

## Technical Architecture

### Manifest (V3)
```
manifest.json
├── permissions: storage, webNavigation, activeTab, declarativeNetRequest
├── host_permissions: *://*.crunchyroll.com/*
├── content_scripts: [crunchyroll.com] → content.js
├── background: service-worker.js
├── chrome_url_overrides: newtab → landing.html
├── options_page: options.html
└── action: popup → landing.html
```

### Key Files (within `extension/`)
```
extension/
├── manifest.json
├── whitelist.json          # Default/fallback whitelist (bundled)
├── landing.html            # Kid-facing landing page with show tiles
├── landing.js              # Reads whitelist from storage, renders tiles
├── landing.css             # Tile grid styling
├── content.js              # Injected on CR pages — hides elements, checks whitelist
├── content.css             # Injected CSS to hide CR UI elements at document_start
├── background.js           # Navigation interception, whitelist enforcement
├── options.html            # Parent whitelist management UI (PIN-protected)
├── options.js              # Add/remove shows, PIN verification, storage management
└── icons/                  # Extension icons
```

### Data Model

Whitelist metadata stored in `chrome.storage.sync` (lightweight, stays under quota):
```json
{
  "whitelist": [
    {
      "seriesId": "GEXH3WKP7",
      "title": "SPY x FAMILY",
      "url": "https://www.crunchyroll.com/series/GEXH3WKP7/spy-x-family",
      "dateAdded": "2026-02-27"
    }
  ]
}
```

Image URLs stored separately in `chrome.storage.local` (no quota pressure):
```json
{
  "images": {
    "GEXH3WKP7": "https://www.crunchyroll.com/imgsrv/display/thumbnail/480x720/catalog/crunchyroll/..."
  }
}
```

PIN hash stored in `chrome.storage.local`:
```json
{
  "pinHash": "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
}
```

## Content Hiding Strategy

Use CSS injection as the first pass (fast, no flicker) and DOM manipulation as cleanup:

1. **Inject CSS immediately** via `content_scripts` with `"run_at": "document_start"` to hide known CR UI elements by selector (nav, footer, search bar, recommendation sections)
2. **DOM cleanup** via `content.js` at `document_idle` to remove/hide any dynamically loaded elements that CSS alone can't catch (CR uses React, so many elements load after initial render)
3. **MutationObserver** to catch late-loading recommendations and promotional content

### Known CR Selectors to Target (will need updating as CR changes their UI)
- Top nav / header
- Search input
- Browse/discover links
- "More Like This" / recommendation carousels
- Footer
- Comments section
- Social/share buttons
- Promotional banners
- Account/profile links

> **Note: Preserve the cast button.** The CR video player includes a native Chromecast button. This must NOT be hidden — it's the primary way kids will send video to the Google TV at full streaming quality (vs. tab casting which compresses to ~720p). Also preserve any resume/progress indicators on series pages so kids can pick up where they left off.

> **Note:** CR selector names will change over time. The content script should use a combination of class-based, ID-based, and structural selectors, and be designed to degrade gracefully (if a selector stops matching, it just doesn't hide that element — it doesn't break the page).

## Browser Hardening (Recommended)

**Important limitation:** CrunchyList only has `host_permissions` for `*.crunchyroll.com`. It cannot block kids from typing non-CR URLs into the address bar (e.g., YouTube, Google). For a truly locked-down experience, browser hardening via Chrome enterprise policies is strongly recommended:

- Force-install the CrunchyList extension (can't be removed)
- Disable Chrome developer tools
- Disable incognito mode
- Set CrunchyList landing page as the homepage and new tab page
- Use Chrome's built-in site allowlist to restrict browsing to only `crunchyroll.com` and the extension's pages

This is outside the extension itself but worth documenting for setup.

## Repository Structure

```
crunchylist/
├── README.md               # Project overview, manual install instructions, usage guide
├── LICENSE                  # MIT recommended for open source
├── .gitignore              # Minimal — mostly just .DS_Store, node_modules if any creep in
├── extension/              # This folder IS the extension — zip this for Chrome Web Store
│   ├── manifest.json
│   ├── landing.html
│   ├── landing.js
│   ├── landing.css
│   ├── content.js
│   ├── content.css
│   ├── background.js
│   ├── options.html
│   ├── options.js
│   └── icons/
│       ├── icon-16.png
│       ├── icon-48.png
│       └── icon-128.png
└── docs/
    └── CRUNCHYLIST-REQUIREMENTS.md
```

No build step, no bundler, no dependencies. The `extension/` folder is the complete, publishable artifact. Anyone can clone the repo and load it as an unpacked extension immediately.

### Manual Install (Development / Sharing Without the Store)

1. Clone the repo or download the zip
2. Open `chrome://extensions/`
3. Enable "Developer mode" (top right)
4. Click "Load unpacked" and select the `extension/` folder
5. Log into Crunchyroll in that Chrome profile
6. Open the extension options to add whitelisted shows

## Chrome Web Store Publishing

### First-Time Setup
- Register as a Chrome Web Store developer: https://chrome.google.com/webstore/devconsole
- One-time $5 registration fee
- Requires a Google account

### Submission Checklist
- Zip the `extension/` folder (not the repo root)
- Prepare store listing assets:
  - Extension description (short + detailed)
  - At least one screenshot (1280x800 or 640x400)
  - Promo tile image (440x280)
  - Icon (128x128, already in the extension)
- Category: "Productivity" or "Lifestyle" (no dedicated "Parental Controls" category)
- Privacy practices disclosure: declare that the extension uses `chrome.storage.sync` for whitelist data only, collects no user data, and makes no external network requests beyond navigating to crunchyroll.com

### Review Expectations
- No remote code execution, no data collection, no third-party analytics — this should pass review quickly
- Typical review: 1–3 business days
- CR DOM changes may break hiding periodically; publish updates as needed

### Community / Growth Potential
- Strong niche audience: anime parents with the exact same CR parental controls frustration
- Future feature idea: shareable whitelist presets (e.g., "Ages 5–8 Starter Pack," "Shonen Safe List") that users can import — would significantly reduce setup friction for new users
- No licensing concerns — the extension filters a website's UI (like an ad blocker), it doesn't distribute or proxy CR content

## Out of Scope (for now)

- Multiple whitelists / per-kid profiles
- Episode-level filtering (block specific episodes within a series)
- Android TV native app
- Automated show metadata/image fetching from CR API
- Content rating display or filtering by rating
- Usage tracking or time limits

## Success Criteria

- Parent can add a show to the whitelist in under 30 seconds (paste URL, confirm)
- Kids see only whitelisted shows on the landing page
- Kids cannot navigate to non-whitelisted content on Crunchyroll
- Video playback works normally for whitelisted episodes
- CR page loads don't feel noticeably slower due to content hiding
- Extension is simple enough to maintain when CR updates their UI (selector changes)

## Open Questions

1. **Episode → Series resolution:** Does CR embed the series ID in watch page metadata? If so, option 1 (page content inspection) becomes viable and more robust than lazy whitelisting.
2. **Image sourcing:** Can we reliably pull series artwork from CR, or should the parent paste an image URL manually? Auto-fetching would be nicer but adds complexity.
3. **CR login state:** If the kids' Chrome profile isn't logged into CR, the extension should show a message directing them to ask a parent to log in, rather than showing CR's login page (which has browse access).
