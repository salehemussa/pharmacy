FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pharmacy && adduser -S pharmacy -G pharmacy
WORKDIR /app
COPY --from=build /src/target/pharmacy-backend-1.0.0.jar app.jar
USER pharmacy
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
