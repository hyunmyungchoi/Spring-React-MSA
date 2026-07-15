output "repository_names" {
  description = "Map of backend service names to ECR repository names."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.name }
}

output "repository_arns" {
  description = "Map of backend service names to ECR repository ARNs."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.arn }
}

output "repository_urls" {
  description = "Map of backend service names to ECR repository URLs."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.repository_url }
}
