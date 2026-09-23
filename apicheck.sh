#!/bin/bash
set -e
JAR=$(readlink -f "${1:?usage: apicheck.sh gtab.jar wtk-dir javap}")
WTK=${2:?wtk dir}
JAVAP=${3:?javap from JDK 8}
cd "$(dirname "$0")"
LIB="$WTK/cldcapi11.jar:$WTK/midpapi20.jar:$WTK/jsr75.jar:$WTK/mmapi.jar"
rm -rf build/chk
mkdir -p build/chk
(cd build/chk && jar xf "$JAR")
API=$(for j in cldcapi11 midpapi20 jsr75 mmapi; do jar tf "$WTK/$j.jar"; done | grep '\.class$' | sed 's/\.class$//; s#/#.#g')
"$JAVAP" -p -s -bootclasspath "$LIB" $API 2>/dev/null | awk '
/^[^ ].*(class|interface) / {
  for (i = 1; i <= NF; i++) {
    if ($i == "class" || $i == "interface") { c = $(i + 1); sub(/<.*/, "", c) }
    if ($i == "extends") { e = $(i + 1); sub(/<.*/, "", e); gsub(/\./, "/", e); gsub(/,/, "", e) }
  }
  d = c; gsub(/\./, "/", c); if (e != "") print "S " c " " e; e = ""; next
}
/descriptor:/ { if (n != "") print "M " c "." n ":" $2; n = ""; next }
/\(/ { m = $0; sub(/\(.*/, "", m); k = split(m, w, " "); n = w[k]; if (n == d) n = "<init>"; next }
' > build/api.txt
BAD=0
for r in $(find build/chk -name '*.class' -exec "$JAVAP" -v {} \; | grep -E '^ +#[0-9]+ = (Interface)?Methodref' | grep -oE '// +(java|javax)/[^ ]+' | awk '{print $2}' | tr -d '"' | sort -u); do
  c=${r%%.*}; m=${r#*.}; ok=0
  while [ -n "$c" ]; do
    grep -qxF "M $c.$m" build/api.txt && { ok=1; break; }
    c=$(grep -m1 "^S $c " build/api.txt | cut -d' ' -f3)
  done
  [ $ok = 1 ] || { echo "not in CLDC/MIDP: $r"; BAD=1; }
done
[ $BAD = 0 ] || exit 1
