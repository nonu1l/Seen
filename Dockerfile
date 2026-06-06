FROM eclipse-temurin:21-jre

WORKDIR /app

ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/media.db

RUN mkdir -p /app/data

COPY target/seen-*.jar /app/seen.jar

EXPOSE 8081

VOLUME ["/app/data"]

ENTRYPOINT ["java", "-jar", "/app/seen.jar"]
