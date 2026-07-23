output "audit_log_group_name" {
  description = "Persistent restore drill audit log group, or null when the foundation is disabled."
  value       = try(aws_cloudwatch_log_group.audit["this"].name, null)
}

output "restored_db_identifier" {
  description = "Temporary restored DB identifier, or null while the drill is disabled."
  value       = try(aws_db_instance.restore["this"].identifier, null)
}

output "restored_db_address" {
  description = "Private temporary restored DB endpoint, or null while the drill is disabled."
  value       = try(aws_db_instance.restore["this"].address, null)
}

output "validator_task_definition_arn" {
  description = "Fargate validator task definition ARN, or null while the drill is disabled."
  value       = try(aws_ecs_task_definition.validator["this"].arn, null)
}

output "validator_security_group_id" {
  description = "No-ingress validator security group ID, or null while the drill is disabled."
  value       = try(aws_security_group.validator["this"].id, null)
}

output "restore_db_security_group_id" {
  description = "Isolated restored database security group ID, or null while the drill is disabled."
  value       = try(aws_security_group.restore_db["this"].id, null)
}

output "validator_run_configuration" {
  description = "Non-secret inputs required to run the one-off Fargate validator."
  value = var.restore_enabled ? {
    launch_type         = "FARGATE"
    platform_version    = "1.4.0"
    task_definition_arn = aws_ecs_task_definition.validator["this"].arn
    subnet_ids          = var.private_app_subnet_ids
    security_group_ids  = [aws_security_group.validator["this"].id]
    assign_public_ip    = "DISABLED"
  } : null
}
