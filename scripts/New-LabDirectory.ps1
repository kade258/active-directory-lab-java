#Requires -Version 5.1
#Requires -RunAsAdministrator
#Requires -Modules ActiveDirectory
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$domain = Assert-LabDomain -Config $config
$paths = Get-LabPaths -Config $config -DomainDN $domain.DistinguishedName
$rows = @(Import-Csv (Join-Path $PSScriptRoot '..\data\users.csv'))
if ($rows.Count -eq 0) { throw 'The sample user list is empty.' }
foreach ($row in $rows) {
    if ($row.SamAccountName -notmatch '^[a-z][a-z0-9.]{0,19}$' -or $row.Group -notmatch '^GG_[A-Za-z]+$') {
        throw 'Invalid sample user or group name in CSV.'
    }
}
if (@($rows.SamAccountName | Select-Object -Unique).Count -ne $rows.Count) { throw 'Duplicate sample usernames.' }
foreach ($entry in @(
    @{ Name = $config.RootOU; Parent = $domain.DistinguishedName; DN = $paths.Root },
    @{ Name = $config.UsersOU; Parent = $paths.Root; DN = $paths.Users },
    @{ Name = $config.GroupsOU; Parent = $paths.Root; DN = $paths.Groups },
    @{ Name = $config.WorkstationsOU; Parent = $paths.Root; DN = $paths.Workstations }
)) {
    $ouName = $entry.Name
    $ou = Get-ADOrganizationalUnit -Filter 'Name -eq $ouName' -SearchBase $entry.Parent -SearchScope OneLevel
    # SearchBase may not exist only for an interrupted run; the root is created first.
    if (-not $ou) {
        New-ADOrganizationalUnit -Name $entry.Name -Path $entry.Parent -ProtectedFromAccidentalDeletion $true
    }
}
foreach ($groupName in @($rows.Group | Select-Object -Unique)) {
    $group = Get-ADGroup -Filter 'SamAccountName -eq $groupName'
    if (-not $group) {
        New-ADGroup -Name $groupName -SamAccountName $groupName -GroupScope Global -GroupCategory Security -Path $paths.Groups
    } elseif ($group.DistinguishedName -notlike "*,$($paths.Groups)" -or $group.GroupScope -ne 'Global' -or $group.GroupCategory -ne 'Security') {
        throw "Existing group $groupName is outside the expected OU or has an unexpected type."
    }
}
foreach ($row in $rows) {
    $name = $row.SamAccountName
    $user = Get-ADUser -Filter 'SamAccountName -eq $name'
    if (-not $user) {
        $password = Read-Host "Choose a unique initial password for $name" -AsSecureString
        $parameters = @{
            Name = "$($row.GivenName) $($row.Surname)"
            SamAccountName = $name
            UserPrincipalName = "$name@$($config.DomainName)"
            GivenName = $row.GivenName
            Surname = $row.Surname
            Department = $row.Department
            Path = $paths.Users
            AccountPassword = $password
            Enabled = $true
            ChangePasswordAtLogon = $true
        }
        New-ADUser @parameters
    } elseif ($user.DistinguishedName -notlike "*,$($paths.Users)") {
        throw "Existing user $name is outside the lab users OU."
    }
    $memberDNs = @(Get-ADGroupMember -Identity $row.Group | Select-Object -ExpandProperty DistinguishedName)
    $user = Get-ADUser -Identity $name
    if ($memberDNs -notcontains $user.DistinguishedName) { Add-ADGroupMember -Identity $row.Group -Members $user }
}
Write-Host 'Lab OUs, department groups, and sample users are ready. Existing account passwords were preserved.'
