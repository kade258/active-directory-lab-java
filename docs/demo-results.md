# Directory Lab results

Generated: 2026-11-09T19:49:32.943385Z

Mode: **DEMO — simulated directory**

Java: 21.0.2

Objects observed: 5 users, 4 groups, 4 OUs.

These are application observations. Windows sign-in, SMB access, certificate trust and delegated-rights validation require separate evidence.

## HD-101 — Onboard a new employee

Status: Checks satisfied

- [x] Create a Helpdesk organizational unit.
- [x] Create GG_Helpdesk in Helpdesk.
- [x] Create enabled user sam.rivera in Helpdesk and require a password change.
- [x] Add sam.rivera to GG_Helpdesk.

## HD-102 — Recover a locked account

Status: Checks satisfied

- [x] Reset taylor.reed's password through the app.
- [x] Unlock taylor.reed through the app and confirm the account is unlocked.
- [x] Keep the account enabled with password change required.

## HD-103 — Transfer an employee to HR

Status: Checks satisfied

- [x] Create an HR organizational unit if it does not exist.
- [x] Move jordan.lee to HR using Account tools.
- [x] Add jordan.lee to GG_HR.
- [x] Remove jordan.lee from GG_IT.

## HD-104 — Offboard a departing employee

Status: Checks satisfied

- [x] Disable casey.brooks using Account tools.
- [x] Remove casey.brooks from GG_Finance.

## Activity

11 successful operations; 1 failed, partial or rejected operations.

Download activity.csv separately for event details. No passwords or password hashes are included.
