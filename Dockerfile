FROM maven:3.9.9-eclipse-temurin-8 AS build
ARG MODULE=server
WORKDIR /src
COPY . .
RUN mvn -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:8-jre
ARG MODULE=server
COPY --from=build /src/${MODULE}/target/app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
