FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package -Dspring.profiles.active=ci

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=build /app/target/*.jar app.jar

ENV SERVER_PORT=${PORT:-8080}

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=$SERVER_PORT -jar /app/app.jar"]
