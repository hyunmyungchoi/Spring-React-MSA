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
