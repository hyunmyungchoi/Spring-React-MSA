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
