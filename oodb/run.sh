#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mvn -q -f oodb/pom.xml -DskipTests package
mvn -q -f oodb/pom.xml dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/oodb/target/classpath.txt"

CP="$ROOT/oodb/target/classes:$(cat "$ROOT/oodb/target/classpath.txt")"
AGENT="$(tr ':' '\n' < "$ROOT/oodb/target/classpath.txt" | grep 'objectdb-.*\.jar' | head -1)"

exec java -javaagent:"$AGENT" -cp "$CP" vn.edu.bom.oodb.cli.OodbApplication "$@"
