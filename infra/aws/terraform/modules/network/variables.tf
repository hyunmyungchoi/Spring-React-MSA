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

variable "common_tags" {
  description = "Common tags applied to taggable network resources."
  type        = map(string)
}
