FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

COPY spring-security-authorization-server/gradlew spring-security-authorization-server/gradlew
COPY spring-security-authorization-server/gradle spring-security-authorization-server/gradle
COPY spring-security-authorization-server/build.gradle spring-security-authorization-server/settings.gradle spring-security-authorization-server/
COPY spring-security-authorization-server/src spring-security-authorization-server/src
COPY spring-msa-common-web spring-msa-common-web

WORKDIR /workspace/spring-security-authorization-server
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon

RUN JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" app.jar

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

COPY --from=build /workspace/spring-security-authorization-server/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30"
ENV JAVA_OPTS=""

EXPOSE 9000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
