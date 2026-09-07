# The default deploy task (`clj -M:run deploy`) doesn't use Docker, but this
# file is provided in case you'd like to deploy with some other method/platform
# that uses containers.
#
# When running the container, make sure you set any environment variables
# defined in config.prod.env, e.g. using whatever tools your deployment platform
# provides for setting environment variables.
#
# Run these commands to test this file locally:
#
#   docker build -t your-app .
#   docker run --rm -v $PWD/config.env:/app/config.env your-app
#
FROM clojure:temurin-21-tools-deps-alpine AS jre-build

WORKDIR /app
COPY src ./src
COPY resources ./resources
COPY deps.edn .

RUN clj -M:dev uberjar && cp target/jar/app.jar . && rm -r target

FROM eclipse-temurin:21-alpine
WORKDIR /app

COPY --from=jre-build /app/app.jar /app/app.jar

EXPOSE 8080

ENV HOST=0.0.0.0
ENV PORT=8080
CMD ["/opt/java/openjdk/bin/java", "-XX:-OmitStackTraceInFastThrow", "-XX:+CrashOnOutOfMemoryError", "-jar", "app.jar"]
