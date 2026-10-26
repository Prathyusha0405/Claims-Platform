# syntax=docker/dockerfile:1
#
# One Dockerfile for all three Spring Boot services. The build context is this folder (backend/), because every service
# builds from the parent pom. Choose the service with a build argument:
#
#   docker build --build-arg SERVICE=claim-service -t chubb-claims/claim-service backend
#
# SERVICE is one of: auth-service, claim-service, assessment-service. docker-compose.yml at the repository root does this
# for all three.

ARG JAVA_VERSION=27

# ---- build: compile and package the one module (and the parent pom) -----------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
# mvnw is committed without its execute bit, and a Windows checkout can turn its line endings into CRLF, which a Linux shell
# cannot run; so strip any carriage returns and start it through sh. Tests are run by CI and by `mvnw verify`, not here.
RUN test -n "${SERVICE}" || (echo "Pass --build-arg SERVICE=<module>" && exit 1)
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -ntp -pl "${SERVICE}" -am -DskipTests package \
    && mkdir /app \
    && cp "${SERVICE}"/target/"${SERVICE}"-*.jar /app/app.jar

# ---- run: a JRE, the one jar, a non-root user ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre
# curl is for the container health checks in docker-compose.yml (they call the service's own /actuator/health/readiness).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
# The services listen on 8084 (auth), 8082 (claim) and 8083 (assessment); the port comes from each service's own configuration.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
