# Troubleshooting

| Symptom | Resolution |
| --- | --- |
| `java` or `javac` missing | Install a JDK. Verify `java -version` and `javac -version` in the same terminal. |
| Release 21 unsupported | Select a JDK 21+ compiler in PATH/JAVA_HOME. |
| Port 8080 occupied | Stop the earlier lab process or the application using that port. |
| Dashboard unreachable | Open `http://127.0.0.1:8080` on the machine running Java; the server is loopback-only. |
| Request token error | Reload after restarting Java. |
| Demo changes disappeared | Expected: demo data is in memory and resets on restart. |
| No password prompt | LDAP mode requires a real terminal with `System.console()`, not an IDE runner. |
| Certificate failure | Check DC FQDN, validity, Server Authentication usage and Java CA trust. Do not bypass validation. |
| LDAPS timeout | Check DNS, TCP 636, the isolated network, firewall rules and DC certificate setup. |
| AD rejects a change | Check delegated rights, domain-wide name conflicts, password policy and ADUC for partial user creation before retrying. |
| Outside objects missing | The adapter is deliberately scoped to one root OU. |
| Membership changed but share access did not | Configure resource ACLs separately and sign out/in to refresh the access token. |
| Too many search results | Use a small root OU; paging is not implemented and each search is capped at 500 results. |

## Optional Windows domain checks

On the client:

```powershell
ipconfig /all
Resolve-DnsName _ldap._tcp.dc._msdcs.lab.example.test -Type SRV -Server 10.10.10.10
Test-NetConnection DC01 -Port 389
Test-ComputerSecureChannel -Server DC01
```

Use the DC for DNS. Check clock synchronization for Kerberos errors. Domain join logs are in `C:\Windows\debug\NetSetup.log`. For existing computer objects, investigate [account-reuse protections](https://learn.microsoft.com/en-us/troubleshoot/windows-server/active-directory/failure-when-you-use-an-existing-computer-account-to-join-a-domain) rather than weakening them.

On DC01:

```powershell
Get-Service NTDS,DNS,Netlogon
dcdiag /test:dns /v
Get-SmbShare -Name SYSVOL,NETLOGON
```

Keep Windows Firewall enabled. A failed ping alone does not prove a service is unreachable. A single-DC lab has no inter-DC replication to validate.
