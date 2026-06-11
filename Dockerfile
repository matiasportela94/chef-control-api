FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/api/target/chef-control-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Railway da DATABASE_URL como postgresql://... — lo convertimos a jdbc:postgresql://
ENTRYPOINT ["sh", "-c", "export DATABASE_URL=jdbc:${DATABASE_URL} && exec java -Dspring.profiles.active=staging -jar app.jar"]
