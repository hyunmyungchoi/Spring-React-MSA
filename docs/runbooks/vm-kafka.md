# VM Kafka runbook

The local Kafka broker runs inside the Ubuntu VM. Windows Docker Desktop is not required.

## Start Kafka

Run these commands in the VM from the checked-out repository:

```bash
cd infra/vm
chmod +x kafka-up.sh
KAFKA_ADVERTISED_HOST=192.168.56.101 ./kafka-up.sh
```

The script starts Apache Kafka 4.3.1 in single-node KRaft mode, waits for the broker, and creates all application and DLT topics.

## Check the broker

```bash
docker ps --filter name=springmsa-kafka
docker exec springmsa-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

Expected Windows-side settings in `infra/vm/.env.local`:

```dotenv
APP_KAFKA_ENABLED=true
SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.56.101:9092
```

The producer services and Member BFF can still start without Kafka by setting `APP_KAFKA_ENABLED=false`. Domain writes continue to record Outbox rows, and the relay publishes them after Kafka is enabled again.

## Stop Kafka

```bash
docker compose -f infra/vm/kafka-compose.yml stop kafka
```

Use `down` only when the Compose network should also be removed. The named `kafka-data` volume is intentionally retained.
