# Stage 1: Build with Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Run with lightweight JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create uploads folder for file storage if needed
RUN mkdir -p /app/uploads

COPY --from=build /app/target/*.jar app.jar

ENV PORT=2026
EXPOSE 2026

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-2026} -jar /app/app.jar"]
