output "service_discovery_namespace" {
  description = "Private Cloud Map DNS namespace used by the eight backend services."
  value       = aws_service_discovery_private_dns_namespace.this.name
}

output "task_definition_arns" {
  description = "Application task definition ARNs keyed by short service name."
  value       = { for key, task in aws_ecs_task_definition.service : key => task.arn }
}

output "ecs_service_names" {
  description = "ECS service names keyed by short service name."
  value       = { for key, service in aws_ecs_service.backend : key => service.name }
}

output "log_group_names" {
  description = "CloudWatch log group names keyed by short service name."
  value       = { for key, group in aws_cloudwatch_log_group.service : key => group.name }
}

output "public_alb_dns_name" {
  description = "Disposable public ALB DNS name, or null while Runtime is OFF."
  value       = try(aws_lb.public["this"].dns_name, null)
}

output "public_alb_zone_id" {
  description = "Disposable public ALB Route 53 zone ID, or null while Runtime is OFF."
  value       = try(aws_lb.public["this"].zone_id, null)
}

output "public_alb_arn_suffix" {
  description = "Disposable public ALB ARN suffix used by CloudWatch metrics, or null while Runtime is OFF."
  value       = try(aws_lb.public["this"].arn_suffix, null)
}

output "public_target_group_arn_suffixes" {
  description = "Public gateway target group ARN suffixes keyed by service name."
  value       = { for key, group in aws_lb_target_group.gateway : key => group.arn_suffix }
}
