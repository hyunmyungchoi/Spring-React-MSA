output "db_instance_identifier" {
  description = "RDS instance identifier, or null when the data layer is disabled."
  value       = one(aws_db_instance.this[*].identifier)
}

output "db_address" {
  description = "Private RDS endpoint address, or null when the data layer is disabled."
  value       = one(aws_db_instance.this[*].address)
}

output "db_port" {
  description = "PostgreSQL port, or null when the data layer is disabled."
  value       = one(aws_db_instance.this[*].port)
}

output "db_name" {
  description = "Shared PostgreSQL database name, or null when the data layer is disabled."
  value       = one(aws_db_instance.this[*].db_name)
}

output "master_user_secret_arn" {
  description = "ARN of the RDS-managed bootstrap secret, or null when disabled."
  value       = one(flatten(aws_db_instance.this[*].master_user_secret)[*].secret_arn)
  sensitive   = true
}

output "application_secret_arns" {
  description = "Map of empty application secret container names to ARNs."
  value       = { for name, secret in aws_secretsmanager_secret.application : name => secret.arn }
}

output "db_subnet_group_name" {
  description = "RDS subnet group name, or null when disabled."
  value       = one(aws_db_subnet_group.this[*].name)
}

output "db_parameter_group_name" {
  description = "RDS parameter group name, or null when disabled."
  value       = one(aws_db_parameter_group.this[*].name)
}
