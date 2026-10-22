#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "Arquivo .env não encontrado. Copie .env.example e preencha as chaves."
  exit 1
fi

# Carrega variáveis locais sem imprimir valores
set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

echo "Variáveis carregadas do .env (valores ocultos)."
echo "Provedores ainda precisam estar enabled=true no application.yml."
exec bash ./mvnw spring-boot:run "$@"
