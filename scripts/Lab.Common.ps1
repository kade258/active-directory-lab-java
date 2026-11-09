# Shared by the lab scripts; dot-source this file rather than running it.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-LabConfig {
    $config = Import-PowerShellDataFile -Path (Join-Path $PSScriptRoot '..\config\lab.psd1')
    $config
}

function Assert-LabDomain {
    param([Parameter(Mandatory)][hashtable]$Config)
    $domain = Get-ADDomain -ErrorAction Stop
    if ($domain.DNSRoot -ne $Config.DomainName) {
        throw "Expected $($Config.DomainName); found $($domain.DNSRoot). Refusing to change another domain."
    }
    $domain
}

function Get-LabPaths {
    param([Parameter(Mandatory)][hashtable]$Config, [Parameter(Mandatory)][string]$DomainDN)
    $root = "OU=$($Config.RootOU),$DomainDN"
    @{
        Root = $root
        Users = "OU=$($Config.UsersOU),$root"
        Groups = "OU=$($Config.GroupsOU),$root"
        Workstations = "OU=$($Config.WorkstationsOU),$root"
    }
}
