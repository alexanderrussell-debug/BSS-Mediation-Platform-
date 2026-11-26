#!/bin/bash
# Shared helpers for the UMS Java 7 -> 21 verification harness. Sourced, not executed.
# See verify/README.md.

VERIFY_DIR=${VERIFY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}
MODULE_DIR=$(cd "$VERIFY_DIR/.." && pwd)
BASELINE_DIR=${BASELINE_DIR:-$VERIFY_DIR/baseline-java7}
TOOLS_DIR=${UMS_VERIFY_TOOLS:-$HOME/.cache/ums-verify}

# ---- deterministic environment -------------------------------------------------------------
# Wall clock is frozen with libfaketime (absolute FAKETIME = clock does not advance). All of
# new Date()/System.currentTimeMillis() in UMS (OutputSet file names, UDR header, DateUtil.today(),
# RecordValidator 90-day/future checks, ControlTotals start/end, SampleCdrGenerator "yesterday",
# SimpleDateFormat 2-digit-year pivot) see these instants. Monotonic clock is left real so JVM
# timers/GC behave normally.
UMS_TZ=${UMS_TZ:-Asia/Singapore}
FAKE_GEN=${FAKE_GEN:-2026-11-20 10:15:00}      # gen-samples (-> sample CDRs dated 2026-11-19 10:00 SGT)
FAKE_CYCLE1=${FAKE_CYCLE1:-2026-11-20 10:30:00} # run-once #1: all generated samples
FAKE_CYCLE2=${FAKE_CYCLE2:-2026-11-20 10:45:00} # run-once #2: restart with persisted state
FAKETIME_LIB=${FAKETIME_LIB:-}

find_faketime() {
    if [ -z "$FAKETIME_LIB" ]; then
        for f in /usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1 /usr/lib/faketime/libfaketime.so.1 \
                 /usr/local/lib/faketime/libfaketime.so.1 /usr/lib64/faketime/libfaketime.so.1; do
            [ -f "$f" ] && FAKETIME_LIB=$f && break
        done
    fi
    if [ -z "$FAKETIME_LIB" ] || [ ! -f "$FAKETIME_LIB" ]; then
        echo "ERROR: libfaketime not found (apt-get install libfaketime / yum install libfaketime, or set FAKETIME_LIB)" >&2
        exit 2
    fi
}

# run_pinned "<faketime>" cmd args...   - runs cmd with frozen clock, fixed TZ/locale, clean JVM env
run_pinned() {
    local ft=$1; shift
    env -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS \
        TZ="$UMS_TZ" LANG=C LC_ALL=C \
        LD_PRELOAD="$FAKETIME_LIB" FAKETIME="$ft" FAKETIME_DONT_FAKE_MONOTONIC=1 \
        "$@"
}

# new_sandbox DIR  - a UMS_HOME with refdata (+ optional conf/ums.properties) and empty data/
new_sandbox() {
    local d=$1
    rm -rf "$d"
    mkdir -p "$d/data" "$d/logs" "$d/conf"
    cp -r "$MODULE_DIR/refdata" "$d/"

    return 0
}

# ums_run_once JAVA_HOME JAR UMS_HOME FAKETIME JVM_OPTS... -- one run-once JVM
# libfaketime + JDK 7 occasionally SIGSEGVs in getenv() from a GC thread while the VM is still
# initialising (hs_err "JRE version: (7.0-b15) (build )"), i.e. before any UMS code ran. Only that
# exact case is retried; any other non-zero exit is a failure.
ums_run_once() {
    local jh=$1 jar=$2 home=$3 ft=$4; shift 4
    local try rc hs
    for try in 1 2 3 4 5; do
        rm -f "$home"/hs_err_pid*.log
        ( cd "$home" && run_pinned "$ft" "$jh/bin/java" "$@" -jar "$jar" run-once ) >> "$home/logs/run-once.out" 2>&1
        rc=$?
        [ $rc -eq 0 ] && return 0
        hs=$(ls "$home"/hs_err_pid*.log 2>/dev/null | head -1)
        if [ -n "$hs" ] && grep -q '^# JRE version: .*(build )' "$hs" && grep -q 'getenv' "$hs"; then
            echo "### JVM crashed during VM init under libfaketime (getenv race) - retry $try" >> "$home/logs/run-once.out"
            mkdir -p "$home/../hs_err"; mv "$hs" "$home/../hs_err/"
            continue
        fi
        return $rc
    done
    return 1
}

# UMS_RUN_MODE=serial (default): one run-once JVM per stream, in ums.streams order, selected with
#   conf/ums.properties "ums.streams=<STREAM>". Needed for byte-for-byte determinism: DateUtil shares
#   one SimpleDateFormat per pattern across the per-stream threads (race -> UMS-433, see docs).
# UMS_RUN_MODE=concurrent: one run-once with all streams, exactly like production (non-deterministic).
UMS_RUN_MODE=${UMS_RUN_MODE:-serial}
UMS_STREAMS=${UMS_STREAMS:-MSC_SG MSC_ID PGW_SG SMSC_SG}

# write_conf UMS_HOME [STREAM]  - conf/ums.properties = $UMS_TEST_CONF (if set) + optional ums.streams
write_conf() {
    local home=$1 s=${2:-}
    : > "$home/conf/ums.properties"
    [ -n "$UMS_TEST_CONF" ] && cat "$UMS_TEST_CONF" >> "$home/conf/ums.properties"
    [ -n "$s" ] && echo "ums.streams=$s" >> "$home/conf/ums.properties"
    return 0
}

# ums_cycle RUNNER... - one mediation cycle; RUNNER is a command taking UMS_HOME as $1 that does run-once
#   (e.g. "ums_run_once_in JH JAR FT OPTS" via the CYCLE_* vars below)
ums_cycle() {
    local home=$1 s; shift
    if [ "$UMS_RUN_MODE" = concurrent ]; then
        write_conf "$home"
        echo "### cycle (concurrent)" >> "$home/logs/run-once.out"
        "$@" || return 1
    else
        for s in $UMS_STREAMS; do
            write_conf "$home" "$s"
            echo "### cycle stream $s" >> "$home/logs/run-once.out"
            "$@" || return 1
        done
    fi
    write_conf "$home"
}

ums_gen_samples() {
    local jh=$1 jar=$2 home=$3 ft=$4
    ( cd "$home" && run_pinned "$ft" "$jh/bin/java" -cp "$jar" com.meridiantel.bss.ums.tools.SampleCdrGenerator ./data ) \
        >> "$home/logs/gen-samples.out" 2>&1
}

# run through a real bin/ums.sh copied into the sandbox (exercises the script's JAVA_OPTS)
ums_sh() {
    local ums_sh=$1 jh=$2 jar=$3 home=$4 ft=$5 cmd=$6
    mkdir -p "$home/bin" "$home/lib"
    cp "$ums_sh" "$home/bin/ums.sh"
    cp "$jar" "$home/lib/ums-mediation-5.2.1-jar-with-dependencies.jar"
    ( cd "$home" && run_pinned "$ft" env UMS_HOME="$home" JAVA_HOME="$jh" sh bin/ums.sh "$cmd" ) >> "$home/logs/$cmd.out" 2>&1
}

# stage_inputs SRC_DIR UMS_HOME   - copy inputs/<cycle>/<STREAM>/* into data/input/<STREAM>/
stage_inputs() {
    local src=$1 home=$2 s
    for s in "$src"/*/; do
        s=$(basename "$s")
        mkdir -p "$home/data/input/$s"
        cp -p "$src/$s"/* "$home/data/input/$s/"
    done
}

# capture UMS_HOME DEST CYCLE_INPUT_DIR - copy comparable artefacts out of a sandbox
#   output/**, audit/* (rows stable-sorted by STREAM, see README), state/seq_*.txt, state/*.ser,
#   archive/** gunzipped (gzip header has mtime -> compare content only), leftover dirs listing.
capture() {
    local home=$1 dest=$2 inputs=$3 f
    rm -rf "$dest"; mkdir -p "$dest"
    [ -d "$home/data/output" ] && cp -r "$home/data/output" "$dest/output"
    mkdir -p "$dest/audit" "$dest/state" "$dest/archive"
    for f in "$home"/data/audit/*.csv; do
        [ -f "$f" ] || continue
        { head -n 1 "$f"; tail -n +2 "$f" | sort -s -t, -k1,1; } > "$dest/audit/$(basename "$f")"
    done
    cp "$home"/data/state/seq_*.txt "$dest/state/" 2>/dev/null
    cp "$home"/data/state/*.ser "$dest/state/" 2>/dev/null
    if [ -d "$home/data/archive" ]; then
        ( cd "$home/data/archive" && find . -name '*.gz' ) | while read -r f; do
            mkdir -p "$dest/archive/$(dirname "$f")"
            gzip -dc "$home/data/archive/$f" > "$dest/archive/${f%.gz}"
        done
    fi
    # anything left in input/, work/, error/ after a cycle (should be empty)
    ( cd "$home/data" && find input work error -type f 2>/dev/null | sort ) > "$dest/leftover-files.txt"
    return 0
}
