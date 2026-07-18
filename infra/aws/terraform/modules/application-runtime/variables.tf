variable "name_prefix" {
  description = "Prefix used for Learning application runtime resource names."
  type        = string
}

variable "aws_region" {
  description = "AWS Region used by CloudWatch Logs."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID used by Cloud Map and ALB target groups."
  type        = string
}

variable "public_subnet_ids" {
  description = "Two Public subnets used by the disposable public ALB."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_ids) == 2
    error_message = "public_subnet_ids must contain exactly two subnet IDs."
  }
}

variable "private_app_subnet_ids" {
  description = "Two Private App subnets used by ECS task ENIs."
  type        = list(string)

  validation {
    condition     = length(var.private_app_subnet_ids) == 2
    error_message = "private_app_subnet_ids must contain exactly two subnet IDs."
  }
}

variable "alb_security_group_id" {
  description = "Security Group attached to the public ALB."
  type        = string
}

variable "ecs_security_group_id" {
  description = "Security Group attached to ECS task ENIs."
  type        = string
}

variable "ecs_cluster_arn" {
  description = "ECS cluster ARN hosting the eight backend services."
  type        = string
}

variable "capacity_provider_name" {
  description = "ECS EC2 capacity provider used by every backend service."
  type        = string
}

variable "service_images" {
  description = "Immutable ECR image URIs for all eight backend services, keyed by short runtime name."
  type        = map(string)

  validation {
    condition = toset(keys(var.service_images)) == toset([
      "admin-bff",
      "admin-gateway",
      "authorization-server",
      "community-service",
      "member-bff",
      "member-gateway",
      "stock-service",
      "user-service",
    ])
    error_message = "service_images must contain exactly the eight approved backend runtime keys."
  }

  validation {
    condition = alltrue([
      for image in values(var.service_images) : can(regex(
        "^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9][a-z0-9._/-]*@sha256:[0-9a-f]{64}$",
        image,
      ))
    ])
    error_message = "Every service image must be an immutable ECR URI pinned with @sha256:<64 hex characters>."
  }
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs keyed by full backend repository service name."
  type        = map(string)
}

variable "application_secret_arns" {
  description = "Secrets Manager ARNs keyed by full approved secret name."
  type        = map(string)
}

variable "redis_host_parameter_arn" {
  description = "Stable SSM String parameter ARN containing the disposable Valkey endpoint while Runtime is ON."
  type        = string
}

variable "db_address" {
  description = "Private RDS endpoint used by the three database-backed services."
  type        = string
}

variable "db_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Shared PostgreSQL database name."
  type        = string
  default     = "spring_msa"
}

variable "learning_runtime_enabled" {
  description = "Whether to run one task per service and create the hourly-billed public ALB."
  type        = bool
  default     = false
}

variable "member_public_origin" {
  description = "Canonical public member origin and authorization-server issuer."
  type        = string
  default     = "https://app.hyuncloudlab.com"
}

variable "admin_public_origin" {
  description = "Canonical public admin origin."
  type        = string
  default     = "https://admin.hyuncloudlab.com"
}

variable "member_bff_client_id" {
  description = "Public OAuth client ID for the member BFF."
  type        = string
  default     = "bff-client"
}

variable "admin_bff_client_id" {
  description = "Public OAuth client ID for the admin BFF."
  type        = string
  default     = "admin-bff-client"
}

variable "toss_api_client_id" {
  description = "Non-secret Toss API client ID; an empty value keeps external stock calls unconfigured."
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention for Learning application tasks."
  type        = number
  default     = 7

  validation {
    condition     = var.log_retention_days == 7
    error_message = "log_retention_days must remain 7 for the Learning cost baseline."
  }
}

variable "common_tags" {
  description = "Common tags applied to application runtime resources."
  type        = map(string)
  default     = {}
}
