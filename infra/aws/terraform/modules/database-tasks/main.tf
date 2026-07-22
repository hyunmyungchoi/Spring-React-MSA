locals {
  admin_bootstrap = var.admin_bootstrap_enabled ? { this = true } : {}

  db_secret_names = {
    user_service  = "/spring-react-msa/learning/user-service"
    member_bff    = "/spring-react-msa/learning/member-bff"
    stock_service = "/spring-react-msa/learning/stock-service"
  }

  bootstrap_script = <<-SCRIPT
    set -eu

    test -n "$DB_ADDRESS"
    test -n "$DB_PORT"
    test -n "$DB_NAME"
    test -n "$DB_MASTER_USERNAME"
    test -n "$DB_MASTER_PASSWORD"
    test -n "$USER_SERVICE_DB_USERNAME"
    test -n "$USER_SERVICE_DB_PASSWORD"
    test -n "$MEMBER_BFF_DB_USERNAME"
    test -n "$MEMBER_BFF_DB_PASSWORD"
    test -n "$STOCK_SERVICE_DB_USERNAME"
    test -n "$STOCK_SERVICE_DB_PASSWORD"

    export PGHOST="$DB_ADDRESS"
    export PGPORT="$DB_PORT"
    export PGDATABASE="$DB_NAME"
    export PGUSER="$DB_MASTER_USERNAME"
    export PGPASSWORD="$DB_MASTER_PASSWORD"
    export PGSSLMODE=require

    bootstrap_role() {
      role_name="$1"
      role_password="$2"
      schema_name="$3"

      psql --no-password --set=ON_ERROR_STOP=1 \
        --set=role_name="$role_name" \
        --set=role_password="$role_password" \
        --set=schema_name="$schema_name" <<'SQL'
    BEGIN;
    SELECT format('CREATE ROLE %I LOGIN', :'role_name')
    WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role_name') \gexec
    SELECT format(
      'ALTER ROLE %I WITH LOGIN PASSWORD %L',
      :'role_name',
      :'role_password'
    ) \gexec
    SELECT format('CREATE SCHEMA IF NOT EXISTS %I', :'schema_name') \gexec
    SELECT format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', :'schema_name') \gexec
    SELECT format('GRANT USAGE, CREATE ON SCHEMA %I TO %I', :'schema_name', :'role_name') \gexec
    SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'role_name') \gexec
    SELECT format(
      'ALTER ROLE %I IN DATABASE %I SET search_path TO %I',
      :'role_name',
      current_database(),
      :'schema_name'
    ) \gexec
    COMMIT;
    SQL

      printf 'Database role and schema ready: role=%s schema=%s\n' "$role_name" "$schema_name"
    }

    psql --no-password --set=ON_ERROR_STOP=1 <<'SQL'
    SELECT format('REVOKE CREATE, TEMPORARY ON DATABASE %I FROM PUBLIC', current_database()) \gexec
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
    SQL

    bootstrap_role "$USER_SERVICE_DB_USERNAME" "$USER_SERVICE_DB_PASSWORD" "user_service"
    bootstrap_role "$MEMBER_BFF_DB_USERNAME" "$MEMBER_BFF_DB_PASSWORD" "member_bff"
    bootstrap_role "$STOCK_SERVICE_DB_USERNAME" "$STOCK_SERVICE_DB_PASSWORD" "stock_service"

    printf 'Database bootstrap completed for 3 service schemas.\n'
  SCRIPT

  bootstrap_secrets = [
    {
      name      = "DB_MASTER_USERNAME"
      valueFrom = "${var.master_secret_arn}:username::"
    },
    {
      name      = "DB_MASTER_PASSWORD"
      valueFrom = "${var.master_secret_arn}:password::"
    },
    {
      name      = "USER_SERVICE_DB_USERNAME"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.user_service]}:db_username::"
    },
    {
      name      = "USER_SERVICE_DB_PASSWORD"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.user_service]}:db_password::"
    },
    {
      name      = "MEMBER_BFF_DB_USERNAME"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.member_bff]}:db_username::"
    },
    {
      name      = "MEMBER_BFF_DB_PASSWORD"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.member_bff]}:db_password::"
    },
    {
      name      = "STOCK_SERVICE_DB_USERNAME"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.stock_service]}:db_username::"
    },
    {
      name      = "STOCK_SERVICE_DB_PASSWORD"
      valueFrom = "${var.application_secret_arns[local.db_secret_names.stock_service]}:db_password::"
    },
  ]

  migration_configs = {
    user-service = {
      repository_service = "spring-user-service"
      schema             = "user_service"
      secret_name        = local.db_secret_names.user_service
      runner_class       = "com.springmsa.userservice.migration.FlywayMigrationMain"
    }
    member-bff = {
      repository_service = "spring-member-bff-service"
      schema             = "member_bff"
      secret_name        = local.db_secret_names.member_bff
      runner_class       = "com.springmsa.memberbff.migration.FlywayMigrationMain"
    }
    stock-service = {
      repository_service = "spring-member-stock-service"
      schema             = "stock_service"
      secret_name        = local.db_secret_names.stock_service
      runner_class       = "com.springmsa.memberstockservice.migration.FlywayMigrationMain"
    }
  }

  migration_task_configs = {
    for key, image in var.migration_images : key => merge(local.migration_configs[key], {
      image = image
    })
  }
}

resource "aws_cloudwatch_log_group" "db_bootstrap" {
  name              = "/ecs/${var.name_prefix}/db-bootstrap"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-db-bootstrap"
  })
}

resource "aws_iam_role" "db_bootstrap_execution" {
  name = "${var.name_prefix}-db-bootstrap-exec"

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

  tags = var.common_tags
}

resource "aws_iam_role_policy" "db_bootstrap_execution" {
  name = "database-bootstrap-execution"
  role = aws_iam_role.db_bootstrap_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "WriteBootstrapLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.db_bootstrap.arn}:*"
      },
      {
        Sid    = "ReadOnlyBootstrapSecrets"
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = concat(
          [var.master_secret_arn],
          [for name in values(local.db_secret_names) : var.application_secret_arns[name]],
        )
      },
    ]
  })
}

resource "aws_ecs_task_definition" "db_bootstrap" {
  family                   = "${var.name_prefix}-db-bootstrap"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.db_bootstrap_execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = "db-bootstrap"
    image                  = var.bootstrap_image
    essential              = true
    readonlyRootFilesystem = true
    user                   = "70"
    workingDirectory       = "/tmp"
    entryPoint             = ["sh", "-c"]
    command                = [local.bootstrap_script]
    environment = [
      {
        name  = "DB_ADDRESS"
        value = var.db_address
      },
      {
        name  = "DB_PORT"
        value = tostring(var.db_port)
      },
      {
        name  = "DB_NAME"
        value = var.db_name
      },
      {
        name  = "HOME"
        value = "/tmp"
      },
    ]
    secrets = local.bootstrap_secrets
    linuxParameters = {
      initProcessEnabled = true
      tmpfs = [{
        containerPath = "/tmp"
        size          = 64
        mountOptions  = ["rw", "nosuid", "nodev", "noexec"]
      }]
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.db_bootstrap.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bootstrap"
      }
    }
  }])

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-db-bootstrap"
  })
}

resource "aws_cloudwatch_log_group" "admin_bootstrap" {
  name              = "/ecs/${var.name_prefix}/admin-bootstrap"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name      = "${var.name_prefix}-admin-bootstrap"
    Lifecycle = "persistent-audit"
  })
}

resource "aws_secretsmanager_secret" "admin_bootstrap" {
  for_each = local.admin_bootstrap

  name                    = var.admin_bootstrap_secret_name
  description             = "Temporary initial administrator bootstrap input; populate outside Terraform and remove after successful validation."
  recovery_window_in_days = 7

  tags = merge(var.common_tags, {
    Name      = var.admin_bootstrap_secret_name
    Lifecycle = "temporary-bootstrap"
  })
}

resource "aws_iam_role" "admin_bootstrap_execution" {
  for_each = local.admin_bootstrap

  name = "${var.name_prefix}-admin-bootstrap-exec"

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

  tags = merge(var.common_tags, {
    Lifecycle = "temporary-bootstrap"
  })
}

resource "aws_iam_role_policy" "admin_bootstrap_execution" {
  for_each = local.admin_bootstrap

  name = "admin-bootstrap-execution"
  role = aws_iam_role.admin_bootstrap_execution[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "GetEcrAuthorizationToken"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "PullOnlyUserServiceImage"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
        ]
        Resource = var.ecr_repository_arns["spring-user-service"]
      },
      {
        Sid    = "WriteAdminBootstrapLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.admin_bootstrap.arn}:*"
      },
      {
        Sid    = "ReadOnlyAdminBootstrapSecrets"
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          var.application_secret_arns[local.db_secret_names.user_service],
          aws_secretsmanager_secret.admin_bootstrap[each.key].arn,
        ]
      },
    ]
  })
}

resource "aws_ecs_task_definition" "admin_bootstrap" {
  for_each = local.admin_bootstrap

  family                   = "${var.name_prefix}-admin-bootstrap"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.admin_bootstrap_execution[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = "admin-bootstrap"
    image                  = var.admin_bootstrap_image
    essential              = true
    readonlyRootFilesystem = true
    user                   = "65534"
    workingDirectory       = "/tmp"
    entryPoint             = ["sh", "-c"]
    command = [
      "exec java -Dloader.main=com.springmsa.userservice.bootstrap.AdminBootstrapMain -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher",
    ]
    environment = [
      {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}?currentSchema=user_service&sslmode=require"
      },
      {
        name  = "HOME"
        value = "/tmp"
      },
    ]
    secrets = [
      {
        name      = "SPRING_DATASOURCE_USERNAME"
        valueFrom = "${var.application_secret_arns[local.db_secret_names.user_service]}:db_username::"
      },
      {
        name      = "SPRING_DATASOURCE_PASSWORD"
        valueFrom = "${var.application_secret_arns[local.db_secret_names.user_service]}:db_password::"
      },
      {
        name      = "ADMIN_BOOTSTRAP_LOGIN_ID"
        valueFrom = "${aws_secretsmanager_secret.admin_bootstrap[each.key].arn}:login_id::"
      },
      {
        name      = "ADMIN_BOOTSTRAP_EMAIL"
        valueFrom = "${aws_secretsmanager_secret.admin_bootstrap[each.key].arn}:email::"
      },
      {
        name      = "ADMIN_BOOTSTRAP_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.admin_bootstrap[each.key].arn}:password::"
      },
      {
        name      = "ADMIN_BOOTSTRAP_USERNAME"
        valueFrom = "${aws_secretsmanager_secret.admin_bootstrap[each.key].arn}:username::"
      },
    ]
    linuxParameters = {
      initProcessEnabled = true
      tmpfs = [{
        containerPath = "/tmp"
        size          = 128
        mountOptions  = ["rw", "nosuid", "nodev", "noexec"]
      }]
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.admin_bootstrap.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "bootstrap"
      }
    }
  }])

  tags = merge(var.common_tags, {
    Name      = "${var.name_prefix}-admin-bootstrap"
    Lifecycle = "temporary-bootstrap"
  })

  lifecycle {
    precondition {
      condition     = contains(keys(var.ecr_repository_arns), "spring-user-service")
      error_message = "The admin bootstrap task requires the spring-user-service ECR repository ARN."
    }
  }
}

resource "aws_cloudwatch_log_group" "migration" {
  for_each = local.migration_task_configs

  name              = "/ecs/${var.name_prefix}/db-migration/${each.key}"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}-migration"
  })
}

resource "aws_iam_role" "migration_execution" {
  for_each = local.migration_task_configs

  name = "${var.name_prefix}-${each.key}-migration-exec"

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

  tags = var.common_tags
}

resource "aws_iam_role_policy" "migration_execution" {
  for_each = local.migration_task_configs

  name = "database-migration-execution"
  role = aws_iam_role.migration_execution[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "GetEcrAuthorizationToken"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "PullOnlyMigrationImage"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
        ]
        Resource = var.ecr_repository_arns[each.value.repository_service]
      },
      {
        Sid    = "WriteMigrationLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "${aws_cloudwatch_log_group.migration[each.key].arn}:*"
      },
      {
        Sid      = "ReadOnlyServiceDatabaseSecret"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.application_secret_arns[each.value.secret_name]
      },
    ]
  })
}

resource "aws_ecs_task_definition" "migration" {
  for_each = local.migration_task_configs

  family                   = "${var.name_prefix}-${each.key}-migration"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.migration_execution[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = "${each.key}-migration"
    image                  = each.value.image
    essential              = true
    readonlyRootFilesystem = true
    user                   = "65534"
    workingDirectory       = "/tmp"
    entryPoint             = ["sh", "-c"]
    command = [
      "exec java -Dloader.main=${each.value.runner_class} -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher",
    ]
    environment = [
      {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}?currentSchema=${each.value.schema}&sslmode=require"
      },
      {
        name  = "HOME"
        value = "/tmp"
      },
    ]
    secrets = [
      {
        name      = "SPRING_DATASOURCE_USERNAME"
        valueFrom = "${var.application_secret_arns[each.value.secret_name]}:db_username::"
      },
      {
        name      = "SPRING_DATASOURCE_PASSWORD"
        valueFrom = "${var.application_secret_arns[each.value.secret_name]}:db_password::"
      },
    ]
    linuxParameters = {
      initProcessEnabled = true
      tmpfs = [{
        containerPath = "/tmp"
        size          = 128
        mountOptions  = ["rw", "nosuid", "nodev", "noexec"]
      }]
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.migration[each.key].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "migration"
      }
    }
  }])

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}-migration"
  })
}
