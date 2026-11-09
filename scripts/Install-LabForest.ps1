#Requires -Version 5.1
#Requires -RunAsAdministrator
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Lab.Common.ps1')
$config = Get-LabConfig
if ((Get-CimInstance Win32_OperatingSystem).ProductType -eq 1) { throw 'Windows Server is required.' }
if ((Get-CimInstance Win32_ComputerSystem).PartOfDomain) { throw 'This VM already belongs to a domain. Do not create another forest.' }
if ($env:COMPUTERNAME -ne $config.DomainController) { throw 'Initialize the server and reboot before creating the forest.' }
$address = @(Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $config.DomainControllerIP)
if ($address.Count -ne 1 -or $address[0].PrefixLength -ne $config.PrefixLength) {
    throw 'Expected domain controller IP is missing. Run Initialize-LabGuest first.'
}
$feature = Install-WindowsFeature AD-Domain-Services -IncludeManagementTools
if (-not $feature.Success) { throw 'AD DS role installation failed.' }
if ([string]$feature.RestartNeeded -eq 'Yes') { throw 'Restart the VM, then rerun this script to create the forest.' }
Import-Module ADDSDeployment
$dsrmPassword = Read-Host 'Choose a unique DSRM recovery password (store it securely)' -AsSecureString
Install-ADDSForest -DomainName $config.DomainName -DomainNetbiosName $config.NetbiosName `
    -InstallDns -SafeModeAdministratorPassword $dsrmPassword -Force
