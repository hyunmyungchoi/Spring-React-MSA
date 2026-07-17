data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

module "network" {
  source = "./modules/network"

  name_prefix               = local.name_prefix
  vpc_cidr                  = var.vpc_cidr
  availability_zones        = local.availability_zones
  public_subnet_cidrs       = var.public_subnet_cidrs
  private_app_subnet_cidrs  = var.private_app_subnet_cidrs
  private_data_subnet_cidrs = var.private_data_subnet_cidrs
  common_tags               = local.common_tags
}

module "ecr" {
  source = "./modules/ecr"

  name_prefix                   = local.name_prefix
  service_names                 = local.backend_service_names
  tagged_image_retention_count  = 5
  untagged_image_retention_days = 1
  common_tags                   = local.common_tags
}

module "github_actions_ecr" {
  source = "./modules/github-actions-ecr"

  name_prefix         = local.name_prefix
  oidc_provider_arn   = data.aws_iam_openid_connect_provider.github.arn
  github_repository   = local.github_repository
  github_branch_ref   = local.github_branch_ref
  ecr_repository_arns = toset(values(module.ecr.repository_arns))
  common_tags         = local.common_tags
}

resource "aws_budgets_budget" "monthly" {
  count = var.enable_budget ? 1 : 0

  name         = "${local.name_prefix}-monthly-budget"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = local.budget_thresholds

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "ABSOLUTE_VALUE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_alert_email]
    }
  }

  tags = local.common_tags
}
