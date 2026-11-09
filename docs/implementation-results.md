# Verified implementation results

Verified on **November 9, 2026** using Java 21 on macOS ARM64. All results below describe this Java application and its fictional demo directory.

| Check | Observed result |
| --- | --- |
| Build and automated suite | **103 behavioral checks passed** |
| LDAP adapter contract tests | Scoped writes, escaping, UAC flag preservation, permission errors, connection loss, staged user setup and partial password resets checked with an instrumented JNDI context |
| HTTP protections | Request tokens, origin checks, invalid inputs, oversized bodies and restricted routes tested |
| Saved demo state | Users, groups, OUs, flags, password hashes and memberships survived reload |
| Actual app process restart | Objects, activity and all four scenario results matched the pre-restart state |
| Onboarding | Helpdesk OU/group, sam.rivera and direct membership created; all checks satisfied |
| Account recovery | Demo Taylor password reset separately from unlock; all checks satisfied |
| Department transfer | Jordan moved to HR, added to GG_HR and removed from GG_IT |
| Offboarding | Demo Casey disabled and removed from GG_Finance |
| Failure history | Duplicate OU rejected and shown under FAILED in the browser |
| Audit storage unavailable | Test confirmed the requested directory mutation was not performed |
| Evidence exports | Markdown results and CSV outcomes tested; password fields and hashes excluded |
| Portfolio media | Screenshots and a silent walkthrough captured from the fictional demo |

See the [actual exported demo results](demo-results.md), [walkthrough](walkthrough.md), and [GitHub Actions](https://github.com/kade258/active-directory-lab-java/actions) for the published build status.

## Real-domain status

**Not run: no Windows lab available.** The live integration runner, Windows scenario preparation, permission-share setup, access probes and delegation check are source-provided. Public CI parses the PowerShell scripts; it does not execute Windows domain operations.

The following require independent execution and evidence: LDAPS bind/write results, domain sign-in/password change, genuine lockout recovery, share denied/allowed/denied behavior, and effective AD delegation. Follow [live validation](live-validation.md) and [permissions demonstration](permissions-demo.md). No simulated result above is evidence that those Windows operations succeeded.
