#Requires -Version 5.1
#Requires -RunAsAdministrator
#Requires -Modules ActiveDirectory
[CmdletBinding(SupportsShouldProcess)]
param()
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$domain = Assert-LabDomain -Config $config
$paths = Get-LabPaths -Config $config -DomainDN $domain.DistinguishedName
Get-ADOrganizationalUnit -Identity $paths.Root | Out-Null
if ($PSCmdlet.ShouldProcess($paths.Root, 'Create department OUs and a Helpdesk security group for the Java exercises')) {
    foreach ($unit in 'IT', 'HR', 'Finance', 'Helpdesk') {
        $found = Get-ADOrganizationalUnit -SearchBase $paths.Root -SearchScope OneLevel -Filter 'Name -eq $unit'
        if (-not $found) { New-ADOrganizationalUnit -Name $unit -Path $paths.Root -ProtectedFromAccidentalDeletion $true }
    }
    $group = Get-ADGroup -Filter 'SamAccountName -eq "GG_Helpdesk"'
    if (-not $group) {
        New-ADGroup -Name 'GG_Helpdesk' -GroupScope Global -GroupCategory Security -Path "OU=Helpdesk,$($paths.Root)"
    } elseif ($group.DistinguishedName -notlike "*,$($paths.Root)" -or $group.GroupScope -ne 'Global' -or $group.GroupCategory -ne 'Security') {
        throw 'GG_Helpdesk already exists outside the expected lab scope or has an unexpected type.'
    }
    Write-Host 'Scenario containers are ready. Existing users, passwords and memberships were preserved.'
}
