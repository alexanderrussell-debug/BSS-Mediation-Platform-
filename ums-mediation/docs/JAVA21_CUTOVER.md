# Java 21 cutover runbook — serialized state (`data/state/`)

Scope: moving `ossmed01` from the JDK 7 build of UMS 5.2.1 to the Java 21 build (5.3.0) without losing or
re-billing usage. Covers `dedup.ser`, `pending.ser`, `seq_<STREAM>.txt`, rollback, and items for Ops.

## 1. Verified compatibility (summary)

Tested with Zulu 1.7.0_352 (JDK 7 build of `main`, `source/target 1.7`) and Temurin 21.0.13.1 (same sources
compiled for Java 21, and the 5.3.0 branch build).

| Class in the state files | serialVersionUID on JDK 7 | on JDK 21 |
|---|---|---|
| `com.meridiantel.bss.ums.core.UsageRecord` (no explicit UID, UMS-412) | `7311000903433512030L` | `7311000903433512030L` (JDK 7-built and JDK 21-built classes) |
| `java.util.HashSet` | `-5024744406713321676L` | same |
| `java.util.HashMap` | `362498820763181265L` | same |
| `java.util.ArrayList` | `8683452581122892189L` | same |
| `java.util.Hashtable` (`UsageRecord.extra`) | `1421746759512286392L` | same |
| `java.util.Date` | `7523967970034938905L` | same |
| `java.lang.String` | `-6849794470754667710L` | same |

State produced by the JDK 7 build (`SampleCdrGenerator` + `run-once`) was loaded by the Java 21 build:

| File | JDK 7 → Java 21 | Java 21 → JDK 7 (rollback) |
|---|---|---|
| `dedup.ser` (32 keys) | loads, 32/32 keys identical; re-running the same input marks every record the JDK 7 run accepted as a duplicate | loads (34 keys written by Java 21) |
| `pending.ser` (1 pending PGW session) | loads; all 32 `UsageRecord` fields identical (reflection dump); session completes when the final partial arrives | loads, fields identical |
| `seq_<STREAM>.txt` | plain text, unaffected; numbering continued (1→2, 2→3) | unaffected |

Verdict: **state files are compatible in both directions. No `dedup.ser` reset and no converter are needed**
as long as `UsageRecord` is not changed in this release. `Jdk7StateCompatibilityTest` keeps the JDK 7
files in `src/test/resources/state/jdk7/` and pins the serialVersionUID, so a change to `UsageRecord`
fails the build instead of failing in production.

The bytes of a re-written file differ slightly (internal `HashMap`/`Hashtable` capacity fields); the
content is identical. Do not compare `.ser` files by checksum across versions — use `StateFileTool`.

## 2. New behaviour in 5.3.0

* **Fail-fast on unreadable state.** If `dedup.ser` or `pending.ser` exists but cannot be loaded
  (corrupt, truncated, empty, wrong type, incompatible class), UMS logs
  `ERROR ... UMS-STATE-LOAD-FAILURE: ...` and **does not start** (`run-once` exits 1; `ums.sh start`
  leaves a dead PID). The file is not modified. 5.2.1 silently started empty and then overwrote the file.
* Deliberately starting empty: move the file aside (`mv dedup.ser dedup.ser.reset-YYYYMMDD`), or start once with
  `-Dums.state.allowEmptyOnLoadFailure=true` — UMS then renames the file to `<name>.unreadable-<timestamp>`
  and starts empty. Both need an RA/Billing decision (section 5).
* **Atomic save.** State is written to `<name>.tmp` and renamed. A `kill -9` during save (`ums.sh stop`
  sends it after 10 s) can no longer leave a truncated file. A leftover `*.tmp` is harmless.
* **`StateFileTool`** — read-only check, never writes:
  ```
  $JAVA_HOME/bin/java -cp lib/ums-mediation-5.2.1-jar-with-dependencies.jar \
      com.meridiantel.bss.ums.tools.StateFileTool check data/state     # 'dump' also lists keys/partials
  ```
  Prints key/session/partial counts and sequence numbers; exit 0 = all loadable, 2 = something failed.
* Monitoring: alert on `UMS-STATE-LOAD-FAILURE` in `logs/ums.log` / `logs/ums.out`, and on
  `ums.sh status` = stopped after the 02:30 cron restart.

## 3. Before the cutover (T-7 days)

1. **Confirm the OS can run JDK 21 (blocker — see section 7).**
2. Copy production state to a scratch directory and run `StateFileTool check` on it with JDK 21 and the
   5.3.0 jar. Expected: `RESULT OK` with the same key/session counts the JDK 7 UMS logged at its last start
   (`Loaded N dedup keys`, `Loaded N pending data sessions`). Any `FAILED` = stop, escalate.
3. Agree with RA which pending sessions are expected to remain (UMS-390 orphans never drain).

## 4. Cutover window

1. Comment out the 02:30 `ums.sh stop; ums.sh start` line in the `ums` crontab for the window.
2. **Drain pending partials (ops runbook step 7).** Even though `pending.ser` is verified compatible,
   minimise what is in flight. Record the remaining count (`StateFileTool check data/state`).
3. Wait for the current 15-minute cycle to finish (last `control_totals` line written), then
   `bin/ums.sh stop`.
4. Check the files the JDK 7 build just wrote — 5.2.1 saves non-atomically and `stop` sends `kill -9`
   after 10 s: `StateFileTool check data/state` must be `RESULT OK`. If not, restore from the most recent
   good backup before continuing.
5. Back up the whole state directory and record checksums and sequence numbers:
   ```
   tar czf /opt/ums/backup/state-pre-java21-$(date +%Y%m%d%H%M).tgz data/state
   cat data/state/seq_*.txt
   ```
6. **Preserve `data/state/seq_<STREAM>.txt` exactly.** Never delete, reset or re-create them —
   BillCore validates the per-stream file sequence and alarms on gaps (UMS-460).
7. Deploy the 5.3.0 jar and `bin/ums.sh`; set `JAVA_HOME` to the JDK 21 install.
8. `StateFileTool check data/state` with JDK 21 → `RESULT OK`, counts equal to step 4.
9. `bin/ums.sh start`; `bin/ums.sh status` = running; `logs/ums.log` shows `Loaded N dedup keys` and
   `Loaded N pending data sessions` with the step 4 numbers and no `UMS-STATE-LOAD-FAILURE`.
10. After the first cycle: every file in `data/audit/control_totals_*.csv` has `BALANCED=Y`; each stream's
    first output file uses `seq` = recorded value + 1 (no reset to 1, no gap).
11. Re-enable the 02:30 cron restart.

## 5. If UMS-STATE-LOAD-FAILURE appears

Do not delete anything. Run `StateFileTool check` for the reason. Preferred action: roll back (section 6) or
restore the step 5 backup. Only with RA/Billing sign-off, accept a reset:

* **`dedup.ser` reset — duplicate re-processing risk.** UMS forgets every key it has seen (all streams,
  never purged, UMS-355). Any file or record delivered again afterwards is billed again: switch re-sends
  after an outage (MSC buffers ~72 h), FTP re-collection, PGW/SMSC re-deliveries, and Ops recycling of
  R10 lines (UMS-301). Mitigation: keep re-delivery windows quarantined, have RA compare billed counts
  against the previous days, and keep the old file (moved aside, not deleted) for forensic key lookups
  (`StateFileTool dump`).
* **`pending.ser` reset.** Partials already counted as `HELD` are lost: those sessions are never billed;
  final partials arriving later are billed with only their own volume/duration. Draining (step 2)
  keeps this small.

`dedup.ser` holds only `java.util.HashSet` of `String` — no UMS classes — so it cannot become
incompatible through a UMS class change or a JDK upgrade. That is why no converter was written.

## 6. Rollback to the JDK 7 build

1. `bin/ums.sh stop` (5.3.0 saves atomically).
2. Restore the 5.2.1 jar and the old `bin/ums.sh` (JDK 7 `JAVA_HOME`, PermGen/CMS flags).
3. **Keep the current `data/state`** — do not restore the pre-cutover backup:
   * state written by the Java 21 build was verified readable by the JDK 7 build;
   * restoring old `seq_<STREAM>.txt` re-uses sequence numbers BillCore has already received;
   * restoring old `dedup.ser` forgets keys of files processed since cutover.
   Restore from backup only if `StateFileTool check` reports `FAILED`, and then keep the **current**
   `seq_*.txt` files.
4. Check with `StateFileTool check` (JDK 21 + 5.3.0 jar) before starting — the JDK 7 build still silently
   starts empty on an unreadable file. `*.tmp` and `*.unreadable-*` files are ignored by 5.2.1.
5. `bin/ums.sh start`, verify as in section 4 steps 9–10.

## 7. For Ops: JDK 21 on RHEL 6 (`ossmed01`) — must be confirmed before scheduling

RHEL 6 is not a supported platform for JDK 21. Tested: Temurin 21.0.13.1 x64 on CentOS 6.10
(glibc 2.12, the RHEL 6 baseline) fails to start —
`/lib64/libc.so.6: version 'GLIBC_2.14' not found (required by .../lib/libjli.so)`. The same check with
Zulu 1.7.0_352 runs. Other vendors' JDK 21 builds were not tested. Expect the cutover to need an OS upgrade
of `ossmed01` (RHEL 8/9) or a new VM — tracked separately, out of scope here.

## 8. Out of scope — follow-ups

* Pin `serialVersionUID = 7311000903433512030L` in `UsageRecord` (verified equal to the JDK 7 value) in a
  later release so future field changes stop breaking `pending.ser` (UMS-412). Not done here by decision:
  no serialized class changes in this migration.
* Replace `.ser` state with a real store and purge old dedup keys (UMS-355); flush orphan partials (UMS-390).
* FTP→SFTP and plain-text credentials (SEC-2023-118).
* RHEL 6 OS upgrade for `ossmed01` (section 7).
* UMS-433 date-parse rejects, RA-2025-007 Makassar timezone.
