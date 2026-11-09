# Reset and cleanup

**Java demo:** stop with Ctrl+C and restart. The seeded in-memory directory returns and session changes/activity disappear. No database or password file is written.

**Real AD:** stopping Java does not roll back changes. Inspect and remove only disposable objects using ADUC under your lab cleanup plan. No bulk deletion or domain teardown endpoint is included.

**VM rebuild:** shut down both disposable VMs, restore matching clean-install snapshots and rebuild in documented order. Avoid restoring the DC and client to unrelated times. Lab snapshots are not production AD backups.

When finished, power off VMs, remove temporary routes/transfer paths and keep truststores/connection settings private. Delete only VM files you have identified as disposable.
