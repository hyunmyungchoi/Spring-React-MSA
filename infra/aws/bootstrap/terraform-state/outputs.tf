output "state_bucket_name" {
  description = "Globally unique S3 bucket name for Terraform state."
  value       = aws_s3_bucket.state.id
}

output "state_access_role_arn" {
  description = "IAM role ARN used by the S3 backend."
  value       = aws_iam_role.state_access.arn
}

output "state_key" {
  description = "S3 object key for the learning Terraform state."
  value       = var.state_key
}

output "state_keys" {
  description = "All S3 object keys accessible through the dedicated state role."
  value       = local.state_keys
}
