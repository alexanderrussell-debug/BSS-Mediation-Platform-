# Known issues

Tracked in JIRA project UMS. Listed here because Revenue Assurance and Ops keep asking.

| Ticket | Issue | Workaround |
|---|---|---|
| UMS-301 | Subscribers activated during the day are unknown until the nightly restart → usage rejected R10 | Ops copies R10 lines back into the input directory the next morning ("recycling"). RA reports recycled volumes don't fully reappear in billing — not yet investigated |
| UMS-355 | Duplicate-key store is never purged; heap raised from 2 GB to 6 GB over the years | Restart nightly. Purge job planned |
| UMS-390 | Data sessions whose final CDR never arrives stay in `pending.ser` forever and are never billed | None. ~200 sessions/month |
| UMS-412 | Adding a field to `UsageRecord` makes `pending.ser` unreadable → pending sessions lost | Release only after draining partials (ops runbook step 7). **Since 5.3.0** UMS refuses to start (`UMS-STATE-LOAD-FAILURE`) instead of silently dropping state, and `Jdk7StateCompatibilityTest` fails the build if `UsageRecord`'s serialVersionUID (`7311000903433512030L`, same on JDK 7 and 21) changes. Java 21 cutover: `docs/JAVA21_CUTOVER.md` |
| UMS-433 | Intermittent R00 / R02 rejects on MSC streams in the busy hour ("Unparseable date", dates in 2002) | Ops recycles them manually. Cause unknown, suspected switch issue |
| UMS-460 | BillCore occasionally raises a file sequence gap alarm for MSC_ID | BillCore team acknowledges the gap manually |
| RA-2024-031 | Revenue Assurance: billed data volume is ~0.4% below PGW interface counters, concentrated on heavy-usage postpaid plans | Under investigation |
| RA-2025-007 | Customer complaints about Makassar call times on itemised bills being one hour off | Billing team adjusts on complaint |
| SEC-2023-118 | Audit finding: FTP (not SFTP) to network elements, plain-text passwords in `ums.properties` and `smsc_pull.pl` | Risk accepted; network VLAN isolated |
| SEC-2024-022 | Scan flags Log4j 1.x, Spring 3.2, Quartz 1.8, Commons Net 2.2 as end-of-life | **Addressed by the JDK 21 migration (5.3.0):** reload4j 1.2.26, Spring 5.3.39, Quartz 2.3.2, Commons Net 3.11.1. Spring 5.3 OSS support ended 2024-08 — a Spring 6 / `jakarta.*` move is the follow-up. Re-scan to close |
| UMS-500 | Build requires JDK 7/8 — the new Jenkins agents (JDK 17) cannot build UMS | **Fixed in 5.3.0:** builds with `<release>21</release>` on JDK 21 (Maven 3.9). Jenkins agents need JDK 21; `bldsrv02` can be retired after cutover. Ops must confirm JDK 21 support on the `ossmed01` OS (RHEL 6 is not a supported JDK 21 platform) |
| UMS-512 | No HA, single VM. Outage > 12h loses SMSC records | DR plan is "restore VM from backup" |
