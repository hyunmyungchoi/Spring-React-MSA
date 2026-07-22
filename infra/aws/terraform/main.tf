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

data "aws_caller_identity" "frontend" {
  count = var.enable_frontend_hosting ? 1 : 0
}

data "aws_route53_zone" "public" {
  count = var.enable_public_domain_routing ? 1 : 0

  name         = var.root_domain
  private_zone = false
}

data "aws_acm_certificate" "cloudfront" {
  count    = var.enable_public_domain_routing ? 1 : 0
  provider = aws.us_east_1

  domain      = var.root_domain
  statuses    = ["ISSUED"]
  types       = ["AMAZON_ISSUED"]
  most_recent = true
}

data "aws_acm_certificate" "origin" {
  count = var.enable_public_domain_routing ? 1 : 0

  domain      = "origin.${var.root_domain}"
  statuses    = ["ISSUED"]
  types       = ["AMAZON_ISSUED"]
  most_recent = true
}

data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  count = var.enable_public_domain_routing ? 1 : 0

  name = "com.amazonaws.global.cloudfront.origin-facing"
}

data "aws_ssm_parameter" "ecs_optimized_al2023_ami" {
  count = var.enable_ecs_compute_foundation ? 1 : 0

  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

module "network" {
  source = "./modules/network"

  name_prefix                      = local.name_prefix
  vpc_cidr                         = var.vpc_cidr
  availability_zones               = local.availability_zones
  public_subnet_cidrs              = var.public_subnet_cidrs
  private_app_subnet_cidrs         = var.private_app_subnet_cidrs
  private_data_subnet_cidrs        = var.private_data_subnet_cidrs
  aws_region                       = var.aws_region
  enable_nat_gateway               = var.enable_nat_gateway
  cloudfront_origin_prefix_list_id = try(data.aws_ec2_managed_prefix_list.cloudfront_origin[0].id, null)
  common_tags                      = local.common_tags
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

module "observability" {
  count  = var.enable_observability_foundation ? 1 : 0
  source = "./modules/observability"

  name_prefix                   = local.name_prefix
  aws_region                    = var.aws_region
  alert_email                   = coalesce(var.budget_alert_email, "disabled@example.invalid")
  db_instance_identifier        = module.data_layer.db_instance_identifier
  db_instance_arn               = module.data_layer.db_instance_arn
  runtime_observability_enabled = var.enable_runtime_observability
  runtime_enabled               = var.learning_runtime_enabled
  watchdog_enabled              = var.enable_runtime_watchdog
  watchdog_schedule_expression  = var.runtime_watchdog_schedule_expression
  watchdog_max_runtime_hours    = var.runtime_watchdog_max_runtime_hours
  watchdog_rds_warning_hours    = var.runtime_watchdog_rds_restart_warning_hours
  ecs_cluster_name              = try(module.ecs_compute[0].cluster_name, "")
  ecs_autoscaling_group_name    = try(module.ecs_compute[0].autoscaling_group_name, "")
  ecs_service_names             = try(module.application_runtime[0].ecs_service_names, {})
  load_balancer_arn_suffix      = try(module.application_runtime[0].public_alb_arn_suffix, null)
  target_group_arn_suffixes     = try(module.application_runtime[0].public_target_group_arn_suffixes, {})
  common_tags                   = local.common_tags
}

module "ecs_compute" {
  count  = var.enable_ecs_compute_foundation ? 1 : 0
  source = "./modules/ecs-compute"

  name_prefix               = local.name_prefix
  private_app_subnet_ids    = module.network.private_app_subnet_ids
  ecs_security_group_id     = module.network.ecs_security_group_id
  ecs_optimized_ami_id      = data.aws_ssm_parameter.ecs_optimized_al2023_ami[0].value
  instance_type             = var.ecs_instance_type
  learning_runtime_enabled  = var.learning_runtime_enabled
  enable_container_insights = var.enable_runtime_observability && var.learning_runtime_enabled
  common_tags               = local.common_tags
}

module "cache" {
  source = "./modules/cache"

  name_prefix             = local.name_prefix
  aws_region              = var.aws_region
  private_data_subnet_ids = module.network.private_data_subnet_ids
  data_security_group_id  = module.network.data_security_group_id
  runtime_enabled         = var.learning_runtime_enabled
  redis_password          = var.redis_password
  redis_password_version  = var.redis_password_version
  common_tags             = local.common_tags
}

module "database_tasks" {
  count  = var.enable_database_tasks_foundation ? 1 : 0
  source = "./modules/database-tasks"

  name_prefix                 = local.name_prefix
  aws_region                  = var.aws_region
  db_address                  = module.data_layer.db_address
  db_port                     = module.data_layer.db_port
  db_name                     = module.data_layer.db_name
  master_secret_arn           = nonsensitive(module.data_layer.master_user_secret_arn)
  application_secret_arns     = module.data_layer.application_secret_arns
  ecr_repository_arns         = module.ecr.repository_arns
  migration_images            = var.database_migration_images
  admin_bootstrap_enabled     = var.enable_admin_bootstrap_foundation
  admin_bootstrap_image       = try(var.application_images["user-service"], null)
  admin_bootstrap_secret_name = "/${var.project_name}/${var.environment}/admin-bootstrap"
  common_tags                 = local.common_tags
}

module "application_runtime" {
  count  = var.enable_application_runtime_foundation ? 1 : 0
  source = "./modules/application-runtime"

  name_prefix                  = local.name_prefix
  aws_region                   = var.aws_region
  vpc_id                       = module.network.vpc_id
  public_subnet_ids            = module.network.public_subnet_ids
  private_app_subnet_ids       = module.network.private_app_subnet_ids
  alb_security_group_id        = module.network.alb_security_group_id
  ecs_security_group_id        = module.network.ecs_security_group_id
  ecs_cluster_arn              = module.ecs_compute[0].cluster_arn
  capacity_provider_name       = module.ecs_compute[0].capacity_provider_name
  service_images               = var.application_images
  ecr_repository_arns          = module.ecr.repository_arns
  application_secret_arns      = module.data_layer.application_secret_arns
  redis_host_parameter_arn     = module.cache.redis_host_parameter_arn
  db_address                   = module.data_layer.db_address
  db_port                      = module.data_layer.db_port
  db_name                      = module.data_layer.db_name
  learning_runtime_enabled     = var.learning_runtime_enabled
  toss_api_client_id           = var.toss_api_client_id
  enable_public_domain_routing = var.enable_public_domain_routing
  public_hosted_zone_id        = try(data.aws_route53_zone.public[0].zone_id, null)
  origin_domain                = "origin.${var.root_domain}"
  origin_certificate_arn       = try(data.aws_acm_certificate.origin[0].arn, null)
  common_tags                  = local.common_tags

  depends_on = [module.cache]
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

module "frontend_hosting" {
  count  = var.enable_frontend_hosting ? 1 : 0
  source = "./modules/frontend-hosting"

  name_prefix                  = local.name_prefix
  account_id                   = data.aws_caller_identity.frontend[0].account_id
  oidc_provider_arn            = data.aws_iam_openid_connect_provider.github.arn
  github_repository            = local.github_repository
  github_branch_ref            = local.github_branch_ref
  enable_public_domain_routing = var.enable_public_domain_routing
  root_domain                  = var.root_domain
  public_hosted_zone_id        = try(data.aws_route53_zone.public[0].zone_id, null)
  cloudfront_certificate_arn   = try(data.aws_acm_certificate.cloudfront[0].arn, null)
  origin_domain                = "origin.${var.root_domain}"
  common_tags                  = local.common_tags
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
