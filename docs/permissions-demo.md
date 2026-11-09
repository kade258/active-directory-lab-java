# Demonstrate real group-based access

This exercise needs the [isolated Windows domain](setup.md). No Windows results have been recorded yet. Run it only with disposable users and a fresh folder on the lab DC; the compact lab hosts a share there for convenience. Use a separate member file server when extending the design beyond this disposable exercise.

## Prepare the server

On DC01, in elevated Windows PowerShell as the lab administrator:

```powershell
Set-Location C:\ADLab
.\scripts\Initialize-ScenarioLab.ps1 -WhatIf
.\scripts\Initialize-ScenarioLab.ps1
.\scripts\New-LabPermissionShare.ps1 -WhatIf
.\scripts\New-LabPermissionShare.ps1
```

The second script refuses any existing `C:\ADLabShare` or `LabHelpdesk` share. It creates a fresh folder, applies explicit NTFS rights, and creates an encrypted SMB share with change access for `DL_LabShare_Modify`. Administrators retain management access. It adds `GG_Helpdesk` to that domain-local group and creates an inbound SMB rule scoped to the configured client IP. Keep the VM network isolated and inspect the resulting rules.

The access path is **user → GG_Helpdesk → DL_LabShare_Modify → share and NTFS rights**. The Java dashboard manages the user's direct global-group membership. Windows tools configure the nested permission group and resource ACLs.

Reference: [Microsoft New-SmbShare](https://learn.microsoft.com/en-us/powershell/module/smbshare/new-smbshare?view=windowsserver2022-ps).

## Capture three actual access tests

Create the enabled disposable user `sam.rivera` using the Java app in **LDAP** mode. Set a unique password and change it at the first Windows console sign-in. Initially leave Sam out of GG_Helpdesk. On CLIENT01, use a non-elevated PowerShell window as Sam:

```powershell
C:\ADLab\scripts\Test-LabShareAccess.ps1 -ExpectedAccess Denied -OutputDirectory "$env:USERPROFILE\Documents\ADLabEvidence"
```

The script first checks TCP 445, then tries to create, read and delete its uniquely named probe file. It accepts only an actual access-denied exception as evidence of denial; missing shares, network errors and unexpected failures are **Unknown**, never a pass.

1. Record **Denied** before membership.
2. In Java, add Sam to `GG_Helpdesk`. Fully sign out of Windows and back in to refresh the access token. Run the script with `-ExpectedAccess Allowed` and record **Allowed**.
3. Remove Sam from the group in Java. Fully sign out/in again. Run with `-ExpectedAccess Denied` and record **Denied**.

Do not test using an administrator or reuse a terminal from before the membership change. `whoami /groups` helps inspect the new token. The script restricts the test identity to `sam.rivera` and refuses an elevated administrator token.

Store all three JSON reports and labeled screenshots privately. Review user/group membership, share permissions and `Get-Acl C:\ADLabShare` if results differ. Failed probes may leave a uniquely named file for administrator inspection; no broad cleanup command is included.

## Effective delegation boundary

After delegating the Java operator as described in [LDAPS setup](ldap.md), run:

```powershell
.\scripts\Test-LabDelegation.ps1 -WhatIf
.\scripts\Test-LabDelegation.ps1 -OutputDirectory .\reports
```

This asks for the ordinary operator credentials at a secure prompt. The administrator creates a dedicated disposable sibling probe OU outside Lab. The operator then tries creating one child inside Lab and one inside that sibling. A pass requires an inside success plus an explicit outside permission denial. Unknown errors fail the check. Probe OUs remain for administrator inspection and manual cleanup.

This verifies **OU creation delegation only**. Test user creation, password reset, group membership, moves and enable/disable rights using the live Java runner and independent Windows checks. This is evidence about the tested operation, not a complete effective-permissions audit of the domain.
