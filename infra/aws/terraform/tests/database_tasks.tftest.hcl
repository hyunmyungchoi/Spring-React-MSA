mock_provider "aws" {}

run "bootstrap_only_contract" {
  command = plan

  module {
    source = "./modules/database-tasks"
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    aws_region        = "ap-northeast-2"
    db_address        = "learning.cluster.local"
    db_port           = 5432
    db_name           = "spring_msa"
    master_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    application_secret_arns = {
      "/spring-react-msa/learning/user-service"  = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
      "/spring-react-msa/learning/member-bff"    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member"
      "/spring-react-msa/learning/stock-service" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    }
    ecr_repository_arns = {}
    migration_images    = {}
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
    }
  }

  assert {
    condition = (
      aws_ecs_task_definition.db_bootstrap.family == "spring-react-msa-learning-db-bootstrap" &&
      toset(aws_ecs_task_definition.db_bootstrap.requires_compatibilities) == toset(["EC2"]) &&
      aws_ecs_task_definition.db_bootstrap.network_mode == "awsvpc" &&
      aws_ecs_task_definition.db_bootstrap.runtime_platform[0].cpu_architecture == "X86_64"
    )
    error_message = "The database bootstrap must be a one-off X86_64 ECS EC2 task using awsvpc networking."
  }

  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].image == "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382" &&
      jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].readonlyRootFilesystem == true &&
      length(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].secrets) == 8 &&
      strcontains(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].command[0], "ALTER ROLE %I WITH LOGIN PASSWORD %L") &&
      !strcontains(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].command[0], "NOSUPERUSER") &&
      !strcontains(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].command[0], "NOREPLICATION") &&
      !strcontains(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].command[0], "ALTER SCHEMA") &&
      strcontains(jsondecode(aws_ecs_task_definition.db_bootstrap.container_definitions)[0].command[0], "GRANT USAGE, CREATE ON SCHEMA")
    )
    error_message = "The bootstrap container must use the approved digest, a read-only root filesystem, secret-key injection, and RDS-compatible role updates."
  }

  assert {
    condition = (
      aws_cloudwatch_log_group.db_bootstrap.retention_in_days == 7 &&
      aws_cloudwatch_log_group.admin_bootstrap.retention_in_days == 7 &&
      length(aws_cloudwatch_log_group.migration) == 0 &&
      length(aws_ecs_task_definition.migration) == 0
    )
    error_message = "Bootstrap-only mode must retain database and admin audit logs for seven days and create no migration resources."
  }

}

run "admin_bootstrap_contract" {
  command = apply

  module {
    source = "./modules/database-tasks"
  }

  override_resource {
    target = aws_iam_role.db_bootstrap_execution
    values = {
      arn = "arn:aws:iam::123456789012:role/db-bootstrap"
    }
  }

  override_resource {
    target = aws_iam_role.admin_bootstrap_execution["this"]
    values = {
      arn = "arn:aws:iam::123456789012:role/admin-bootstrap"
    }
  }

  override_resource {
    target = aws_secretsmanager_secret.admin_bootstrap["this"]
    values = {
      arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:admin-bootstrap"
    }
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    aws_region        = "ap-northeast-2"
    db_address        = "learning.cluster.local"
    db_port           = 5432
    db_name           = "spring_msa"
    master_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    application_secret_arns = {
      "/spring-react-msa/learning/user-service"  = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
      "/spring-react-msa/learning/member-bff"    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member"
      "/spring-react-msa/learning/stock-service" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    }
    ecr_repository_arns = {
      "spring-user-service" = "arn:aws:ecr:ap-northeast-2:123456789012:repository/user"
    }
    admin_bootstrap_enabled     = true
    admin_bootstrap_image       = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning/spring-user-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    admin_bootstrap_secret_name = "/spring-react-msa/learning/admin-bootstrap"
  }

  assert {
    condition = (
      length(aws_ecs_task_definition.admin_bootstrap) == 1 &&
      length(aws_iam_role.admin_bootstrap_execution) == 1 &&
      length(aws_iam_role_policy.admin_bootstrap_execution) == 1 &&
      length(aws_secretsmanager_secret.admin_bootstrap) == 1
    )
    error_message = "Admin bootstrap mode must create exactly one temporary task, execution role, inline policy, and input secret container."
  }

  assert {
    condition = (
      aws_ecs_task_definition.admin_bootstrap["this"].family == "spring-react-msa-learning-admin-bootstrap" &&
      toset(aws_ecs_task_definition.admin_bootstrap["this"].requires_compatibilities) == toset(["EC2"]) &&
      aws_ecs_task_definition.admin_bootstrap["this"].network_mode == "awsvpc" &&
      aws_ecs_task_definition.admin_bootstrap["this"].runtime_platform[0].cpu_architecture == "X86_64" &&
      aws_ecs_task_definition.admin_bootstrap["this"].task_role_arn == null
    )
    error_message = "The admin bootstrap must be a one-off X86_64 ECS EC2 task without an application Task Role."
  }

  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].image == "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning/spring-user-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" &&
      jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].readonlyRootFilesystem == true &&
      jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].user == "65534" &&
      strcontains(jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].command[0], "AdminBootstrapMain") &&
      length(jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].secrets) == 6 &&
      toset([for secret in jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].secrets : secret.name]) == toset([
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "ADMIN_BOOTSTRAP_LOGIN_ID",
        "ADMIN_BOOTSTRAP_EMAIL",
        "ADMIN_BOOTSTRAP_PASSWORD",
        "ADMIN_BOOTSTRAP_USERNAME",
      ]) &&
      !contains(
        [for item in jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].environment : item.name],
        "ADMIN_BOOTSTRAP_AUDIT_ACTOR",
      ) &&
      !contains(
        [for item in jsondecode(aws_ecs_task_definition.admin_bootstrap["this"].container_definitions)[0].environment : item.name],
        "ADMIN_BOOTSTRAP_REQUEST_ID",
      )
    )
    error_message = "The admin bootstrap container must use the digest image, non-root read-only execution, six secret keys, and require audit values at RunTask time."
  }

  assert {
    condition = (
      aws_secretsmanager_secret.admin_bootstrap["this"].recovery_window_in_days == 7 &&
      aws_cloudwatch_log_group.admin_bootstrap.retention_in_days == 7 &&
      jsondecode(aws_iam_role_policy.admin_bootstrap_execution["this"].policy).Statement[3].Action == ["secretsmanager:GetSecretValue"] &&
      length(jsondecode(aws_iam_role_policy.admin_bootstrap_execution["this"].policy).Statement[3].Resource) == 2
    )
    error_message = "Admin credentials must use a seven-day temporary secret, seven-day audit log, and read-only access to exactly two secrets."
  }
}

run "admin_bootstrap_requires_digest_image" {
  command = plan

  module {
    source = "./modules/database-tasks"
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    aws_region        = "ap-northeast-2"
    db_address        = "learning.cluster.local"
    master_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    application_secret_arns = {
      "/spring-react-msa/learning/user-service"  = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
      "/spring-react-msa/learning/member-bff"    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member"
      "/spring-react-msa/learning/stock-service" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    }
    ecr_repository_arns = {
      "spring-user-service" = "arn:aws:ecr:ap-northeast-2:123456789012:repository/user"
    }
    admin_bootstrap_enabled = true
    admin_bootstrap_image   = "latest"
  }

  expect_failures = [var.admin_bootstrap_image]
}

run "three_flyway_tasks_contract" {
  command = plan

  module {
    source = "./modules/database-tasks"
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    aws_region        = "ap-northeast-2"
    db_address        = "learning.cluster.local"
    db_port           = 5432
    db_name           = "spring_msa"
    master_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    application_secret_arns = {
      "/spring-react-msa/learning/user-service"  = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
      "/spring-react-msa/learning/member-bff"    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member"
      "/spring-react-msa/learning/stock-service" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    }
    ecr_repository_arns = {
      "spring-user-service"         = "arn:aws:ecr:ap-northeast-2:123456789012:repository/user"
      "spring-member-bff-service"   = "arn:aws:ecr:ap-northeast-2:123456789012:repository/member"
      "spring-member-stock-service" = "arn:aws:ecr:ap-northeast-2:123456789012:repository/stock"
    }
    migration_images = {
      user-service  = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning-spring-user-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      member-bff    = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning-spring-member-bff-service@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      stock-service = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning-spring-member-stock-service@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    }
  }

  assert {
    condition = (
      length(aws_ecs_task_definition.migration) == 3 &&
      length(aws_iam_role.migration_execution) == 3 &&
      length(aws_cloudwatch_log_group.migration) == 3
    )
    error_message = "Exactly three service-owned Flyway task definitions, roles, and log groups must be created."
  }

  assert {
    condition = alltrue([
      for key, task in aws_ecs_task_definition.migration :
      startswith(jsondecode(task.container_definitions)[0].image, "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/") &&
      strcontains(jsondecode(task.container_definitions)[0].image, "@sha256:") &&
      jsondecode(task.container_definitions)[0].readonlyRootFilesystem == true &&
      length(jsondecode(task.container_definitions)[0].secrets) == 2
    ])
    error_message = "Every migration must use a digest-pinned ECR image, read-only filesystem, and only its DB secret keys."
  }

  assert {
    condition = alltrue([
      for key, task in aws_ecs_task_definition.migration :
      strcontains(jsondecode(task.container_definitions)[0].environment[0].value, "sslmode=require") &&
      strcontains(jsondecode(task.container_definitions)[0].command[0], "FlywayMigrationMain")
    ])
    error_message = "Every migration task must require PostgreSQL TLS and invoke its dedicated Flyway runner."
  }
}

run "partial_flyway_image_set_is_rejected" {
  command = plan

  module {
    source = "./modules/database-tasks"
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    aws_region        = "ap-northeast-2"
    db_address        = "learning.cluster.local"
    master_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    application_secret_arns = {
      "/spring-react-msa/learning/user-service"  = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
      "/spring-react-msa/learning/member-bff"    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member"
      "/spring-react-msa/learning/stock-service" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    }
    ecr_repository_arns = {
      "spring-user-service" = "arn:aws:ecr:ap-northeast-2:123456789012:repository/user"
    }
    migration_images = {
      user-service = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/spring-react-msa-learning-spring-user-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [var.migration_images]
}
