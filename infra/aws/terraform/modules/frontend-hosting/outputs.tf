output "bucket_names" {
  description = "Private frontend bucket names keyed by the six independent deployment units."
  value       = { for key, bucket in aws_s3_bucket.site : key => bucket.bucket }
}

output "distribution_ids" {
  description = "CloudFront distribution IDs keyed by member and admin."
  value       = { for key, distribution in aws_cloudfront_distribution.site : key => distribution.id }
}

output "distribution_domain_names" {
  description = "CloudFront default domain names keyed by member and admin."
  value       = { for key, distribution in aws_cloudfront_distribution.site : key => distribution.domain_name }
}

output "deployment_role_name" {
  description = "GitHub Actions role name for independent frontend deployment."
  value       = aws_iam_role.github_frontend_deploy.name
}

output "deployment_role_arn" {
  description = "GitHub Actions role ARN for independent frontend deployment."
  value       = aws_iam_role.github_frontend_deploy.arn
}
