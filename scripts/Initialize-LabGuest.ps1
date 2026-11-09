#Requires -Version 5.1
#Requires -RunAsAdministrator
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateSet('DomainController', 'Client')][string]$Role,
    [Parameter(Mandatory)][string]$InterfaceAlias
)
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$system = Get-CimInstance Win32_ComputerSystem
if ($system.PartOfDomain) { throw 'Use this script only before joining or creating a domain.' }
$adapters = @(Get-NetAdapter | Where-Object Status -eq 'Up')
if ($adapters.Count -ne 1 -or $adapters[0].Name -ne $InterfaceAlias) {
    throw 'Connect exactly one lab adapter. Use Get-NetAdapter to select its exact interface alias.'
}
if ($Role -eq 'DomainController') {
    $address = $config.DomainControllerIP
    $computerName = $config.DomainController
    if ((Get-CimInstance Win32_OperatingSystem).ProductType -eq 1) {
        throw 'The domain controller requires Windows Server.'
    }
} else {
    $address = $config.ClientIP
    $computerName = $config.ClientName
}
$index = $adapters[0].ifIndex
if ($PSCmdlet.ShouldProcess("$env:COMPUTERNAME / $InterfaceAlias", "Set $address/$($config.PrefixLength), DNS, no IPv4 gateway, and name $computerName")) {
    Set-NetIPInterface -InterfaceIndex $index -AddressFamily IPv4 -Dhcp Disabled
    Get-NetIPAddress -InterfaceIndex $index -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -ne $address -or $_.PrefixLength -ne $config.PrefixLength } |
        Remove-NetIPAddress -Confirm:$false
    Get-NetRoute -InterfaceIndex $index -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Remove-NetRoute -Confirm:$false
    $existing = Get-NetIPAddress -InterfaceIndex $index -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -eq $address -and $_.PrefixLength -eq $config.PrefixLength }
    if (-not $existing) {
        New-NetIPAddress -InterfaceIndex $index -IPAddress $address -PrefixLength $config.PrefixLength | Out-Null
    }
    Set-DnsClientServerAddress -InterfaceIndex $index -ServerAddresses $config.DomainControllerIP
    if ($env:COMPUTERNAME -ne $computerName) { Rename-Computer -NewName $computerName | Out-Null }
    Write-Host 'Configuration complete. Restart this VM before the next step.'
}
