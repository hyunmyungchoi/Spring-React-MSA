variable "project_name" {
  description = "Project name used in the backend bucket and IAM role names."
  type        = string
  default     = "spring-react-msa"

  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.project_name))
    error_message = "project_name must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "environment" {
  description = "Environment whose Terraform state is stored by this backend."
  type        = string
  default     = "learning"

  validation {
    condition     = var.environment == "learning"
    error_message = "This bootstrap stack supports only the learning environment."
  }
}

variable "aws_region" {
  description = "AWS region for the Terraform state bucket."
  type        = string
  default     = "ap-northeast-2"
}

variable "operator_user_name" {
  description = "Existing IAM user allowed to assume the dedicated Terraform state role."
  type        = string
  default     = "hyun-terraform-admin"

  validation {
    condition     = length(trimspace(var.operator_user_name)) > 0
    error_message = "operator_user_name must not be blank."
  }
}

variable "state_key" {
  description = "S3 object key for the existing learning Terraform state."
  type        = string
  default     = "learning/runtime/terraform.tfstate"

  validation {
    condition = (
      length(trimspace(var.state_key)) > 0 &&
      !startswith(var.state_key, "/") &&
      endswith(var.state_key, ".tfstate")
    )
    error_message = "state_key must be a relative path ending in .tfstate."
  }
}

variable "additional_state_keys" {
  description = "Additional S3 object keys managed by the same least-privilege Terraform state role."
  type        = set(string)
  default     = []

  validation {
    condition = alltrue([
      for key in var.additional_state_keys :
      length(trimspace(key)) > 0 &&
      !startswith(key, "/") &&
      endswith(key, ".tfstate")
    ])
    error_message = "Every additional_state_keys entry must be a relative path ending in .tfstate."
  }

  validation {
    condition     = !contains(var.additional_state_keys, var.state_key)
    error_message = "additional_state_keys must not repeat state_key."
  }
}
