// CrunchyList — Landing Page Script
// Reads the whitelist from storage and renders show tiles.

(async function () {
  'use strict';

  const grid = document.getElementById('grid');
  const emptyState = document.getElementById('empty-state');

  // Load whitelist metadata from sync storage, falling back to bundled whitelist.json
  const syncData = await chrome.storage.sync.get('whitelist');
  let whitelist = syncData.whitelist || [];

  if (whitelist.length === 0) {
    try {
      const resp = await fetch(chrome.runtime.getURL('whitelist.json'));
      const defaults = await resp.json();
      whitelist = defaults.whitelist || [];
      // Seed sync storage so background.js picks up these defaults too
      if (whitelist.length > 0) {
        await chrome.storage.sync.set({ whitelist });
      }
    } catch {
      // No bundled whitelist or fetch failed — that's fine
    }
  }

  // Load image URLs from local storage
  const localData = await chrome.storage.local.get('images');
  const images = localData.images || {};

  if (whitelist.length === 0) {
    emptyState.style.display = 'block';
    grid.style.display = 'none';
    return;
  }

  // Sort alphabetically by title
  whitelist.sort((a, b) => a.title.localeCompare(b.title));

  for (const show of whitelist) {
    const tile = document.createElement('a');
    tile.className = 'tile';
    tile.href = show.url;
    tile.title = show.title;

    const imageUrl = images[show.seriesId];

    if (imageUrl) {
      const img = document.createElement('img');
      img.className = 'tile-image';
      img.src = imageUrl;
      img.alt = show.title;
      img.loading = 'lazy';
      // Fallback if image fails to load
      img.onerror = function () {
        this.replaceWith(createPlaceholder(show.title));
      };
      tile.appendChild(img);
    } else {
      tile.appendChild(createPlaceholder(show.title));
    }

    const title = document.createElement('div');
    title.className = 'tile-title';
    title.textContent = show.title;
    tile.appendChild(title);

    grid.appendChild(tile);
  }

  /**
   * Create a placeholder element for shows without artwork.
   */
  function createPlaceholder(showTitle) {
    const placeholder = document.createElement('div');
    placeholder.className = 'tile-placeholder';
    // Use the first character of the title as the placeholder icon
    placeholder.textContent = showTitle.charAt(0).toUpperCase();
    return placeholder;
  }
})();
