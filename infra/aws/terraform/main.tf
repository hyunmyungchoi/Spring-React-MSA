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

data "aws_ssm_parameter" "ecs_optimized_al2023_ami" {
  count = var.enable_ecs_compute_foundation ? 1 : 0

  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

module "network" {
  source = "./modules/network"

  name_prefix               = local.name_prefix
  vpc_cidr                  = var.vpc_cidr
  availability_zones        = local.availability_zones
  public_subnet_cidrs       = var.public_subnet_cidrs
  private_app_subnet_cidrs  = var.private_app_subnet_cidrs
  private_data_subnet_cidrs = var.private_data_subnet_cidrs
  aws_region                = var.aws_region
  enable_nat_gateway        = var.enable_nat_gateway
  common_tags               = local.common_tags
}

module "data_layer" {
  source = "./modules/data-layer"

  name_prefix              = local.name_prefix
  private_data_subnet_ids  = module.network.private_data_subnet_ids
  data_security_group_id   = module.network.data_security_group_id
  enable_data_layer        = var.enable_data_layer
  db_engine_version        = var.db_engine_version
  db_instance_class        = var.db_instance_class
  application_secret_names = local.application_secret_names
  common_tags              = local.common_tags
}

module "ecs_compute" {
  count  = var.enable_ecs_compute_foundation ? 1 : 0
  source = "./modules/ecs-compute"

  name_prefix              = local.name_prefix
  private_app_subnet_ids   = module.network.private_app_subnet_ids
  ecs_security_group_id    = module.network.ecs_security_group_id
  ecs_optimized_ami_id     = data.aws_ssm_parameter.ecs_optimized_al2023_ami[0].value
  instance_type            = var.ecs_instance_type
  learning_runtime_enabled = var.learning_runtime_enabled
  common_tags              = local.common_tags
}

module "database_tasks" {
  count  = var.enable_database_tasks_foundation ? 1 : 0
  source = "./modules/database-tasks"

  name_prefix             = local.name_prefix
  aws_region              = var.aws_region
  db_address              = module.data_layer.db_address
  db_port                 = module.data_layer.db_port
  db_name                 = module.data_layer.db_name
  master_secret_arn       = nonsensitive(module.data_layer.master_user_secret_arn)
  application_secret_arns = module.data_layer.application_secret_arns
  ecr_repository_arns     = module.ecr.repository_arns
  migration_images        = var.database_migration_images
  common_tags             = local.common_tags
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
