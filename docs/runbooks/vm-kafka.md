# Kafka VM 런북

Kafka는 Kubernetes 밖의 전용 VM에서 실행한다.

- Host: kafka-1
- IP: 192.168.147.131
- Mode: 단일 KRaft broker/controller
- Container: springmsa-kafka

단일 broker이므로 HA가 아니다.

## 시작과 확인

~~~bash
cd ~/kafka
chmod +x kafka-up.sh
KAFKA_ADVERTISED_HOST=192.168.147.131 ./kafka-up.sh
docker ps --filter name=springmsa-kafka
docker exec springmsa-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
~~~

~~~powershell
Test-NetConnection 192.168.147.131 -Port 9092
~~~

~~~dotenv
APP_KAFKA_ENABLED=true
SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.147.131:9092
~~~

Community, Stock, User는 Outbox에 먼저 저장하므로 Kafka 중단 중에도 도메인 transaction은 보존된다.

## 중지

~~~bash
docker compose -f kafka-compose.yml stop kafka
~~~

down -v는 데이터를 지우므로 사용하지 않는다.
