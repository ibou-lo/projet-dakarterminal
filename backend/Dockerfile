# Image de production (Render, Railway, etc.) : JDK 17 (Lombok n'est pas compatible avec le JDK 26).
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 1001 autotrack
COPY --from=build /src/target/autotrack-backend-0.1.0-SNAPSHOT.jar app.jar
USER autotrack
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
