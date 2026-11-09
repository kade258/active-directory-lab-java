# Optional Windows domain lab

The Java demo does not require Windows. Use this guide when you want a real domain for LDAPS and Windows administration practice.

Use an x64-capable Hyper-V, VirtualBox, VMware or other Windows-compatible hypervisor. An Apple Silicon Mac cannot natively virtualize these x64 Server images; use a suitable remote x64 host. The Java demo runs directly on Apple Silicon.

| VM | OS | Suggested resources | Address |
| --- | --- | --- | --- |
| DC01 | Windows Server 2025 or 2022, Desktop Experience | 2 vCPU, 4 GB RAM, 60 GB disk | `10.10.10.10/24` |
| CLIENT01 | Windows 11 Pro or Enterprise | 2 vCPU, 4 GB RAM, 64 GB disk | `10.10.10.20/24` |

Enable the firmware, virtual TPM and other hardware required by your Windows 11 media. Obtain [Server media](https://www.microsoft.com/en-us/evalcenter/evaluate-windows-server-2025) and [Windows 11 Enterprise evaluation](https://www.microsoft.com/en-us/evalcenter/evaluate-windows-11-enterprise) from Microsoft, following current activation and evaluation terms.

1. Install, activate and update both VMs using temporary NAT internet access. Remove that connection before lab configuration.
2. Use a **Hyper-V Private** switch for VM-only traffic or **Internal** for host access. VirtualBox: **Internal Network** or **Host-only** with DHCP disabled. VMware: host-only/custom network with NAT and DHCP disabled.
3. Attach exactly one NIC per VM to the same isolated network. Avoid bridged networking and port forwarding. Keep Windows Firewall enabled.
4. Copy this repository to `C:\ADLab` inside both guests before removing the transfer path. Take matching powered-off clean-install snapshots.
5. Check that `10.10.10.0/24` does not overlap another host network. Edit `config/lab.psd1` consistently if needed. Keep its simple OU labels and dotted domain-name format.

The network script replaces IPv4 addresses and default routes on the selected adapter. Use the VM console on fresh machines. Keep IPv6 enabled and verify isolation in the hypervisor; the script cannot detect a bridged network or IPv6 router.

## Server

In elevated **Windows PowerShell 5.1** on the server, replacing `Ethernet` with the alias shown by `Get-NetAdapter`:

```powershell
Set-Location C:\ADLab
Get-NetAdapter
.\scripts\Initialize-LabGuest.ps1 -Role DomainController -InterfaceAlias Ethernet -WhatIf
.\scripts\Initialize-LabGuest.ps1 -Role DomainController -InterfaceAlias Ethernet
Restart-Computer
```

After reboot, reopen elevated PowerShell:

```powershell
Set-Location C:\ADLab
.\scripts\Install-LabForest.ps1
```

Enter a unique DSRM recovery password. Promotion reboots the server. If role installation requests a restart first, restart and rerun the script. Wait for AD DS/DNS startup and sign in as `LAB\Administrator`.

```powershell
Set-Location C:\ADLab
.\scripts\New-LabDirectory.ps1
.\scripts\Test-Lab.ps1 -Role DomainController
dcdiag /test:dns /v
```

Choose a different initial password for each fictional user. The script creates `OU=Lab` containing `Lab Users`, `Lab Groups` and `Workstations`, and preserves existing passwords on reruns. New users must change their password at Windows sign-in.

## Client

In elevated Windows PowerShell:

```powershell
Set-Location C:\ADLab
.\scripts\Initialize-LabGuest.ps1 -Role Client -InterfaceAlias Ethernet
Restart-Computer
```

After reboot, reopen elevated PowerShell:

```powershell
Set-Location C:\ADLab
.\scripts\Join-LabDomain.ps1
```

Enter lab administrator credentials for the one-time join. The client joins Workstations and restarts. Sign in as `LAB\alex.morgan` and change the initial password. Use an elevated administrator session to run `C:\ADLab\scripts\Test-Lab.ps1 -Role Client`.

Both VMs use `10.10.10.10` for DNS and no IPv4 default gateway. Do not add a public DNS server as the client's alternate resolver. The simplest Java host is CLIENT01: install JDK 21 there and open the dashboard in that VM. Host-only networking also allows a trusted host with working DNS and certificate trust. Continue with [LDAPS setup](ldap.md).

Infrastructure scripts are provided as source and parsed in Windows CI. VM deployment and domain sign-in require validation in your environment.
