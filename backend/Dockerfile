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

# 安装 CJK 字体，供知文导出 PDF 渲染中文（openhtmltopdf 需显式注册字体文件）
RUN apk add --no-cache font-noto-cjk font-dejavu

ENV JAVA_OPTS="-Djava.awt.headless=true"
ENV SERVER_PORT=8080

COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
