<#
    CrunchyList — Crunchyroll deep-link probe
    ------------------------------------------
    Answers the blocking question in docs/AUDIT-2026-08.md §5:
    does Crunchyroll's Android TV app accept deep links to a specific series?

    Works against either an emulator or the physical Google TV Streamer
    (adb connect <ip>:5555 first).

    Usage:
        .\tools\probe-deeplinks.ps1
        .\tools\probe-deeplinks.ps1 -SeriesId G4PH0WXVJ -Slug spy-x-family

    Output lands in tools/probe-out/ (screenshots + manifest dump).
#>

param(
    [string]$SeriesId  = "G4PH0WXVJ",
    [string]$Slug      = "spy-x-family",
    # Placeholder episode ID. Route resolution is what matters here — an invalid ID
    # still proves whether the verb maps to PlayerActivity. Pass a real one to test playback.
    [string]$EpisodeId = "GZ7UVPVX5",
    [string]$Serial    = ""         # e.g. emulator-5554, or <ip>:5555
)

$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$adbArgs = @()
if ($Serial -ne "") { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

$outDir = Join-Path $PSScriptRoot "probe-out"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Shot([string]$name) {
    # Binary-safe capture. PowerShell decodes native stdout to strings, so both
    # '>' redirection AND capturing `adb exec-out` into a variable corrupt the PNG.
    # Capture on-device, then pull the file.
    $path = Join-Path $outDir "$name.png"
    Adb shell screencap -p /sdcard/_probe.png | Out-Null
    Adb pull /sdcard/_probe.png $path 2>&1 | Out-Null
    Adb shell rm -f /sdcard/_probe.png | Out-Null
    if (Test-Path $path) {
        Write-Host "    screenshot -> $path"
    } else {
        Write-Host "    screenshot FAILED for $name" -ForegroundColor Red
    }
}

function CurrentActivity {
    # Android 14+ reports 'topResumedActivity'; older builds use 'mResumedActivity'.
    $d = Adb shell dumpsys activity activities 2>$null
    $line = $d | Select-String -Pattern "topResumedActivity|mResumedActivity" | Select-Object -First 1
    if ($line) { return $line.Line.Trim() }
    return "(could not determine)"
}

Write-Host "=== 1. locating Crunchyroll package ===" -ForegroundColor Cyan
$pkgLines = Adb shell "pm list packages | grep -i crunchy"
if (-not $pkgLines) {
    Write-Host "Crunchyroll is NOT installed on this device." -ForegroundColor Red
    Write-Host "Install it from the Play Store, then re-run."
    exit 1
}
$pkg = ($pkgLines | Select-Object -First 1) -replace '^package:', ''
$pkg = $pkg.Trim()
Write-Host "    package = $pkg" -ForegroundColor Green

Write-Host ""
Write-Host "=== 2. dumping declared intent filters ===" -ForegroundColor Cyan
$dump = Adb shell "dumpsys package $pkg"
$dumpPath = Join-Path $outDir "dumpsys-package.txt"
$dump | Out-File -FilePath $dumpPath -Encoding utf8
Write-Host "    full dump -> $dumpPath"

Write-Host ""
Write-Host "    --- schemes / hosts declared ---"
$dump | Select-String -Pattern "Scheme:|Authority:|android.intent.action.VIEW" |
    ForEach-Object { "      " + $_.Line.Trim() } | Sort-Object -Unique

Write-Host ""
Write-Host "    --- activities accepting VIEW ---"
$dump | Select-String -Pattern "^\s+[0-9a-f]+ $pkg/" |
    ForEach-Object { "      " + $_.Line.Trim() } | Sort-Object -Unique | Select-Object -First 25

Write-Host ""
Write-Host "=== 3. probing deep links ===" -ForegroundColor Cyan

# Confirmed 2026-08-07 on Google TV API 36 (see docs/AUDIT-2026-08.md §5.1):
#   crunchyroll://series/{ID}   -> ShowDetailsActivity   WORKS
#   crunchyroll://episode/{ID}  -> PlayerActivity        WORKS (direct playback)
#   anything with a host segment, or https -> home / unresolved
$probes = @(
    @{ name = "scheme-series";       uri = "crunchyroll://series/$SeriesId" },
    @{ name = "scheme-episode";      uri = "crunchyroll://episode/$EpisodeId" },
    @{ name = "scheme-with-host";    uri = "crunchyroll://www.crunchyroll.com/series/$SeriesId/$Slug" },
    @{ name = "scheme-watch-verb";   uri = "crunchyroll://watch/$EpisodeId" },
    @{ name = "https-series";        uri = "https://www.crunchyroll.com/series/$SeriesId/$Slug" },
    @{ name = "https-series-locale"; uri = "https://www.crunchyroll.com/en-us/series/$SeriesId/$Slug" }
)

$results = @()
foreach ($p in $probes) {
    Write-Host ""
    Write-Host "  [$($p.name)] $($p.uri)" -ForegroundColor Yellow

    # send Home first so each probe starts from a known state
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 2

    $r = Adb shell "am start -a android.intent.action.VIEW -d '$($p.uri)' $pkg" 2>&1
    $r | ForEach-Object { "      $_" }
    Start-Sleep -Seconds 6

    $act = CurrentActivity
    Write-Host "    resumed: $act"
    Shot $p.name

    $results += [pscustomobject]@{
        Probe    = $p.name
        Uri      = $p.uri
        Resumed  = $act
        Launched = ($r -join " ") -notmatch "Error|Exception|does not exist"
    }
}

Write-Host ""
Write-Host "=== 4. summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize -Wrap
$results | ConvertTo-Json -Depth 4 | Out-File (Join-Path $outDir "results.json") -Encoding utf8

Write-Host ""
Write-Host "VERDICT GUIDE:" -ForegroundColor Cyan
Write-Host "  - 'resumed' naming a CR series/detail activity  => deep links WORK, architecture unlocked"
Write-Host "  - 'resumed' naming CR's home/browse activity    => app opens but ignores the destination"
Write-Host "  - launch error / stays on launcher              => no deep-link support"
Write-Host ""
Write-Host "Compare the screenshots in $outDir to confirm what actually rendered."
