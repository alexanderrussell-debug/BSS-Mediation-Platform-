# UMS Java 7 -> 21 verification harness

Regression evidence for the Java 21 migration. Findings for Revenue Assurance: `../docs/JAVA21_VERIFICATION.md`.

| Path | What |
|---|---|
| `setup-tools.sh` | Downloads Zulu 7u80 (= prod runtime), Zulu 7u352 (build), Temurin 21, Maven 3.6.3 into `~/.cache/ums-verify` (`UMS_VERIFY_TOOLS`). No Docker needed. |
| `make-baseline.sh [--ref main] [--jar J]` | Builds the ref with JDK 7, runs the scenario on JDK 7u80, writes `baseline-java7/`, self-checks that a second run is byte-identical. |
| `baseline-java7/` | Committed baseline: `inputs/` (exact CDR files), `cycle1/`, `cycle2/` (outputs, audit, state, archive), `MANIFEST.txt`, `SHA256SUMS`, `run-once.log`. |
| `compare.sh <fat jar>` | Replays the baseline on JDK 21 and prints PASS/FAIL per file and per control-totals row. Exit 0 = all PASS. |
| `ftp-test.sh <fat jar>` | MSC_ID FTP collection against a local pyftpdlib server: download, remote delete, processing, outputs vs baseline, empty poll, bad password. |
| `tz-check.sh <fat jar>` | Runs `tz/TzCheck`, `tz/MscIdTzCheck` and `tz/DateUtilRaceCheck` on JDK 7 and JDK 21 and diffs the output. |
| `java/SerCompare.java` | Content comparison for `dedup.ser`/`pending.ser`. |
| `results/` | Output of the runs quoted in the report. |

Prereqs: `libfaketime` (`apt-get install faketime`/`yum install libfaketime`), `python3`, `unzip`, `gzip`; `python3 -m pip install pyftpdlib` for `ftp-test.sh`.

```sh
verify/setup-tools.sh
verify/compare.sh target/ums-mediation-5.2.1-jar-with-dependencies.jar               # java -jar + JAVA_OPTS parsed from bin/ums.sh
verify/compare.sh target/ums-mediation-5.2.1-jar-with-dependencies.jar --via-ums-sh  # through bin/ums.sh run-once
verify/ftp-test.sh target/ums-mediation-5.2.1-jar-with-dependencies.jar
verify/tz-check.sh target/ums-mediation-5.2.1-jar-with-dependencies.jar
verify/make-baseline.sh            # only to regenerate the baseline (needs JDK 7 tools)
```

## Scenario

Every run uses an empty sandbox `UMS_HOME` (copy of `refdata/`, empty `conf/`, `data/`, `logs/`):

1. `gen-samples` (`SampleCdrGenerator`) at frozen clock `2026-11-20 10:15:00` gives 5 files for MSC_SG, MSC_ID, PGW_SG and SMSC_SG dated 2026-11-19. `compare.sh` regenerates them on the target JDK and diffs them against `inputs/cycle1`.
2. Cycle 1 at `10:30:00`: `run-once` on all inputs.
3. Cycle 2 at `10:45:00` (restart with persisted state): the MSC_SG and MSC_ID files are sent again (dedup.ser must reject them all as duplicates) plus `PGW_SG01_..._0003.csv`, the final partial of session 10009 that cycle 1 left in `pending.ser`.
4. Only in `compare.sh`, **cutover**: a fresh sandbox is seeded with the JDK 7 baseline's cycle-1 `output/`, `audit/`, `state/` and `archive/`, then cycle 2 runs on the target JDK. This is a Java 21 deploy between two Java 7 cycles, and it must produce the baseline's cycle 2.

What is compared (byte-for-byte unless noted): `data/output/**` (billing, dwh, fraud, ocs, reject), `data/audit/control_totals_*.csv` (byte-for-byte and per row, keyed by STREAM+FILE+occurrence), `data/state/seq_*.txt`, archived input files (gunzipped), the list of files left in `input/ work/ error/`, and `dedup.ser`/`pending.ser`.

## Determinism and normalisation (exhaustive)

* **Clock**: `libfaketime` with an absolute `FAKETIME` freezes the wall clock for the JVM. `new Date()`/`currentTimeMillis()` therefore return the same instant in every place UMS uses them: `OutputSet` file names, the `BillingUdrWriter` header, `DateUtil.today()`, the `RecordValidator` future/90-day checks, `ControlTotals` start/end (so `ELAPSED_MS` is 0), the generator's "yesterday", and the two-digit-year pivot of `SimpleDateFormat`. The monotonic clock stays real (`FAKETIME_DONT_FAKE_MONOTONIC=1`).
* **Environment**: `TZ=Asia/Singapore` (prod host TZ), `LANG=C LC_ALL=C`, and `JDK_JAVA_OPTIONS`/`JAVA_TOOL_OPTIONS`/`_JAVA_OPTIONS` unset. `-Dfile.encoding=ISO-8859-1` comes from `bin/ums.sh`.
* **One JVM per stream ("serial" mode)**: each cycle is one `run-once` per stream (`conf/ums.properties: ums.streams=<S>`, in `ums.streams` order). This is required because `DateUtil` shares one `SimpleDateFormat` per pattern across the stream threads, so production-style concurrent runs are not reproducible on either JDK (see the report, finding F1). `--concurrent` runs the production style for evidence only.
* **Normalisation applied to captured files** (nothing else is changed):
  1. Audit CSV data rows are stable-sorted by STREAM, with the header kept first. This only matters in `--concurrent` mode, where append order depends on thread timing. In serial mode the order is already fixed.
  2. Archive `.gz` files are compared after gunzip, because the gzip header carries the mtime.
  3. `dedup.ser`/`pending.ser` are not byte-identical across JDK 7 and 8+. `HashSet`/`HashMap` serialise their bucket count and iterate in hash-bucket order, and both changed in JDK 8. When the bytes differ, `SerCompare` deserialises both files and compares their content (set members, map keys, every field of every `UsageRecord`). The check runs on the target JDK and again on JDK 7 with the Java 7 jar (rollback). If the content matches the result is `PASS*`, otherwise `FAIL`.
* **libfaketime crash retry**: JDK 7 under libfaketime sometimes segfaults in `getenv()` while the VM is still initialising, before any UMS code runs. `ums_run_once` retries only that exact signature (hs_err shows `(build )` and `getenv`).
