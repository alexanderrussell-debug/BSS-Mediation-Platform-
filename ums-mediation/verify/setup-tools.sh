#!/bin/bash
# Downloads the JDKs + Maven used by the harness into $UMS_VERIFY_TOOLS (default ~/.cache/ums-verify).
# No Docker needed (Docker Hub rate limits made image pulls unreliable). Also needs: libfaketime,
# gzip, python3 + pyftpdlib (ftp-test.sh only).
set -e
. "$(dirname "$0")/lib.sh"
mkdir -p "$TOOLS_DIR"; cd "$TOOLS_DIR"
get() { [ -e "$2" ] || { echo "downloading $1"; curl -fsSL -o "$2.part" "$1" && mv "$2.part" "$2"; }; }
# Zulu 7u80 = same update level as production /usr/java/jdk1.7.0_80 (runtime for the baseline)
get https://cdn.azul.com/zulu/bin/zulu1.7.0_80-7.10.0.1-x86lx64.zip zulu7u80.zip
[ -d zulu1.7.0_80-7.10.0.1-x86lx64 ] || unzip -q zulu7u80.zip
# Zulu 7u352 = last JDK 7 build; used to compile (TLS 1.2 to Maven Central)
get https://cdn.azul.com/zulu/bin/zulu7.56.0.11-ca-jdk7.0.352-linux_x64.tar.gz zulu7u352.tgz
[ -d zulu7.56.0.11-ca-jdk7.0.352-linux_x64 ] || tar xzf zulu7u352.tgz
# Temurin 21 (latest GA)
get "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" temurin21.tgz
if [ ! -e jdk-21 ]; then d=$(tar tzf temurin21.tgz | head -1 | cut -d/ -f1); tar xzf temurin21.tgz; ln -sfn "$d" jdk-21; fi
get https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz maven363.tgz
[ -d apache-maven-3.6.3 ] || tar xzf maven363.tgz
find_faketime
echo "tools ready in $TOOLS_DIR (faketime: $FAKETIME_LIB)"
