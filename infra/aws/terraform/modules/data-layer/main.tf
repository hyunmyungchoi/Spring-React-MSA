resource "aws_db_subnet_group" "this" {
  count = var.enable_data_layer ? 1 : 0

  name       = "${var.name_prefix}-postgres"
  subnet_ids = var.private_data_subnet_ids

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-postgres"
  })
}

resource "aws_db_parameter_group" "this" {
  count = var.enable_data_layer ? 1 : 0

  name   = "${var.name_prefix}-postgres16"
  family = "postgres16"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-postgres16"
  })
}

resource "aws_db_instance" "this" {
  count = var.enable_data_layer ? 1 : 0

  identifier = "${var.name_prefix}-postgres"

  engine                      = "postgres"
  engine_version              = var.db_engine_version
  instance_class              = var.db_instance_class
  db_name                     = var.db_name
  username                    = var.master_username
  manage_master_user_password = true

  allocated_storage     = 20
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this[0].name
  parameter_group_name   = aws_db_parameter_group.this[0].name
  vpc_security_group_ids = [var.data_security_group_id]
  publicly_accessible    = false
  multi_az               = false
  port                   = 5432

  auto_minor_version_upgrade = true
  apply_immediately          = false
  maintenance_window         = "sun:18:00-sun:19:00"

  backup_retention_period  = 7
  backup_window            = "17:00-18:00"
  copy_tags_to_snapshot    = true
  delete_automated_backups = false

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name_prefix}-postgres-final"

  performance_insights_enabled = false
  monitoring_interval          = 0

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-postgres"
    Runtime = "stoppable"
  })

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret" "application" {
  for_each = var.enable_data_layer ? var.application_secret_names : toset([])

  name                    = each.value
  description             = "Empty learning secret container managed by Terraform; populate the value outside Terraform."
  recovery_window_in_days = 7

  tags = merge(var.common_tags, {
    Name = each.value
  })

  lifecycle {
    prevent_destroy = true
  }
}
