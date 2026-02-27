// CrunchyList — Content Script
// Runs at document_idle on crunchyroll.com pages.
// Handles: DOM cleanup, MutationObserver for late-loading elements,
// and watch page series validation (fallback for lazy whitelisting).

(async function () {
  'use strict';

  const SERIES_URL_RE = /\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?series\/([A-Z0-9]+)/i;
  const WATCH_URL_RE = /\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?watch\//i;

  // Get the landing page URL from the background script
  let landingUrl;
  try {
    const resp = await chrome.runtime.sendMessage({ type: 'GET_LANDING_URL' });
    landingUrl = resp?.url;
  } catch {
    // Fallback — shouldn't happen, but don't break the page
    landingUrl = null;
  }

  function redirectToLanding() {
    if (landingUrl) {
      window.location.href = landingUrl;
    }
  }

  // --- Watch page validation (fallback for lazy whitelisting) ---
  if (WATCH_URL_RE.test(window.location.pathname)) {
    const seriesId = extractSeriesIdFromPage();
    if (seriesId) {
      try {
        const resp = await chrome.runtime.sendMessage({
          type: 'CHECK_SERIES_ID',
          seriesId
        });
        if (resp && !resp.allowed) {
          redirectToLanding();
          return;
        }
      } catch {
        // If messaging fails, don't break the page
      }
    }
    // If we couldn't extract a series ID, we still allow the page
    // (the background script's lazy whitelist should have caught it)
  }

  /**
   * Try to extract the series ID from the current watch page.
   * Checks multiple sources: meta tags, JSON-LD, __NEXT_DATA__, etc.
   */
  function extractSeriesIdFromPage() {
    // 1. Check <meta> tags
    const metaTags = document.querySelectorAll('meta');
    for (const meta of metaTags) {
      const content = meta.getAttribute('content') || '';
      const match = content.match(/\/series\/([A-Z0-9]+)/i);
      if (match) return match[1];
    }

    // 2. Check canonical/og:url link
    const ogUrl = document.querySelector('meta[property="og:url"]');
    if (ogUrl) {
      const match = ogUrl.getAttribute('content')?.match(/\/series\/([A-Z0-9]+)/i);
      if (match) return match[1];
    }

    // 3. Check JSON-LD structured data
    const ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (const script of ldScripts) {
      try {
        const data = JSON.parse(script.textContent);
        const url = data.url || data.partOfSeries?.url || '';
        const match = url.match(/\/series\/([A-Z0-9]+)/i);
        if (match) return match[1];
      } catch {
        // Invalid JSON — skip
      }
    }

    // 4. Check __NEXT_DATA__ (CR uses Next.js)
    const nextDataEl = document.getElementById('__NEXT_DATA__');
    if (nextDataEl) {
      try {
        const text = nextDataEl.textContent;
        // Look for series ID patterns in the JSON blob
        const match = text.match(/"seriesId"\s*:\s*"([A-Z0-9]+)"/i);
        if (match) return match[1];
      } catch {
        // Skip
      }
    }

    // 5. Check page links — any link pointing to a /series/ page
    const links = document.querySelectorAll('a[href*="/series/"]');
    for (const link of links) {
      const match = link.href.match(/\/series\/([A-Z0-9]+)/i);
      if (match) return match[1];
    }

    return null;
  }

  // --- DOM cleanup: remove elements that CSS might have missed ---
  // CSS handles the first pass, but CR's React app loads elements dynamically.

  // Selectors for elements to remove. These are broader than the CSS rules
  // because content.js has the shouldPreserve() safety check to avoid
  // accidentally hiding player/episode elements.
  const HIDE_SELECTORS = [
    // Navigation & header
    'header',
    '[data-t="header"]',
    // Footer
    'footer',
    '[data-t="footer"]',
    // Recommendations / "More Like This"
    '[data-t="recommendations"]',
    '[data-t="more-like-this"]',
    '[class*="recommendation"]',
    '[class*="Recommendation"]',
    '[class*="more-like"]',
    '[class*="MoreLike"]',
    '[class*="similar-"]',
    '[class*="Similar"]',
    '[class*="up-next"]',
    '[class*="UpNext"]',
    // Comments
    '[data-t="comments"]',
    '[class*="comment"]',
    '[class*="Comment"]',
    // Search
    '[data-t="search-input"]',
    '[class*="search-"]',
    // Social / share
    '[class*="share"]',
    '[class*="Share"]',
    '[class*="social"]',
    '[class*="Social"]',
    // Account / profile
    '[class*="profile"]',
    '[class*="Profile"]',
    '[class*="avatar"]',
    '[class*="Avatar"]',
    '[class*="user-menu"]',
    '[class*="UserMenu"]',
    // Promotional / banner / hero (but NOT the player hero)
    '[class*="banner"]',
    '[class*="Banner"]',
    '[class*="promo"]',
    '[class*="Promo"]',
    '[class*="marketing"]',
    '[class*="Marketing"]',
    // News / editorial
    '[class*="news"]',
    '[class*="News"]',
    '[class*="editorial"]',
    '[class*="Editorial"]',
  ];

  // Selectors that must NEVER be hidden (whitelist for preservation)
  const PRESERVE_SELECTORS = [
    'video',
    '[class*="vilos"]',
    '[class*="player"]',
    '[class*="Player"]',
    '[class*="cast"]',
    '[class*="chromecast"]',
    '[class*="episode"]',
    '[class*="Episode"]',
    '[class*="season"]',
    '[class*="Season"]',
    '[class*="progress"]',
    '[class*="playhead"]',
  ];

  function shouldPreserve(el) {
    for (const sel of PRESERVE_SELECTORS) {
      if (el.matches(sel) || el.querySelector(sel)) {
        return true;
      }
    }
    return false;
  }

  // Section headings whose parent containers should be hidden entirely.
  // This catches "More Like This" even if CR doesn't use a matchable class name.
  const HIDE_SECTION_HEADINGS = [
    'more like this',
    'you may also like',
    'recommended',
    'because you watched',
    'popular',
    'trending',
    'top picks',
  ];

  function hideElements() {
    // 1. Selector-based hiding
    for (const sel of HIDE_SELECTORS) {
      const els = document.querySelectorAll(sel);
      for (const el of els) {
        if (!shouldPreserve(el)) {
          el.style.display = 'none';
        }
      }
    }

    // 2. Text-content-based hiding: find headings that indicate recommendation sections
    //    and hide their parent container
    const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6, [class*="heading"], [class*="Heading"], [class*="title"], [class*="Title"]');
    for (const heading of headings) {
      const text = (heading.textContent || '').trim().toLowerCase();
      if (HIDE_SECTION_HEADINGS.some(phrase => text.includes(phrase))) {
        // Walk up to find a meaningful container (section, div with multiple children)
        let container = heading.parentElement;
        // Go up at most 3 levels to find a section-level container
        for (let i = 0; i < 3 && container; i++) {
          if (container.tagName === 'SECTION' || container.children.length > 1) {
            break;
          }
          container = container.parentElement;
        }
        if (container && !shouldPreserve(container)) {
          container.style.display = 'none';
        }
      }
    }
  }

  // Initial cleanup
  hideElements();

  // MutationObserver to catch dynamically loaded content
  const observer = new MutationObserver((mutations) => {
    let needsCleanup = false;
    for (const mutation of mutations) {
      if (mutation.addedNodes.length > 0) {
        needsCleanup = true;
        break;
      }
    }
    if (needsCleanup) {
      hideElements();
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
})();
