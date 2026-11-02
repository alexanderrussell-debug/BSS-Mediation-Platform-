# UMS architecture

_Last reviewed: 2019. Indonesia section added 2016._

## Deployment

* One RHEL 6 VM (`ossmed01`), 8 vCPU, 12 GB RAM; JVM heap 6 GB
* Originally Solaris 10 / SPARC (2011–2017)
* No HA: if `ossmed01` is down, switches buffer files (MSC ~72h, PGW ~24h, SMSC ~12h)
* Downstream systems pick up output files from NFS mounts of `data/output/*`

## Processing chain

| Step | Class | Notes |
|---|---|---|
| Collect | `collect.LocalDirCollector`, `collect.FtpCollector` | Files moved to `data/work/<STREAM>`. SMSC files are pulled by `bin/legacy/smsc_pull.pl` |
| Decode | `decode.MscBerDecoder`, `PgwCsvDecoder`, `SmscPipeDecoder` | Whole file read into memory. Undecodable records kept and rejected R00 |
| Validate | `process.RecordValidator` | Reject codes R00–R05 |
| Normalise | `process.RecordNormalizer` | Numbers to international format without `+` |
| Dedup | `process.DuplicateChecker` | Keys held in memory, saved to `state/dedup.ser` after each file |
| Aggregate | `process.PartialCdrAggregator` | Data partials (same charging id) held until the final record; saved to `state/pending.ser` |
| Enrich | `process.RecordEnricher` | Account, rate plan, payment type, zone, roaming partner, peak/off-peak |
| Filter | `process.RecordFilter` | Test SIMs, unanswered calls, home MT, zero usage, undelivered SMS, short codes |
| Fraud | `process.FraudRule` | Long IDD/premium/satellite calls, long roaming calls, heavy roaming data |
| Distribute | `output.*` | Postpaid → BillCore, prepaid → OCS recon, everything → DWH |
| Audit | `audit.AuditLogger` | Control totals per input file |
| Archive | `util.FileUtil` | Input moved to `data/archive` and gzipped |

`engine.StreamProcessor` runs the chain for one stream; `engine.MediationEngine` starts one thread per stream.

## Control totals

For every input file: `input = rejected + duplicates + filtered + held + written`.
A stitched data session counts once as written; its earlier partials were counted as held in the file
they arrived in. Revenue Assurance alarms on any file with `BALANCED=N`.

## Formats

### MSC BER

Each record is a constructed `[0]` (0xA0) with primitive fields 0x80–0x8A
(record type, served MSISDN, called/calling number as TBCD AddressString, answer time as 3GPP 9-octet
TimeStamp, duration, MSC id, serving PLMN, cause, sequence, IMSI). See `MscBerDecoder` javadoc.

### PGW CSV

Header line, 13 columns. Cause 0/4 = final, 16/17/18 = partial (volume limit / time limit / serving node change).

### SMSC

`MSG_ID|ORIG|DEST|SUBMIT_TIME|STATUS|SMSC_ID|SEGMENTS`, no header.

### BillCore UDR v2 (output)

Fixed width: `H` header, `D` detail records, `T` trailer with count, total duration, total volume (KB) and checksum.
BillCore checks file sequence numbers per stream for gaps. Layout in `BillingUdrWriter` javadoc.

## Rating zones

Zones describe the **destination**. BillCore rates on (origin country of the subscriber, zone) — e.g. an
Indonesian subscriber calling `62…` has zone `INTL_ASEAN` but is rated domestic by BillCore.

## State files

| File | Content |
|---|---|
| `state/dedup.ser` | `HashSet` of duplicate keys (all streams, never purged) |
| `state/pending.ser` | `HashMap` charging id → partial records |
| `state/seq_<STREAM>.txt` | Last output file sequence number |

If a `.ser` file can't be read (e.g. after a class change), UMS starts with empty state — see KNOWN_ISSUES.
