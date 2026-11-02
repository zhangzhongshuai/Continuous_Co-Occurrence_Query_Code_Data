#!/usr/bin/env bash
# Compiles the code and the tests into build/. Needs a JDK (8 or later), nothing else.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build
javac -encoding UTF-8 -d build src/owim/*.java test/owim/*.java
echo "compiled into build/"
