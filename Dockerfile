FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package && cp target/resource-entitlement-engine-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine-3.23
WORKDIR /app
RUN addgroup -S vera && adduser -S vera -G vera
COPY --from=build --chown=vera:vera /build/app.jar ./app.jar
USER vera
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
