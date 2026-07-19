variable "project_name" {
  description = "Project name used for global DNS and certificate tags."
  type        = string
  default     = "spring-react-msa"
}

variable "environment" {
  description = "Environment represented by the public domain contract."
  type        = string
  default     = "learning"

  validation {
    condition     = var.environment == "learning"
    error_message = "environment must be learning for this stack."
  }
}

variable "aws_region" {
  description = "AWS Region containing the ALB origin certificate."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "aws_region must be ap-northeast-2."
  }
}

variable "root_domain" {
  description = "Existing public Route 53 hosted-zone domain."
  type        = string
  default     = "hyuncloudlab.com"

  validation {
    condition     = var.root_domain == "hyuncloudlab.com"
    error_message = "root_domain must remain hyuncloudlab.com for the approved public contract."
  }
}

variable "hosted_zone_id" {
  description = "Existing public Route 53 hosted-zone ID imported into this state; never create a replacement zone."
  type        = string

  validation {
    condition     = can(regex("^Z[A-Z0-9]+$", var.hosted_zone_id))
    error_message = "hosted_zone_id must be a valid public Route 53 hosted-zone ID."
  }
}
