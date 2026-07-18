variable "name_prefix" {
  description = "Prefix used for database task resource names."
  type        = string

  validation {
    condition     = length(trimspace(var.name_prefix)) > 0
    error_message = "name_prefix must not be empty."
  }
}

variable "aws_region" {
  description = "AWS region used by the awslogs driver."
  type        = string
}

variable "db_address" {
  description = "Private RDS endpoint used by bootstrap and migration tasks."
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

variable "master_secret_arn" {
  description = "ARN of the RDS-managed master secret used only by the bootstrap task."
  type        = string
}

variable "application_secret_arns" {
  description = "Application secret ARNs keyed by full Secrets Manager name."
  type        = map(string)

  validation {
    condition = alltrue([
      for name in [
        "/spring-react-msa/learning/user-service",
        "/spring-react-msa/learning/member-bff",
        "/spring-react-msa/learning/stock-service",
      ] : contains(keys(var.application_secret_arns), name)
    ])
    error_message = "application_secret_arns must contain the user-service, member-bff, and stock-service secrets."
  }
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs keyed by backend service name."
  type        = map(string)
}

variable "migration_images" {
  description = "Immutable ECR image URIs keyed by user-service, member-bff, and stock-service. Empty creates no migration task definitions."
  type        = map(string)
  default     = {}

  validation {
    condition = length(var.migration_images) == 0 || (
      length(var.migration_images) == 3 &&
      alltrue([
        for key in [
          "user-service",
          "member-bff",
          "stock-service",
        ] : contains(keys(var.migration_images), key)
      ])
    )
    error_message = "migration_images must be empty or contain all three keys: user-service, member-bff, and stock-service."
  }

  validation {
    condition = alltrue([
      for image in values(var.migration_images) : can(regex(
        "^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9][a-z0-9._/-]*@sha256:[0-9a-f]{64}$",
        image,
      ))
    ])
    error_message = "Every migration image must be an immutable ECR URI pinned with @sha256:<64 hex characters>."
  }
}

variable "bootstrap_image" {
  description = "Immutable public ECR PostgreSQL client image used by the bootstrap task."
  type        = string
  default     = "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382"

  validation {
    condition     = can(regex("^public\\.ecr\\.aws/.+@sha256:[0-9a-f]{64}$", var.bootstrap_image))
    error_message = "bootstrap_image must be pinned to an immutable public ECR digest."
  }
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for one-off database tasks."
  type        = number
  default     = 7

  validation {
    condition     = var.log_retention_days == 7
    error_message = "log_retention_days must remain 7 for the Learning cost baseline."
  }
}

variable "common_tags" {
  description = "Common tags applied to database task resources."
  type        = map(string)
  default     = {}
}
