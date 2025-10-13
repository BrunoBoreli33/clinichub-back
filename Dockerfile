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

# Copia o certificado SSL (seu keystore)
COPY src/main/resources/clinichub-cert.p12 /app/clinichub-cert.p12

# Expõe a porta HTTPS
EXPOSE 8443

# Define as configs do Spring Boot para SSL
ENV SERVER_PORT=8443
ENV SERVER_SSL_KEY_STORE=/app/clinichub-cert.p12
ENV SERVER_SSL_KEY_STORE_PASSWORD=ChangeMe123
ENV SERVER_SSL_KEY_STORE_TYPE=PKCS12
ENV SERVER_SSL_KEY_ALIAS=clinichub-cert

# Sobe o app com as configs SSL
ENTRYPOINT ["sh", "-c", "java -jar \
  -Dserver.port=$SERVER_PORT \
  -Dserver.ssl.key-store=$SERVER_SSL_KEY_STORE \
  -Dserver.ssl.key-store-password=$SERVER_SSL_KEY_STORE_PASSWORD \
  -Dserver.ssl.key-store-type=$SERVER_SSL_KEY_STORE_TYPE \
  -Dserver.ssl.key-alias=$SERVER_SSL_KEY_ALIAS \
  /app/app.jar"]
