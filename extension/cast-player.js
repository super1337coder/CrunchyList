// CrunchyList — Cast Player Script
// Runs inside CR's Vilos player iframe (static.crunchyroll.com).
// Listens for a message from the parent page to trigger Remote Playback.

(function () {
  'use strict';

  window.addEventListener('message', async (e) => {
    if (e.data?.type !== 'CRUNCHYLIST_CAST') return;

    try {
      const video = document.querySelector('video');
      if (!video) {
        window.parent.postMessage({ type: 'CRUNCHYLIST_CAST_RESULT', success: false, reason: 'no-video' }, '*');
        return;
      }

      // Remove disableRemotePlayback if CR set it
      video.disableRemotePlayback = false;

      if (!video.remote) {
        window.parent.postMessage({ type: 'CRUNCHYLIST_CAST_RESULT', success: false, reason: 'no-remote-api' }, '*');
        return;
      }

      await video.remote.prompt();
      window.parent.postMessage({ type: 'CRUNCHYLIST_CAST_RESULT', success: true }, '*');
    } catch (err) {
      window.parent.postMessage({ type: 'CRUNCHYLIST_CAST_RESULT', success: false, reason: err.message || 'unknown' }, '*');
    }
  });
})();
