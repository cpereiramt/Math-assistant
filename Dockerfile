# Multi-stage Dockerfile for Math-assistant (Spring Boot, Gradle, Java 21)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . /app
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java","-jar","/app/app.jar"]
