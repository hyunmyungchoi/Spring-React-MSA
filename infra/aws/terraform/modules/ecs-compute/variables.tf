variable "name_prefix" {
  description = "Prefix used for ECS compute resource names."
  type        = string

  validation {
    condition     = length(trimspace(var.name_prefix)) > 0
    error_message = "name_prefix must not be empty."
  }
}

variable "private_app_subnet_ids" {
  description = "Two private application subnet IDs used by the ECS Auto Scaling Group."
  type        = list(string)

  validation {
    condition     = length(var.private_app_subnet_ids) == 2
    error_message = "private_app_subnet_ids must contain exactly two subnet IDs."
  }
}

variable "ecs_security_group_id" {
  description = "Security group ID attached to ECS container instances."
  type        = string

  validation {
    condition     = length(trimspace(var.ecs_security_group_id)) > 0
    error_message = "ecs_security_group_id must not be empty."
  }
}

variable "ecs_optimized_ami_id" {
  description = "ECS-optimized Amazon Linux 2023 AMI ID resolved in the deployment region."
  type        = string

  validation {
    condition     = length(trimspace(var.ecs_optimized_ami_id)) > 0
    error_message = "ecs_optimized_ami_id must not be empty."
  }
}

variable "instance_type" {
  description = "Approved Learning ECS container instance type."
  type        = string
  default     = "m6i.xlarge"

  validation {
    condition     = var.instance_type == "m6i.xlarge"
    error_message = "instance_type must be m6i.xlarge for the approved two-AZ Learning design."
  }
}

variable "learning_runtime_enabled" {
  description = "Whether the Learning ASG should run capacity. False keeps min, desired, and max at zero."
  type        = bool
  default     = false
}

variable "common_tags" {
  description = "Common tags applied to ECS compute resources."
  type        = map(string)
}
