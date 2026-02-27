// CrunchyList — Options Page Script
// PIN-protected whitelist management for parents.

(async function () {
  'use strict';

  const SERIES_URL_RE = /crunchyroll\.com\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?series\/([A-Z0-9]+)(?:\/([^/?#]+))?/i;

  // --- PIN Hashing ---
  async function hashPin(pin) {
    const encoder = new TextEncoder();
    const data = encoder.encode(pin);
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  // --- PIN Gate ---
  const pinGate = document.getElementById('pin-gate');
  const pinTitle = document.getElementById('pin-title');
  const pinDescription = document.getElementById('pin-description');
  const pinDigits = document.querySelectorAll('.pin-digit');
  const pinError = document.getElementById('pin-error');
  const pinSubmit = document.getElementById('pin-submit');
  const settingsDiv = document.getElementById('settings');

  // Check if PIN is set
  const localData = await chrome.storage.local.get('pinHash');
  const isFirstRun = !localData.pinHash;

  if (isFirstRun) {
    pinTitle.textContent = 'Create a PIN';
    pinDescription.textContent = 'Set a 4-digit PIN to protect these settings.';
    pinSubmit.textContent = 'Set PIN';
  }

  // PIN digit input behavior: auto-advance, backspace navigation
  pinDigits.forEach((input, index) => {
    input.addEventListener('input', (e) => {
      const val = e.target.value.replace(/[^0-9]/g, '');
      e.target.value = val;
      if (val && index < pinDigits.length - 1) {
        pinDigits[index + 1].focus();
      }
    });
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !e.target.value && index > 0) {
        pinDigits[index - 1].focus();
      }
      if (e.key === 'Enter') {
        pinSubmit.click();
      }
    });
  });

  pinSubmit.addEventListener('click', async () => {
    const pin = Array.from(pinDigits).map(d => d.value).join('');
    if (pin.length !== 4 || !/^\d{4}$/.test(pin)) {
      pinError.textContent = 'Please enter 4 digits.';
      return;
    }

    const hash = await hashPin(pin);

    if (isFirstRun) {
      // Save the PIN
      await chrome.storage.local.set({ pinHash: hash });
      showSettings();
    } else {
      // Verify the PIN
      if (hash === localData.pinHash) {
        showSettings();
      } else {
        pinError.textContent = 'Incorrect PIN. Try again.';
        pinDigits.forEach(d => { d.value = ''; });
        pinDigits[0].focus();
      }
    }
  });

  function showSettings() {
    pinGate.style.display = 'none';
    settingsDiv.style.display = 'block';
    loadWhitelist();
  }

  // --- Whitelist Management ---
  const showUrlInput = document.getElementById('show-url');
  const showTitleInput = document.getElementById('show-title');
  const showImageInput = document.getElementById('show-image');
  const addShowBtn = document.getElementById('add-show');
  const statusDiv = document.getElementById('status');
  const showListContainer = document.getElementById('show-list-container');

  // Auto-fetch title and image when a CR URL is pasted
  let fetchDebounce = null;
  showUrlInput.addEventListener('input', () => {
    const match = showUrlInput.value.match(SERIES_URL_RE);
    if (!match) return;

    // Immediately set slug-based title as placeholder
    if (match[2] && !showTitleInput.value) {
      const slug = match[2];
      const title = slug
        .split('-')
        .map(word => {
          if (word.length <= 2) return word;
          return word.charAt(0).toUpperCase() + word.slice(1);
        })
        .join(' ');
      showTitleInput.value = title;
    }

    // Debounce the fetch so we don't fire on every keystroke
    clearTimeout(fetchDebounce);
    fetchDebounce = setTimeout(async () => {
      const url = showUrlInput.value.trim();
      if (!SERIES_URL_RE.test(url)) return;

      showStatus('Fetching show info from Crunchyroll...', 'success');
      try {
        const result = await chrome.runtime.sendMessage({
          type: 'FETCH_SERIES_IMAGE',
          url
        });
        if (result?.imageUrl && !showImageInput.value) {
          showImageInput.value = result.imageUrl;
        }
        if (result?.title && !showTitleInput.dataset.userEdited) {
          showTitleInput.value = result.title;
        }
        showStatus('Show info loaded!', 'success');
      } catch {
        // Fetch failed — no big deal, slug title and manual image still work
      }
    }, 500);
  });

  // Track if user manually edited the title (don't overwrite with fetched title)
  showTitleInput.addEventListener('input', () => {
    showTitleInput.dataset.userEdited = 'true';
  });

  addShowBtn.addEventListener('click', async () => {
    const url = showUrlInput.value.trim();
    const match = url.match(SERIES_URL_RE);

    if (!match) {
      showStatus('Please paste a valid Crunchyroll series URL.', 'error');
      return;
    }

    const seriesId = match[1].toUpperCase();
    let title = showTitleInput.value.trim();
    const imageUrl = showImageInput.value.trim();

    // Auto-generate title from slug if not provided
    if (!title && match[2]) {
      title = match[2].split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    }
    if (!title) {
      title = seriesId; // Last resort
    }

    // Normalize the URL (strip locale, ensure canonical form)
    const canonicalUrl = `https://www.crunchyroll.com/series/${seriesId}/${match[2] || ''}`;

    // Load current whitelist
    const syncData = await chrome.storage.sync.get('whitelist');
    const whitelist = syncData.whitelist || [];

    // Check for duplicates
    if (whitelist.some(entry => entry.seriesId === seriesId)) {
      showStatus(`"${title}" is already in your list.`, 'error');
      return;
    }

    // Add to whitelist
    const entry = {
      seriesId,
      title,
      url: canonicalUrl,
      dateAdded: new Date().toISOString().split('T')[0]
    };
    whitelist.push(entry);

    // Save whitelist metadata to sync
    await chrome.storage.sync.set({ whitelist });

    // Save image URL to local storage
    let finalImageUrl = imageUrl;

    // If no image was provided and auto-fetch didn't fill it, try fetching now
    if (!finalImageUrl) {
      try {
        const result = await chrome.runtime.sendMessage({
          type: 'FETCH_SERIES_IMAGE',
          url: canonicalUrl
        });
        if (result?.imageUrl) {
          finalImageUrl = result.imageUrl;
        }
        // Also update the title if we got a better one from CR
        if (result?.title && title === entry.title) {
          entry.title = result.title;
          // Update the whitelist entry we just pushed
          whitelist[whitelist.length - 1].title = result.title;
          await chrome.storage.sync.set({ whitelist });
        }
      } catch {
        // Fetch failed — continue without image
      }
    }

    if (finalImageUrl) {
      const imgData = await chrome.storage.local.get('images');
      const images = imgData.images || {};
      images[seriesId] = finalImageUrl;
      await chrome.storage.local.set({ images });
    }

    // Clear form
    showUrlInput.value = '';
    showTitleInput.value = '';
    showImageInput.value = '';
    delete showTitleInput.dataset.userEdited;

    showStatus(`Added "${entry.title}" to the whitelist.`, 'success');
    loadWhitelist();
  });

  async function removeShow(seriesId) {
    // Remove from sync whitelist
    const syncData = await chrome.storage.sync.get('whitelist');
    let whitelist = syncData.whitelist || [];
    const removed = whitelist.find(e => e.seriesId === seriesId);
    whitelist = whitelist.filter(e => e.seriesId !== seriesId);
    await chrome.storage.sync.set({ whitelist });

    // Remove image from local storage
    const localData = await chrome.storage.local.get('images');
    const images = localData.images || {};
    delete images[seriesId];
    await chrome.storage.local.set({ images });

    showStatus(`Removed "${removed?.title || seriesId}" from the whitelist.`, 'success');
    loadWhitelist();
  }

  async function loadWhitelist() {
    const syncData = await chrome.storage.sync.get('whitelist');
    const whitelist = syncData.whitelist || [];
    const localData = await chrome.storage.local.get('images');
    const images = localData.images || {};

    showListContainer.innerHTML = '';

    if (whitelist.length === 0) {
      showListContainer.innerHTML = '<div class="empty-list">No shows in the whitelist yet. Add one above!</div>';
      return;
    }

    // Sort alphabetically
    whitelist.sort((a, b) => a.title.localeCompare(b.title));

    for (const show of whitelist) {
      const item = document.createElement('div');
      item.className = 'show-item';

      const imageUrl = images[show.seriesId];
      if (imageUrl) {
        const img = document.createElement('img');
        img.className = 'show-item-image';
        img.src = imageUrl;
        img.alt = show.title;
        img.onerror = function () {
          this.replaceWith(createSmallPlaceholder(show.title));
        };
        item.appendChild(img);
      } else {
        item.appendChild(createSmallPlaceholder(show.title));
      }

      const info = document.createElement('div');
      info.className = 'show-item-info';

      const titleEl = document.createElement('div');
      titleEl.className = 'show-item-title';
      titleEl.textContent = show.title;
      info.appendChild(titleEl);

      const idEl = document.createElement('div');
      idEl.className = 'show-item-id';
      idEl.textContent = show.seriesId;
      info.appendChild(idEl);

      item.appendChild(info);

      const removeBtn = document.createElement('button');
      removeBtn.className = 'btn btn-danger';
      removeBtn.textContent = 'Remove';
      removeBtn.addEventListener('click', () => removeShow(show.seriesId));
      item.appendChild(removeBtn);

      showListContainer.appendChild(item);
    }
  }

  function createSmallPlaceholder(title) {
    const placeholder = document.createElement('div');
    placeholder.className = 'show-item-placeholder';
    placeholder.textContent = title.charAt(0).toUpperCase();
    return placeholder;
  }

  function showStatus(message, type) {
    statusDiv.textContent = message;
    statusDiv.className = `status-message status-${type}`;
    statusDiv.style.display = 'block';
    setTimeout(() => {
      statusDiv.style.display = 'none';
    }, 4000);
  }

  // --- Change PIN ---
  const newPinInput = document.getElementById('new-pin');
  const changePinBtn = document.getElementById('change-pin');

  changePinBtn.addEventListener('click', async () => {
    const newPin = newPinInput.value.trim();
    if (!/^\d{4}$/.test(newPin)) {
      showStatus('PIN must be exactly 4 digits.', 'error');
      return;
    }
    const hash = await hashPin(newPin);
    await chrome.storage.local.set({ pinHash: hash });
    newPinInput.value = '';
    showStatus('PIN updated successfully.', 'success');
  });

  // --- Fetch All Images (backfill) ---
  const fetchAllBtn = document.getElementById('fetch-all-images');
  if (fetchAllBtn) {
    fetchAllBtn.addEventListener('click', async () => {
      const syncData = await chrome.storage.sync.get('whitelist');
      const whitelist = syncData.whitelist || [];
      const imgData = await chrome.storage.local.get('images');
      const images = imgData.images || {};

      // Find shows missing images
      const missing = whitelist.filter(show => !images[show.seriesId]);
      if (missing.length === 0) {
        showStatus('All shows already have images!', 'success');
        return;
      }

      fetchAllBtn.disabled = true;
      fetchAllBtn.textContent = `Fetching 0/${missing.length}...`;
      let fetched = 0;
      let updated = false;

      for (const show of missing) {
        try {
          const result = await chrome.runtime.sendMessage({
            type: 'FETCH_SERIES_IMAGE',
            url: show.url
          });
          if (result?.imageUrl) {
            images[show.seriesId] = result.imageUrl;
            updated = true;
          }
          // Also update title if we got a better one from CR
          if (result?.title) {
            const entry = whitelist.find(e => e.seriesId === show.seriesId);
            if (entry) {
              entry.title = result.title;
            }
          }
        } catch {
          // Skip this one
        }
        fetched++;
        fetchAllBtn.textContent = `Fetching ${fetched}/${missing.length}...`;
        // Small delay to avoid hammering CR
        await new Promise(r => setTimeout(r, 300));
      }

      if (updated) {
        await chrome.storage.local.set({ images });
        await chrome.storage.sync.set({ whitelist });
      }

      fetchAllBtn.disabled = false;
      fetchAllBtn.textContent = 'Fetch All Images';
      showStatus(`Fetched images for ${fetched} show(s).`, 'success');
      loadWhitelist();
    });
  }
})();
