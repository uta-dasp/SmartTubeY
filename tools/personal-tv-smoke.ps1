#requires -Version 7.0
<#
.SYNOPSIS
Runs a bounded, read-only navigation smoke journey against a connected physical TV.
.EXAMPLE
./tools/personal-tv-smoke.ps1 -AdbPath C:/Android/platform-tools/adb.exe -Serial DEVICE_SERIAL -OutputFolder ./work/tv-smoke
.NOTES
Requires the personal flavor, English UI, a populated Home page, and the public NASA search fixture.
Preserves accounts/settings. Account/password prompts require manual input and stop the run.
Raw hierarchy XML and screenshots are PRIVATE and may contain account names or viewing history.
No sign-in, rating, subscription, settings mutation, playback, logcat, or network change is performed.
Only -ColdStart permits force-stop, and only for org.smarttubey.personal.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$AdbPath,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Serial,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$OutputFolder,
    [switch]$ColdStart,
    [switch]$CheckSubscriptions,
    [ValidateRange(1, 20)][int]$StepTimeoutSeconds = 20,
    [ValidateRange(1, 300)][int]$PollBudgetSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Package = 'org.smarttubey.personal'
$script:AdbExecutable = (Resolve-Path -LiteralPath $AdbPath).Path
if (-not (Test-Path -LiteralPath $script:AdbExecutable -PathType Leaf)) { throw 'AdbPath must name an executable file.' }
$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$script:RunFolder = Join-Path ([IO.Path]::GetFullPath($OutputFolder)) $runId
$script:PrivateFolder = Join-Path $script:RunFolder 'private'
[void][IO.Directory]::CreateDirectory($script:PrivateFolder)
$script:RemoteXml = '/sdcard/sty-smoke-' + $runId + '.xml'
$script:RemotePng = '/sdcard/sty-smoke-' + $runId + '.png'
$script:Checks = [Collections.Generic.List[object]]::new()
$script:CaptureIndex = 0
$script:PollUsedMs = 0L
$script:SnapshotEstimateMs = 4000L
$script:LastState = $null
$script:RunWatch = [Diagnostics.Stopwatch]::StartNew()
$script:CurrentPollWatch = $null
$script:CurrentStepWatch = $null
$script:CurrentStep = 'setup'
$resultStatus = 'failed'
$resultReason = 'Setup did not finish.'
$exitCode = 1
$subscriptionObservation = 'not_requested'

function Get-AdbTimeout {
    $remaining = 20000L
    if ($null -ne $script:CurrentPollWatch) {
        $remaining = [Math]::Min($remaining, $PollBudgetSeconds * 1000L - $script:PollUsedMs - $script:CurrentPollWatch.ElapsedMilliseconds)
        $remaining = [Math]::Min($remaining, $StepTimeoutSeconds * 1000L - $script:CurrentStepWatch.ElapsedMilliseconds)
    }
    if ($remaining -le 0) { throw 'State polling time budget exhausted.' }
    return [int]$remaining
}

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $script:AdbExecutable
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in (@('-s', $Serial) + $Arguments)) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $operationTimeout = Get-AdbTimeout
        if (-not $process.WaitForExit($operationTimeout)) {
            $process.Kill()
            if ($null -ne $script:CurrentPollWatch -and $operationTimeout -lt 20000) {
                throw 'State polling window expired during hierarchy collection.'
            }
            throw 'An ADB operation exceeded its bounded timeout.'
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            [IO.File]::AppendAllText((Join-Path $script:PrivateFolder 'adb-errors.txt'), $stderr + "`n")
            throw 'ADB operation failed; private/adb-errors.txt contains its diagnostic.'
        }
        return $stdout
    } finally { $process.Dispose() }
}

function Send-Keys {
    param([string[]]$Keys)
    foreach ($key in $Keys) { [void](Invoke-Adb @('shell', 'input', 'keyevent', $key)) }
}

function Get-SafeFailure {
    param([string]$Message)
    if ($Message -match '^(NEEDS_INPUT:|Expected state|Required visible rail|State polling|An ADB|ADB operation|UI hierarchy|The resumed|Could not identify|The selected)') { return $Message }
    return 'Unexpected failure; see private/failure.txt.'
}

function Read-State {
    param([string]$StepId)
    $activityDump = Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')
    $resumed = [regex]::Match($activityDump, '(?:mResumedActivity|topResumedActivity):\s*ActivityRecord\{[^\r\n}]*\su\d+\s+([A-Za-z0-9_.]+)/([^\s}]+)')
    if (-not $resumed.Success) { throw 'Could not identify the resumed activity.' }
    $activePackage = $resumed.Groups[1].Value
    if ($activePackage -ne $script:Package) { throw 'The resumed package is not org.smarttubey.personal.' }
    # Compressed dumps omit non-important ancestors used as screen/container selectors.
    $dump = Invoke-Adb @('shell', 'uiautomator', 'dump', $script:RemoteXml)
    if ($dump -notmatch '(?i)dumped to:') { throw 'UI hierarchy collection failed; refusing a stale snapshot.' }
    $raw = Invoke-Adb @('shell', 'cat', $script:RemoteXml)
    $xml = [xml]::new()
    $xml.LoadXml($raw)
    $script:CaptureIndex++
    $file = '{0:D3}-{1}.xml' -f $script:CaptureIndex, $StepId
    [IO.File]::WriteAllText((Join-Path $script:PrivateFolder $file), $raw)
    $state = [pscustomobject]@{
        Xml = $xml; Package = $activePackage; Activity = $resumed.Groups[2].Value
        Focus = @($xml.SelectNodes('//node[@focused="true" and @focusable="true"]')); XmlFile = 'private/' + $file
    }
    $script:LastState = $state
    # Never enter credentials or silently accept an authentication/settings gate.
    $password = $xml.SelectNodes('//node[@password="true"]').Count -gt 0
    $auth = $xml.SelectNodes('//node[contains(@text,"yt.be/activate") or contains(@text,"youtube.com/activate") or contains(@text,"Enter settings password")]').Count -gt 0
    if ($password -or $auth) { throw 'NEEDS_INPUT: Account or settings authentication requires manual input.' }
    return $state
}

function Has-Text {
    param($State, [string]$Text)
    return @($State.Xml.SelectNodes('//node') | Where-Object { $_.GetAttribute('text') -eq $Text }).Count -gt 0
}

function Has-Resource {
    param($State, [string]$Name)
    return $State.Xml.SelectNodes('//node[contains(@resource-id, ":id/' + $Name + '")]').Count -gt 0
}

function Focus-Is {
    param($State, [string]$Text)
    return @($State.Focus | Where-Object {
        $_.GetAttribute('text') -eq $Text -or $_.GetAttribute('content-desc') -eq $Text
    }).Count -gt 0
}

function Focus-ContainsText {
    param($State, [string]$Text)
    foreach ($focused in $State.Focus) {
        if (@($focused.SelectNodes('descendant-or-self::node') | Where-Object { $_.GetAttribute('text') -eq $Text }).Count -gt 0) { return $true }
    }
    return $false
}

function Focus-IsVideoCard {
    param($State)
    foreach ($focused in $State.Focus) {
        if ($focused.SelectNodes('.//node[contains(@resource-id, ":id/title_text") and string-length(@text)>0]').Count -gt 0) { return $true }
    }
    return $false
}

function Focus-IsAvatar {
    param($State)
    foreach ($focused in $State.Focus) {
        $bounds = [regex]::Match($focused.GetAttribute('bounds'), '^\[(\d+),(\d+)\]')
        if ($bounds.Success -and [int]$bounds.Groups[1].Value -lt 120 -and [int]$bounds.Groups[2].Value -lt 160 -and
            $focused.SelectNodes('./node[@class="android.widget.ImageView"]').Count -gt 0) { return $true }
    }
    return $false
}

function Focus-IsNasaChannel {
    param($State)
    foreach ($focused in $State.Focus) {
        if ($focused.GetAttribute('class') -eq 'android.widget.LinearLayout' -and
            @($focused.SelectNodes('./node') | Where-Object { $_.GetAttribute('text') -eq 'NASA' }).Count -gt 0) { return $true }
    }
    return $false
}

function Focus-IsRail {
    param($State)
    foreach ($focused in $State.Focus) {
        $bounds = [regex]::Match($focused.GetAttribute('bounds'), '^\[(\d+),')
        if ($bounds.Success -and [int]$bounds.Groups[1].Value -lt 80 -and $focused.GetAttribute('content-desc').Length -gt 0) { return $true }
    }
    return $false
}

function Get-SubscriptionCards {
    param($State)
    $pane = $State.Xml.SelectSingleNode('//node[contains(@resource-id, ":id/personal_subscriptions_videos")]')
    if ($null -eq $pane) { return 0 }
    return $pane.SelectNodes('.//node[contains(@resource-id, ":id/main_image")]').Count
}

function Subscription-MetadataVisible {
    param($State)
    $pane = $State.Xml.SelectSingleNode('//node[contains(@resource-id, ":id/personal_subscriptions_videos")]')
    return $null -ne $pane -and $pane.SelectNodes('.//node[contains(@resource-id, ":id/title_text") and string-length(@text)>0]').Count -gt 0 -and (Focus-IsVideoCard $State)
}

function Save-Screen {
    param([string]$StepId)
    [void](Invoke-Adb @('shell', 'screencap', '-p', $script:RemotePng))
    [void](Invoke-Adb @('pull', $script:RemotePng, (Join-Path $script:PrivateFolder ($StepId + '.png'))))
}

function Wait-State {
    param([string]$Id, [string]$Expected, [scriptblock]$Predicate, [switch]$Screenshot)
    $script:CurrentStep = $Id
    $script:CurrentStepWatch = [Diagnostics.Stopwatch]::StartNew()
    $attempts = 0
    $passed = $false
    $failure = $null
    $lastSnapshotMs = 0L
    $termination = 'matched'
    $script:LastState = $null
    try {
        while ($true) {
            $remaining = [Math]::Min($StepTimeoutSeconds * 1000L - $script:CurrentStepWatch.ElapsedMilliseconds,
                    $PollBudgetSeconds * 1000L - $script:PollUsedMs)
            if ($remaining -lt $script:SnapshotEstimateMs) {
                $termination = 'expected_state_budget'
                break
            }
            $attempts++
            $script:CurrentPollWatch = [Diagnostics.Stopwatch]::StartNew()
            $collected = $false
            try {
                $state = Read-State $Id
                $collected = $true
                $script:LastState = $state
                $passed = [bool](& $Predicate $state)
            } finally {
                $lastSnapshotMs = $script:CurrentPollWatch.ElapsedMilliseconds
                $script:PollUsedMs += $lastSnapshotMs
                if ($collected) { $script:SnapshotEstimateMs = [Math]::Max(1000L, [long][Math]::Ceiling($lastSnapshotMs * 1.25 + 250)) }
                $script:CurrentPollWatch = $null
            }
            if ($passed) { break }
            # Each hierarchy request itself waits for UI idle. No fixed sleep or busy polling.
        }
        if (-not $passed) { throw ('Expected state was not reached: ' + $Expected + '. The last snapshot did not match, or too little polling budget remained for another hierarchy collection.') }
    } catch {
        if ($_.Exception.Message.StartsWith('State polling window expired')) {
            $termination = 'collection_budget'
            $failure = 'Expected state was not reached: ' + $Expected + '. The harness polling window expired during hierarchy collection.'
            throw [TimeoutException]::new($failure)
        }
        $failure = Get-SafeFailure $_.Exception.Message
        if ($termination -eq 'matched') { $termination = 'collection_error' }
        throw
    }
    finally {
        $script:Checks.Add([pscustomobject]@{
            id = $Id; expected = $Expected; passed = $passed; attempts = $attempts
            elapsedMs = $script:CurrentStepWatch.ElapsedMilliseconds
            lastSnapshotMs = $lastSnapshotMs; nextSnapshotEstimateMs = $script:SnapshotEstimateMs
            termination = $termination
            reason = $failure
            evidence = if ($null -ne $script:LastState) { $script:LastState.XmlFile } else { $null }
        })
        $script:CurrentStepWatch = $null
    }
    if ($Screenshot) { Save-Screen $Id }
}

function Step {
    param([string]$Id, [string[]]$Keys, [string]$Expected, [scriptblock]$Predicate, [switch]$Screenshot)
    $script:CurrentStep = $Id
    Send-Keys $Keys
    Wait-State -Id $Id -Expected $Expected -Predicate $Predicate -Screenshot:$Screenshot
}

function Focus-RailTarget {
    param([string]$Description)
    # Use current visible rail geometry, so enabled optional categories need no hardcoded key count.
    $state = $script:LastState
    $buttons = @($state.Xml.SelectNodes('//node[@focusable="true" and string-length(@content-desc)>0]') | Where-Object {
        $b = [regex]::Match($_.GetAttribute('bounds'), '^\[(\d+),(\d+)\]')
        $b.Success -and [int]$b.Groups[1].Value -lt 80 -and [int]$b.Groups[2].Value -ge 150
    } | Sort-Object { [int]([regex]::Match($_.GetAttribute('bounds'), '^\[\d+,(\d+)\]').Groups[1].Value) })
    $from = -1; $to = -1
    for ($i = 0; $i -lt $buttons.Count; $i++) {
        if ($buttons[$i].GetAttribute('focused') -eq 'true') { $from = $i }
        if ($buttons[$i].GetAttribute('content-desc') -eq $Description) { $to = $i }
    }
    if ($from -lt 0 -or $to -lt 0) { throw ('Required visible rail control is unavailable: ' + $Description) }
    $direction = if ($to -ge $from) { 'KEYCODE_DPAD_DOWN' } else { 'KEYCODE_DPAD_UP' }
    $keys = @(); for ($i = 0; $i -lt [Math]::Abs($to - $from); $i++) { $keys += $direction }
    # GetNewClosure creates a module that cannot resolve this script's local helper functions.
    # Capture only the target value and inspect the supplied focus nodes directly.
    Step -Id ('rail-' + $Description.ToLowerInvariant()) -Keys $keys -Expected ($Description + ' rail focus') -Predicate ({ param($s)
        return @($s.Focus | Where-Object {
            $_.GetAttribute('text') -eq $Description -or $_.GetAttribute('content-desc') -eq $Description
        }).Count -gt 0
    }.GetNewClosure())
}

try {
    if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'The selected device is not ready.' }
    if ($ColdStart) { [void](Invoke-Adb @('shell', 'am', 'force-stop', $script:Package)) }
    [void](Invoke-Adb @('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LEANBACK_LAUNCHER', '-p', $script:Package))
    Wait-State 'browse-launch' 'Browse content with a focused real card' { param($s) (Has-Resource $s 'personal_browse_content') -and (Focus-IsVideoCard $s) } -Screenshot
    Step 'browse-home-rail' @('KEYCODE_DPAD_LEFT') 'Home rail focus' { param($s) Focus-Is $s 'Home' }
    Step 'browse-search-rail' @('KEYCODE_DPAD_UP') 'Search rail focus' { param($s) Focus-Is $s 'Search' }
    Step 'search-microphone' @('KEYCODE_DPAD_CENTER') 'Search microphone focus and native keyboard' { param($s) (Has-Resource $s 'personal_search_keyboard') -and (Focus-Is $s 'Voice search') }
    Step 'search-key-a' @('KEYCODE_DPAD_RIGHT') 'Native keyboard A focus' { param($s) Focus-Is $s 'A' }
    [void](Invoke-Adb @('shell', 'input', 'text', 'nasa'))
    Wait-State 'search-hardware-text' 'nasa input while A retains focus' { param($s) (Has-Text $s 'nasa') -and (Focus-Is $s 'A') }
    Step 'search-submit-focus' @('KEYCODE_DPAD_DOWN','KEYCODE_DPAD_DOWN','KEYCODE_DPAD_DOWN','KEYCODE_DPAD_DOWN','KEYCODE_DPAD_RIGHT','KEYCODE_DPAD_RIGHT') 'Native SEARCH button focus' { param($s) Focus-Is $s 'SEARCH' }
    Step 'search-nasa-results' @('KEYCODE_DPAD_CENTER') 'Public NASA channel result focused' { param($s) (Has-Resource $s 'personal_search_results') -and (Focus-IsNasaChannel $s) } -Screenshot
    Step 'channel-nasa' @('KEYCODE_DPAD_CENTER') 'NASA page with Watch live focused' { param($s) (Has-Text $s 'NASA') -and (Focus-Is $s 'Watch live') } -Screenshot
    Step 'channel-live-shelf' @('KEYCODE_DPAD_DOWN') 'Focused real video card on the channel shelf' { param($s) (Has-Resource $s 'channel_fragment') -and (Focus-IsVideoCard $s) } -Screenshot
    Step 'channel-back-results' @('KEYCODE_BACK') 'Same public NASA channel result restored' { param($s) (Has-Resource $s 'personal_search_results') -and (Focus-IsNasaChannel $s) }
    Step 'search-back-editing' @('KEYCODE_BACK') 'Native search editing with microphone focused' { param($s) (Has-Resource $s 'personal_search_keyboard') -and (Focus-Is $s 'Voice search') }
    Step 'search-back-browse' @('KEYCODE_BACK') 'Browse Search rail restored' { param($s) (Has-Resource $s 'personal_browse_content') -and (Focus-Is $s 'Search') }
    Step 'browse-account-avatar' @('KEYCODE_DPAD_UP') 'Browse account avatar focus' { param($s) Focus-IsAvatar $s }
    Step 'account-chooser' @('KEYCODE_DPAD_CENTER') 'Real account chooser with Add account and guest controls' { param($s)
        (Has-Text $s 'Add account') -and (Has-Text $s 'Watch as guest') -and $s.Focus.Count -gt 0
    } -Screenshot
    Step 'account-back' @('KEYCODE_BACK') 'Browse avatar restored without selecting an account' { param($s) (Has-Resource $s 'personal_browse_content') -and (Focus-IsAvatar $s) }
    Step 'account-to-search-rail' @('KEYCODE_DPAD_DOWN') 'Search rail focus' { param($s) Focus-Is $s 'Search' }
    Focus-RailTarget 'Advanced'
    Step 'advanced-open' @('KEYCODE_DPAD_CENTER') 'Advanced disclosure expanded with an extra rail item focused' { param($s)
        (Has-Text $s 'Advanced ▾') -and (Focus-IsRail $s) -and -not (Focus-Is $s 'Advanced')
    }
    Step 'advanced-back-collapse' @('KEYCODE_BACK') 'Advanced disclosure collapsed and focused' { param($s) (Has-Text $s 'Advanced ▸') -and (Focus-Is $s 'Advanced') }
    Step 'settings-rail' @('KEYCODE_DPAD_DOWN') 'Settings reachable from the last visible rail control' { param($s) Focus-Is $s 'Settings' }
    Step 'settings-category' @('KEYCODE_DPAD_CENTER') 'Accounts settings category focused' { param($s) (Has-Text $s 'Open options') -and (Focus-Is $s 'Accounts') } -Screenshot
    Step 'settings-open-options-focus' @('KEYCODE_DPAD_RIGHT') 'Open options focused' { param($s) Focus-Is $s 'Open options' }
    Step 'settings-account-options' @('KEYCODE_DPAD_CENTER') 'Original read-only account manager displayed' { param($s)
        (Has-Resource $s 'app_settings_fragment') -and (Has-Text $s 'Sign in') -and $s.Focus.Count -gt 0
    } -Screenshot
    Step 'settings-account-options-back' @('KEYCODE_BACK') 'Settings options button restored' { param($s) (Has-Text $s 'Open options') -and (Focus-Is $s 'Open options') }
    Step 'settings-category-left' @('KEYCODE_DPAD_LEFT') 'Accounts category restored' { param($s) Focus-Is $s 'Accounts' }
    Step 'settings-back-rail' @('KEYCODE_BACK') 'Settings rail restored' { param($s) Focus-Is $s 'Settings' } -Screenshot
    if ($CheckSubscriptions) {
        Focus-RailTarget 'Subscriptions'
        Step 'subscriptions-all' @('KEYCODE_DPAD_CENTER') 'Subscriptions All control focused' { param($s)
            (Has-Resource $s 'personal_subscriptions_videos') -and (Focus-Is $s 'All')
        }
        Step 'subscriptions-metadata-or-empty' @('KEYCODE_DPAD_RIGHT') 'Visible real video metadata, or focused empty-feed status' { param($s)
            if (-not (Has-Resource $s 'personal_subscriptions_videos')) { return $false }
            if ((Get-SubscriptionCards $s) -gt 0) { return Subscription-MetadataVisible $s }
            return @($s.Focus | Where-Object {
                $_.GetAttribute('class') -eq 'android.widget.TextView' -and $_.GetAttribute('clickable') -eq 'true' -and
                ($_.GetAttribute('text') -eq 'No videos to display. Select to retry.' -or $_.GetAttribute('text') -match '(?is)\bSIGN IN\s*$')
            }).Count -gt 0
        } -Screenshot
        $subscriptionObservation = if ((Get-SubscriptionCards $script:LastState) -gt 0) { 'cards_verified' } else { 'empty_skipped' }
        Step 'subscriptions-back-rail' @('KEYCODE_BACK') 'Subscriptions rail restored' { param($s) Focus-Is $s 'Subscriptions' }
    }
    $resultStatus = 'passed'; $resultReason = 'All navigation assertions passed.'; $exitCode = 0
} catch {
    $resultReason = $_.Exception.Message
    if ($resultReason.StartsWith('NEEDS_INPUT:')) { $resultStatus = 'needs_input'; $exitCode = 2 }
    # Keep unexpected provider/process details private; summary contains no device identity or UI text.
    [IO.File]::WriteAllText((Join-Path $script:PrivateFolder 'failure.txt'), $_.ToString())
    $resultReason = Get-SafeFailure $resultReason
    try { Save-Screen ('failed-' + $script:CurrentStep) } catch { }
} finally {
    $script:CurrentPollWatch = $null; $script:CurrentStepWatch = $null
    try { [void](Invoke-Adb @('shell', 'rm', '-f', $script:RemoteXml, $script:RemotePng)) } catch { }
    $summary = [ordered]@{
        schemaVersion = 1; package = $script:Package; status = $resultStatus; reason = $resultReason
        finishedUtc = [DateTime]::UtcNow.ToString('o'); coldStart = [bool]$ColdStart
        elapsedMs = $script:RunWatch.ElapsedMilliseconds; pollingMs = $script:PollUsedMs
        pollingBudgetMs = $PollBudgetSeconds * 1000; maximumSingleWaitMs = 20000
        subscriptions = $subscriptionObservation
        privateEvidence = 'private/'; checks = $script:Checks.ToArray()
    }
    $summaryPath = Join-Path $script:RunFolder 'summary.json'
    [IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 6))
    Write-Output $summaryPath
}
exit $exitCode
