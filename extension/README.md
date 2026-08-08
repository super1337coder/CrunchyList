# CrunchyList — Chrome extension (LEGACY)

> **Status: legacy. Do not rely on this to filter anything.**
>
> This is the original laptop-only version, kept because it may be useful later. The active
> product is [`tv-app/`](../tv-app), a Google TV app — see the [root README](../README.md).
>
> **It is a separate codebase.** It shares no code with the TV app, and none of the TV app's
> fixes were applied here. It runs, and it looks like it is working, but it does not reliably
> enforce the whitelist.

## Why it is not trusted

The [2026-08 audit](../docs/AUDIT-2026-08.md#3-code-audit) found five fail-open paths. Every
one of them means "looks fine, protects nothing" — the failure mode that matters for a
parental control:

| # | Problem | Where |
|---|---|---|
| 1 | An empty whitelist allows **all** of Crunchyroll. On a fresh profile, before `storage.sync` populates, everything is open. | [`background.js:45`](background.js#L45) |
| 2 | `/watch/` URLs are **never** blocked by the background script — both branches return `true`. | [`background.js:60`](background.js#L60) |
| 3 | The content script also fails open: "if we couldn't extract a series ID, we still allow the page". Combined with #2, nothing blocks a watch URL. | [`content.js:44`](content.js#L44) |
| 4 | Series-ID extraction can approve the **wrong show** — its last fallback grabs the first `/series/` link on the page, which on a watch page is usually a recommendation for a different series. | [`content.js:95`](content.js#L95) |
| 5 | Lazy-whitelist state is an in-memory `Map` in an MV3 service worker, so it is wiped whenever Chrome sleeps the worker (~30s idle). | [`background.js:15`](background.js#L15) |

Also worth knowing: the PIN is decorative. Any kid can disable the extension from
`chrome://extensions`. That is inherent to extensions, not a bug — it is why the TV app uses a
foreground service instead.

## If you pick this back up

Roughly in order of value. The TV app already solves each of these, so there is a worked
reference for every one:

1. **Fail closed.** No whitelist should mean "allow nothing but the landing page", never
   "allow everything". Compare `ScreenClassifier` in the TV app, which treats *unrecognised*
   as *deny*.
2. **Block `/watch/` properly.** Resolve episode → series before allowing, and deny while
   unresolved rather than after.
3. **Order the ID fallbacks by reliability** — explicit `og:url` and `__NEXT_DATA__` first,
   and drop the "first link on the page" heuristic entirely. It is worse than no answer,
   because a wrong answer is confidently wrong.
4. **Move tab state to `chrome.storage.session`** so it survives worker restarts.
5. **Use `declarativeNetRequest`** instead of redirecting after `onBeforeNavigate`. The
   current approach doesn't cancel the navigation, so the page starts loading and can flash
   content before the redirect lands. The original spec called for this; it was never wired up
   and the permission isn't even in the manifest.

## What it does when it works

Kids open Chrome to a tile grid of approved shows. Clicking one loads that series on
Crunchyroll with the nav, search, recommendations and comments hidden. Parents manage the list
from a PIN-protected options page.

Setup instructions are in the [root README](../README.md#chrome-extension).
