# Active Directory Lab — Java

A Java 21 directory-management dashboard for practicing help desk and junior administrator tasks: create users, security groups and organizational units; reset passwords; unlock accounts; and assign access through group membership.

**Run the demo on macOS, Windows or Linux with only a JDK.** Switch to LDAPS mode to perform the same operations against your own isolated Active Directory lab.

![Java demo dashboard with fictional users and saved activity](docs/images/dashboard.png)

> Demo mode is a locally saved simulation, not an Active Directory server. No Windows lab is available yet, so live domain results remain **not run**. The project includes a real LDAPS adapter, a live validation runner, Windows permission/delegation checks, and automated tests using an instrumented LDAP context. See [validation status](docs/implementation-results.md).

## Features

| Task | Demo mode | Real AD mode |
| --- | --- | --- |
| List users, groups and OUs | Fictional sample directory | Read from configured lab OU |
| Create users | Salted PBKDF2 password hashes | Create disabled user, set password, require change, then enable |
| Create groups | Security-group simulation | Global security groups |
| Create OUs | Local demo containers | Direct children of the lab root OU |
| Reset passwords | Replace stored hash; require change | Write `unicodePwd`, then `pwdLastSet=0` |
| Unlock accounts | Clear simulated lock | Set `lockoutTime=0` |
| Add/remove direct membership | Update local membership | Modify the group's `member` attribute |
| Simulate account lock | Available for exercises | Disabled |
| Move/disable accounts | Saved department and enabled state | Scoped LDAP rename and UAC updates preserving unrelated flags |
| Help desk tickets | Four scenarios with state-based checks | Same checks against the connected lab OU |
| Review activity | Saved attempts, success, failure and partial outcomes | Separate saved AD journal; authoritative audit is in Windows |
| Export evidence | Mode-labeled results and event CSV | Same exports plus independent Windows validation tools |

Permissions in this app are **group membership assignments**. Windows file/share ACLs, OU delegation, nested membership and primary-group membership are configured outside the app. The dashboard does not edit arbitrary ACLs or implement Windows authentication.

## Run in five minutes

Install a **JDK 21 or newer**. No Maven, database, npm or third-party Java libraries are required.

**macOS / Linux**

```sh
git clone https://github.com/kade258/active-directory-lab-java.git
cd active-directory-lab-java
sh tools/build.sh
java -jar .build/active-directory-lab.jar
```

**Windows PowerShell**

```powershell
git clone https://github.com/kade258/active-directory-lab-java.git
Set-Location active-directory-lab-java
.\tools\Build.ps1
java -jar .build/active-directory-lab.jar
```

Open **[http://127.0.0.1:8080](http://127.0.0.1:8080)**. Stop with **Ctrl+C**. Demo data and activity are saved in ignored `.adlab/` files and survive restarts. Seeded users have random initial passwords; use **Manage → Reset password** to set one for an exercise. There is no user sign-in endpoint. See [storage and reset behavior](docs/storage-and-audit.md).

If a downloaded PowerShell script is blocked, inspect it, then use `Unblock-File .\tools\Build.ps1`. Your organization's execution policy may require an approved way to run scripts.

## Try the project

1. Open **Organizational units** and create `Helpdesk`.
2. Create `GG_Helpdesk` in **Groups & permissions**.
3. Create `sam.rivera` in the Helpdesk OU with a unique initial password.
4. Add Sam to `GG_Helpdesk`, verify membership, then remove it.
5. Open **Manage** beside `taylor.reed`, who begins locked in demo mode.
6. Reset Taylor's password and unlock the account. Resetting alone preserves the lock.
7. Review **Activity**. No passwords are included in the history.

See [guided exercises](docs/exercises.md) and the [validation checklist](docs/validation.md).

## Complete realistic tickets

Open **Help desk tickets** to practice onboarding, locked-account recovery, a department transfer and offboarding. Checks are derived from actual app state and saved activity, and reopen if you undo a requirement. See [scenario walkthroughs](docs/scenarios.md).

![Help desk scenario checks in Demo mode](docs/images/tickets.png)

Use **Results & evidence** to download your results and **Activity** to export attempts/outcomes. Files exclude passwords, password hashes and raw server diagnostics. Exported reports explicitly distinguish demo observations from independent Windows evidence. See the [short walkthrough](docs/walkthrough.md) and [results template](docs/results-template.md).

## Connect to your own Active Directory lab

Follow [Windows setup](docs/setup.md), then [Java LDAPS setup](docs/ldap.md). The Java machine must resolve and reach your domain controller. The adapter uses a delegated operator account and only manages descendants of the configured root OU.

```sh
java -jar .build/active-directory-lab.jar --ldap config/ldap.properties
```

Enter the bind password at the masked terminal prompt. It is not stored in the properties file or passed on the command line. AD mode requires a trusted LDAPS certificate, a dedicated lab OU and delegated rights. It does not fall back to plaintext LDAP or demo data on connection failure.

Then follow the [live integration runner](docs/live-validation.md) and [shared-folder permission demonstration](docs/permissions-demo.md). The Windows tools prepare a disposable share and produce actual denied → allowed → denied access evidence. They also test a delegated operator's OU-creation boundary. These tools are prepared; Windows outcomes are not claimed until you run them.

## Tests

```sh
# macOS / Linux
sh tools/test.sh
```

```powershell
# Windows
.\tools\Build.ps1 -Test
```

The dependency-free suite checks persistence/reload, hashes, lock/unlock, transfer/offboarding, membership, case-insensitive names, invalid input, protected HTTP writes, request limits, exports and password exclusion. Instrumented LDAP tests verify the OU boundary before writes, permission failures, connection loss, staged account creation, partial resets, and preservation of unrelated account flags. GitHub Actions runs on Linux and Windows, parses Windows scripts, and publishes a runnable JAR artifact with a SHA-256 checksum.

Live AD behavior, certificates, delegation, Windows sign-in and VM provisioning require the separate [VM validation checklist](docs/validation.md).

## Design

```mermaid
flowchart LR
  UI[Local browser dashboard] --> HTTP[Java HTTP server\n127.0.0.1:8080]
  HTTP --> API[Directory interface]
  API --> Demo[DemoDirectory\nAtomically saved simulation]
  HTTP --> Audit[Saved attempt and outcome journal]
  HTTP --> Tickets[Scenario checks and evidence exports]
  API --> LDAP[LdapDirectory\nJNDI + LDAPS]
  LDAP --> AD[Your lab domain controller\nDedicated lab OU]
```

| Path | Purpose |
| --- | --- |
| `src/main/java/lab/` | HTTP server, model, demo store and AD adapter |
| `src/main/resources/web/` | Dashboard HTML, CSS and JavaScript |
| `src/test/java/lab/LabTests.java` | Behavioral tests |
| `tools/` | Build and test commands |
| `config/ldap.example.properties` | Non-secret AD connection template |
| `scripts/`, `config/lab.psd1`, `data/users.csv` | Optional Windows guest setup and initial AD objects |
| `docs/` | Setup, exercises, troubleshooting and validation |

This is a single-operator local learning tool. It binds only to IPv4 loopback, checks its Host header, rejects cross-origin writes and requires a random per-process request token. Its saved application journal is not tamper-proof or a substitute for Windows security auditing. It has no multi-user authentication. Other processes with access to your local account can interact with it. Keep it local; do not expose it through a public proxy or network listener.

## Portfolio wording

After completing the demo:

> Built a Java directory-management lab with user, group and OU management, password resets, account unlocking, group membership controls, and automated behavioral checks.

After validating against a real Windows domain, describe the real AD operations you performed. Keep evidence aligned with the mode you used; see [validation](docs/validation.md).

## References

- [JDK HTTP server API — Oracle](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html)
- [AD password changes over LDAP — Microsoft](https://learn.microsoft.com/en-us/troubleshoot/windows-server/active-directory/change-windows-active-directory-user-password)
- [Enable LDAPS — Microsoft](https://learn.microsoft.com/en-us/troubleshoot/windows-server/active-directory/enable-ldap-over-ssl-3rd-certification-authority)
- [Install Active Directory Domain Services — Microsoft](https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/deploy/install-active-directory-domain-services--level-100-)
- [Domain join prerequisites — Microsoft](https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/join-computer-to-domain)

[MIT license](LICENSE). Windows and third-party software have separate licensing terms.
