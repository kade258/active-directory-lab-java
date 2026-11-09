# Reset and cleanup

**Java demo:** changes and activity now survive restart. Stop Java and use a fresh folder with `--data-dir .adlab/new-practice` to start a new exercise while preserving old evidence. See [saved state and audit](storage-and-audit.md). Delete a data folder only after identifying it as disposable and saving wanted evidence.

**Real AD:** stopping Java does not roll back changes. Inspect and remove only disposable objects using ADUC under your lab cleanup plan. No bulk deletion or domain teardown endpoint is included.

**VM rebuild:** shut down both disposable VMs, restore matching clean-install snapshots and rebuild in documented order. Avoid restoring the DC and client to unrelated times. Lab snapshots are not production AD backups.

When finished, power off VMs, remove temporary routes/transfer paths and keep truststores/connection settings private. Delete only VM files you have identified as disposable.
