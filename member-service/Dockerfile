FROM eclipse-temurin:17-jre
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]