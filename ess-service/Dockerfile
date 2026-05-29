FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
COPY build/libs/ess-service-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=8085"]
