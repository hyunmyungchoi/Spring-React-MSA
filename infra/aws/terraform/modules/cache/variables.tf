variable "name_prefix" {
  description = "Prefix used for Learning cache resource names."
  type        = string
}

variable "aws_region" {
  description = "AWS Region used for the runtime Redis host parameter ARN."
  type        = string
}

variable "private_data_subnet_ids" {
  description = "Two Private Data subnet IDs available to the Valkey subnet group."
  type        = list(string)

  validation {
    condition     = length(var.private_data_subnet_ids) == 2
    error_message = "private_data_subnet_ids must contain exactly two subnet IDs."
  }
}

variable "data_security_group_id" {
  description = "Data Security Group allowing port 6379 only from the ECS tier."
  type        = string
}

variable "runtime_enabled" {
  description = "Whether to create the hourly-billed disposable Learning Valkey runtime."
  type        = bool
  default     = false
}

variable "redis_password" {
  description = "Valkey application password passed ephemerally to the write-only provider argument."
  type        = string
  default     = null
  nullable    = true
  sensitive   = true
  ephemeral   = true

  validation {
    condition = var.redis_password == null || (
      length(var.redis_password) >= 32 &&
      length(var.redis_password) <= 128 &&
      can(regex("^[A-Za-z0-9]+$", var.redis_password))
    )
    error_message = "redis_password must be null or a 32-128 character alphanumeric value."
  }
}

variable "redis_password_version" {
  description = "Operator-controlled version that triggers a write-only Valkey password rotation."
  type        = number
  default     = 1

  validation {
    condition     = var.redis_password_version >= 1
    error_message = "redis_password_version must be at least 1."
  }
}

variable "redis_host_parameter_name" {
  description = "SSM String parameter read by ECS to resolve the disposable Valkey endpoint."
  type        = string
  default     = "/spring-react-msa/learning/runtime/redis-host"
}

variable "common_tags" {
  description = "Common tags applied to cache resources."
  type        = map(string)
  default     = {}
}
