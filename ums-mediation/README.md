# UMS — Usage Mediation System

**BSS usage mediation for Meridian Telecom Group (ASEAN)**

UMS collects raw usage records (CDRs) from the network — voice switches, the packet core and the SMSC —
decodes them, cleans them, removes duplicates, stitches partial data sessions together, enriches them with
customer and tariff information, and distributes them to billing, the data warehouse, the fraud system and
the prepaid OCS.

In production since 2011 (Singapore). Indonesia was added in 2016.

> **Status:** business-critical, maintenance only. Every unbilled record is lost revenue — changes need
> Revenue Assurance sign-off.

> This is a fictional company and a demonstration codebase. All numbers, IPs and credentials are fake.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 7 (source/target 1.7) |
| Framework | Spring Framework 3.2 — XML bean wiring |
| Scheduling | Quartz 1.8 via Spring `CronTriggerBean` |
| Collection | Local directory pickup, Commons Net 2.2 FTP, a Perl script for the SMSC |
| Decoding | Hand-written ASN.1 BER reader (MSC), CSV (PGW), pipe-delimited (SMSC) |
| State | Java serialization (`.ser` files) for duplicate keys and pending partial CDRs |
| Reference data | CSV extracts from BSS, loaded at startup |
| Logging | Log4j 1.2 |
| Tests | JUnit 4.10 |
| Build / run | Maven (legacy Ant `build.xml` still in the repo), fat JAR run by `bin/ums.sh` on RHEL 6 |

## How it works

```
 MSC (BER) ─┐                                                                   ┌─► BillCore UDR (postpaid)
 PGW (CSV) ─┼─► collect ─► decode ─► validate ─► normalise ─► dedup ─► aggregate ┤
 SMSC (|)  ─┘                                                  partials    │     ├─► OCS recon (prepaid)
                                                                            ▼     ├─► DWH feed (all)
                                                      enrich ─► filter ─► fraud ──┼─► FMS fraud feed
                                                                                  └─► reject files
                                         control totals per file ─► data/audit/
```

Every 15 minutes Quartz triggers a cycle; each stream runs in its own thread. See `docs/ARCHITECTURE.md`.

## Building

Requires **JDK 7 or 8** and Maven 3.

```bash
mvn clean package
```

Produces `target/ums-mediation-5.2.1-jar-with-dependencies.jar`. Does **not** build on JDK 11+.

## Running locally

### Option A — Docker (easiest)

```bash
docker build -t ums:legacy .
docker run --rm ums:legacy
```

Generates a set of sample network files, runs one mediation cycle and prints the control totals and the
output files produced.

### Option B — Local JVM

```bash
mvn clean package
bin/ums.sh gen-samples     # writes sample CDRs into data/input/<STREAM>/
bin/ums.sh run-once        # one cycle, then exit
bin/ums.sh start           # daemon with the 15-minute schedule
```

Set `JAVA_HOME` to a JDK 7/8 install first.

### Where things land

| Path | What |
|---|---|
| `data/input/<STREAM>/` | Incoming network files |
| `data/output/billing/` | `UDR_*.dat` — BillCore fixed-width UDR v2 (postpaid) |
| `data/output/ocs/` | Prepaid usage for OCS reconciliation |
| `data/output/dwh/` | Data warehouse CSV (UTC) |
| `data/output/fraud/` | FMS feed |
| `data/output/reject/` | Rejected records with reason codes |
| `data/audit/` | Control totals per input file (Revenue Assurance) |
| `data/state/` | Duplicate keys, pending partial sessions, output file sequences |
| `data/archive/` | Processed input files (gzipped) |

## Streams

Configured in `src/main/resources/ums.properties` (overridden by `conf/ums.properties` on the server).

| Stream | Source | Format |
|---|---|---|
| `MSC_SG` | Singapore MSC | ASN.1 BER (3GPP 32.205 style) |
| `MSC_ID` | Indonesia MSCs (Jakarta, Makassar) | ASN.1 BER |
| `PGW_SG` | Singapore packet core | CSV, partial CDRs |
| `SMSC_SG` | Singapore SMSC | Pipe-delimited |

## Reference data

`refdata/` holds the nightly extracts: `subscribers.csv` (from BSS), `rating_zones.csv` (BSS Config team)
and `roaming_partners.csv` (Roaming team). They are loaded once at startup — the nightly cron restarts
UMS after the new extract lands (`bin/crontab.txt`).

## Reject reason codes

| Code | Meaning |
|---|---|
| R00 | Record could not be decoded |
| R01 | Served MSISDN missing / invalid |
| R02 | Start time missing, in the future, or older than 90 days |
| R03 / R04 | Negative duration / longer than 24h |
| R05 | Unknown record type |
| R10 | Subscriber not in reference data |
| R20 | No rating zone for destination |

## Documentation

* `docs/ARCHITECTURE.md` — processing chain, formats, state
* `docs/KNOWN_ISSUES.md` — open issues the team is aware of
* `CHANGELOG.txt` — release history

## Contacts

BSS Engineering (Mediation) — `bss-mediation@meridiantel.example` · Revenue Assurance — `ra@meridiantel.example`
