locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Purpose     = "aws-learning"
  }

  availability_zones = slice(sort(data.aws_availability_zones.available.names), 0, 2)

  github_repository = "hyunmyungchoi/Spring-React-MSA"
  github_branch_ref = "refs/heads/master"

  backend_service_names = toset([
    "spring-member-gateway",
    "spring-admin-gateway",
    "spring-security-authorization-server",
    "spring-user-service",
    "spring-member-community-service",
    "spring-member-stock-service",
    "spring-member-bff-service",
    "spring-admin-bff-service",
  ])

  application_secret_names = toset([
    "/spring-react-msa/learning/admin-bff",
    "/spring-react-msa/learning/auth-server",
    "/spring-react-msa/learning/member-bff",
    "/spring-react-msa/learning/shared/internal-api",
    "/spring-react-msa/learning/shared/redis",
    "/spring-react-msa/learning/stock-service",
    "/spring-react-msa/learning/user-service",
  ])

  budget_thresholds = {
    "10" = 10
    "30" = 30
    "40" = 40
    "50" = 50
  }
}
