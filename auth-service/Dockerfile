FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY auth-service/pom.xml auth-service/pom.xml
COPY task-service/pom.xml task-service/pom.xml
COPY auth-service/src auth-service/src
RUN mvn -B -ntp -pl auth-service package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache su-exec
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar
COPY auth-service/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
EXPOSE 8081
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
