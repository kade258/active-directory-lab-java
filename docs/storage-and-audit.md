# Saved demo state and activity

The command-line app creates `.adlab/` by default, ignored by Git. Use an isolated folder with `--data-dir PATH` to keep separate exercises:

```sh
java -jar .build/active-directory-lab.jar --data-dir .adlab/practice-one
java -jar .build/active-directory-lab.jar --ldap config/ldap.properties --data-dir .adlab/real-domain
```

| File | Contents |
| --- | --- |
| `demo.properties` | Demo users, groups, OUs, account flags and salted PBKDF2 password hashes |
| `audit-demo.tsv` | Demo attempts, outcomes and correlation IDs |
| `audit-ldap.tsv` | Real adapter attempts/outcomes, stored separately |
| `runtime.lock` | Exclusive process lock preventing concurrent writes by two instances |

New demo changes survive restarts. Saves use a temporary file, force its data to disk, then atomically replace the previous state on the same filesystem. A failed replacement restores the old saved state in memory. Unsupported atomic moves fail the operation rather than silently switching to an unsafe write. Damaged or unknown-version data stops startup and is preserved for inspection; it is never silently reset.

The app sets private POSIX directory/file permissions where supported. On Windows, use a user-private data folder and review its inherited Windows ACLs. Password hashes are sensitive even though they are not plaintext. Do not share the data folder or commit it.

## Activity outcomes

- **STARTED:** persisted before the directory mutation is attempted.
- **SUCCEEDED:** the operation returned successfully and the final record was saved.
- **FAILED:** validation, permission or other failure was observed.
- **PARTIAL:** a multi-step operation partly succeeded, connectivity made the result uncertain, or the final audit record could not be saved.
- **REJECTED:** request-token/origin checks, invalid host/content type, malformed forms, excessive bodies or unknown operations blocked the request.

Each event includes a timestamp, mode, action, target, outcome and a fixed reason or exception type. Password fields, hashes and raw server error messages are excluded. A start/final pair shares an operation ID. If logging cannot save STARTED, the directory operation is not attempted. A crash may leave STARTED unresolved; the operation may still have happened. AD and the local journal are not one transaction.

Activity is an append-only application journal, not tamper-proof evidence or an authenticated multi-user audit. It identifies the local operator context, not a verified human identity. Use Windows security auditing for authoritative domain events. A damaged journal stops startup. The UI shows the latest 200 records; CSV exports all recorded events and protects leading spreadsheet formula characters. This small-lab implementation retains history in memory after loading and has no automatic rotation; archive data folders between longer exercises.

## Reset safely

Stop Java and choose a fresh data folder to start a new demo while preserving old evidence. Remove a data folder only after you have identified it as disposable and saved any wanted evidence. Switching folders does not reset a real domain; AD changes persist until you explicitly undo them in Windows tools.
