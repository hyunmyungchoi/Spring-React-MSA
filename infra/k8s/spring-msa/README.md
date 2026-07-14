# Local Kubernetes Manifests

This directory is for local Kubernetes only.

- `localtest.me` routes traffic to local ingress and is intentionally used by these manifests.
- These manifests are not the AWS ECS deployment source.
- AWS ECS will use ECS Task Definitions plus SSM Parameter Store or Secrets Manager for environment values.
- Do not add AWS VPC, ECS, ALB, RDS, ElastiCache, Route 53, Terraform resources, or `docker-compose-aws.yml` here.
- Keep local values such as `http://user.localtest.me` and `http://admin.localtest.me` scoped to this directory.
