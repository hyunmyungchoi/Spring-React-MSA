data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  bucket_name = "${local.name_prefix}-tfstate-${data.aws_caller_identity.current.account_id}-${var.aws_region}"
  bucket_arn  = "arn:${data.aws_partition.current.partition}:s3:::${local.bucket_name}"
  lock_key    = "${var.state_key}.tflock"

  operator_user_arn = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:user/${var.operator_user_name}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform-bootstrap"
    Purpose     = "terraform-state"
  }
}
