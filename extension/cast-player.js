// CrunchyList — Cast Player Script
// Runs inside CR's Vilos player iframe (static.crunchyroll.com).
// Injects a cast button directly inside the player so the click
// preserves the user gesture required by RemotePlayback.prompt().

(function () {
  'use strict';

  function injectCastButton() {
    if (document.getElementById('crunchylist-iframe-cast-btn')) return;

    const video = document.querySelector('video');
    if (!video) return;

    // Remove disableRemotePlayback if CR set it
    video.disableRemotePlayback = false;

    // Check if Remote Playback API is available
    if (!video.remote) return;

    const btn = document.createElement('button');
    btn.id = 'crunchylist-iframe-cast-btn';
    btn.innerHTML = `
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" style="vertical-align: middle; margin-right: 5px;">
        <path d="M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/>
      </svg>
      Cast
    `;
    Object.assign(btn.style, {
      position: 'fixed',
      bottom: '70px',
      right: '16px',
      zIndex: '999999',
      background: '#f47521',
      color: '#fff',
      border: 'none',
      borderRadius: '10px',
      padding: '8px 14px',
      fontSize: '13px',
      fontWeight: '600',
      fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
      cursor: 'pointer',
      boxShadow: '0 4px 16px rgba(0, 0, 0, 0.5)',
      display: 'flex',
      alignItems: 'center',
      opacity: '0.85',
      transition: 'opacity 0.2s, transform 0.2s',
    });

    btn.addEventListener('mouseenter', () => {
      btn.style.opacity = '1';
      btn.style.transform = 'scale(1.05)';
    });
    btn.addEventListener('mouseleave', () => {
      btn.style.opacity = '0.85';
      btn.style.transform = 'scale(1)';
    });

    btn.addEventListener('click', async () => {
      try {
        // Re-check video in case it changed
        const v = document.querySelector('video');
        if (!v || !v.remote) return;
        v.disableRemotePlayback = false;
        await v.remote.prompt();
      } catch (err) {
        console.log('[CrunchyList] Cast failed inside iframe:', err.message);
      }
    });

    document.body.appendChild(btn);
  }

  // Wait for video to appear, then inject
  function waitAndInject() {
    if (document.querySelector('video')) {
      injectCastButton();
    } else {
      // Poll for video element
      let attempts = 0;
      const interval = setInterval(() => {
        attempts++;
        if (document.querySelector('video')) {
          clearInterval(interval);
          injectCastButton();
        } else if (attempts > 30) {
          clearInterval(interval);
        }
      }, 500);
    }
  }

  if (document.readyState === 'complete') {
    waitAndInject();
  } else {
    window.addEventListener('load', waitAndInject);
  }
})();
