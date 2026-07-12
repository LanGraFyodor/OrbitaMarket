FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -pl orders-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /app/orders-service/target/orders-service-1.0.0.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
