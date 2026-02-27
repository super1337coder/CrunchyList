// CrunchyList — Background Service Worker
// Handles navigation interception and whitelist enforcement

const LANDING_PAGE = chrome.runtime.getURL('landing.html');

// Regex to extract series ID from CR series URLs (with optional locale)
const SERIES_URL_RE = /crunchyroll\.com\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?series\/([A-Z0-9]+)/i;
// Regex to detect watch pages (with optional locale)
const WATCH_URL_RE = /crunchyroll\.com\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?watch\//i;
// Regex to detect any crunchyroll.com page
const CR_DOMAIN_RE = /^https?:\/\/([^/]*\.)?crunchyroll\.com/i;

// Track which series page was last visited per tab (for lazy whitelisting)
// Maps tabId -> seriesId
const tabSeriesContext = new Map();

/**
 * Get the current whitelist from storage.
 * Returns an array of whitelist entry objects.
 */
async function getWhitelist() {
  const data = await chrome.storage.sync.get('whitelist');
  return data.whitelist || [];
}

/**
 * Get the set of whitelisted series IDs.
 */
async function getWhitelistedIds() {
  const whitelist = await getWhitelist();
  return new Set(whitelist.map(entry => entry.seriesId.toUpperCase()));
}

/**
 * Check if a URL is an allowed CR page.
 * Returns true if the URL should be allowed, false if it should redirect.
 */
async function isAllowedUrl(url, tabId) {
  // Not a CR URL — we don't control these
  if (!CR_DOMAIN_RE.test(url)) return true;

  const ids = await getWhitelistedIds();

  // Empty whitelist — let everything through (extension not configured yet)
  if (ids.size === 0) return true;

  // Check if it's a whitelisted series page
  const seriesMatch = url.match(SERIES_URL_RE);
  if (seriesMatch) {
    const seriesId = seriesMatch[1].toUpperCase();
    if (ids.has(seriesId)) {
      // Track this as the active series for this tab (lazy whitelisting)
      tabSeriesContext.set(tabId, seriesId);
      return true;
    }
    return false;
  }

  // Check if it's a watch page
  if (WATCH_URL_RE.test(url)) {
    // Lazy whitelist: allow if this tab was last on a whitelisted series page
    if (tabSeriesContext.has(tabId)) {
      return true;
    }
    // Otherwise, the content script will do a secondary check via page metadata.
    // For now, allow navigation and let content.js handle validation.
    // We mark it as "pending" by not blocking — content.js will redirect if needed.
    return true;
  }

  // Any other CR page (homepage, browse, search, account, etc.) — redirect
  return false;
}

// Intercept navigation on crunchyroll.com
chrome.webNavigation.onBeforeNavigate.addListener(async (details) => {
  // Only intercept top-level navigation (not iframes, subframes, etc.)
  if (details.frameId !== 0) return;

  const allowed = await isAllowedUrl(details.url, details.tabId);
  if (!allowed) {
    chrome.tabs.update(details.tabId, { url: LANDING_PAGE });
  }
}, {
  url: [{ hostSuffix: 'crunchyroll.com' }]
});

// Also catch client-side navigation (SPA route changes)
chrome.webNavigation.onHistoryStateUpdated.addListener(async (details) => {
  if (details.frameId !== 0) return;

  const allowed = await isAllowedUrl(details.url, details.tabId);
  if (!allowed) {
    chrome.tabs.update(details.tabId, { url: LANDING_PAGE });
  }
}, {
  url: [{ hostSuffix: 'crunchyroll.com' }]
});

// Clean up tab context when tabs are closed
chrome.tabs.onRemoved.addListener((tabId) => {
  tabSeriesContext.delete(tabId);
});

// Listen for messages from content.js (e.g., watch page series validation)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'CHECK_SERIES_ID') {
    // Content script found a series ID on a watch page — validate it
    getWhitelistedIds().then(ids => {
      const seriesId = (message.seriesId || '').toUpperCase();
      const allowed = ids.has(seriesId);
      if (allowed && sender.tab) {
        tabSeriesContext.set(sender.tab.id, seriesId);
      }
      sendResponse({ allowed });
    });
    return true; // Keep message channel open for async response
  }

  if (message.type === 'GET_WHITELIST') {
    getWhitelist().then(whitelist => {
      sendResponse({ whitelist });
    });
    return true;
  }

  if (message.type === 'GET_LANDING_URL') {
    sendResponse({ url: LANDING_PAGE });
    return true;
  }

  if (message.type === 'FETCH_SERIES_IMAGE') {
    // Fetch a CR series page and extract the poster image URL
    fetchSeriesImage(message.url).then(result => {
      sendResponse(result);
    });
    return true;
  }
});

/**
 * Fetch series image and title from Crunchyroll.
 *
 * CR's pages are client-side rendered, so fetching HTML won't give us og:image.
 * Instead we use CR's public CMS API which returns JSON with poster images.
 *
 * Strategy:
 *   1. Get an anonymous auth token from CR's public API
 *   2. Use it to query the CMS series endpoint for the poster art
 */

// Cache the anonymous auth token (valid for ~5 minutes typically)
let cachedToken = null;
let tokenExpiry = 0;

async function getAnonToken() {
  if (cachedToken && Date.now() < tokenExpiry) return cachedToken;

  try {
    const resp = await fetch('https://www.crunchyroll.com/auth/v1/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'grant_type=client_id&client_id=cr_web&client_secret=',
      credentials: 'include'
    });
    if (!resp.ok) return null;
    const data = await resp.json();
    cachedToken = data.access_token;
    // Expire 30s early to be safe
    tokenExpiry = Date.now() + ((data.expires_in || 300) - 30) * 1000;
    return cachedToken;
  } catch {
    return null;
  }
}

async function fetchSeriesImage(seriesUrl) {
  // Extract series ID from the URL
  const match = seriesUrl.match(/series\/([A-Z0-9]+)/i);
  if (!match) return { error: 'Invalid series URL' };
  const seriesId = match[1];

  try {
    const token = await getAnonToken();
    if (!token) return { error: 'Could not get auth token' };

    // Query the CMS API for series info
    const apiUrl = `https://www.crunchyroll.com/content/v2/cms/series/${seriesId}?locale=en-US`;
    const resp = await fetch(apiUrl, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json'
      }
    });

    if (!resp.ok) return { error: `API returned ${resp.status}` };

    const data = await resp.json();
    const series = data?.data?.[0];
    if (!series) return { error: 'No series data found' };

    // Extract poster image — CR provides multiple sizes
    // Prefer the tall poster (poster_tall) for our tile grid
    let imageUrl = null;
    const posterTall = series.images?.poster_tall;
    if (posterTall && posterTall.length > 0) {
      // posterTall is an array of arrays of size variants — pick the largest
      const sizes = posterTall[0];
      if (sizes && sizes.length > 0) {
        // Last one is usually the largest
        imageUrl = sizes[sizes.length - 1]?.source;
      }
    }

    // Fallback to poster_wide if no tall poster
    if (!imageUrl) {
      const posterWide = series.images?.poster_wide;
      if (posterWide && posterWide.length > 0) {
        const sizes = posterWide[0];
        if (sizes && sizes.length > 0) {
          imageUrl = sizes[sizes.length - 1]?.source;
        }
      }
    }

    const title = series.title || null;

    return { imageUrl, title };
  } catch (err) {
    return { error: err.message };
  }
}
