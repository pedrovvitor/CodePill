# syntax=docker/dockerfile:1
# codepill-catalog — service image. All configuration comes from the
# environment (application.yml / application-prod.yml placeholders); the image
# itself is environment-agnostic.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY codepill-catalog/ codepill-catalog/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests package

# Layered extraction: dependencies change far less often than application
# code, so pulls after a code-only change download kilobytes, not the world.
RUN java -Djarmode=tools \
      -jar codepill-catalog/bootstrap/target/codepill-catalog-bootstrap-*.jar \
      extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 --user-group codepill
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER codepill
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
