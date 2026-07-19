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

variable "common_tags" {
  description = "Tags applied to frontend resources that support tags."
  type        = map(string)
}
