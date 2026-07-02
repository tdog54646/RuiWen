ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

FROM --platform=$BUILDPLATFORM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM ${JRE_IMAGE}
WORKDIR /app

ENV JAVA_OPTS=""
ENV SERVER_PORT=8080

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
