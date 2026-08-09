# Local Kubernetes Manifests

This directory is for local Kubernetes only.

- PostgreSQL, Redis, and Kafka run outside Kubernetes on the Storage VM at `192.168.147.101`.
- `02-external-data-services.yaml` exposes those external endpoints through stable in-cluster service names.
- Member-facing workloads prefer `worker-1`; Admin and domain workloads prefer `worker-2`.
- Web workloads are pinned to their requested worker. Backend preferences remain soft so they can fail over.
- `localtest.me` routes traffic to local ingress and is intentionally used by these manifests.
- These manifests are not the AWS ECS deployment source.
- AWS ECS will use ECS Task Definitions plus SSM Parameter Store or Secrets Manager for environment values.
- Do not add AWS VPC, ECS, ALB, RDS, ElastiCache, Route 53, Terraform resources, or `docker-compose-aws.yml` here.
- Keep local values such as `http://user.localtest.me` and `http://admin.localtest.me` scoped to this directory.
