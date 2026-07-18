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
      length(aws_cloudwatch_log_group.migration) == 0 &&
      length(aws_ecs_task_definition.migration) == 0
    )
    error_message = "Bootstrap-only mode must retain logs for seven days and create no migration resources."
  }

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
