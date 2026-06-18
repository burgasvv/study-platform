
FROM gradle:9.4.1 AS build
WORKDIR /gradle
COPY . .
RUN gradle buildFatJar

FROM bellsoft/liberica-openjdk-alpine:25 AS prod
WORKDIR /app
EXPOSE 8080
EXPOSE 8443
COPY --from=build /gradle/build/libs/*.jar /app/app.jar
COPY ./src/main/resources/keystore.jks /app/keystore.jks
ENTRYPOINT ["java", "-jar", "/app/app.jar"]