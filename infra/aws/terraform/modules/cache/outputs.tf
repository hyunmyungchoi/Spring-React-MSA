output "redis_host_parameter_name" {
  description = "Stable SSM parameter name referenced by ECS task definitions."
  value       = var.redis_host_parameter_name
}

output "redis_host_parameter_arn" {
  description = "Stable SSM parameter ARN referenced by ECS execution roles."
  value       = "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.redis_host_parameter_name}"
}

output "primary_endpoint_address" {
  description = "Valkey primary endpoint, or null while the Learning runtime is OFF."
  value       = try(aws_elasticache_replication_group.this["this"].primary_endpoint_address, null)
}

output "replication_group_id" {
  description = "Valkey replication group ID, or null while the Learning runtime is OFF."
  value       = try(aws_elasticache_replication_group.this["this"].replication_group_id, null)
}
