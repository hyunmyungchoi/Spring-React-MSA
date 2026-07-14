FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

COPY spring-member-bff-service/gradlew spring-member-bff-service/gradlew
COPY spring-member-bff-service/gradle spring-member-bff-service/gradle
COPY spring-member-bff-service/build.gradle spring-member-bff-service/settings.gradle spring-member-bff-service/
COPY spring-member-bff-service/src spring-member-bff-service/src
COPY spring-msa-common-kafka spring-msa-common-kafka
COPY spring-msa-common-web spring-msa-common-web

WORKDIR /workspace/spring-member-bff-service
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon

RUN JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

COPY --from=build /workspace/spring-member-bff-service/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30"
ENV JAVA_OPTS=""

EXPOSE 8079

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
