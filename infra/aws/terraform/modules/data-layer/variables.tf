variable "name_prefix" {
  description = "Prefix used for learning data-layer resource names."
  type        = string
}

variable "private_data_subnet_ids" {
  description = "Private Data subnet IDs used by the RDS subnet group."
  type        = list(string)

  validation {
    condition     = length(var.private_data_subnet_ids) == 2
    error_message = "private_data_subnet_ids must contain exactly two subnet IDs."
  }
}

variable "data_security_group_id" {
  description = "Security Group ID that permits PostgreSQL only from the ECS tier."
  type        = string
}

variable "enable_data_layer" {
  description = "Whether to create the persistent learning RDS and application secret containers."
  type        = bool
  default     = false
}

variable "db_engine_version" {
  description = "PostgreSQL engine version verified for the learning region."
  type        = string
  default     = "16.14"
}

variable "db_instance_class" {
  description = "Small Graviton RDS class used by the learning environment."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Shared PostgreSQL database name."
  type        = string
  default     = "spring_msa"
}

variable "master_username" {
  description = "Bootstrap-only RDS master username. The password is managed by RDS in Secrets Manager."
  type        = string
  default     = "spring_admin"
}

variable "application_secret_names" {
  description = "Secrets Manager container names. Values are populated outside Terraform."
  type        = set(string)
}

variable "common_tags" {
  description = "Tags applied to all data-layer resources."
  type        = map(string)
  default     = {}
}
