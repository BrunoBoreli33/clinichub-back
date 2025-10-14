# Etapa 1 - Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package -Dspring.profiles.active=ci

# Etapa 2 - Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copia o JAR gerado
COPY --from=build /app/target/*.jar app.jar

# Expõe a porta HTTPS
EXPOSE 8443

# Sobe o app com as configs SSL
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
