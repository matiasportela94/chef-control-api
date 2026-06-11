FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY domain ./domain
COPY application ./application
COPY infrastructure ./infrastructure
COPY api ./api
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/api/target/chef-control-api-*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
