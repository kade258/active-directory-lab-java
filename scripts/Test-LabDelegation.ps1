#Requires -Version 5.1
#Requires -RunAsAdministrator
#Requires -Modules ActiveDirectory
[CmdletBinding(SupportsShouldProcess)]
param([string]$OutputDirectory = '.\reports')
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$domain = Assert-LabDomain -Config $config
$paths = Get-LabPaths -Config $config -DomainDN $domain.DistinguishedName
Get-ADOrganizationalUnit -Identity $paths.Root | Out-Null
$credential = Get-Credential -UserName "lab.operator@$($config.DomainName)" -Message 'Enter the delegated test operator credentials; not Domain Admin credentials'
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
$outsideName = "ADLab-OutsideProbe-$suffix"
$outside = "OU=$outsideName,$($domain.DistinguishedName)"
if (-not $PSCmdlet.ShouldProcess($domain.DNSRoot, 'Create disposable probe containers and test that the operator can write inside Lab but cannot write outside it')) { return }
# The administrator creates an isolated, disposable sibling container. No production object is touched.
New-ADOrganizationalUnit -Name $outsideName -Path $domain.DistinguishedName -ProtectedFromAccidentalDeletion $true
$insideResult = 'Unknown'; $outsideResult = 'Unknown'
try {
    New-ADOrganizationalUnit -Name "Inside-$suffix" -Path $paths.Root -Credential $credential -ErrorAction Stop
    $insideResult = 'Allowed'
} catch { $insideResult = 'Failed-' + $_.Exception.GetType().Name }
try {
    New-ADOrganizationalUnit -Name "Outside-$suffix" -Path $outside -Credential $credential -ErrorAction Stop
    $outsideResult = 'AllowedUnexpectedly'
} catch [Microsoft.ActiveDirectory.Management.ADUnauthorizedAccessException] {
    $outsideResult = 'Denied'
} catch [System.UnauthorizedAccessException] {
    $outsideResult = 'Denied'
} catch { $outsideResult = 'Unknown-' + $_.Exception.GetType().Name }
$result = [pscustomobject]@{
    TimeUtc = [datetime]::UtcNow.ToString('o'); Operator = $credential.UserName
    Inside = $insideResult; Outside = $outsideResult; OutsideProbeDN = $outside
    Passed = ($insideResult -eq 'Allowed' -and $outsideResult -eq 'Denied')
    Scope = 'OU creation only; validate user, password, group, move and disable delegation separately'
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$result | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory "delegation-$suffix.json") -Encoding UTF8
$result | Format-List
Write-Host 'Probe OUs were retained for administrator inspection and manual cleanup.'
if (-not $result.Passed) { throw 'Delegation verification failed or was inconclusive. Inspect rights and retained probe OUs.' }
