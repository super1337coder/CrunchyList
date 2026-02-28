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
    // Never hide our own injected elements
    if (el.id && el.id.startsWith('crunchylist-')) return true;
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

  // --- Cast button for watch pages ---
  // CR uses SPA navigation (React/Next.js), so we need to check the URL
  // both on initial load and whenever the DOM changes (URL may have changed).
  let lastCastCheckUrl = '';

  function manageCastButton() {
    const isWatchPage = WATCH_URL_RE.test(window.location.pathname);
    const currentUrl = window.location.pathname;
    const btn = document.getElementById('crunchylist-cast-btn');

    if (isWatchPage && !btn) {
      injectCastButton();
    } else if (!isWatchPage && btn) {
      btn.remove();
      // Also remove overlay if open
      const overlay = document.getElementById('crunchylist-cast-overlay');
      if (overlay) overlay.remove();
    }
    lastCastCheckUrl = currentUrl;
  }

  function injectCastButton() {
    // Don't double-inject
    if (document.getElementById('crunchylist-cast-btn')) return;

    // Floating cast button
    const btn = document.createElement('button');
    btn.id = 'crunchylist-cast-btn';
    btn.innerHTML = `
      <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" style="vertical-align: middle; margin-right: 6px;">
        <path d="M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/>
      </svg>
      Cast to TV
    `;
    btn.addEventListener('click', attemptCast);
    document.body.appendChild(btn);
  }

  // Run on initial load
  manageCastButton();

  /**
   * Try to cast using the Remote Playback API (video.remote.prompt()).
   * This opens Chrome's native cast device picker and can stream the actual
   * video at full quality. If it fails (DRM, no video, not supported, etc.),
   * fall back to showing manual instructions.
   */
  async function attemptCast() {
    try {
      // CR loads the video player asynchronously — wait up to 5 seconds for it.
      // Check main DOM, shadow DOMs, and iframes since CR may embed the player.
      function findVideo() {
        // 1. Direct query
        let v = document.querySelector('video');
        if (v) return v;
        // 2. Check shadow roots (CR's Vilos player may use shadow DOM)
        const allEls = document.querySelectorAll('*');
        for (const el of allEls) {
          if (el.shadowRoot) {
            v = el.shadowRoot.querySelector('video');
            if (v) return v;
          }
        }
        // 3. Check iframes (same-origin only)
        const iframes = document.querySelectorAll('iframe');
        for (const iframe of iframes) {
          try {
            v = iframe.contentDocument?.querySelector('video');
            if (v) return v;
          } catch { /* cross-origin — skip */ }
        }
        return null;
      }

      let video = findVideo();
      if (!video) {
        for (let i = 0; i < 10; i++) {
          await new Promise(r => setTimeout(r, 500));
          video = findVideo();
          if (video) break;
        }
      }
      if (!video) {
        // Diagnostic: log what player elements exist
        const playerEls = document.querySelectorAll('[class*="vilos"], [class*="player"], [class*="Player"], [id*="player"], [id*="vilos"], #player0');
        console.log('[CrunchyList] No video element found. Player-related elements:', playerEls.length);
        playerEls.forEach(el => console.log('[CrunchyList]  -', el.tagName, el.id || '', el.className?.substring?.(0, 80) || ''));
        // Check for any iframes
        const iframes = document.querySelectorAll('iframe');
        console.log('[CrunchyList] Iframes on page:', iframes.length);
        iframes.forEach(f => console.log('[CrunchyList]  - iframe src:', f.src?.substring(0, 120) || '(no src)'));
        showCastOverlay();
        return;
      }
      console.log('[CrunchyList] Found video element:', video.src || '(no src, likely MSE)');
      // Remove disableRemotePlayback if CR set it
      video.disableRemotePlayback = false;
      if (!video.remote) {
        console.log('[CrunchyList] Remote Playback API not available — showing manual instructions');
        showCastOverlay();
        return;
      }
      await video.remote.prompt();
      console.log('[CrunchyList] Remote Playback prompt opened successfully');
    } catch (err) {
      console.log('[CrunchyList] Remote Playback failed:', err.message, '— showing manual instructions');
      showCastOverlay();
    }
  }

  function showCastOverlay() {
    // Don't double-show
    if (document.getElementById('crunchylist-cast-overlay')) return;

    const overlay = document.createElement('div');
    overlay.id = 'crunchylist-cast-overlay';
    overlay.innerHTML = `
      <div id="crunchylist-cast-modal">
        <h3 style="margin: 0 0 12px; font-size: 16px; color: #fff;">Cast to your TV</h3>
        <ol style="margin: 0 0 16px; padding-left: 20px; font-size: 14px; color: #ccc; line-height: 1.8;">
          <li>Click the <strong style="color:#fff;">⋮ menu</strong> (three dots, top-right of Chrome)</li>
          <li>Select <strong style="color:#fff;">Cast, Save, and Share</strong></li>
          <li>Click <strong style="color:#fff;">Cast</strong></li>
          <li>Pick your TV from the list</li>
        </ol>
        <p style="margin: 0 0 16px; font-size: 13px; color: #888;">
          Tip: Start casting first, then press <strong style="color:#aaa;">F</strong> to go fullscreen for the best picture.
        </p>
        <button id="crunchylist-cast-dismiss" style="
          background: #f47521; color: #fff; border: none; border-radius: 8px;
          padding: 8px 24px; font-size: 14px; font-weight: 600; cursor: pointer;
        ">Got it</button>
      </div>
    `;
    overlay.addEventListener('click', (e) => {
      // Close on clicking backdrop or dismiss button
      if (e.target === overlay || e.target.id === 'crunchylist-cast-dismiss') {
        overlay.remove();
      }
    });
    document.body.appendChild(overlay);
  }

  // Initial cleanup
  hideElements();

  // MutationObserver to catch dynamically loaded content + SPA navigation
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
      // Check if URL changed (SPA navigation) and manage cast button
      if (window.location.pathname !== lastCastCheckUrl) {
        manageCastButton();
      }
    }
  });

  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
})();
