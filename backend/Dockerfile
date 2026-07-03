# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY user-service/pom.xml user-service/
COPY home-service/pom.xml home-service/
COPY health-service/pom.xml health-service/
COPY checkin-service/pom.xml checkin-service/
COPY consultation-service/pom.xml consultation-service/
COPY plan-service/pom.xml plan-service/
COPY article-service/pom.xml article-service/
COPY audit-service/pom.xml audit-service/
RUN mvn dependency:go-offline -B

COPY common common
COPY gateway gateway
COPY user-service user-service
COPY home-service home-service
COPY health-service health-service
COPY checkin-service checkin-service
COPY consultation-service consultation-service
COPY plan-service plan-service
COPY article-service article-service
COPY audit-service audit-service

ARG SERVICE=gateway
RUN mvn -pl ${SERVICE} -am package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG SERVICE=gateway
COPY --from=build /app/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8099
ENTRYPOINT ["java", "-jar", "app.jar"]
