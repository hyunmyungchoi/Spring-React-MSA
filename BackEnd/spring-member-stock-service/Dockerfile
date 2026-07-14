FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

COPY spring-member-stock-service/gradlew spring-member-stock-service/gradlew
COPY spring-member-stock-service/gradle spring-member-stock-service/gradle
COPY spring-member-stock-service/build.gradle spring-member-stock-service/settings.gradle spring-member-stock-service/
COPY spring-member-stock-service/src spring-member-stock-service/src
COPY spring-msa-common-web spring-msa-common-web

WORKDIR /workspace/spring-member-stock-service
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon

RUN JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

COPY --from=build /workspace/spring-member-stock-service/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30"
ENV JAVA_OPTS=""

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
