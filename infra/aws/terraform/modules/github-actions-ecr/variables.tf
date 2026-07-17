variable "name_prefix" {
  description = "Prefix used for the GitHub Actions IAM role and policy."
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the existing GitHub Actions OIDC provider."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository in owner/name format."
  type        = string
}

variable "github_branch_ref" {
  description = "Full Git ref allowed to assume the role."
  type        = string
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs that GitHub Actions may publish to."
  type        = set(string)
}

variable "common_tags" {
  description = "Tags applied to the GitHub Actions IAM role."
  type        = map(string)
}
