# Validation and evidence

See [observed implementation results](implementation-results.md) for completed demo checks and explicitly unrun Windows checks. Use [live validation](live-validation.md), [permission tests](permissions-demo.md) and the [results template](results-template.md) for your own lab.

The included Java suite checks demo object creation, salted password hashing/replacement, lock/unlock, membership, duplicate/input validation, LDAP encoding/configuration guards, protected HTTP operations and password exclusion from responses. Run the README commands; the printed count describes your checkout.

These checks do **not** prove live AD connectivity or VM deployment. Windows CI parses the optional infrastructure scripts without creating a forest or signing into a domain.

Record date, Java version, OS and mode. Mark only tasks you completed.

- [ ] Java build and behavioral checks pass.
- [ ] Demo shows fictional users, groups, OUs and a locked account.
- [ ] Created a department OU, group and user.
- [ ] Added and removed direct membership.
- [ ] Reset a password and verified that reset alone preserves the lock.
- [ ] Unlocked the account and reviewed saved activity.
- [ ] Verified no password appears in responses, screenshots or activity.
- [ ] Demo data and saved activity survive a real application restart.
- [ ] Completed department-transfer and offboarding tickets.
- [ ] Failed and uncertain operations appear with the correct outcomes.
- [ ] Exported a mode-labeled report and password-free activity CSV.

For a real domain:

- [ ] Patched, isolated and joined DC01/CLIENT01; health checks pass.
- [ ] LDAPS certificate chain and hostname validate normally.
- [ ] Operator has delegated rights only over the lab OU.
- [ ] Java connects in LDAP mode without demo fallback.
- [ ] User, group and OU creations are independently confirmed in ADUC.
- [ ] Password reset, forced change at next Windows sign-in and unlock are confirmed.
- [ ] Membership changes are confirmed in ADUC.
- [ ] Operator cannot write outside the lab OU, verified through Windows tools.

Label screenshots **Demo** or **Real AD**. Use fictional accounts and omit credentials, certificate keys, private addresses and real organizational data. Local `evidence/` and `reports/` are ignored by Git.

Running the simulator supports a Java directory-management project claim. Claim hands-on Active Directory administration after completing the real-domain checks.
