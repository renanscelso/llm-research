#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# .env opcional — a análise só lê results.jsonl (não chama APIs).
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

INPUT_DIR="${1:-}"

SPRING_ARGS=(
  "--research.runner.enabled=false"
  "--research.analysis.enabled=true"
)

if [[ -n "$INPUT_DIR" ]]; then
  SPRING_ARGS+=("--research.analysis.input-directory=${INPUT_DIR}")
  echo "Analisando pasta: ${INPUT_DIR}"
else
  echo "Analisando a execução mais recente em ./output (com results.jsonl)."
fi

# Junta argumentos para o processo Spring Boot (não propriedades Maven soltas).
JOINED="$(printf '%s ' "${SPRING_ARGS[@]}")"
JOINED="${JOINED% }"

exec bash ./mvnw spring-boot:run "-Dspring-boot.run.arguments=${JOINED}"