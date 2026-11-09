#Requires -Version 5.1
#Requires -RunAsAdministrator
[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('DomainController', 'Client')][string]$Role)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$checks = [System.Collections.Generic.List[object]]::new()
function Invoke-LabCheck {
    param([string]$Name, [scriptblock]$Check)
    try {
        & $Check | Out-Null
        $checks.Add([pscustomobject]@{ Check = $Name; Result = 'PASS'; Detail = '' })
    } catch {
        $checks.Add([pscustomobject]@{ Check = $Name; Result = 'FAIL'; Detail = $_.Exception.Message })
    }
}
Invoke-LabCheck 'Domain membership' {
    $system = Get-CimInstance Win32_ComputerSystem
    if (-not $system.PartOfDomain -or $system.Domain -ne $config.DomainName) { throw 'Unexpected domain membership.' }
}
Invoke-LabCheck 'IPv4 address and DNS configuration' {
    $expectedIP = if ($Role -eq 'DomainController') { $config.DomainControllerIP } else { $config.ClientIP }
    $address = @(Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $expectedIP)
    if ($address.Count -ne 1 -or $address[0].PrefixLength -ne $config.PrefixLength) { throw 'Unexpected IPv4 configuration.' }
    $dns = @((Get-DnsClientServerAddress -InterfaceIndex $address[0].InterfaceIndex -AddressFamily IPv4).ServerAddresses)
    if ($dns -notcontains $config.DomainControllerIP) { throw 'The configured DNS server is not the lab DC.' }
}
Invoke-LabCheck 'DNS domain controller service record' {
    $records = @(Resolve-DnsName -Name "_ldap._tcp.dc._msdcs.$($config.DomainName)" -Type SRV -Server $config.DomainControllerIP -DnsOnly)
    if (-not ($records | Where-Object Type -eq 'SRV')) { throw 'No DC SRV records returned.' }
}
Invoke-LabCheck 'SYSVOL and NETLOGON shares' {
    foreach ($share in 'SYSVOL', 'NETLOGON') {
        if (-not (Test-Path "\\$($config.DomainController)\$share")) { throw "Cannot reach $share." }
    }
}
if ($Role -eq 'DomainController') {
    Import-Module ActiveDirectory
    Invoke-LabCheck 'Directory objects and department memberships' {
        $domain = Assert-LabDomain -Config $config
        $paths = Get-LabPaths -Config $config -DomainDN $domain.DistinguishedName
        foreach ($dn in $paths.Values) { Get-ADOrganizationalUnit -Identity $dn | Out-Null }
        foreach ($row in Import-Csv (Join-Path $PSScriptRoot '..\data\users.csv')) {
            $user = Get-ADUser -Identity $row.SamAccountName -Properties Enabled
            if (-not $user.Enabled) { throw "User $($row.SamAccountName) is disabled." }
            $members = @(Get-ADGroupMember -Identity $row.Group | Select-Object -ExpandProperty DistinguishedName)
            if ($members -notcontains $user.DistinguishedName) { throw "Missing $($row.Group) membership." }
        }
    }
    Invoke-LabCheck 'AD DS, DNS, and Netlogon services' {
        foreach ($name in 'NTDS', 'DNS', 'Netlogon') {
            if ((Get-Service $name).Status -ne 'Running') { throw "$name is not running." }
        }
    }
} else {
    Invoke-LabCheck 'Workstation secure channel' {
        if (-not (Test-ComputerSecureChannel -Server $config.DomainController)) { throw 'Secure channel validation failed.' }
    }
}
$checks | Format-Table -AutoSize -Wrap
if (@($checks | Where-Object Result -eq 'FAIL').Count -gt 0) { throw 'One or more lab checks failed. See docs/troubleshooting.md.' }
