# Java LDAPS setup

Complete [Windows setup](setup.md). Use a dedicated test domain and fictional accounts. Real mode requires the explicit `--ldap` argument.

## Certificate and connectivity

Follow [Microsoft's LDAPS requirements](https://learn.microsoft.com/en-us/troubleshoot/windows-server/active-directory/enable-ldap-over-ssl-3rd-certification-authority). DC01 needs a certificate with Server Authentication usage, its FQDN, a private key and a trusted certificate chain. AD DS installation alone does not configure LDAPS.

Ensure `dc01.lab.example.test` resolves to the DC from the Java machine and TCP 636 is reachable. If you use a private lab CA, export its **public CA certificate** and import it into a separate truststore:

```sh
keytool -importcert -alias ad-lab-ca -file lab-ca.cer -keystore config/lab-truststore.p12 -storetype PKCS12
```

Review the certificate before trusting it. `keytool` prompts for the truststore password. Keep private keys and truststores out of Git. Never disable certificate-chain or hostname checks.

## Delegate a lab operator

In Active Directory Users and Computers (ADUC):

1. Confirm `OU=Lab,DC=lab,DC=example,DC=test` exists.
2. Create an ordinary `lab.operator` account. Do not add it to Domain Admins.
3. On **Lab**, use **Delegate Control** for creating/managing lab users and groups, resetting passwords/forcing password change, and modifying group membership. Custom delegation must also permit creating OU objects and writing the required user properties: `lockoutTime`, `pwdLastSet`, and `userAccountControl`, on the appropriate descendant classes.
4. Validate the operator's effective rights using disposable objects. It should have no write rights outside the lab OU.

UI details vary by Windows version and existing ACLs. The app's OU restriction supplements AD permissions; it does not reduce an overprivileged account's rights outside this application.

## Configure and connect

Copy `config/ldap.example.properties` to ignored `config/ldap.properties`:

```properties
ldap.url=ldaps://dc01.lab.example.test:636
ldap.rootDn=OU=Lab,DC=lab,DC=example,DC=test
ldap.upnSuffix=lab.example.test
ldap.bindDn=lab.operator@lab.example.test
```

Do not put a password in this file. The root must be an existing dedicated OU, never a domain root. Use direct child OUs for predictable exercises. Nested objects may be listed, but the app does not edit nested OU structure.

Build, then start from a real terminal:

```sh
java -Djavax.net.ssl.trustStore=config/lab-truststore.p12 -jar .build/active-directory-lab.jar --ldap config/ldap.properties
```

This truststore contains public certificates only. JSSE can read trusted entries without a command-line password; if your distribution requires additional truststore password configuration, use an approved local secret mechanism rather than a committed argument file. A CA already trusted by the JDK needs no custom truststore.

Enter the bind password at the masked prompt and open [the local dashboard](http://127.0.0.1:8080). Confirm **Connected to Active Directory**. Connection failure stops startup, without demo fallback. Restart to bind again after credential changes; automatic reconnection is not implemented.

## Verify actual AD operations

Create a disposable OU, group and user; reset the password; change membership; verify each operation independently in ADUC. User creation is staged: disabled account, password, forced change, then enable. A failed stage may leave a partial account; inspect it before retrying. These operations are not a transaction.

The adapter writes a quoted UTF-16LE `unicodePwd` over LDAPS, following [Microsoft's password-operation requirements](https://learn.microsoft.com/en-us/troubleshoot/windows-server/active-directory/change-windows-active-directory-user-password). AD policy may be stricter than the app's minimum. A reset does not unlock the user; unlock separately.

For real lockout tests, use a disposable user and the domain's lockout policy. **Simulate lock** is demo-only. Real lock status is read from `msDS-User-Account-Control-Computed`, rather than assuming any nonzero historical `lockoutTime` means a current lock.

This is a small-lab adapter: searches are limited to 500 objects. AD paging, large group member ranges, nested/primary-group membership and cross-domain referrals are not implemented. It does not provision shares or resource ACLs. Live integration must be verified with [the checklist](validation.md).
