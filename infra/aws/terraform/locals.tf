locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Purpose     = "aws-learning"
  }

  availability_zones = slice(sort(data.aws_availability_zones.available.names), 0, 2)

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

  budget_thresholds = {
    "10" = 10
    "30" = 30
    "40" = 40
    "50" = 50
  }
}
