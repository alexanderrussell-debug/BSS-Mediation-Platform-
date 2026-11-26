#!/bin/sh
# UMS control script. Installed at /opt/ums/bin/ums.sh, run as user 'ums'.
# Usage: ums.sh start|stop|status|run-once|gen-samples
UMS_HOME=${UMS_HOME:-$(cd "$(dirname "$0")/.." && pwd)}
JAVA=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}/bin/java
JAR=$UMS_HOME/lib/ums-mediation-5.2.1-jar-with-dependencies.jar
[ -f $JAR ] || JAR=$UMS_HOME/target/ums-mediation-5.2.1-jar-with-dependencies.jar
PIDFILE=$UMS_HOME/ums.pid
# JDK 21: PermGen (JDK 8) and CMS (JDK 14) are gone - G1. file.encoding must stay ISO-8859-1 (JDK 18+ defaults to UTF-8).
JAVA_OPTS="-Xms2g -Xmx6g -XX:+UseG1GC -Dfile.encoding=ISO-8859-1"

cd $UMS_HOME
mkdir -p logs data

case "$1" in
  start)
    nohup $JAVA $JAVA_OPTS -jar $JAR daemon > logs/ums.out 2>&1 &
    echo $! > $PIDFILE
    echo "UMS started, pid `cat $PIDFILE`"
    ;;
  stop)
    kill `cat $PIDFILE`
    sleep 10
    kill -9 `cat $PIDFILE` 2>/dev/null
    rm -f $PIDFILE
    ;;
  status)
    ps -p `cat $PIDFILE 2>/dev/null` > /dev/null 2>&1 && echo running || echo stopped
    ;;
  run-once)
    $JAVA $JAVA_OPTS -jar $JAR run-once
    ;;
  gen-samples)
    $JAVA -cp $JAR com.meridiantel.bss.ums.tools.SampleCdrGenerator $UMS_HOME/data
    ;;
  *)
    echo "usage: $0 start|stop|status|run-once|gen-samples"; exit 1
    ;;
esac
