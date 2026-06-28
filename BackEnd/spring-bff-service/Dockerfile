FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS=""

EXPOSE 8079

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
