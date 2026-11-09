# Project validation record

Status: **not yet completed**. Replace each pending field only with an observed result.

Date: pending

Mode: Demo / Real AD

Java version: pending

Windows Server/client version (if used): pending

Source commit: pending

| Check | Result | Evidence and limitations |
| --- | --- | --- |
| Local build and automated checks | Pending | Test output and commit |
| Saved demo survives restart | Pending | Before/after state |
| Onboarding ticket | Pending | Mode-labeled screenshot and report |
| Password reset and unlock | Pending | Distinct outcomes; Windows logon if real |
| Department transfer | Pending | OU and membership changes |
| Offboarding | Pending | Disabled state and removal of tested membership |
| Failed/partial operation history | Pending | Password-free event export |
| LDAPS bind and read | Not run | Live runner report |
| Real write operations | Not run | Live runner plus independent ADUC checks |
| Shared folder denied → allowed → denied | Not run | Three actual client access reports |
| Delegated OU scope | Not run | Inside success/outside permission denial; operation-specific |
| Genuine domain lockout recovery | Not run | Independent Windows evidence |

## Explanation for a reviewer

Describe what you changed, how you verified it, one failure you diagnosed, and what remains untested. Simulator completion alone does not establish Windows administration experience. Redact real identity/network details and keep passwords, hashes and private certificates out of published evidence.
