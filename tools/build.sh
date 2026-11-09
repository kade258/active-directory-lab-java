#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .build/classes
find src/main/java -name '*.java' -print > .build/sources.txt
javac --release 21 -encoding UTF-8 -d .build/classes @.build/sources.txt
cp -R src/main/resources/. .build/classes/
jar --create --file .build/active-directory-lab.jar --main-class lab.Main -C .build/classes .
echo 'Built .build/active-directory-lab.jar'
