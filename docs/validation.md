# Validation and evidence

The included Java suite checks demo object creation, salted password hashing/replacement, lock/unlock, membership, duplicate/input validation, LDAP encoding/configuration guards, protected HTTP operations and password exclusion from responses. Run the README commands; the printed count describes your checkout.

These checks do **not** prove live AD connectivity or VM deployment. Windows CI parses the optional infrastructure scripts without creating a forest or signing into a domain.

Record date, Java version, OS and mode. Mark only tasks you completed.

- [ ] Java build and behavioral checks pass.
- [ ] Demo shows fictional users, groups, OUs and a locked account.
- [ ] Created a department OU, group and user.
- [ ] Added and removed direct membership.
- [ ] Reset a password and verified that reset alone preserves the lock.
- [ ] Unlocked the account and reviewed session activity.
- [ ] Verified no password appears in responses, screenshots or activity.

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
