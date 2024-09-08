# Dockerfile for Spring Boot backend
FROM openjdk:17-jdk-alpine
VOLUME /tmp
COPY target/Culin-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
