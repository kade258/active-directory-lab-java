# Validate against a real domain

**Current real-domain evidence: not run.** No Windows lab was available during implementation. Automated adapter tests use an instrumented JNDI context; they are not live Active Directory tests.

After [Windows setup](setup.md), [LDAPS/delegation](ldap.md), and optional [scenario preparation](scenarios.md), run read-only validation from a terminal:

```sh
java -cp .build/active-directory-lab.jar lab.LiveValidation config/ldap.properties reports/live-ad.md
```

Enter the operator password at the masked prompt. A successful read-only run records an actual LDAPS bind, configured root lookup and directory read. The JDK's normal hostname and certificate-chain checks remain enabled. Any connection or permission failure yields an incomplete report and a nonzero exit.

To exercise writes in the explicitly configured disposable lab OU:

```sh
java -cp .build/active-directory-lab.jar lab.LiveValidation config/ldap.properties reports/live-ad-write-test.md --exercise
```

The explicit `--exercise` flag creates uniquely named verification OUs, a global security group and a disposable user. It sets/resets passwords, requests an unlock, adds/removes membership, moves the user and disables it, with read-back checks. Objects remain for inspection and manual cleanup. Generated test passwords are not logged or exported. This does not deliberately lock an account or perform Windows sign-in, and it says so in the report.

Never run this against a production domain. The adapter restricts searches and mutations to descendants of the configured OU, but that guard does not replace effective AD delegation.

Complete the independent checks:

1. Confirm created objects, password-change flag, disabled state and memberships in ADUC.
2. Use a separate disposable user to verify actual forced password change at Windows console sign-in.
3. Lock a disposable account under your domain policy and independently verify recovery.
4. Complete the [three shared-folder access tests](permissions-demo.md).
5. Run `Test-LabDelegation.ps1` for the OU creation boundary, then separately validate the other delegated operations.

Record date, versions, domain mode, commands and actual outcomes in the [results template](results-template.md). Keep raw reports private; publish only reviewed, redacted evidence.
