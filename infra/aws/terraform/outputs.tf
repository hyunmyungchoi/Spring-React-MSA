output "vpc_id" {
  description = "ID of the foundation VPC."
  value       = module.network.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs in availability-zone order."
  value       = module.network.public_subnet_ids
}

output "private_app_subnet_ids" {
  description = "Private application subnet IDs in availability-zone order."
  value       = module.network.private_app_subnet_ids
}

output "private_data_subnet_ids" {
  description = "Private data subnet IDs in availability-zone order."
  value       = module.network.private_data_subnet_ids
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway attached to the VPC."
  value       = module.network.internet_gateway_id
}

output "nat_gateway_id" {
  description = "ID of the single learning NAT Gateway, or null when disabled."
  value       = module.network.nat_gateway_id
}

output "nat_eip_public_ip" {
  description = "Elastic public IPv4 address retained for the learning NAT Gateway across OFF and ON cycles."
  value       = module.network.nat_eip_public_ip
}

output "s3_gateway_endpoint_id" {
  description = "ID of the S3 Gateway VPC Endpoint for the Private App Route Table."
  value       = module.network.s3_gateway_endpoint_id
}

output "private_app_route_table_id" {
  description = "ID of the route table shared by the Private App subnets."
  value       = module.network.private_app_route_table_id
}

output "alb_security_group_id" {
  description = "ID of the future ALB Security Group."
  value       = module.network.alb_security_group_id
}

output "ecs_security_group_id" {
  description = "ID of the future ECS application Security Group."
  value       = module.network.ecs_security_group_id
}

output "data_security_group_id" {
  description = "ID of the future data tier Security Group."
  value       = module.network.data_security_group_id
}

output "db_instance_identifier" {
  description = "Learning RDS instance identifier, or null when disabled."
  value       = module.data_layer.db_instance_identifier
}

output "db_address" {
  description = "Private learning RDS endpoint, or null when disabled."
  value       = module.data_layer.db_address
}

output "db_name" {
  description = "Shared PostgreSQL database name, or null when disabled."
  value       = module.data_layer.db_name
}

output "db_port" {
  description = "Private PostgreSQL port, or null when disabled."
  value       = module.data_layer.db_port
}

output "rds_master_user_secret_arn" {
  description = "ARN of the RDS-managed bootstrap secret, or null when disabled."
  value       = module.data_layer.master_user_secret_arn
  sensitive   = true
}

output "application_secret_arns" {
  description = "Map of application secret container names to ARNs. Values are populated outside Terraform."
  value       = module.data_layer.application_secret_arns
}

output "ecs_cluster_name" {
  description = "ECS cluster name, or null when the ECS compute foundation is disabled."
  value       = try(module.ecs_compute[0].cluster_name, null)
}

output "ecs_capacity_provider_name" {
  description = "ECS EC2 capacity provider name, or null when the ECS compute foundation is disabled."
  value       = try(module.ecs_compute[0].capacity_provider_name, null)
}

output "ecs_autoscaling_group_name" {
  description = "ECS Auto Scaling Group name, or null when the ECS compute foundation is disabled."
  value       = try(module.ecs_compute[0].autoscaling_group_name, null)
}

output "ecs_instance_role_arn" {
  description = "ECS container instance IAM role ARN, or null when the ECS compute foundation is disabled."
  value       = try(module.ecs_compute[0].instance_role_arn, null)
}

output "ecs_runtime_capacity" {
  description = "Configured Learning ECS ASG capacity, or null when the ECS compute foundation is disabled."
  value       = try(module.ecs_compute[0].runtime_capacity, null)
}

output "ecs_awsvpc_trunking" {
  description = "Account-level awsvpcTrunking value managed by the ECS compute foundation."
  value       = try(module.ecs_compute[0].awsvpc_trunking, null)
}

output "redis_replication_group_id" {
  description = "Disposable Learning Valkey replication group ID, or null while Runtime is OFF."
  value       = module.cache.replication_group_id
}

output "redis_host_parameter_name" {
  description = "Stable SSM parameter name that exists only while the disposable Valkey runtime is ON."
  value       = module.cache.redis_host_parameter_name
}

output "application_task_definition_arns" {
  description = "Eight backend task definition ARNs, or an empty map when the application foundation is disabled."
  value       = try(module.application_runtime[0].task_definition_arns, {})
}

output "application_ecs_service_names" {
  description = "Eight backend ECS service names, or an empty map when the application foundation is disabled."
  value       = try(module.application_runtime[0].ecs_service_names, {})
}

output "application_service_discovery_namespace" {
  description = "Cloud Map private DNS namespace, or null when the application foundation is disabled."
  value       = try(module.application_runtime[0].service_discovery_namespace, null)
}

output "public_alb_dns_name" {
  description = "Disposable public ALB DNS name, or null while Runtime is OFF."
  value       = try(module.application_runtime[0].public_alb_dns_name, null)
}

output "database_bootstrap_task_definition_arn" {
  description = "Database bootstrap task definition ARN, or null when the database task foundation is disabled."
  value       = try(module.database_tasks[0].bootstrap_task_definition_arn, null)
}

output "database_bootstrap_task_family" {
  description = "Database bootstrap task family, or null when the database task foundation is disabled."
  value       = try(module.database_tasks[0].bootstrap_task_family, null)
}

output "database_bootstrap_log_group_name" {
  description = "CloudWatch log group for the database bootstrap task, or null when disabled."
  value       = try(module.database_tasks[0].bootstrap_log_group_name, null)
}

output "database_migration_task_definition_arns" {
  description = "Flyway migration task definition ARNs keyed by service. Empty until immutable images are configured."
  value       = try(module.database_tasks[0].migration_task_definition_arns, {})
}

output "availability_zones" {
  description = "Availability zones selected for the two-AZ network."
  value       = module.network.availability_zones
}

output "ecr_repository_names" {
  description = "Map of backend service names to ECR repository names."
  value       = module.ecr.repository_names
}

output "ecr_repository_arns" {
  description = "Map of backend service names to ECR repository ARNs."
  value       = module.ecr.repository_arns
}

output "ecr_repository_urls" {
  description = "Map of backend service names to ECR repository URLs."
  value       = module.ecr.repository_urls
}

output "github_actions_ecr_role_name" {
  description = "Name of the GitHub Actions ECR publication role."
  value       = module.github_actions_ecr.role_name
}

output "github_actions_ecr_role_arn" {
  description = "ARN registered as the GitHub AWS_ECR_PUSH_ROLE_ARN repository variable."
  value       = module.github_actions_ecr.role_arn
}
