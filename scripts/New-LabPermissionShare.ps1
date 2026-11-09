#Requires -Version 5.1
#Requires -RunAsAdministrator
#Requires -Modules ActiveDirectory, SmbShare
[CmdletBinding(SupportsShouldProcess)]
param()
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$domain = Assert-LabDomain -Config $config
$paths = Get-LabPaths -Config $config -DomainDN $domain.DistinguishedName
if ($env:COMPUTERNAME -ne $config.DomainController) { throw 'Run on the disposable lab domain controller.' }
$folder = 'C:\ADLabShare'
$shareName = 'LabHelpdesk'
$marker = Join-Path $folder '.adlab-managed'
$existing = Get-SmbShare -Name $shareName -ErrorAction SilentlyContinue
if ($existing) { throw 'LabHelpdesk already exists. Inspect its share and NTFS permissions; this script does not overwrite shares.' }
if (Test-Path $folder) { throw 'C:\ADLabShare already exists. Use a fresh lab path; this script never changes existing folder ACLs.' }
$global = Get-ADGroup -Identity 'GG_Helpdesk'
if ($global.DistinguishedName -notlike "*,$($paths.Root)" -or $global.GroupScope -ne 'Global' -or $global.GroupCategory -ne 'Security') { throw 'Expected GG_Helpdesk global security group beneath the lab root.' }
$local = Get-ADGroup -Filter 'SamAccountName -eq "DL_LabShare_Modify"'
if ($local -and ($local.DistinguishedName -notlike "*,$($paths.Root)" -or $local.GroupScope -ne 'DomainLocal' -or $local.GroupCategory -ne 'Security')) { throw 'Unexpected existing permission group.' }
if ($PSCmdlet.ShouldProcess("$env:COMPUTERNAME\$shareName", 'Create disposable SMB share with group-based share and NTFS permissions')) {
    if (-not $local) { $local = New-ADGroup -Name 'DL_LabShare_Modify' -GroupScope DomainLocal -GroupCategory Security -Path $paths.Groups -PassThru }
    Add-ADGroupMember -Identity $local -Members $global
    New-Item -ItemType Directory -Path $folder | Out-Null
    Set-Content -Path $marker -Value 'Disposable Directory Lab share' -Encoding UTF8
    $permissionIdentity = "$($config.NetbiosName)\DL_LabShare_Modify"
    $admins = (New-Object System.Security.Principal.SecurityIdentifier "$($domain.DomainSID.Value)-512").Translate([System.Security.Principal.NTAccount]).Value
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $inherit = [System.Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    $propagate = [System.Security.AccessControl.PropagationFlags]::None
    foreach ($sid in 'S-1-5-18', 'S-1-5-32-544') {
        $identity = New-Object System.Security.Principal.SecurityIdentifier $sid
        $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', $inherit, $propagate, 'Allow')
        $acl.AddAccessRule($rule)
    }
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($permissionIdentity, 'Modify', $inherit, $propagate, 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $folder -AclObject $acl
    New-SmbShare -Name $shareName -Path $folder -FullAccess $admins -ChangeAccess $permissionIdentity -FolderEnumerationMode AccessBased -EncryptData $true | Out-Null
    if (-not (Get-NetFirewallRule -Name 'ADLab-SMB-Client' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name 'ADLab-SMB-Client' -DisplayName 'Directory Lab SMB from CLIENT01' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 445 -RemoteAddress $config.ClientIP -Profile Domain | Out-Null
    }
    Write-Host "Created \\$env:COMPUTERNAME\$shareName. Grant/remove users through GG_Helpdesk, then sign out and back in before each access test."
}
