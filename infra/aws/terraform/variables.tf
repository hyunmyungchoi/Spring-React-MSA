variable "project_name" {
  description = "Project name used for resource names and tags."
  type        = string
  default     = "spring-react-msa"

  validation {
    condition     = length(trimspace(var.project_name)) > 0
    error_message = "project_name must not be empty."
  }
}
variable "environment" {
  description = "Deployment environment name. This foundation supports the learning environment only."
  type        = string
  default     = "learning"

  validation {
    condition     = var.environment == "learning"
    error_message = "environment must be learning for this foundation."
  }
}

variable "aws_region" {
  description = "AWS region for the learning environment."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "aws_region must be ap-northeast-2."
  }
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR block."
  }
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDR blocks in availability-zone order."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 2 && alltrue([for cidr in var.public_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "public_subnet_cidrs must contain exactly two valid IPv4 CIDR blocks."
  }
}

variable "private_app_subnet_cidrs" {
  description = "Private application subnet CIDR blocks in availability-zone order."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]

  validation {
    condition     = length(var.private_app_subnet_cidrs) == 2 && alltrue([for cidr in var.private_app_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "private_app_subnet_cidrs must contain exactly two valid IPv4 CIDR blocks."
  }
}

variable "private_data_subnet_cidrs" {
  description = "Private data subnet CIDR blocks in availability-zone order."
  type        = list(string)
  default     = ["10.20.20.0/24", "10.20.21.0/24"]

  validation {
    condition     = length(var.private_data_subnet_cidrs) == 2 && alltrue([for cidr in var.private_data_subnet_cidrs : can(cidrnetmask(cidr))])
    error_message = "private_data_subnet_cidrs must contain exactly two valid IPv4 CIDR blocks."
  }
}

variable "enable_nat_gateway" {
  description = "Whether to create the hourly-billed single NAT Gateway for the learning environment."
  type        = bool
  default     = false
}

variable "enable_data_layer" {
  description = "Whether to create the persistent learning RDS and empty Secrets Manager containers."
  type        = bool
  default     = false
}

variable "enable_rds_restore_drill_foundation" {
  description = "Whether to retain the seven-day RDS restore drill audit log group while Runtime is OFF."
  type        = bool
  default     = false
}

variable "rds_restore_drill_enabled" {
  description = "Whether to create the temporary isolated PITR database and Fargate validator."
  type        = bool
  default     = false

  validation {
    condition = !var.rds_restore_drill_enabled || (
      var.enable_rds_restore_drill_foundation &&
      var.enable_data_layer &&
      var.enable_ecs_compute_foundation &&
      var.enable_nat_gateway &&
      !var.learning_runtime_enabled
    )
    error_message = "rds_restore_drill_enabled requires the restore audit foundation, data layer, ECS compute foundation, NAT, and Learning Runtime OFF."
  }
}

variable "rds_restore_drill_identifier" {
  description = "Unique target identifier for the temporary PITR database."
  type        = string
  default     = "spring-react-msa-learning-postgres-restore-drill"

  validation {
    condition = (
      length(var.rds_restore_drill_identifier) >= 1 &&
      length(var.rds_restore_drill_identifier) <= 63 &&
      can(regex("^[a-z][a-z0-9-]*$", var.rds_restore_drill_identifier)) &&
      !endswith(var.rds_restore_drill_identifier, "-") &&
      !strcontains(var.rds_restore_drill_identifier, "--")
    )
    error_message = "rds_restore_drill_identifier must satisfy the RDS 1-63 character lowercase identifier contract."
  }
}

variable "rds_restore_drill_use_latest_restorable_time" {
  description = "Whether the restore drill uses the latest available PITR time."
  type        = bool
  default     = true

  validation {
    condition     = !var.rds_restore_drill_enabled || var.rds_restore_drill_use_latest_restorable_time
    error_message = "The Learning restore drill must use the latest restorable time."
  }
}

variable "rds_restore_drill_expires_at_utc" {
  description = "Operator-supplied RFC3339 UTC expiry tag required only while the temporary restore is enabled."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = !var.rds_restore_drill_enabled || (
      var.rds_restore_drill_expires_at_utc != null &&
      can(formatdate("YYYY-MM-DD'T'hh:mm:ss'Z'", var.rds_restore_drill_expires_at_utc)) &&
      endswith(var.rds_restore_drill_expires_at_utc, "Z")
    )
    error_message = "rds_restore_drill_enabled requires rds_restore_drill_expires_at_utc as an RFC3339 UTC timestamp ending in Z."
  }
}

variable "rds_restore_drill_validator_image" {
  description = "Immutable public ECR PostgreSQL client image used by the read-only Fargate validator."
  type        = string
  default     = "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382"

  validation {
    condition     = can(regex("^public\\.ecr\\.aws/.+@sha256:[0-9a-f]{64}$", var.rds_restore_drill_validator_image))
    error_message = "rds_restore_drill_validator_image must be pinned to an immutable public ECR digest."
  }
}

variable "enable_observability_foundation" {
  description = "Whether to create the persistent SNS operations channel, RDS alarms, and RDS event subscription."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_observability_foundation || (
      var.enable_data_layer &&
      var.budget_alert_email != null &&
      can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_alert_email))
    )
    error_message = "enable_observability_foundation requires the data layer and a valid budget_alert_email."
  }
}

variable "enable_runtime_observability" {
  description = "Whether Runtime ON should enable standard ECS Container Insights and create lifecycle-scoped ECS and ALB alarms."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_runtime_observability || (
      var.enable_observability_foundation &&
      var.enable_ecs_compute_foundation &&
      var.enable_application_runtime_foundation
    )
    error_message = "enable_runtime_observability requires the observability, ECS compute, and application runtime foundations."
  }
}

variable "enable_runtime_watchdog" {
  description = "Whether to run the alert-only Learning Runtime and RDS lifecycle watchdog without mutating Runtime resources."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_runtime_watchdog || (
      var.enable_runtime_observability &&
      var.enable_observability_foundation &&
      var.enable_data_layer &&
      var.enable_ecs_compute_foundation &&
      var.enable_application_runtime_foundation
    )
    error_message = "enable_runtime_watchdog requires the Runtime observability, data, ECS compute, and application foundations."
  }
}

variable "runtime_watchdog_schedule_expression" {
  description = "EventBridge schedule used by the alert-only Runtime watchdog."
  type        = string
  default     = "rate(15 minutes)"

  validation {
    condition     = can(regex("^rate\\([1-9][0-9]* (minute|minutes|hour|hours)\\)$", var.runtime_watchdog_schedule_expression))
    error_message = "runtime_watchdog_schedule_expression must be a simple EventBridge rate expression."
  }
}

variable "runtime_watchdog_max_runtime_hours" {
  description = "Maximum continuous Learning Runtime EC2 age before the Watchdog alerts."
  type        = number
  default     = 6

  validation {
    condition     = var.runtime_watchdog_max_runtime_hours >= 1 && var.runtime_watchdog_max_runtime_hours <= 168 && floor(var.runtime_watchdog_max_runtime_hours) == var.runtime_watchdog_max_runtime_hours
    error_message = "runtime_watchdog_max_runtime_hours must be a whole number from 1 through 168."
  }
}

variable "runtime_watchdog_rds_restart_warning_hours" {
  description = "Hours before the RDS automatic restart time when the Watchdog alerts."
  type        = number
  default     = 24

  validation {
    condition     = var.runtime_watchdog_rds_restart_warning_hours >= 1 && var.runtime_watchdog_rds_restart_warning_hours <= 168 && floor(var.runtime_watchdog_rds_restart_warning_hours) == var.runtime_watchdog_rds_restart_warning_hours
    error_message = "runtime_watchdog_rds_restart_warning_hours must be a whole number from 1 through 168."
  }
}

variable "enable_ecs_compute_foundation" {
  description = "Whether to create the ECS cluster, EC2 launch template, zero-capacity ASG, and capacity provider."
  type        = bool
  default     = false
}

variable "learning_runtime_enabled" {
  description = "Whether the disposable Learning cache, ALB, ECS capacity, and eight backend services should run."
  type        = bool
  default     = false

  validation {
    condition = !var.learning_runtime_enabled || (
      var.enable_ecs_compute_foundation &&
      var.enable_application_runtime_foundation &&
      var.enable_nat_gateway &&
      var.enable_data_layer
    )
    error_message = "learning_runtime_enabled requires the NAT, data layer, ECS compute, and application runtime foundations."
  }
}

variable "enable_application_runtime_foundation" {
  description = "Whether to create eight digest-pinned task definitions, ECS services, Cloud Map, target groups, IAM, and logs."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_application_runtime_foundation || (
      var.enable_data_layer &&
      var.enable_ecs_compute_foundation &&
      var.enable_database_tasks_foundation
    )
    error_message = "enable_application_runtime_foundation requires the data, ECS compute, and database task foundations."
  }
}

variable "enable_frontend_hosting" {
  description = "Whether to create six private frontend buckets, two CloudFront distributions, and the GitHub deployment role."
  type        = bool
  default     = false
}

variable "enable_public_domain_routing" {
  description = "Whether to attach the approved Route 53 domains, ACM certificates, and CloudFront-to-ALB API routing."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_public_domain_routing || (
      var.enable_frontend_hosting &&
      var.enable_application_runtime_foundation
    )
    error_message = "enable_public_domain_routing requires both frontend hosting and the application runtime foundation."
  }
}

variable "root_domain" {
  description = "Stable public domain managed by the separate global DNS state."
  type        = string
  default     = "hyuncloudlab.com"

  validation {
    condition     = var.root_domain == "hyuncloudlab.com"
    error_message = "root_domain must remain hyuncloudlab.com for the approved public contract."
  }
}

variable "application_images" {
  description = "Immutable ECR image URIs for all eight backend services, keyed by short runtime name."
  type        = map(string)
  default     = {}

  validation {
    condition = !var.enable_application_runtime_foundation || toset(keys(var.application_images)) == toset([
      "admin-bff",
      "admin-gateway",
      "authorization-server",
      "community-service",
      "member-bff",
      "member-gateway",
      "stock-service",
      "user-service",
    ])
    error_message = "application_images must contain all eight backend runtime keys when the application runtime foundation is enabled."
  }
}

variable "redis_password" {
  description = "Valkey password supplied ephemerally from Secrets Manager only while the Learning runtime is ON."
  type        = string
  default     = null
  nullable    = true
  sensitive   = true
  ephemeral   = true
}

variable "redis_password_version" {
  description = "Operator-controlled write-only Valkey password rotation version."
  type        = number
  default     = 1
}

variable "toss_api_client_id" {
  description = "Non-secret Toss API client ID injected into the stock service."
  type        = string
  default     = ""
}

variable "enable_database_tasks_foundation" {
  description = "Whether to create the one-off database bootstrap task and optional Flyway migration task definitions."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_database_tasks_foundation || (
      var.enable_data_layer && var.enable_ecs_compute_foundation
    )
    error_message = "enable_database_tasks_foundation requires both the data layer and ECS compute foundation."
  }
}

variable "enable_admin_bootstrap_foundation" {
  description = "Whether to create the temporary one-off initial administrator task, least-privilege role, and input secret container."
  type        = bool
  default     = false

  validation {
    condition = !var.enable_admin_bootstrap_foundation || (
      var.enable_database_tasks_foundation &&
      var.enable_application_runtime_foundation &&
      contains(keys(var.application_images), "user-service")
    )
    error_message = "enable_admin_bootstrap_foundation requires the database task and application foundations plus the User Service image."
  }
}

variable "database_migration_images" {
  description = "Immutable ECR image URIs for database migrations. Keep empty until the current Flyway-enabled images are promoted."
  type        = map(string)
  default     = {}

  validation {
    condition     = var.enable_database_tasks_foundation || length(var.database_migration_images) == 0
    error_message = "database_migration_images requires enable_database_tasks_foundation to be true."
  }
}

variable "ecs_instance_type" {
  description = "Approved EC2 instance type for the Learning ECS capacity provider."
  type        = string
  default     = "m6i.xlarge"

  validation {
    condition     = var.ecs_instance_type == "m6i.xlarge"
    error_message = "ecs_instance_type must be m6i.xlarge for the approved two-AZ Learning design."
  }
}

variable "db_engine_version" {
  description = "PostgreSQL 16 version verified as available in ap-northeast-2."
  type        = string
  default     = "16.14"

  validation {
    condition     = startswith(var.db_engine_version, "16.")
    error_message = "db_engine_version must remain on PostgreSQL major version 16."
  }
}

variable "db_instance_class" {
  description = "RDS instance class for the learning data layer."
  type        = string
  default     = "db.t4g.micro"

  validation {
    condition     = var.db_instance_class == "db.t4g.micro"
    error_message = "db_instance_class must be db.t4g.micro for the approved learning design."
  }
}

variable "enable_budget" {
  description = "Whether to include the AWS monthly cost budget. Enable only with a real alert email."
  type        = bool
  default     = false
}

variable "monthly_budget_usd" {
  description = "Monthly AWS cost budget limit in USD."
  type        = number
  default     = 50

  validation {
    condition     = var.monthly_budget_usd == 50
    error_message = "monthly_budget_usd must be 50 for this learning foundation."
  }
}

variable "budget_alert_email" {
  description = "Email address for AWS Budget alerts. Store the real value only in an untracked terraform.tfvars file."
  type        = string
  default     = null
  nullable    = true
  sensitive   = true

  validation {
    condition = !var.enable_budget || (
      var.budget_alert_email != null &&
      can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_alert_email))
    )
    error_message = "budget_alert_email must be a valid email address when enable_budget is true."
  }
}
