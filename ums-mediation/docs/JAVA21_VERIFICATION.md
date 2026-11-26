# UMS Java 7 -> 21 verification report (for Revenue Assurance sign-off)

Harness: `verify/` (see `verify/README.md`). Raw output for every result below: `verify/results/`.

## 1. Summary

| Check | Result |
|---|---|
| Java 7 baseline (main `4f18634`, built with JDK 7u352, run on Zulu 7u80, the prod update level) | Recorded in `verify/baseline-java7/`. A second JDK 7 run is byte-identical. |
| Java 7 jar on JDK 21 runtime (isolates runtime differences: tzdata, encoding, collections) | **PASS**. All output files, control totals, `seq_*.txt` and archives are byte-identical. `.ser` content is identical (bytes differ, see 4). |
| `devin/java21-migration` jar (`a5ee068`, built on JDK 21, Spring 5.3 / Quartz 2.3 / reload4j / Commons Net 3.11.1), run via its `bin/ums.sh run-once` | **PASS**. 130 checks, 0 FAIL, including the cutover over JDK 7-written state. |
| Existing JUnit suite on JDK 21 (integration branch) | 34 run, 0 failures, 0 errors (`mvn package`, Temurin 21.0.13.1). |
| Timezone `Asia/Jakarta` / `Asia/Makassar` / `Asia/Singapore` (tzdata 2015a vs 2026f) | **No offset change** in the CDR range or later. MSC_ID decode is identical on both JDKs. |
| FTP collection (MSC_ID) | **PASS**: Commons Net 2.2 on JDK 7 and on JDK 21; Commons Net 3.11.1 on JDK 21. |
| Production-style concurrent run | Not reproducible on **either** JDK. A pre-existing `DateUtil` race, see **F1**. This is the main finding. |

## 2. Method

* Inputs: `SampleCdrGenerator` files for MSC_SG, MSC_ID (Jakarta + Makassar MSCs), PGW_SG and SMSC_SG. They are committed in `verify/baseline-java7/inputs/` and regenerate byte-identically on JDK 21.
* Scenario: cycle 1, then a restart and cycle 2. Cycle 2 re-sends the MSC files, so `dedup.ser` must reject all of them, and adds the closing partial of PGW session 10009 held in `pending.ser`. A **cutover** variant runs cycle 2 on JDK 21 on top of the state, output, audit and archive that JDK 7 left after cycle 1.
* Compared byte-for-byte: `data/output/**` (billing UDR, DWH, fraud, OCS, rejects), `control_totals_*.csv` (also row by row), `seq_<STREAM>.txt`, archived inputs, leftover files.
* Determinism: libfaketime freezes the wall clock; TZ is `Asia/Singapore` and the locale is `C`. Each stream runs in its own `run-once` JVM. The only normalisations are the audit row sort, gunzipping archives, and content comparison for `.ser` files. All of them are listed in `verify/README.md`.
* JVM options: those of `bin/ums.sh` with `-Dfile.encoding=ISO-8859-1`. On JDK 21 these are `-Xms2g -Xmx6g -XX:+UseG1GC -Dfile.encoding=ISO-8859-1`. The legacy `-XX:MaxPermSize` and `-XX:+UseConcMarkSweepGC` stop JDK 21 from starting ("Unrecognized VM option").

```
verify/compare.sh ~/ums-java7.jar                                  -> RESULT: PASS (130 PASS of which 6 PASS*, 0 FAIL)
verify/compare.sh target/ums-...-with-dependencies.jar --via-ums-sh -> RESULT: PASS (130 PASS of which 6 PASS*, 0 FAIL)
```

Control totals per input file (identical on JDK 7 and 21):

| Stream / file | INPUT | REJ | DUP | FILT | HELD | WRITTEN | FRAUD | DURATION_SEC | VOLUME_BYTES |
|---|---|---|---|---|---|---|---|---|---|
| MSC_SG `CDR_MSC_SG01_..._0001.ber` cycle 1 | 16 | 2 | 1 | 4 | 0 | 9 | 2 | 7245 | 0 |
| MSC_SG same file re-sent, cycle 2 | 16 | 1 | 15 | 0 | 0 | 0 | 0 | 0 | 0 |
| MSC_ID `CDR_MSC_ID01_..._0001.ber` cycle 1 | 5 | 0 | 0 | 0 | 0 | 5 | 0 | 1115 | 0 |
| MSC_ID same file re-sent, cycle 2 | 5 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 |
| PGW_SG `_0001.csv` | 10 | 1 | 1 | 1 | 4 | 3 | 1 | 7800 | 702545920 |
| PGW_SG `_0002.csv` | 2 | 0 | 0 | 0 | 0 | 2 | 0 | 6000 | 346030080 |
| PGW_SG `_0003.csv` (closes held session 10009, cycle 2) | 1 | 0 | 0 | 0 | 0 | 1 | 0 | 1800 | 157286400 |
| SMSC_SG `_0001.txt` | 9 | 1 | 2 | 2 | 0 | 4 | 0 | 0 | 0 |

## 3. Timezone (tzdata 2015a in JDK 7u80 vs 2026f in JDK 21.0.13.1)

`verify/tz-check.sh` covers the transition history from 1900 and quarterly offsets for 2010-2030. Output is in `verify/results/tz/`.

| Zone | Result |
|---|---|
| Asia/Jakarta (WIB) | Same: +07:00 since 1964-01-01, no DST, identical history. |
| Asia/Makassar (WITA), Asia/Ujung_Pandang | Same: +08:00 since 1945-09-22, no DST. |
| Asia/Pontianak, Asia/Jayapura | Same: +07:00 and +09:00. |
| Asia/Singapore (all SG streams, host TZ) | Same since 1982 (+08:00). Only the minute of the 1981-12-31 +07:30 to +08:00 change differs (16:30Z vs 16:00Z). |
| Asia/Kuala_Lumpur | Same since 1982. The 1900-1905 LMT start offset changed. |
| Asia/Manila (regional reference) | Same since 1990. Historical 1937-1990 transitions were corrected. |

The long display names changed ("West Indonesia Time" became "Western Indonesia Time", "Singapore Time" became "Singapore Standard Time"). UMS never prints zone names, and the byte comparison confirms no output is affected. **No offset change between the JDKs affects any CDR date.**

### MSC_ID decode, Makassar and RA-2025-007 (documented, not fixed)

* `ums.properties` maps every Indonesian MSC to one stream with `stream.MSC_ID.timezone=Asia/Jakarta`. This includes `MSC-ID-MKS01` (Makassar, WITA, UTC+8).
* `BerTlvReader.decodeTimestamp` drops the 3GPP UTC-offset octets (UMS-77, "Ericsson R12 sends garbage"). `MscBerDecoder` then parses the wall-clock digits as Asia/Jakarta.
* Result for a Makassar call at 10:23 WITA (switch sends `+0800`), identical on JDK 7 and JDK 21:

| | Value | Correct value |
|---|---|---|
| Instant / DWH `START_TIME` (UTC) | 2026-11-19 **03:23** | 02:23. UMS is **60 min late**. |
| Billing UDR local time | 20261119**1023** | 10:23 WITA. Correct digits, but the record claims they are WIB. |
| Peak/off-peak hour | 10 | 10 |

`verify/results/tz/MscIdTzCheck_*.txt` shows this for every Makassar case, including the 20:00 peak boundary and the day/year boundaries. It is consistent with RA-2025-007: any consumer that converts the instant, or the WIB-labelled local time, to the customer's WITA time is one hour off. **The Java 21 migration neither causes nor changes this.** The values are bit-identical across the JDKs. The fix (per-MSC timezone, or trusting the offset for non-Ericsson nodes) is out of scope and remains RA-2025-007.

## 4. Serialized state (`dedup.ser`, `pending.ser`)

* Content is identical after every cycle (`verify/java/SerCompare.java`, every field of every `UsageRecord`). JDK 21 reads the JDK 7-written files, and JDK 7 with the Java 7 jar reads the JDK 21-written files, so rollback works.
* The bytes differ. JDK 8+ `HashMap`/`HashSet` serialise a different bucket count and write elements in a different hash order. This is expected and harmless. **Do not checksum-compare `.ser` files across the cutover.**
* The cutover test (JDK 21 restart on JDK 7 state) correctly rejected all 20 re-sent MSC records as duplicates and closed PGW session 10009 from `pending.ser`. `serialVersionUID` of `UsageRecord` from `serialver` on JDK 7 is `7311000903433512030L`, matching the explicit value on the integration branch.

## 5. FTP collection (`verify/ftp-test.sh`)

The test uses a local pyftpdlib server with a throw-away user and a random password, and `conf/ums.properties` with `stream.MSC_ID.collector=FTP`. It checks:

* RETR, then DELE, of the BER file.
* A non-matching file and a sub-directory are left on the server.
* The file is processed and archived, and the outputs, `seq_MSC_ID.txt` and control totals are byte-identical to the LOCAL-collector baseline.
* An empty poll reports "collected 0 files".
* A wrong password logs `FTP login failed` and leaves the file on the server.

| Jar | Runtime | Result |
|---|---|---|
| main (Commons Net 2.2) | JDK 7u80 | PASS 17/17 |
| main (Commons Net 2.2) | JDK 21 | PASS 17/17 |
| devin/java21-migration (Commons Net 3.11.1) | JDK 21 | PASS 17/17 |

Behaviour kept from the legacy code: after a failed login, `run-once` still exits 0, so failures are only visible in the log. Plaintext FTP and passwords (SEC-2023-118) are out of scope.

## 6. Findings and risks for sign-off

**F1 (high, pre-existing, not a Java 21 regression): the `DateUtil` race corrupts billing output in production-style runs.**
`DateUtil` caches one `SimpleDateFormat` per pattern in a static `HashMap` and changes its timezone on every call. The four stream threads share it, and `SimpleDateFormat` is not thread-safe. Evidence (`verify/results/concurrent-mode-race_16runs.txt`, `compare_concurrent_example_jdk21.txt`, `tz/DateUtilRaceCheck_*.txt`):

* `compare.sh --concurrent` failed in 8 of 8 rounds on JDK 7u80 and 8 of 8 on JDK 21. The same inputs pass 100% in serial mode on both.
* Observed corruptions:
  * Billing UDR records with a start time shifted by ±1 h (SG vs WIB).
  * A UDR header timestamp in the wrong zone.
  * **Re-sent CDRs not detected as duplicates and billed a second time.** The dedup key contains the parsed time. Example: `DUPLICATES 15->14; WRITTEN 0->1; DURATION_SEC 0->300`.
  * R00 rejects with `Unparseable date: "261119100700"` and `For input string: "E.1102E22"` on valid records.
* The R00 rejects are exactly the UMS-433 symptom ("intermittent R00/R02 rejects in the busy hour, dates in 2002", "suspected switch issue"). This harness shows that UMS-433 is caused by UMS itself, not by the switches.
* The ±1 h shifts are a plausible second contributor to the hour-off complaints in RA-2025-007. This is a hypothesis.
* The race shows similar frequency on both JDKs. With 4 threads × 200k parses, `DateUtilRaceCheck` gives 53-58k wrong-timezone results on JDK 7 and 70k on JDK 21. Runs vary, and load and JIT differ, so these numbers do not show that either JDK is safer.
* Recommendation: make `DateUtil` thread-safe (a `ThreadLocal` formatter or a new formatter per call) before or with the cutover. It changes no format, and `compare.sh` (serial and `--concurrent`) verifies it. This is outside this slice and needs a decision from the coordinator or RA. Under the original scope it is "UMS-433, don't fix".

**F2: no offset changes.** tzdata 2015a to 2026f changes no offset for any configured zone after 1982. Java 21 does not change billed times.

**F3: RA-2025-007 unchanged by the migration.** Makassar records are 60 min late in UTC because MSC_ID uses `Asia/Jakarta` and the encoded offset is ignored (section 3).

**F4: state files.** They are compatible in both directions. Byte layout differs, content is identical (section 4).

**F5: JVM flags.** JDK 21 refuses to start with the legacy `bin/ums.sh` flags. The integration branch's `bin/ums.sh` was verified via `--via-ums-sh`.

## 7. Not verified here

* Quartz 2.x cron firing in daemon mode (`ums.sh start`). This harness only uses `run-once`, so the parent or slice A must verify it.
* `ums.sh start/stop/status` PID-file behaviour on JDK 21.
* The RHEL 6 host. All runs were on Ubuntu x86_64 with Zulu/Temurin builds. JDK 21 support on `ossmed01` still has to be confirmed by ops.
* Real production CDR volumes and real switch files. Only the generator samples were used (5 files, 42 records), so a parallel run on a copy of a production day is recommended before cutover.
* The `smsc_pull.pl` path and the Docker image.
