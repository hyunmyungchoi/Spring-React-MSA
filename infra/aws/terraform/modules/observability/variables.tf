variable "name_prefix" {
  description = "Prefix used for Learning observability resources."
  type        = string
}

variable "aws_region" {
  description = "AWS Region containing the monitored RDS instance and CloudWatch alarms."
  type        = string
}

variable "alert_email" {
  description = "Email endpoint subscribed to operational alerts."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email must be a valid email address."
  }
}

variable "db_instance_identifier" {
  description = "RDS DB instance identifier monitored by alarms and event notifications."
  type        = string

  validation {
    condition     = length(trimspace(var.db_instance_identifier)) > 0
    error_message = "db_instance_identifier must not be empty."
  }
}

variable "db_instance_arn" {
  description = "RDS DB instance ARN allowed to publish operational events."
  type        = string

  validation {
    condition     = can(regex("^arn:[^:]+:rds:[^:]+:[0-9]{12}:db:.+$", var.db_instance_arn))
    error_message = "db_instance_arn must be a valid RDS DB instance ARN."
  }
}

variable "runtime_observability_enabled" {
  description = "Whether lifecycle-scoped ECS and ALB observability should be available."
  type        = bool
  default     = false
}

variable "runtime_enabled" {
  description = "Whether the disposable Learning runtime is currently ON."
  type        = bool
  default     = false
}

variable "ecs_cluster_name" {
  description = "ECS cluster name used by Runtime alarm dimensions."
  type        = string
  default     = ""
}

variable "ecs_service_names" {
  description = "ECS service names keyed by the eight short service names."
  type        = map(string)
  default     = {}
}

variable "load_balancer_arn_suffix" {
  description = "Runtime public ALB ARN suffix used by ApplicationELB metrics."
  type        = string
  default     = null
  nullable    = true
}

variable "target_group_arn_suffixes" {
  description = "Public gateway target group ARN suffixes keyed by service name."
  type        = map(string)
  default     = {}
}

variable "common_tags" {
  description = "Common tags applied to observability resources."
  type        = map(string)
  default     = {}
}
