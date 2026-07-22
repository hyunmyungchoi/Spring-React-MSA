output "bootstrap_task_definition_arn" {
  description = "ARN of the database role and schema bootstrap task definition."
  value       = aws_ecs_task_definition.db_bootstrap.arn
}

output "bootstrap_task_family" {
  description = "Family name used to run the database bootstrap task."
  value       = aws_ecs_task_definition.db_bootstrap.family
}

output "bootstrap_log_group_name" {
  description = "CloudWatch log group for the database bootstrap task."
  value       = aws_cloudwatch_log_group.db_bootstrap.name
}

output "bootstrap_execution_role_arn" {
  description = "Least-privilege execution role used by the database bootstrap task."
  value       = aws_iam_role.db_bootstrap_execution.arn
}

output "migration_task_definition_arns" {
  description = "Migration task definition ARNs keyed by service. Empty until immutable application images are supplied."
  value       = { for key, task in aws_ecs_task_definition.migration : key => task.arn }
}

output "migration_task_families" {
  description = "Migration task family names keyed by service."
  value       = { for key, task in aws_ecs_task_definition.migration : key => task.family }
}

output "migration_log_group_names" {
  description = "Migration CloudWatch log group names keyed by service."
  value       = { for key, log_group in aws_cloudwatch_log_group.migration : key => log_group.name }
}

output "admin_bootstrap_task_definition_arn" {
  description = "Temporary initial administrator bootstrap task definition ARN, or null when disabled."
  value       = try(aws_ecs_task_definition.admin_bootstrap["this"].arn, null)
}

output "admin_bootstrap_task_family" {
  description = "Temporary initial administrator bootstrap task family, or null when disabled."
  value       = try(aws_ecs_task_definition.admin_bootstrap["this"].family, null)
}

output "admin_bootstrap_secret_arn" {
  description = "Temporary initial administrator input secret ARN, or null when disabled."
  value       = try(aws_secretsmanager_secret.admin_bootstrap["this"].arn, null)
}

output "admin_bootstrap_log_group_name" {
  description = "Persistent audit log group for initial administrator bootstrap runs."
  value       = aws_cloudwatch_log_group.admin_bootstrap.name
}
