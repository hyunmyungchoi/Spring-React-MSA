variable "name_prefix" {
  description = "Prefix used for restore drill resource names."
  type        = string

  validation {
    condition     = length(trimspace(var.name_prefix)) > 0
    error_message = "name_prefix must not be empty."
  }
}

variable "aws_region" {
  description = "AWS region used by the awslogs driver."
  type        = string

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "aws_region must remain ap-northeast-2 for the Learning restore drill."
  }
}

variable "foundation_enabled" {
  description = "Whether to retain the seven-day restore drill audit log group."
  type        = bool
  default     = false
}

variable "restore_enabled" {
  description = "Whether to create the temporary isolated PITR database, security groups, IAM, and Fargate validator."
  type        = bool
  default     = false

  validation {
    condition = !var.restore_enabled || (
      var.foundation_enabled &&
      var.data_layer_enabled &&
      var.ecs_compute_foundation_enabled &&
      var.nat_gateway_enabled &&
      !var.application_runtime_enabled
    )
    error_message = "restore_enabled requires the audit foundation, data layer, ECS compute foundation, NAT, and Application Runtime OFF."
  }
}

variable "data_layer_enabled" {
  description = "Whether the source RDS data layer is retained."
  type        = bool
  default     = false
}

variable "ecs_compute_foundation_enabled" {
  description = "Whether the ECS cluster foundation used for the Fargate task is retained."
  type        = bool
  default     = false
}

variable "nat_gateway_enabled" {
  description = "Whether private Fargate tasks can reach public ECR and AWS APIs through NAT."
  type        = bool
  default     = false
}

variable "application_runtime_enabled" {
  description = "Whether the disposable application runtime is running. Restore drill requires false."
  type        = bool
  default     = false
}

variable "vpc_id" {
  description = "VPC in which the isolated restore security groups are created."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.restore_enabled || (var.vpc_id != null && startswith(var.vpc_id, "vpc-"))
    error_message = "restore_enabled requires a VPC ID."
  }
}

variable "vpc_cidr" {
  description = "VPC IPv4 CIDR used only for DNS egress from the validator."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.restore_enabled || (var.vpc_cidr != null && can(cidrnetmask(var.vpc_cidr)))
    error_message = "restore_enabled requires a valid VPC CIDR."
  }
}

variable "private_app_subnet_ids" {
  description = "Two private application subnets used when the Fargate validator is run."
  type        = list(string)
  default     = []

  validation {
    condition = !var.restore_enabled || (
      length(var.private_app_subnet_ids) == 2 &&
      alltrue([for subnet_id in var.private_app_subnet_ids : startswith(subnet_id, "subnet-")])
    )
    error_message = "restore_enabled requires exactly two private application subnet IDs."
  }
}

variable "source_db_instance_identifier" {
  description = "Identifier of the protected source RDS instance."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.restore_enabled || (var.source_db_instance_identifier != null && length(var.source_db_instance_identifier) > 0)
    error_message = "restore_enabled requires the source DB instance identifier."
  }
}

variable "source_master_secret_arn" {
  description = "ARN of the source RDS-managed master secret. PostgreSQL PITR reuses the restored credential and cannot create a new managed secret during restore."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = !var.restore_enabled || (
      var.source_master_secret_arn != null &&
      can(regex("^arn:aws:secretsmanager:ap-northeast-2:[0-9]{12}:secret:", var.source_master_secret_arn))
    )
    error_message = "restore_enabled requires the source RDS-managed Secrets Manager ARN."
  }
}

variable "db_subnet_group_name" {
  description = "Existing private DB subnet group reused by the restored instance."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.restore_enabled || (var.db_subnet_group_name != null && length(var.db_subnet_group_name) > 0)
    error_message = "restore_enabled requires the existing DB subnet group name."
  }
}

variable "db_parameter_group_name" {
  description = "Existing PostgreSQL 16 parameter group reused by the restored instance."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.restore_enabled || (var.db_parameter_group_name != null && length(var.db_parameter_group_name) > 0)
    error_message = "restore_enabled requires the existing DB parameter group name."
  }
}

variable "db_name" {
  description = "Shared PostgreSQL database restored from the source."
  type        = string
  default     = "spring_msa"

  validation {
    condition     = var.db_name == "spring_msa"
    error_message = "db_name must remain spring_msa for the restore validation contract."
  }
}

variable "db_instance_class" {
  description = "Temporary restored DB instance class."
  type        = string
  default     = "db.t4g.micro"

  validation {
    condition     = var.db_instance_class == "db.t4g.micro"
    error_message = "db_instance_class must remain db.t4g.micro for the approved restore cost gate."
  }
}

variable "restore_identifier" {
  description = "Unique target identifier for the temporary restored DB instance."
  type        = string
  default     = "spring-react-msa-learning-postgres-restore-drill"

  validation {
    condition = (
      length(var.restore_identifier) >= 1 &&
      length(var.restore_identifier) <= 63 &&
      can(regex("^[a-z][a-z0-9-]*$", var.restore_identifier)) &&
      !endswith(var.restore_identifier, "-") &&
      !strcontains(var.restore_identifier, "--")
    )
    error_message = "restore_identifier must satisfy the RDS 1-63 character lowercase identifier contract."
  }
}

variable "use_latest_restorable_time" {
  description = "Whether PITR uses the latest restorable time. This drill intentionally supports only true."
  type        = bool
  default     = true

  validation {
    condition     = !var.restore_enabled || var.use_latest_restorable_time
    error_message = "The Learning restore drill must use the latest restorable time."
  }
}

variable "expires_at_utc" {
  description = "Operator-supplied RFC3339 UTC expiry tag for the temporary restore resources."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = !var.restore_enabled || (
      var.expires_at_utc != null &&
      can(formatdate("YYYY-MM-DD'T'hh:mm:ss'Z'", var.expires_at_utc)) &&
      endswith(var.expires_at_utc, "Z")
    )
    error_message = "restore_enabled requires expires_at_utc as an RFC3339 UTC timestamp ending in Z."
  }
}

variable "validator_image" {
  description = "Immutable public ECR PostgreSQL client image used by the read-only Fargate validator."
  type        = string
  default     = "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382"

  validation {
    condition     = can(regex("^public\\.ecr\\.aws/.+@sha256:[0-9a-f]{64}$", var.validator_image))
    error_message = "validator_image must be pinned to an immutable public ECR digest."
  }
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for restore drill audit evidence."
  type        = number
  default     = 7

  validation {
    condition     = var.log_retention_days == 7
    error_message = "log_retention_days must remain 7 for the Learning cost baseline."
  }
}

variable "common_tags" {
  description = "Common tags applied to restore drill resources."
  type        = map(string)
  default     = {}
}
