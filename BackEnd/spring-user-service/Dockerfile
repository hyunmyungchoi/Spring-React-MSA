FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

COPY spring-user-service/gradlew spring-user-service/gradlew
COPY spring-user-service/gradle spring-user-service/gradle
COPY spring-user-service/build.gradle spring-user-service/settings.gradle spring-user-service/
COPY spring-user-service/src spring-user-service/src
COPY spring-msa-common-web spring-msa-common-web

WORKDIR /workspace/spring-user-service
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon

RUN JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build /workspace/spring-user-service/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30"
ENV JAVA_OPTS=""

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
