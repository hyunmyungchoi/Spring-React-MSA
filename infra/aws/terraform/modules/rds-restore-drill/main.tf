locals {
  foundation = var.foundation_enabled ? { this = true } : {}
  restore    = var.restore_enabled ? { this = true } : {}

  temporary_tags = merge(var.common_tags, {
    Lifecycle = "temporary-restore-drill"
    ExpiresAt = coalesce(var.expires_at_utc, "disabled")
  })

  validator_script = <<-SCRIPT
    set -eu

    test -n "$DB_ADDRESS"
    test -n "$DB_PORT"
    test -n "$DB_NAME"
    test -n "$DB_MASTER_USERNAME"
    test -n "$DB_MASTER_PASSWORD"

    export PGHOST="$DB_ADDRESS"
    export PGPORT="$DB_PORT"
    export PGDATABASE="$DB_NAME"
    export PGUSER="$DB_MASTER_USERNAME"
    export PGPASSWORD="$DB_MASTER_PASSWORD"
    export PGSSLMODE=require
    export PGCONNECT_TIMEOUT=15
    export PGAPPNAME=rds-restore-drill-validator

    observed="$(
      psql --no-password --set=ON_ERROR_STOP=1 --quiet --tuples-only --no-align <<'SQL'
    BEGIN TRANSACTION READ ONLY;

    WITH expected_tables(schema_name, table_name) AS (
      VALUES
        ('user_service', 'users'),
        ('user_service', 'user_roles'),
        ('member_bff', 'chat_rooms'),
        ('member_bff', 'chat_messages'),
        ('stock_service', 'stock_watch_items')
    ),
    table_state AS (
      SELECT
        expected.schema_name,
        expected.table_name,
        role.rolname AS owner_name,
        role.rolcanlogin
      FROM expected_tables expected
      JOIN pg_namespace namespace
        ON namespace.nspname = expected.schema_name
      JOIN pg_class relation
        ON relation.relnamespace = namespace.oid
       AND relation.relname = expected.table_name
       AND relation.relkind IN ('r', 'p')
      JOIN pg_roles role
        ON role.oid = relation.relowner
    ),
    schema_roles AS (
      SELECT
        schema_name,
        min(owner_name) AS owner_name,
        count(DISTINCT owner_name) AS owner_count
      FROM table_state
      GROUP BY schema_name
    ),
    flyway_rows AS (
      SELECT version, success FROM user_service.flyway_schema_history
      UNION ALL
      SELECT version, success FROM member_bff.flyway_schema_history
      UNION ALL
      SELECT version, success FROM stock_service.flyway_schema_history
    ),
    admin_roles AS (
      SELECT
        users.user_id,
        users.enabled,
        bool_or(roles.role = 'ROLE_ADMIN') AS has_admin,
        bool_or(roles.role = 'ROLE_USER') AS has_user
      FROM user_service.users users
      JOIN user_service.user_roles roles
        ON roles.user_id = users.user_id
      GROUP BY users.user_id, users.enabled
    ),
    metrics AS (
      SELECT
        current_setting('transaction_read_only') AS transaction_mode,
        (SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()) AS tls_enabled,
        (
          SELECT count(*)
          FROM pg_namespace
          WHERE nspname IN ('user_service', 'member_bff', 'stock_service')
        ) AS schema_count,
        (
          SELECT count(*)
          FROM pg_namespace
          WHERE nspname IN ('user_service', 'member_bff', 'stock_service')
            AND pg_get_userbyid(nspowner) = current_user
        ) AS bootstrap_owned_schema_count,
        (SELECT count(DISTINCT owner_name) FROM table_state) AS application_role_count,
        (SELECT count(*) FROM table_state) AS expected_table_count,
        (
          SELECT count(*)
          FROM pg_class relation
          JOIN pg_namespace namespace
            ON namespace.oid = relation.relnamespace
          WHERE namespace.nspname IN ('user_service', 'member_bff', 'stock_service')
            AND relation.relkind IN ('r', 'p')
            AND relation.relname <> 'flyway_schema_history'
        ) AS application_table_count,
        (SELECT count(*) FROM schema_roles WHERE owner_count = 1) AS single_owner_schema_count,
        (
          SELECT count(*)
          FROM (
            SELECT DISTINCT owner_name, rolcanlogin
            FROM table_state
          ) owners
          WHERE rolcanlogin
        ) AS login_role_count,
        (
          SELECT count(*)
          FROM schema_roles
          WHERE owner_count = 1
            AND has_schema_privilege(owner_name, schema_name, 'USAGE')
            AND has_schema_privilege(owner_name, schema_name, 'CREATE')
        ) AS own_schema_grant_count,
        (
          SELECT count(*)
          FROM schema_roles role
          CROSS JOIN (
            VALUES ('user_service'), ('member_bff'), ('stock_service')
          ) target(schema_name)
          WHERE role.schema_name <> target.schema_name
            AND (
              has_schema_privilege(role.owner_name, target.schema_name, 'USAGE')
              OR has_schema_privilege(role.owner_name, target.schema_name, 'CREATE')
            )
        ) AS cross_schema_grant_count,
        (
          SELECT count(*)
          FROM information_schema.tables
          WHERE table_schema IN ('user_service', 'member_bff', 'stock_service')
            AND table_name = 'flyway_schema_history'
        ) AS flyway_table_count,
        (SELECT count(*) FROM flyway_rows WHERE version = '1' AND success) AS flyway_v1_success_count,
        (SELECT count(*) FROM flyway_rows WHERE NOT success) AS failed_migration_count,
        (SELECT count(*) FROM admin_roles WHERE has_admin) AS admin_count,
        (SELECT count(*) FROM admin_roles WHERE has_admin AND has_user) AS admin_with_user_role_count,
        (SELECT count(*) FROM admin_roles WHERE has_admin AND enabled) AS enabled_admin_count
    )
    SELECT concat_ws(
      '|',
      transaction_mode,
      tls_enabled,
      schema_count,
      bootstrap_owned_schema_count,
      application_role_count,
      expected_table_count,
      application_table_count,
      single_owner_schema_count,
      login_role_count,
      own_schema_grant_count,
      cross_schema_grant_count,
      flyway_table_count,
      flyway_v1_success_count,
      failed_migration_count,
      admin_count,
      admin_with_user_role_count,
      enabled_admin_count
    )
    FROM metrics;

    ROLLBACK;
    SQL
    )"

    expected="on|t|3|3|3|5|5|3|3|3|0|3|3|0|1|1|1"

    if [ "$observed" != "$expected" ]; then
      printf 'restore_validation_passed=false\n' >&2
      printf 'restore_validation_observed=%s\n' "$observed" >&2
      printf 'restore_validation_expected=%s\n' "$expected" >&2
      exit 1
    fi

    fingerprint="$(printf '%s' "$observed" | sha256sum | cut -d ' ' -f1)"
    printf 'restore_validation_passed=true\n'
    printf 'restore_validation_counts=schemas:3,application_roles:3,application_tables:5,flyway_v1:3,failed_migrations:0,admins:1\n'
    printf 'restore_validation_fingerprint=%s\n' "$fingerprint"
  SCRIPT
}

resource "aws_cloudwatch_log_group" "audit" {
  for_each = local.foundation

  name              = "/ecs/${var.name_prefix}/rds-restore-drill"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name      = "${var.name_prefix}-rds-restore-drill"
    Lifecycle = "persistent-audit"
  })
}

resource "aws_security_group" "restore_db" {
  for_each = local.restore

  name                   = "${var.name_prefix}-rds-restore-drill-db-sg"
  description            = "Temporary PITR database reachable only from the restore validator"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-rds-restore-drill-db-sg"
  })
}

resource "aws_security_group" "validator" {
  for_each = local.restore

  name                   = "${var.name_prefix}-rds-restore-drill-validator-sg"
  description            = "Temporary no-ingress Fargate validator for the isolated PITR database"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-rds-restore-drill-validator-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "restore_from_validator" {
  for_each = local.restore

  security_group_id            = aws_security_group.restore_db[each.key].id
  description                  = "Allow PostgreSQL only from the temporary restore validator"
  referenced_security_group_id = aws_security_group.validator[each.key].id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-restore-from-validator"
  })
}

resource "aws_vpc_security_group_egress_rule" "validator_to_restore" {
  for_each = local.restore

  security_group_id            = aws_security_group.validator[each.key].id
  description                  = "Allow the validator to reach only the restored PostgreSQL instance"
  referenced_security_group_id = aws_security_group.restore_db[each.key].id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-validator-to-restore"
  })
}

resource "aws_vpc_security_group_egress_rule" "validator_https" {
  for_each = local.restore

  security_group_id = aws_security_group.validator[each.key].id
  description       = "Allow public ECR, Secrets Manager, and CloudWatch Logs over HTTPS through NAT"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-validator-https"
  })
}

resource "aws_vpc_security_group_egress_rule" "validator_dns_udp" {
  for_each = local.restore

  security_group_id = aws_security_group.validator[each.key].id
  description       = "Allow VPC DNS resolution over UDP"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-validator-dns-udp"
  })
}

resource "aws_vpc_security_group_egress_rule" "validator_dns_tcp" {
  for_each = local.restore

  security_group_id = aws_security_group.validator[each.key].id
  description       = "Allow VPC DNS resolution over TCP"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-validator-dns-tcp"
  })
}

resource "aws_db_instance" "restore" {
  for_each = local.restore

  identifier     = var.restore_identifier
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = var.db_subnet_group_name
  parameter_group_name   = var.db_parameter_group_name
  vpc_security_group_ids = [aws_security_group.restore_db[each.key].id]
  publicly_accessible    = false
  multi_az               = false
  network_type           = "IPV4"
  port                   = 5432

  auto_minor_version_upgrade = false
  apply_immediately          = true

  # PITR inherits the source backup retention period. Keep the configuration
  # aligned with the observed seven-day source policy to avoid post-restore drift.
  backup_retention_period  = 7
  copy_tags_to_snapshot    = false
  delete_automated_backups = true

  deletion_protection = false
  skip_final_snapshot = true

  performance_insights_enabled = false
  monitoring_interval          = 0

  restore_to_point_in_time {
    source_db_instance_identifier = var.source_db_instance_identifier
    use_latest_restorable_time    = var.use_latest_restorable_time
  }

  timeouts {
    create = "90m"
    delete = "60m"
  }

  tags = merge(local.temporary_tags, {
    Name   = var.restore_identifier
    Source = var.source_db_instance_identifier
  })
}

resource "aws_iam_role" "validator_execution" {
  for_each = local.restore

  name = "${var.name_prefix}-rds-restore-validator-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = local.temporary_tags
}

resource "aws_iam_role_policy" "validator_execution" {
  for_each = local.restore

  name = "rds-restore-validator-execution"
  role = aws_iam_role.validator_execution[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "WriteRestoreValidationLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.audit[each.key].arn}:*"
      },
      {
        Sid      = "ReadOnlySourceMasterSecret"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.source_master_secret_arn
      },
    ]
  })
}

resource "aws_ecs_task_definition" "validator" {
  for_each = local.restore

  family                   = "${var.name_prefix}-rds-restore-validator"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.validator_execution[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = "rds-restore-validator"
    image                  = var.validator_image
    essential              = true
    readonlyRootFilesystem = true
    user                   = "70"
    workingDirectory       = "/"
    entryPoint             = ["sh", "-c"]
    command                = [local.validator_script]
    stopTimeout            = 30
    environment = [
      {
        name  = "DB_ADDRESS"
        value = aws_db_instance.restore[each.key].address
      },
      {
        name  = "DB_PORT"
        value = tostring(aws_db_instance.restore[each.key].port)
      },
      {
        name  = "DB_NAME"
        value = var.db_name
      },
      {
        name  = "HOME"
        value = "/"
      },
    ]
    secrets = [
      {
        name      = "DB_MASTER_USERNAME"
        valueFrom = "${var.source_master_secret_arn}:username::"
      },
      {
        name      = "DB_MASTER_PASSWORD"
        valueFrom = "${var.source_master_secret_arn}:password::"
      },
    ]
    linuxParameters = {
      initProcessEnabled = true
      capabilities = {
        drop = ["ALL"]
      }
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.audit[each.key].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "validator"
      }
    }
  }])

  tags = merge(local.temporary_tags, {
    Name = "${var.name_prefix}-rds-restore-validator"
  })
}
