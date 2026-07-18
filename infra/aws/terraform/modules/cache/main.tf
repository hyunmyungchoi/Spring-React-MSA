data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  runtime = var.runtime_enabled ? { this = true } : {}
}

resource "aws_elasticache_subnet_group" "this" {
  for_each = local.runtime

  name       = "${var.name_prefix}-valkey"
  subnet_ids = var.private_data_subnet_ids

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey"
    Runtime = "disposable"
  })
}

resource "aws_elasticache_parameter_group" "this" {
  for_each = local.runtime

  name   = "${var.name_prefix}-valkey7"
  family = "valkey7"

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey7"
    Runtime = "disposable"
  })
}

resource "aws_elasticache_user" "default_disabled" {
  for_each = local.runtime

  user_id              = "${var.name_prefix}-default"
  user_name            = "default"
  access_string        = "off ~* -@all"
  engine               = "valkey"
  no_password_required = true

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey-default-disabled"
    Runtime = "disposable"
  })
}

resource "aws_elasticache_user" "application" {
  for_each = local.runtime

  user_id              = "${var.name_prefix}-application"
  user_name            = "spring-msa"
  access_string        = "on ~* +@all"
  engine               = "valkey"
  no_password_required = false
  passwords_wo         = var.redis_password
  passwords_wo_version = var.redis_password_version

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey-application"
    Runtime = "disposable"
  })

  lifecycle {
    precondition {
      condition     = var.redis_password != null
      error_message = "runtime_enabled requires redis_password to be supplied ephemerally."
    }
  }
}

resource "aws_elasticache_user_group" "this" {
  for_each = local.runtime

  engine        = "valkey"
  user_group_id = "${var.name_prefix}-valkey"
  user_ids = [
    aws_elasticache_user.default_disabled[each.key].user_id,
    aws_elasticache_user.application[each.key].user_id,
  ]

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey"
    Runtime = "disposable"
  })
}

resource "aws_elasticache_replication_group" "this" {
  for_each = local.runtime

  replication_group_id = "${var.name_prefix}-valkey"
  description          = "Disposable single-node Valkey runtime for the Learning environment"

  engine               = "valkey"
  engine_version       = "7.2"
  node_type            = "cache.t4g.micro"
  parameter_group_name = aws_elasticache_parameter_group.this[each.key].name
  port                 = 6379

  num_cache_clusters         = 1
  automatic_failover_enabled = false
  multi_az_enabled           = false

  subnet_group_name  = aws_elasticache_subnet_group.this[each.key].name
  security_group_ids = [var.data_security_group_id]
  user_group_ids     = [aws_elasticache_user_group.this[each.key].user_group_id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"

  auto_minor_version_upgrade = true
  apply_immediately          = true
  snapshot_retention_limit   = 0
  maintenance_window         = "sun:19:00-sun:20:00"

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-valkey"
    Runtime = "disposable"
  })
}

resource "aws_ssm_parameter" "redis_host" {
  for_each = local.runtime

  name        = var.redis_host_parameter_name
  description = "Disposable Learning Valkey primary endpoint consumed by ECS tasks."
  type        = "String"
  value       = aws_elasticache_replication_group.this[each.key].primary_endpoint_address

  tags = merge(var.common_tags, {
    Name    = var.redis_host_parameter_name
    Runtime = "disposable"
  })
}
