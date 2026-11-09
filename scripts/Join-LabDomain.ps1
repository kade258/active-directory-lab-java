#Requires -Version 5.1
#Requires -RunAsAdministrator
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
$system = Get-CimInstance Win32_ComputerSystem
if ($system.PartOfDomain) {
    if ($system.Domain -eq $config.DomainName) { Write-Host 'Already joined to the lab domain.'; return }
    throw 'This computer belongs to another domain.'
}
if ($env:COMPUTERNAME -ne $config.ClientName) { throw 'Initialize the client and reboot before joining.' }
$clientAddress = @(Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $config.ClientIP)
if ($clientAddress.Count -ne 1) { throw 'Expected client IP is missing. Initialize networking first.' }
$dns = @((Get-DnsClientServerAddress -InterfaceIndex $clientAddress[0].InterfaceIndex -AddressFamily IPv4).ServerAddresses)
if ($dns.Count -ne 1 -or $dns[0] -ne $config.DomainControllerIP) { throw 'The client must use only the lab domain controller for IPv4 DNS.' }
Resolve-DnsName -Name "_ldap._tcp.dc._msdcs.$($config.DomainName)" -Type SRV -Server $config.DomainControllerIP -DnsOnly | Out-Null
$domainDN = ($config.DomainName.Split('.') | ForEach-Object { "DC=$_" }) -join ','
$paths = Get-LabPaths -Config $config -DomainDN $domainDN
$credential = Get-Credential -UserName "$($config.NetbiosName)\Administrator" -Message 'Enter your lab domain administrator credentials for the join operation'
Add-Computer -DomainName $config.DomainName -OUPath $paths.Workstations -Credential $credential -Restart
