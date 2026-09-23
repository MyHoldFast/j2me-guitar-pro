#!/bin/bash
set -e
cd "$(dirname "$0")"

JAVAC=${JAVAC:-javac}
WTK=${WTK:?WTK=dir with cldcapi11.jar midpapi20.jar jsr75.jar mmapi.jar}
PROGUARD=${PROGUARD:?PROGUARD=path to proguard.jar}
JAVA=${JAVA:-java}
JAVAP=${JAVAP:-$(dirname "$(command -v "$JAVAC")")/javap}
VERSION=1.0
SERVER=${SERVER:-94.159.98.141:7000}
LIB="$WTK/cldcapi11.jar:$WTK/midpapi20.jar:$WTK/jsr75.jar:$WTK/mmapi.jar"

rm -rf build out
mkdir -p build/cls build/stub out

"$JAVAC" -source 1.3 -target 1.1 -nowarn -bootclasspath "$LIB" -d build/stub stub/com/nokia/mid/ui/*.java
"$JAVAC" -encoding UTF-8 -source 1.3 -target 1.1 -nowarn -bootclasspath "$LIB:build/stub" -d build/cls src/gtab/*.java

cat > build/MANIFEST.MF <<MF
MIDlet-1: GTab,/icon.png,gtab.App
MIDlet-Icon: /icon.png
Nokia-MIDlet-On-Screen-Keypad: no
MIDlet-Name: GTab
MIDlet-Vendor: HoldFast
MIDlet-Version: $VERSION
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MIDlet-Permissions-Opt: javax.microedition.io.Connector.file.read, javax.microedition.io.Connector.file.write, javax.microedition.io.Connector.socket
GTab-Server: $SERVER
MF

cp res/* build/cls/
(cd build/cls && jar cfm ../raw.jar ../MANIFEST.MF .)

cat > build/pg.pro <<PG
-injars $PWD/build/raw.jar
-outjars $PWD/out/gtab.jar
-libraryjars $WTK/cldcapi11.jar
-libraryjars $WTK/midpapi20.jar
-libraryjars $WTK/jsr75.jar
-libraryjars $WTK/mmapi.jar
-libraryjars $PWD/build/stub
-microedition
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 3
-optimizations !code/simplification/object
-dontnote
-keep public class gtab.App
-keep,allowobfuscation class gtab.Light { static void on(); }
PG

"$JAVA" -jar "$PROGUARD" @build/pg.pro > build/pg.log || { tail -20 build/pg.log; exit 1; }

./apicheck.sh out/gtab.jar "$WTK" "$JAVAP"

SIZE=$(stat -c %s out/gtab.jar)
{ cat build/MANIFEST.MF; echo "MIDlet-Jar-URL: gtab.jar"; echo "MIDlet-Jar-Size: $SIZE"; } > out/gtab.jad
echo "out/gtab.jar $SIZE"
