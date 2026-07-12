FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -pl notifications-service -am -DskipTests package
FROM eclipse-temurin:21-jre
COPY --from=build /app/notifications-service/target/notifications-service-1.0.0.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
