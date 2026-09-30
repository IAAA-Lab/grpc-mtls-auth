FROM maven:3-eclipse-temurin-26 AS build
ARG MODULE=server
WORKDIR /src
COPY . .
RUN mvn -B -ntp -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:25-jre
ARG MODULE=server
COPY --from=build /src/${MODULE}/target/app.jar /app.jar
ENTRYPOINT ["java", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]
