#!/usr/bin/env bash
# Creates the Ticketly Kafka topics (REQUIREMENTS.md §8.1) inside the running
# kafka container. Idempotent: --if-not-exists makes reruns a no-op, so this
# script is safe to run after every `docker compose up`.
#
# Why pre-create instead of auto-create? Auto-created topics get broker
# defaults (1 partition) — we want 3 partitions on business topics because the
# partition count fixes the max consumer parallelism and, keyed by aggregate
# ID, guarantees per-aggregate ordering (§8.1).
set -euo pipefail

COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"
BOOTSTRAP="localhost:9092"

create() {
  local topic="$1" partitions="$2"
  docker compose -f "$COMPOSE_FILE" exec -T kafka \
    "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor 1
}

# Business topics: 3 partitions (dev), keyed by aggregate ID.
create catalog.events 3
create booking.events 3
create payment.events 3

# Dead-letter topics: 1 partition — ordering no longer matters, volume is low.
create catalog.events.DLT 1
create booking.events.DLT 1
create payment.events.DLT 1

echo
echo "Topics now present:"
docker compose -f "$COMPOSE_FILE" exec -T kafka \
  "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list