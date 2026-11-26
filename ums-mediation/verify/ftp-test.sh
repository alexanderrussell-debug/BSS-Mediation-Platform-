#!/bin/bash
# ftp-test.sh <ums fat jar> [--jdk DIR] [--opts "..."] [--work DIR]
# FTP collection end-to-end: starts a local pyftpdlib server (127.0.0.1, throw-away user/password),
# stages the baseline MSC_ID BER file (+ a non-matching file and a sub-directory that must be left
# alone), runs one run-once of stream MSC_ID with conf/ums.properties collector=FTP and checks:
# file downloaded (RETR), deleted remotely (DELE), processed, archived, outputs + control totals
# byte-identical to the LOCAL-collector Java 7 baseline; then an empty poll and a bad-password poll.
# Exit 0 = all PASS.  Needs: python3 -m pip install pyftpdlib
. "$(dirname "$0")/lib.sh"

JAR=; JDK=$TOOLS_DIR/jdk-21; OPTS="-Xms2g -Xmx6g -Dfile.encoding=ISO-8859-1"
WORK=${UMS_VERIFY_WORK:-/tmp/ums-ftp.$$}
while [ $# -gt 0 ]; do
    case $1 in
        --jdk) JDK=$2; shift ;; --opts) OPTS=$2; shift ;; --work) WORK=$2; shift ;;
        -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
        *) JAR=$1 ;;
    esac; shift
done
[ -f "$JAR" ] || { echo "usage: $0 <fat jar> [--jdk DIR]" >&2; exit 2; }
JAR=$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")
python3 -c 'import pyftpdlib' 2>/dev/null || { echo "pyftpdlib missing: python3 -m pip install pyftpdlib" >&2; exit 2; }
find_faketime || exit 2

NETVER=$(unzip -p "$JAR" META-INF/maven/commons-net/commons-net/pom.properties 2>/dev/null | sed -n 's/^version=//p')
JVER=$(env -u JDK_JAVA_OPTIONS "$JDK/bin/java" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p')
echo "== UMS FTP collector test: jar commons-net ${NETVER:-?}, java $JVER, opts: $OPTS"

rm -rf "$WORK"; mkdir -p "$WORK"
ROOT=$WORK/ftproot; REMOTE=/export/cdr/out
mkdir -p "$ROOT$REMOTE/archive.d"
cp "$BASELINE_DIR"/inputs/cycle1/MSC_ID/*.ber "$ROOT$REMOTE/"
echo "not a CDR" > "$ROOT$REMOTE/README.txt"
BER=$(cd "$BASELINE_DIR/inputs/cycle1/MSC_ID" && ls *.ber)
PORT=$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1])')
FTPUSER=umstest; FTPPASS=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
python3 -m pyftpdlib -i 127.0.0.1 -p "$PORT" -w -d "$ROOT" -u "$FTPUSER" -P "$FTPPASS" -r 61000-61100 > "$WORK/ftpd.log" 2>&1 &
FTPD=$!
trap 'kill $FTPD 2>/dev/null' EXIT
for i in $(seq 50); do python3 -c "import socket;socket.create_connection(('127.0.0.1',$PORT),1)" 2>/dev/null && break; sleep 0.2; done

conf() {  # conf PASSWORD
    cat > "$WORK/test.properties" <<P
stream.MSC_ID.collector=FTP
stream.MSC_ID.ftp.host=127.0.0.1
stream.MSC_ID.ftp.port=$PORT
stream.MSC_ID.ftp.user=$FTPUSER
stream.MSC_ID.ftp.password=$1
stream.MSC_ID.ftp.remoteDir=$REMOTE
P
}
NP=0; NF=0
check() { if eval "$2"; then echo "PASS  $1"; NP=$((NP+1)); else echo "FAIL  $1"; NF=$((NF+1)); fi; }

home=$WORK/home; new_sandbox "$home"
export UMS_TEST_CONF=$WORK/test.properties
conf "$FTPPASS"; UMS_STREAMS=MSC_ID
ums_cycle "$home" ums_run_once "$JDK" "$JAR" "$home" "$FAKE_CYCLE1" $OPTS; rc=$?
check "run-once exit 0" "[ $rc -eq 0 ]"
check "UMS logged 'collected 1 files via FTP'" "grep -q 'MSC_ID: collected 1 files via FTP from 127.0.0.1' '$home/logs/ums.log'"
check "server saw RETR $REMOTE/$BER" "grep -q 'RETR .*$REMOTE/$BER' '$WORK/ftpd.log'"
check "server saw DELE $REMOTE/$BER" "grep -q 'DELE .*$REMOTE/$BER' '$WORK/ftpd.log'"
check "remote $BER deleted" "[ ! -e '$ROOT$REMOTE/$BER' ]"
check "non-matching README.txt left on server" "[ -e '$ROOT$REMOTE/README.txt' ] && ! grep -q 'DELE.*README' '$WORK/ftpd.log'"
check "sub-directory left on server" "[ -d '$ROOT$REMOTE/archive.d' ]"
check "downloaded file processed + archived" "[ -e '$home/data/archive/MSC_ID/$BER.gz' ] && gzip -dc '$home/data/archive/MSC_ID/$BER.gz' | cmp -s - '$BASELINE_DIR/inputs/cycle1/MSC_ID/$BER'"
check "work/ and input/ empty after cycle" "[ -z \"\$(find '$home/data/work' '$home/data/input' -type f 2>/dev/null)\" ]"
for f in $(cd "$BASELINE_DIR/cycle1/output" && find . -type f -name '*MSC_ID*' | sort); do
    check "output ${f#./} identical to LOCAL-collector Java 7 baseline" "cmp -s '$BASELINE_DIR/cycle1/output/$f' '$home/data/output/$f'"
done
check "no extra MSC_ID output files" "[ \"\$(cd '$home/data/output' && find . -type f | sort)\" = \"\$(cd '$BASELINE_DIR/cycle1/output' && find . -type f -name '*MSC_ID*' | sort)\" ]"
check "seq_MSC_ID.txt identical to baseline" "cmp -s '$BASELINE_DIR/cycle1/state/seq_MSC_ID.txt' '$home/data/state/seq_MSC_ID.txt'"
check "control totals row identical to baseline" "[ \"\$(grep '^MSC_ID,' '$home'/data/audit/*.csv)\" = \"\$(grep '^MSC_ID,' '$BASELINE_DIR'/cycle1/audit/*.csv)\" ]"

# second poll: nothing new on the server
ums_cycle "$home" ums_run_once "$JDK" "$JAR" "$home" "$FAKE_CYCLE2" $OPTS; rc=$?
check "empty poll: exit 0, 'collected 0 files via FTP'" "[ $rc -eq 0 ] && grep -q 'MSC_ID: collected 0 files via FTP' '$home/logs/ums.log'"

# bad credentials: must log the login failure and process nothing (file stays on the server)
cp "$BASELINE_DIR"/inputs/cycle1/MSC_ID/*.ber "$ROOT$REMOTE/"
conf "wrong-$FTPPASS"
ums_cycle "$home" ums_run_once "$JDK" "$JAR" "$home" "$FAKE_CYCLE2" $OPTS; rc=$?
echo "      (bad-password run-once exit code: $rc)"
check "bad password: 'FTP login failed' logged, remote file kept" "grep -q 'FTP login failed for $FTPUSER@127.0.0.1' '$home/logs/ums.log' && [ -e '$ROOT$REMOTE/$BER' ]"

echo "--- ftpd commands seen:"; grep -oE '(USER|PASS|RETR|DELE|LIST|MLSD|STOR) [^ ]*|failed login' "$WORK/ftpd.log" | sed 's/^PASS .*/PASS ****/' | sort | uniq -c
echo "== RESULT: $([ $NF -eq 0 ] && echo PASS || echo FAIL) ($NP PASS, $NF FAIL)  commons-net ${NETVER:-?} on java $JVER  (logs: $WORK)"
[ $NF -eq 0 ]
