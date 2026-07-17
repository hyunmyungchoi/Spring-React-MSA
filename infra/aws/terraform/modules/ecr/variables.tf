variable "name_prefix" {
  description = "Prefix used for ECR repository namespaces."
  type        = string
}

variable "service_names" {
  description = "Backend service names that receive private ECR repositories."
  type        = set(string)
}

variable "tagged_image_retention_count" {
  description = "Number of tagged images retained in each repository."
  type        = number

  validation {
    condition     = var.tagged_image_retention_count > 0
    error_message = "tagged_image_retention_count must be greater than zero."
  }
}

variable "untagged_image_retention_days" {
  description = "Number of days untagged images are retained."
  type        = number

  validation {
    condition     = var.untagged_image_retention_days > 0
    error_message = "untagged_image_retention_days must be greater than zero."
  }
}

variable "common_tags" {
  description = "Tags applied to every managed ECR repository."
  type        = map(string)
}
