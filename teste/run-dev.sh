#!/usr/bin/env bash
#
# run-dev.sh — sobe a aplicação em DEV com flags de JVM voltadas a startup rápido.
#
# Uso:
#   ./run-dev.sh                 # sobe a app
#   ./run-dev.sh --server.port=8080   # argumentos extras são repassados ao Spring Boot
#
# Pré-requisito: o jar precisa estar buildado em target/ (ex.: ./mvnw -DskipTests package).
#
# Por que estas flags:
#   -XX:TieredStopAtLevel=1  -> Para a compilação JIT no nível 1 (C1, sem C2).
#                               Acelera o BOOT porque a JVM gasta menos tempo
#                               compilando/otimizando código que só roda uma vez
#                               no startup. Ótimo para dev (ciclos curtos);
#                               em produção de longa duração prefira o padrão.
#   -XX:+UseParallelGC       -> GC mais simples e barato de inicializar para um
#                               processo dev de curta vida (menos overhead que o G1).
#   -Dspring.jmx.enabled=false -> Reforça o desligamento do JMX no boot.

set -euo pipefail

JAR="target/teste-0.0.1-SNAPSHOT.jar"

if [[ ! -f "$JAR" ]]; then
  echo "ERRO: $JAR não encontrado. Rode primeiro: ./mvnw -DskipTests package" >&2
  exit 1
fi

java \
  -XX:TieredStopAtLevel=1 \
  -XX:+UseParallelGC \
  -Dspring.jmx.enabled=false \
  -jar "$JAR" "$@"

# --- Alternativa ainda mais rápida (use com cuidado) -----------------------------
# Lazy initialization adia a criação de beans até o primeiro uso, deixando o boot
# bem mais rápido. PORÉM a primeira requisição fica mais lenta e, principalmente,
# beans que dependem de inicialização EAGER podem não rodar no startup:
#   - o agendador (@Scheduled dos monitores Defesa Civil / INMET / Gmail)
#   - o registro do webhook do Telegram
# Só habilite se NÃO precisar do agendador/registro de webhook ativos de imediato.
#
# java \
#   -XX:TieredStopAtLevel=1 \
#   -XX:+UseParallelGC \
#   -Dspring.jmx.enabled=false \
#   -Dspring.main.lazy-initialization=true \
#   -jar "$JAR" "$@"
