variable "name_prefix" {
  description = "Prefix used for frontend buckets, CloudFront distributions, and the deployment role."
  type        = string

  validation {
    condition     = length(trimspace(var.name_prefix)) > 0
    error_message = "name_prefix must not be empty."
  }
}

variable "account_id" {
  description = "AWS account ID used to make the six frontend bucket names globally unique."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit AWS account ID."
  }
}

variable "oidc_provider_arn" {
  description = "ARN of the existing GitHub Actions OIDC provider."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository in owner/name format."
  type        = string

  validation {
    condition     = can(regex("^[^/]+/[^/]+$", var.github_repository))
    error_message = "github_repository must use owner/name format."
  }
}

variable "github_branch_ref" {
  description = "Full Git ref allowed to assume the frontend deployment role."
  type        = string

  validation {
    condition     = startswith(var.github_branch_ref, "refs/heads/")
    error_message = "github_branch_ref must be a full branch ref."
  }
}

variable "enable_public_domain_routing" {
  description = "Whether to attach Route 53 aliases, ACM viewer TLS, and the CloudFront API origin."
  type        = bool
  default     = false
}

variable "root_domain" {
  description = "Approved root domain; the Member distribution redirects this hostname to app."
  type        = string
  default     = "hyuncloudlab.com"
}

variable "public_hosted_zone_id" {
  description = "Existing public Route 53 hosted-zone ID."
  type        = string
  default     = null
}

variable "cloudfront_certificate_arn" {
  description = "Issued us-east-1 ACM certificate ARN for root, app, and admin aliases."
  type        = string
  default     = null
}

variable "origin_domain" {
  description = "TLS custom origin used for API, OAuth, session, and WebSocket routes."
  type        = string
  default     = "origin.hyuncloudlab.com"
}

variable "common_tags" {
  description = "Tags applied to frontend resources that support tags."
  type        = map(string)
}
