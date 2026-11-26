#!/bin/bash
# tz-check.sh <ums fat jar> [JDK_A] [JDK_B]
# Compiles verify/tz/*.java (Java 7 source) with JDK_A and runs on both runtimes:
#   TzCheck           - tzdata version, offsets, transition history, 2010-2030 quarterly offsets
#   MscIdTzCheck      - UMS DateUtil.parse/format + MscBerDecoder on the baseline MSC_ID BER file
#   DateUtilRaceCheck - concurrent DateUtil use from 4 threads (UMS-433 / RA-2025-007 diagnostic)
# Writes <out>/<prog>_<jdk>.txt and prints a diff of the two runtimes. Default out: /tmp/ums-tz
. "$(dirname "$0")/lib.sh"
JAR=$1; A=${2:-$TOOLS_DIR/zulu1.7.0_80-7.10.0.1-x86lx64}; B=${3:-$TOOLS_DIR/jdk-21}
OUTD=${UMS_TZ_OUT:-/tmp/ums-tz}
[ -f "$JAR" ] || { echo "usage: $0 <fat jar> [JDK_A] [JDK_B]" >&2; exit 2; }
rm -rf "$OUTD"; mkdir -p "$OUTD/classes"
env -u JDK_JAVA_OPTIONS "$A/bin/javac" -nowarn -encoding ISO-8859-1 -cp "$JAR" -d "$OUTD/classes" "$VERIFY_DIR"/tz/*.java 2>&1 | grep -v '^Note:'
BER=$(ls "$BASELINE_DIR"/inputs/cycle1/MSC_ID/*.ber)
for jh in "$A" "$B"; do
    v=$(env -u JDK_JAVA_OPTIONS "$jh/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p')
    for p in TzCheck "MscIdTzCheck $BER" DateUtilRaceCheck; do
        env -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS TZ="$UMS_TZ" LC_ALL=C "$jh/bin/java" -Dfile.encoding=ISO-8859-1 \
            -cp "$OUTD/classes:$JAR" $p > "$OUTD/${p%% *}_$v.txt" 2>&1
    done
    echo "$v" >> "$OUTD/versions"
done
va=$(sed -n 1p "$OUTD/versions"); vb=$(sed -n 2p "$OUTD/versions")
for p in TzCheck MscIdTzCheck DateUtilRaceCheck; do
    echo "===== $p: $va vs $vb"
    if [ $p = DateUtilRaceCheck ]; then cat "$OUTD/${p}_$va.txt" "$OUTD/${p}_$vb.txt"; continue; fi
    diff "$OUTD/${p}_$va.txt" "$OUTD/${p}_$vb.txt" && echo "(identical)"
done
echo "outputs in $OUTD"
