# Bundled watch list

Source: *What to Watch Next* (30 shows), supplied 2026-08-07. Every title on that list
that exists on Crunchyroll is bundled as
[`tv-app/app/src/main/assets/default_whitelist.json`](../tv-app/app/src/main/assets/default_whitelist.json)
and seeds the app on first run — entering ~30 series IDs with a TV remote is not a
reasonable ask.

Both sections of the source list were checked, not just the Crunchyroll one: the document
itself notes that titles shift between services, and several Netflix-section shows are on
Crunchyroll too.

Series IDs and titles come from Crunchyroll's CMS API, not from search results. Crunchyroll's
search is noisy — it returned *Akebi's Sailor Uniform* for the query "sailor moon" — so every
match here was verified against the title rather than taken from the first hit.

## On Crunchyroll — 29 entries, 28 titles

| Title | Series ID | Listed under |
|---|---|---|
| Frieren: Beyond Journey's End | `GG5H5XQX4` | Netflix |
| Witch Hat Atelier | `GT00258001` | Netflix |
| Puella Magi Madoka Magica | `GRDQK39GY` | Netflix |
| Fullmetal Alchemist: Brotherhood | `GRGGPG93R` | Netflix |
| Daemons of the Shadow Realm | `GT00371630` | Netflix |
| Wistoria: Wand and Sword | `GW4HM7WK9` | Netflix |
| Hunter x Hunter | `GY3VKX1MR` | Netflix |
| MASHLE: MAGIC AND MUSCLES | `GDKHZEP8W` | Netflix |
| SPY x FAMILY | `G4PH0WXVJ` | Netflix |
| WITCH WATCH | `G5PHNM9P0` | Netflix |
| Shangri-La Frontier | `G79H23Z8P` | Netflix |
| Haikyu!! | `GY8VM8MWY` | Netflix |
| Campfire Cooking in Another World with My Absurd Skill | `GG5H5X3EE` | Crunchyroll |
| Ranking of Kings | `G79H23W70` | Crunchyroll |
| Trigun | `GYP58QMMY` | Crunchyroll |
| TRIGUN STAMPEDE | `GXJHM3PK5` | Crunchyroll |
| Ascendance of a Bookworm | `G6793XKZY` | Crunchyroll |
| Yona of the Dawn | `G6VN35J7R` | Crunchyroll |
| Kaiju No. 8 | `GG5H5XQ7D` | Crunchyroll |
| Solo Leveling | `GDKHZEJ0K` | Crunchyroll |
| BOCCHI THE ROCK! | `GXJHM3P19` | Crunchyroll |
| Nichijou - My Ordinary Life | `GR24PVM76` | Crunchyroll |
| Log Horizon | `GRVNMG93Y` | Crunchyroll |
| BOFURI: I Don't Want to Get Hurt, so I'll Max Out My Defense. | `GKEH2G428` | Crunchyroll |
| Skip and Loafer | `G9VHN9185` | Crunchyroll |
| Natsume's Book of Friends | `GRE5XQJV6` | Crunchyroll |
| Laid-Back Camp | `GRWEW95KR` | Crunchyroll |
| Mob Psycho 100 | `GY190DKQR` | added later |
| Dr. STONE | `GYEXQKJG6` | added later |

**Trigun** is two entries because the source list explicitly offers both: the 1998 original and
the newer *Trigun Stampede*. Remove whichever isn't wanted.

**Mob Psycho 100** and **Dr. STONE** are not from the source document — both were added on
request afterwards. Mob Psycho was missing from the list only because they were already
watching it.

## Not on Crunchyroll — 4 titles

Searched under English titles, Japanese titles and alternate spellings; none returned a match.

| Title | Listed under | Note |
|---|---|---|
| Blue Box | Netflix | Also tried "Ao no Hako" |
| Little Witch Academia | Netflix | — |
| **One Punch Man** | **Crunchyroll** | Also tried "One-Punch Man" |
| **Silver Spoon** | **Crunchyroll** | Also tried "Gin no Saji" |

The last two are worth knowing about: the source list has them under Crunchyroll, but they are
not in the catalogue this account can see. Licensing most likely moved. They are simply absent
from the app rather than broken — CrunchyList only ever contains what a parent adds.

## Changing the list

The bundled file is a **starting point for membership, and the source of truth for text**. The
split, implemented in `SeedMerge`:

- **Which shows are allowed is the parent's.** A bundled show is added only if this install has
  never seeded it before, so removing one keeps it gone however many updates land. That is the
  mistake the Chrome extension made — it re-seeded whenever the list hit zero (audit §3.9).
- **The write-ups, cast and labels are the bundle's.** Every show still on the list has its text
  refreshed from the APK on each launch, so shipping better copy actually reaches an installed
  app. Before this, improving a write-up did nothing: the new text sat in the APK while the
  device rendered what it had cached on first run.

Each entry carries a short `description` for the side panel and a longer `about` for the More
Info screen, plus a `role` line and a `bio` paragraph for each cast member. All of that prose is
hand-written. Scraped synopses were tried first and were unusable — wiki markup leaked through,
the voice changed every entry, and several gave away endings.

To add a show, `powershell -File tools/fetch-show.ps1 <SERIES_ID> "<AniList search>"` pulls the
poster art, episode counts, maturity rating, content labels and main-cast portraits. **Check the
AniList title it prints.** Three of the original 27 matched the wrong entry, one of them a
completely different show.
