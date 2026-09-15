FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home iotops
COPY target/iot-ops-platform-1.0.0.jar /app/app.jar
USER iotops
EXPOSE 8080 9999
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
  CMD curl --fail --silent http://127.0.0.1:8080/api/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
