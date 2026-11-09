#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Allowed', 'Denied')][string]$ExpectedAccess,
    [string]$OutputDirectory = '.\reports'
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name.Split('\')[-1] -ne 'sam.rivera') { throw 'Run as the disposable LAB\sam.rivera account, after a fresh Windows sign-in.' }
$principal = New-Object System.Security.Principal.WindowsPrincipal $identity
if ($principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Do not run elevated or as an administrator.' }
$share = "\\$($config.DomainController)\LabHelpdesk"
$probe = Join-Path $share ("probe-" + [guid]::NewGuid().ToString('N') + '.txt')
$actual = 'Unknown'
$detail = 'NotAttempted'
# Verify the SMB service is reachable so an offline server is never reported as denied access.
if (-not (Test-NetConnection -ComputerName $config.DomainController -Port 445 -InformationLevel Quiet)) { throw 'SMB is unreachable. A connectivity failure does not prove an access denial.' }
try {
    [System.IO.File]::WriteAllText($probe, 'Disposable permission verification')
    if ([System.IO.File]::ReadAllText($probe) -ne 'Disposable permission verification') { throw 'Probe read-back failed.' }
    [System.IO.File]::Delete($probe)
    $actual = 'Allowed'; $detail = 'CreateReadDeleteSucceeded'
} catch [System.UnauthorizedAccessException] {
    $actual = 'Denied'; $detail = 'UnauthorizedAccessException'
} catch {
    $actual = 'Unknown'; $detail = $_.Exception.GetType().Name
}
$result = [pscustomobject]@{
    TimeUtc = [datetime]::UtcNow.ToString('o'); User = $identity.Name; Share = $share
    Expected = $ExpectedAccess; Actual = $actual; Passed = ($actual -eq $ExpectedAccess); Detail = $detail
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$output = Join-Path $OutputDirectory ("share-$ExpectedAccess-" + [datetime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.json')
$result | ConvertTo-Json | Set-Content $output -Encoding UTF8
$result | Format-List
if (-not $result.Passed) { throw "Share verification did not match the expected result. Inspect $output. An Unknown result is never accepted as proof." }
