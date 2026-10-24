FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY auth-service/pom.xml auth-service/pom.xml
COPY task-service/pom.xml task-service/pom.xml
COPY task-service/src task-service/src
RUN mvn -B -ntp -pl task-service package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /workspace/task-service/target/task-service-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

