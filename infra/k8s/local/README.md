# Local Kubernetes Manifests

These manifests are for local Kubernetes deployment rehearsal.

`SPRING_PROFILES_ACTIVE=prod` is intentional here. The application `prod` profile is used as the shared Docker/Kubernetes deployment profile, while `infra/k8s/local` provides local cluster wiring, service URLs, and rehearsal manifests around that deployment profile.

If the application later needs Kubernetes-only behavior, add a dedicated profile such as `k8s` or `local-k8s` and update these manifests deliberately.
