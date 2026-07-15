output "role_name" {
  description = "Name of the GitHub Actions ECR publication role."
  value       = aws_iam_role.this.name
}

output "role_arn" {
  description = "ARN of the GitHub Actions ECR publication role."
  value       = aws_iam_role.this.arn
}
