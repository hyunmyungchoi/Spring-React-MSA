# Repository Instructions

## Spring Profile Configuration Rules

- Treat `application.yml` as common configuration only. It must contain only values that are identical across local, Docker, and production/Kubernetes runtimes.
- Do not put environment-specific infrastructure settings in `application.yml`: datasource URLs or credentials, Redis host/port/password, Kafka bootstrap servers, OAuth endpoint URLs, external API credentials, and internal service base URLs belong in profile-specific files.
- Do not hardcode environment-specific values in `application-local.yml`, `application-docker.yml`, `application-test.yml`, or `application-prod.yml`.
- Use environment-variable placeholders for environment-specific values: datasource URLs or credentials, Redis host/port/password, Kafka bootstrap servers, OAuth endpoint URLs, external API credentials, internal API tokens, and internal service base URLs.
- Keep profile files separated by runtime, but source values from environment variables rather than hardcoding them.
- `application-local.yml` is for local JVM/IDE runtime wiring, `application-docker.yml` is for Docker runtime wiring, `application-test.yml` is for test runtime wiring, and `application-prod.yml` is for production/Kubernetes runtime wiring.
- Only use literal values for truly common non-secret constants that do not change by runtime.
- Do not add code defaults for properties that are expected to come from environment variables. If a runtime value is required, bind it through `@ConfigurationProperties` or `@Value` and let validation fail when it is missing.
- Before editing any `application-*.yml`, verify which profile is actually activated by checking Dockerfiles, Docker Compose files, Kubernetes manifests, and IDE/CLI run configuration evidence when available.
- Do not assume a file named `application-k8s.yml` is used just because the runtime is Kubernetes; Kubernetes only uses it if `SPRING_PROFILES_ACTIVE=k8s` is actually set.
- Prefer environment-variable driven configuration over hardcoded runtime values.
