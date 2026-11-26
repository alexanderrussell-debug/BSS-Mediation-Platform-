#!/bin/bash
# compare.sh <ums fat jar> [options]
# Replays the committed Java 7 baseline inputs (verify/baseline-java7/inputs) through the given jar on
# a JDK 21 runtime (frozen clock, same scenario as make-baseline.sh) and compares every artefact
# byte-for-byte with the baseline, plus the control totals row by row. Prints PASS/FAIL per file.
# Exit 0 = everything PASS.
#   --jdk DIR        runtime JAVA_HOME (default $UMS_VERIFY_TOOLS/jdk-21)
#   --ums-sh FILE    bin/ums.sh whose JAVA_OPTS are used (default ../bin/ums.sh of this checkout)
#   --opts "..."     JVM options to use instead of the ones parsed from ums.sh
#   --via-ums-sh     run "bin/ums.sh run-once" (copied into the sandbox) instead of java -jar; the
#                    script's own JAVA_OPTS/JAVA path are then used as-is (--opts is ignored)
#   --concurrent     production-style single run-once with all streams (expect DateUtil-race noise)
#   --rollback-jar J Java 7 UMS jar used for the JDK 7 "can the old version still read the new state"
#                    check (default $UMS_VERIFY_TOOLS/ums-java7-baseline.jar, written by make-baseline.sh)
#   --work DIR       scratch dir (default /tmp/ums-compare.$$)

. "$(dirname "$0")/lib.sh"

JAR=; JDK=$TOOLS_DIR/jdk-21; UMS_SH_FILE=$MODULE_DIR/bin/ums.sh; OPTS=; VIA_SH=0
WORK=${UMS_VERIFY_WORK:-/tmp/ums-compare.$$}
while [ $# -gt 0 ]; do
    case $1 in
        --jdk) JDK=$2; shift ;;
        --ums-sh) UMS_SH_FILE=$2; shift ;;
        --opts) OPTS=$2; shift ;;
        --via-ums-sh) VIA_SH=1 ;;
        --concurrent) UMS_RUN_MODE=concurrent ;;
        --work) WORK=$2; shift ;;
        --rollback-jar) ROLLBACK_JAR=$2; shift ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) [ -z "$JAR" ] && JAR=$1 || { echo "unknown arg $1" >&2; exit 2; } ;;
    esac; shift
done
[ -f "$JAR" ] || { echo "usage: $0 <ums-mediation-5.2.1-jar-with-dependencies.jar> [--jdk DIR] ..." >&2; exit 2; }
JAR=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")
[ -x "$JDK/bin/java" ] || { echo "no java in $JDK" >&2; exit 2; }
[ -d "$BASELINE_DIR/inputs" ] || { echo "no baseline in $BASELINE_DIR" >&2; exit 2; }
find_faketime || exit 2

JVER=$(env -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS "$JDK/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p')
JMAJOR=$(echo "$JVER" | awk -F. '{print ($1=="1")?$2:$1}')
if [ -z "$OPTS" ]; then
    OPTS=$(sed -n 's/^[[:space:]]*JAVA_OPTS="\([^"]*\)".*/\1/p' "$UMS_SH_FILE" | head -1)
    [ -n "$OPTS" ] || { echo "could not parse JAVA_OPTS from $UMS_SH_FILE" >&2; exit 2; }
    if [ "$JMAJOR" -ge 9 ]; then
        STRIPPED=
        for o in $OPTS; do
            case $o in -XX:MaxPermSize=*|-XX:PermSize=*|-XX:+UseConcMarkSweepGC|-XX:+CMSClassUnloadingEnabled)
                STRIPPED="$STRIPPED $o" ;; esac
        done
        if [ -n "$STRIPPED" ]; then
            echo "NOTE: $UMS_SH_FILE still has JDK 7-only flags:$STRIPPED - removed for JDK $JMAJOR (PermGen gone in 8, CMS gone in 14)"
            NEW=; for o in $OPTS; do case " $STRIPPED " in *" $o "*) ;; *) NEW="$NEW $o" ;; esac; done
            OPTS="${NEW# } -XX:+UseG1GC"
        fi
    fi
fi
case " $OPTS " in *" -Dfile.encoding=ISO-8859-1 "*) ;; *) echo "WARNING: options lack -Dfile.encoding=ISO-8859-1 (JDK 18+ default is UTF-8)";; esac

echo "== UMS Java 21 regression compare"
echo "jar      : $JAR ($(sha256sum "$JAR" | cut -c1-16)..., Main.class bytecode $(unzip -p "$JAR" com/meridiantel/bss/ums/Main.class | od -An -j6 -N2 -tu1 | awk '{print $1*256+$2}'))"
echo "runtime  : $JDK (java $JVER)"
echo "options  : $OPTS"
echo "mode     : $UMS_RUN_MODE$([ $VIA_SH = 1 ] && echo ', via bin/ums.sh run-once')"
echo "baseline : $BASELINE_DIR ($(grep '^run.jdk=' "$BASELINE_DIR/MANIFEST.txt" | cut -d= -f2-))"
echo "clock    : gen=$FAKE_GEN cycle1=$FAKE_CYCLE1 cycle2=$FAKE_CYCLE2 TZ=$UMS_TZ LC_ALL=C"
echo "work     : $WORK"
echo "rollback : .ser files also read back on ${ROLLBACK_JDK:-$TOOLS_DIR/zulu1.7.0_80-7.10.0.1-x86lx64} with ${ROLLBACK_JAR:-$TOOLS_DIR/ums-java7-baseline.jar}"

rm -rf "$WORK"; mkdir -p "$WORK/cand"
home=$WORK/home
new_sandbox "$home"; write_conf "$home"
run1() {   # run1 FAKETIME
    if [ $VIA_SH = 1 ]; then
        ums_cycle "$home" ums_sh "$UMS_SH_FILE" "$JDK" "$JAR" "$home" "$1" run-once
    else
        ums_cycle "$home" ums_run_once "$JDK" "$JAR" "$home" "$1" $OPTS
    fi
}
RUNFAIL=0
stage_inputs "$BASELINE_DIR/inputs/cycle1" "$home"
run1 "$FAKE_CYCLE1" || { echo "FAIL  run-once cycle1 exited non-zero (see $home/logs)"; RUNFAIL=1; }
capture "$home" "$WORK/cand/cycle1"
stage_inputs "$BASELINE_DIR/inputs/cycle2" "$home"
run1 "$FAKE_CYCLE2" || { echo "FAIL  run-once cycle2 exited non-zero (see $home/logs)"; RUNFAIL=1; }
capture "$home" "$WORK/cand/cycle2"
cat "$home"/logs/*.out > "$WORK/run.log" 2>/dev/null

# cutover: restart on the target runtime on top of the state/output/audit/archive the JDK 7 baseline
# left after cycle 1 (= deploy Java 21 between two cycles), then cycle 2 must match the baseline
cut=$WORK/cutover; new_sandbox "$cut"; write_conf "$cut"
mkdir -p "$cut/data/state" "$cut/data/audit" "$cut/data/archive"
cp -r "$BASELINE_DIR/cycle1/output" "$cut/data/output"
cp "$BASELINE_DIR"/cycle1/state/* "$cut/data/state/"
cp "$BASELINE_DIR"/cycle1/audit/* "$cut/data/audit/"
( cd "$BASELINE_DIR/cycle1/archive" && find . -type f ) | while read -r f; do
    mkdir -p "$cut/data/archive/$(dirname "$f")"; gzip -c "$BASELINE_DIR/cycle1/archive/$f" > "$cut/data/archive/$f.gz"
done
stage_inputs "$BASELINE_DIR/inputs/cycle2" "$cut"
home=$cut run1 "$FAKE_CYCLE2" || { echo "FAIL  cutover run-once exited non-zero (see $cut/logs)"; RUNFAIL=1; }
capture "$cut" "$WORK/cand/cutover"

# .ser files: content comparison (see verify/java/SerCompare.java), on the target runtime and on JDK 7
SERC=$WORK/serc; mkdir -p "$SERC"
env -u JDK_JAVA_OPTIONS "$JDK/bin/javac" --release 8 -nowarn -d "$SERC" "$VERIFY_DIR/java/SerCompare.java" 2>/dev/null \
  || env -u JDK_JAVA_OPTIONS "$JDK/bin/javac" -nowarn -d "$SERC" "$VERIFY_DIR/java/SerCompare.java" >/dev/null 2>&1
ROLLBACK_JDK=${ROLLBACK_JDK:-$TOOLS_DIR/zulu1.7.0_80-7.10.0.1-x86lx64}
ROLLBACK_JAR=${ROLLBACK_JAR:-$TOOLS_DIR/ums-java7-baseline.jar}
[ -f "$ROLLBACK_JAR" ] || ROLLBACK_JAR=$JAR
SERC7=$WORK/serc7; mkdir -p "$SERC7"
[ -x "$ROLLBACK_JDK/bin/javac" ] && env -u JDK_JAVA_OPTIONS "$ROLLBACK_JDK/bin/javac" -nowarn -d "$SERC7" "$VERIFY_DIR/java/SerCompare.java" >/dev/null 2>&1
: > "$WORK/ser.txt"
for pair in cycle1:cycle1 cycle2:cycle2 cycle2:cutover; do
    b=${pair%%:*}; c=${pair#*:}
    for f in dedup.ser pending.ser; do
        [ -f "$BASELINE_DIR/$b/state/$f" ] && [ -f "$WORK/cand/$c/state/$f" ] || continue
        m=$(env -u JDK_JAVA_OPTIONS "$JDK/bin/java" -cp "$SERC:$JAR" SerCompare "$BASELINE_DIR/$b/state/$f" "$WORK/cand/$c/state/$f"); r=$?
        if [ -x "$ROLLBACK_JDK/bin/java" ] && [ -f "$SERC7/SerCompare.class" ] && [ "$ROLLBACK_JDK" != "$JDK" ]; then
            m2=$(env -u JDK_JAVA_OPTIONS "$ROLLBACK_JDK/bin/java" -cp "$SERC7:$ROLLBACK_JAR" SerCompare "$BASELINE_DIR/$b/state/$f" "$WORK/cand/$c/state/$f"); r2=$?
            [ $r2 -ne 0 ] && r=$r2; m="$m; $m2"
        fi
        echo "$c/state/$f|$r|$m" >> "$WORK/ser.txt"
    done
done

# SampleCdrGenerator on the target runtime must still produce the baseline inputs (encoding/BER/date check)
gen=$WORK/gen; new_sandbox "$gen"
ums_gen_samples "$JDK" "$JAR" "$gen" "$FAKE_GEN"
mkdir -p "$WORK/cand/inputs"; cp -r "$gen/data/input" "$WORK/cand/inputs/cycle1"

python3 - "$BASELINE_DIR" "$WORK/cand" "$RUNFAIL" "$WORK/ser.txt" <<'PY'
import os, sys, csv, difflib
base, cand, runfail, serf = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
ser = {}
for l in open(serf):
    k, r, m = l.rstrip("\n").split("|", 2); ser[k] = (int(r), m)
def files(root, sub):
    out = set(); top = os.path.join(root, sub)
    for d, _, fs in os.walk(top):
        for f in fs: out.add(os.path.relpath(os.path.join(d, f), top))
    return out
npass = nfail = nsem = 0
def res(ok, label, detail="", tag=None):
    global npass, nfail
    if ok: npass += 1
    else: nfail += 1
    print("%-5s %s%s" % (tag or ("PASS" if ok else "FAIL"), label, ("  " + detail) if detail else ""))
def first_diff(a, b):
    if b'\x00' in a[:4096] or a[:2] == b'\xac\xed':
        n = next((i for i in range(min(len(a), len(b))) if a[i] != b[i]), min(len(a), len(b)))
        return "binary differs at byte %d (sizes %d vs %d)" % (n, len(a), len(b))
    la, lb = a.decode('latin-1').splitlines(), b.decode('latin-1').splitlines()
    d = [l for l in difflib.unified_diff(la, lb, 'java7', 'candidate', n=0, lineterm='') if not l.startswith(('---', '+++', '@@'))]
    return " | ".join(d[:4])
def rows(p):
    if not os.path.exists(p): return [], {}
    r = list(csv.reader(open(p, encoding='latin-1')))
    hdr, seen, out = r[0], {}, {}
    for row in r[1:]:
        k = (row[0], row[1]); seen[k] = seen.get(k, 0) + 1
        out[k + (seen[k],)] = row
    return hdr, out
for title, bsub, csub in (("generated inputs (SampleCdrGenerator on target JDK vs JDK 7)", "inputs/cycle1", "inputs/cycle1"),
                          ("cycle 1 (fresh state)", "cycle1", "cycle1"),
                          ("cycle 2 (restart on own state: dedup.ser/pending.ser/seq reloaded)", "cycle2", "cycle2"),
                          ("cutover: cycle 2 on target JDK over JDK 7-written state/output (vs baseline cycle2)", "cycle2", "cutover")):
    print("\n-- %s" % title)
    allf = sorted(files(base, bsub) | files(cand, csub))
    for rel in allf:
        pb, pc = os.path.join(base, bsub, rel), os.path.join(cand, csub, rel)
        label = "%s/%s" % (csub, rel)
        if not os.path.exists(pc): res(False, label, "MISSING in candidate"); continue
        if not os.path.exists(pb): res(False, label, "EXTRA in candidate (not in baseline)"); continue
        a, b = open(pb, 'rb').read(), open(pc, 'rb').read()
        if a == b: res(True, label); continue
        if rel.endswith('.ser') and label in ser:
            r, m = ser[label]
            if r == 0:
                nsem += 1
                res(True, label, "bytes differ (HashSet/HashMap bucket layout+order); " + m, "PASS*")
            else:
                res(False, label, first_diff(a, b) + "; " + m)
            continue
        res(False, label, first_diff(a, b))
    for ct in sorted(f for f in files(base, bsub) | files(cand, csub) if f.startswith('audit/')):
        hb, rb = rows(os.path.join(base, bsub, ct)); hc, rc = rows(os.path.join(cand, csub, ct))
        for k in sorted(set(rb) | set(rc)):
            label = "control totals %s %s/%s#%d" % (csub, k[0], k[1], k[2])
            if k not in rb or k not in rc: res(False, label, "row missing in " + ("candidate" if k not in rc else "baseline")); continue
            d = ["%s %s->%s" % (hb[i], rb[k][i], rc[k][i]) for i in range(len(hb)) if rb[k][i] != rc[k][i]]
            res(not d, label, "; ".join(d))
            if not d: print("        " + ",".join("%s=%s" % (hb[i], rb[k][i]) for i in range(2, len(hb))))
if runfail: nfail += 1
print("\n== RESULT: %s  (%d PASS of which %d PASS* = .ser content-identical, %d FAIL)" % ("PASS" if nfail == 0 else "FAIL", npass, nsem, nfail))
print("   PASS* = Java-serialized state file: bytes differ only because JDK 8+ HashMap orders/sizes buckets differently;")
print("           deserialised content is identical and readable on both the target JDK and JDK 7 (rollback).")
sys.exit(0 if nfail == 0 else 1)
PY
rc=$?
W=$(grep -c 'VM warning\|WARNING' "$WORK/run.log" 2>/dev/null); E=$(grep -c ' ERROR ' "$WORK/run.log" 2>/dev/null)
echo "jvm/app log: $W warning line(s), $E ERROR line(s) in $WORK/run.log"
grep 'VM warning' "$WORK/run.log" | sort -u | head -5
exit $rc
