#!/bin/bash
# Builds UMS from a git ref (default: main) with a real JDK 7, runs the two-cycle regression
# scenario on a JDK 7 runtime with a frozen clock and writes the result to verify/baseline-java7/.
#
#   verify/make-baseline.sh [--ref main] [--build-jdk DIR] [--run-jdk DIR] [--jar JAR] [--out DIR]
#
# Defaults (see setup-tools.sh): build with Zulu 7u352 (TLS 1.2 to Maven Central works),
# run with Zulu 7u80 (same update level / tzdata as production jdk1.7.0_80 on ossmed01).
set -e
. "$(dirname "$0")/lib.sh"

REF=main
BUILD_JDK=$TOOLS_DIR/zulu7.56.0.11-ca-jdk7.0.352-linux_x64
RUN_JDK=$TOOLS_DIR/zulu1.7.0_80-7.10.0.1-x86lx64
MVN=${MVN:-$TOOLS_DIR/apache-maven-3.6.3/bin/mvn}
JAR=
OUT=$BASELINE_DIR
while [ $# -gt 0 ]; do
    case "$1" in
        --ref) REF=$2; shift 2 ;;
        --build-jdk) BUILD_JDK=$2; shift 2 ;;
        --run-jdk) RUN_JDK=$2; shift 2 ;;
        --jar) JAR=$2; shift 2 ;;
        --out) OUT=$2; shift 2 ;;
        *) echo "unknown option $1"; exit 1 ;;
    esac
done
find_faketime
# exactly the JAVA_OPTS of bin/ums.sh on main (JDK 7)
JAVA7_OPTS="-Xms2g -Xmx6g -XX:MaxPermSize=256m -XX:+UseConcMarkSweepGC -Dfile.encoding=ISO-8859-1"
WORK=${UMS_VERIFY_WORK:-$(mktemp -d /tmp/ums-baseline.XXXXXX)}
mkdir -p "$WORK"
echo "work dir: $WORK"

SRC_COMMIT=$(git -C "$MODULE_DIR" rev-parse "$REF")
if [ -z "$JAR" ]; then
    echo "== building $REF ($SRC_COMMIT) with $BUILD_JDK"
    rm -rf "$WORK/src"
    git -C "$MODULE_DIR" worktree add --detach "$WORK/src" "$SRC_COMMIT" > /dev/null
    ( cd "$WORK/src/ums-mediation" && env -u JDK_JAVA_OPTIONS JAVA_HOME="$BUILD_JDK" "$MVN" -B -q \
        -Dhttps.protocols=TLSv1.2 ${MVN_ARGS} package ) | tail -20
    JAR=$WORK/ums-mediation-5.2.1-jar-with-dependencies.jar
    cp "$WORK/src/ums-mediation/target/ums-mediation-5.2.1-jar-with-dependencies.jar" "$JAR"
    git -C "$MODULE_DIR" worktree remove --force "$WORK/src"
fi

run_scenario() {   # $1 = sandbox, $2 = capture dir
    local home=$1 cap=$2
    new_sandbox "$home"
    write_conf "$home"
    ums_gen_samples "$RUN_JDK" "$JAR" "$home" "$FAKE_GEN"
    rm -rf "$cap/inputs/cycle1"; mkdir -p "$cap/inputs"
    cp -r "$home/data/input" "$cap/inputs/cycle1"

    # cycle 2 inputs: switch re-sends both MSC files (must all be dropped as duplicates via
    # dedup.ser) + the final partial of PGW session 10009 that cycle 1 left in pending.ser
    if [ ! -d "$cap/inputs/cycle2" ]; then
        mkdir -p "$cap/inputs/cycle2/MSC_SG" "$cap/inputs/cycle2/MSC_ID" "$cap/inputs/cycle2/PGW_SG"
        cp "$cap"/inputs/cycle1/MSC_SG/* "$cap/inputs/cycle2/MSC_SG/"
        cp "$cap"/inputs/cycle1/MSC_ID/* "$cap/inputs/cycle2/MSC_ID/"
        local stamp
        stamp=$(ls "$cap"/inputs/cycle1/PGW_SG/ | head -1 | sed 's/^PGW_SG01_\([0-9]*\)_.*/\1/')
        {
            head -n 1 "$cap"/inputs/cycle1/PGW_SG/*_0001.csv
            echo "PGW,10009,2,525770000000006,6591000006,internet,${stamp:0:4}-${stamp:4:2}-${stamp:6:2} 10:55:00,600,5242880,47185920,0,52577,NR"
        } > "$cap/inputs/cycle2/PGW_SG/PGW_SG01_${stamp}_0003.csv"
    fi

    ums_cycle "$home" ums_run_once "$RUN_JDK" "$JAR" "$home" "$FAKE_CYCLE1" $JAVA7_OPTS
    capture "$home" "$cap/cycle1"
    stage_inputs "$cap/inputs/cycle2" "$home"
    ums_cycle "$home" ums_run_once "$RUN_JDK" "$JAR" "$home" "$FAKE_CYCLE2" $JAVA7_OPTS
    capture "$home" "$cap/cycle2"
}

echo "== run 1 on $RUN_JDK"
rm -rf "$OUT"; mkdir -p "$OUT"
run_scenario "$WORK/home1" "$OUT"
cp "$WORK/home1/logs/run-once.out" "$OUT/run-once.log"

echo "== run 2 (determinism self-check)"
mkdir -p "$WORK/cap2"; cp -r "$OUT/inputs" "$WORK/cap2/"
run_scenario "$WORK/home2" "$WORK/cap2"
if diff -r "$OUT/cycle1" "$WORK/cap2/cycle1" && diff -r "$OUT/cycle2" "$WORK/cap2/cycle2" \
        && diff -r "$OUT/inputs" "$WORK/cap2/inputs"; then
    echo "determinism self-check: PASS (two JDK 7 runs byte-identical)"
else
    echo "determinism self-check: FAIL"; exit 1
fi

SUID=$(env -u JDK_JAVA_OPTIONS "$RUN_JDK/bin/serialver" -classpath "$JAR" com.meridiantel.bss.ums.core.UsageRecord | awk '{print $NF}')
{
    echo "source.ref=$REF"
    echo "source.commit=$SRC_COMMIT"
    echo "build.jdk=$(env -u JDK_JAVA_OPTIONS "$BUILD_JDK/bin/java" -version 2>&1 | sed -n 2p)"
    echo "run.jdk=$(env -u JDK_JAVA_OPTIONS "$RUN_JDK/bin/java" -version 2>&1 | sed -n 2p)"
    echo "run.java_opts=$JAVA7_OPTS"
    echo "jar.sha256=$(sha256sum "$JAR" | awk '{print $1}')"
    echo "jar.Main.class.bytecode=$(unzip -p "$JAR" com/meridiantel/bss/ums/Main.class | od -An -j6 -N2 -tu1 | awk '{print $1*256+$2}')"
    echo "UsageRecord.serialVersionUID(computed,serialver)=$SUID"
    echo "TZ=$UMS_TZ LANG=C LC_ALL=C"
    echo "UMS_RUN_MODE=$UMS_RUN_MODE streams=$UMS_STREAMS"
    echo "FAKE_GEN=$FAKE_GEN"
    echo "FAKE_CYCLE1=$FAKE_CYCLE1"
    echo "FAKE_CYCLE2=$FAKE_CYCLE2"
} > "$OUT/MANIFEST.txt"
( cd "$OUT" && find inputs cycle1 cycle2 -type f | sort | xargs sha256sum ) > "$OUT/SHA256SUMS"
cp "$JAR" "$TOOLS_DIR/ums-java7-baseline.jar"   # used by compare.sh for the JDK 7 rollback read check
cat "$OUT/MANIFEST.txt"
echo "baseline written to $OUT"
