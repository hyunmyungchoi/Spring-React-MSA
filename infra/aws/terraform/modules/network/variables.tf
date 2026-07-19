variable "name_prefix" {
  description = "Prefix used for network resource names."
  type        = string
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block for the VPC."
  type        = string
}

variable "availability_zones" {
  description = "Exactly two availability zones used by the network."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "availability_zones must contain exactly two entries."
  }
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDR blocks in availability-zone order."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "public_subnet_cidrs must contain exactly two entries."
  }
}

variable "private_app_subnet_cidrs" {
  description = "Private application subnet CIDR blocks in availability-zone order."
  type        = list(string)

  validation {
    condition     = length(var.private_app_subnet_cidrs) == 2
    error_message = "private_app_subnet_cidrs must contain exactly two entries."
  }
}

variable "private_data_subnet_cidrs" {
  description = "Private data subnet CIDR blocks in availability-zone order."
  type        = list(string)

  validation {
    condition     = length(var.private_data_subnet_cidrs) == 2
    error_message = "private_data_subnet_cidrs must contain exactly two entries."
  }
}

variable "aws_region" {
  description = "AWS region used to build regional VPC endpoint service names."
  type        = string
}

variable "enable_nat_gateway" {
  description = "Whether to create the single learning NAT Gateway and Private App default route."
  type        = bool
}

variable "cloudfront_origin_prefix_list_id" {
  description = "AWS-managed CloudFront origin-facing prefix list used to restrict ALB HTTPS ingress; null preserves the pre-domain public baseline."
  type        = string
  default     = null
}

variable "common_tags" {
  description = "Common tags applied to taggable network resources."
  type        = map(string)
}
