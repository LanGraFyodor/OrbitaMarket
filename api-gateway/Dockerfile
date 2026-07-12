FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -pl api-gateway -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /app/api-gateway/target/api-gateway-1.0.0.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
