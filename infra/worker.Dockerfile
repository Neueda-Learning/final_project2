FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
COPY worker/pom.xml worker/pom.xml
COPY worker/src worker/src
RUN mvn -pl worker -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/worker/target/portfolio-manager-worker-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
