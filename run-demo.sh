#!/bin/bash
set -e
./gradlew :terminus-demo:shadowJar --quiet
echo "Starting Terminus demo... (Ctrl+C or Ctrl+Q to quit)"
java --enable-preview -jar terminus-demo/build/libs/terminus-demo.jar