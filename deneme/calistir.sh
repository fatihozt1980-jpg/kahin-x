#!/bin/sh
# Çeviri motorunu (Motor.java) telefonsuz, sahte oyun ekranlarıyla dener.
# Kullanım: ANDROID_JAR=/yol/android.jar deneme/calistir.sh   (Android SDK'daki platforms/android-34/android.jar)
set -e
D=$(cd "$(dirname "$0")" && pwd)
S="$D/../app/src/main/java/app/anindaceviri"
C=$(mktemp -d)
javac -proc:none -encoding UTF-8 -cp "$ANDROID_JAR" -d "$C" $(find "$D/sahte" -name "*.java") \
  "$S/Motor.java" "$S/Etiket.java" "$S/Fark.java" "$S/Metin.java" "$S/Blok.java" "$D/sim/Sim.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$C:$ANDROID_JAR" app.anindaceviri.Sim
