# Pull everything the bundled watch list needs for one show, ready to hand-edit
# into tv-app/app/src/main/assets/default_whitelist.json.
#
#   powershell -File tools/fetch-show.ps1 GY190DKQR "Mob Psycho 100"
#
# Poster art, episode counts, maturity rating and content labels come from
# Crunchyroll's CMS API. Main-cast names and portraits come from AniList.
#
# It deliberately prints rather than writes. Everything a kid reads in this app
# is hand-written: scraped synopses leaked wiki markup, changed voice between
# entries and gave away endings. This gets the facts and the pictures; the words
# are still someone's job.
#
# ALWAYS CHECK THE ANILIST TITLE IT PRINTS. Three of the original 27 shows
# matched the wrong entry -- "Daemons of the Shadow Realm" matched "Saga of Tanya
# the Evil", and two others matched spin-off specials rather than the series.
# AniList often files a show under its Japanese title, so pass one explicitly as
# the second argument when the Crunchyroll title misses.

param(
    [Parameter(Mandatory = $true)][string]$SeriesId,
    [Parameter(Mandatory = $true)][string]$Search
)

$ErrorActionPreference = "Stop"
$tmp = Join-Path $env:TEMP "crunchylist-fetch"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# A browser User-Agent, because this runs on a desktop. Note that the Android app
# needs the opposite -- Cloudflare 403s a browser UA coming from a phone. See the
# class note on CrunchyrollApi.
$UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

curl.exe -s -X POST "https://www.crunchyroll.com/auth/v1/token" -H "User-Agent: $UA" `
    -H "Content-Type: application/x-www-form-urlencoded" -H "Referer: https://www.crunchyroll.com/" `
    --data "grant_type=client_id&client_id=cr_web&client_secret=" --max-time 25 -o "$tmp\tok.json"
$tok = (Get-Content -Path "$tmp\tok.json" -Raw -Encoding UTF8 | ConvertFrom-Json).access_token
if (-not $tok) { Write-Output "Crunchyroll token request failed."; exit 1 }

curl.exe -s ("https://www.crunchyroll.com/content/v2/cms/series/$SeriesId" + "?locale=en-US") `
    -H "Authorization: Bearer $tok" -H "User-Agent: $UA" --max-time 25 -o "$tmp\series.json"
$cr = $null
try { $cr = (Get-Content -Path "$tmp\series.json" -Raw -Encoding UTF8 | ConvertFrom-Json).data[0] } catch {}
if (-not $cr) { Write-Output "No Crunchyroll series found for $SeriesId."; exit 1 }

$poster = ""
try { $poster = ($cr.images.poster_tall[0] | Select-Object -Last 1).source } catch {}

Write-Output ""
Write-Output "CRUNCHYROLL"
Write-Output ("  title       {0}" -f $cr.title)
Write-Output ("  seriesId    {0}" -f $SeriesId)
Write-Output ("  imageUrl    {0}" -f $poster)
Write-Output ("  facts       {0} episodes   {1} seasons   {2}" -f `
    $cr.episode_count, $cr.season_count, $cr.series_launch_year)
Write-Output ("  rating      {0}" -f ($cr.maturity_ratings -join '/'))
Write-Output ("  advisories  {0}" -f ($cr.content_descriptors -join ', '))
Write-Output ""
Write-Output "  Crunchyroll's episode and season counts include dub tracks, so they run high."
Write-Output "  Put the real figure in 'meta' as a sentence rather than editing 'facts'."

# sort:SEARCH_MATCH matters -- without it "Frieren: Beyond Journey's End" returns
# a spin-off special instead of the series.
$query = @'
query($s:String){Media(search:$s,type:ANIME,sort:SEARCH_MATCH){title{romaji english} episodes seasonYear characters(sort:ROLE,perPage:10){edges{role node{name{full} image{large}}}}}}
'@
$body = @{ query = $query; variables = @{ s = $Search } } | ConvertTo-Json -Depth 5 -Compress
[IO.File]::WriteAllText("$tmp\q.json", $body, [Text.UTF8Encoding]::new($false))
curl.exe -s -X POST "https://graphql.anilist.co" -H "Content-Type: application/json" `
    -H "Accept: application/json" --data "@$tmp\q.json" --max-time 30 -o "$tmp\anilist.json"

$m = $null
try { $m = (Get-Content -Path "$tmp\anilist.json" -Raw -Encoding UTF8 | ConvertFrom-Json).data.Media } catch {}
Write-Output ""
if (-not $m) {
    # ASCII only in this file. PowerShell 5.1 reads a BOM-less .ps1 as ANSI, so a
    # single em-dash in a string literal is a parse error, not a wrong character.
    Write-Output ("ANILIST     no match for '{0}' - try the Japanese title" -f $Search)
    exit 0
}

$matched = if ($m.title.english) { $m.title.english } else { $m.title.romaji }
Write-Output "ANILIST"
# Not "<--": PowerShell reserves '<' as a redirection operator and refuses to parse it.
Write-Output ("  matched     {0}   ***  IS THIS THE RIGHT SHOW?" -f $matched)
Write-Output ("  romaji      {0}" -f $m.title.romaji)
Write-Output ""
Write-Output "  Main cast (write the role line and bio yourself):"
foreach ($e in $m.characters.edges) {
    if ($e.role -ne "MAIN") { continue }
    Write-Output ("    {0}" -f ($e.node.name.full -replace '\s+', ' ').Trim())
    Write-Output ("      {0}" -f $e.node.image.large)
}
