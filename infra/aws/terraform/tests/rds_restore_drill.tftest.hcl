mock_provider "aws" {}

variables {
  name_prefix                    = "spring-react-msa-learning"
  aws_region                     = "ap-northeast-2"
  data_layer_enabled             = true
  ecs_compute_foundation_enabled = true
  nat_gateway_enabled            = true
  application_runtime_enabled    = false
  vpc_id                         = "vpc-learning"
  vpc_cidr                       = "10.20.0.0/16"
  private_app_subnet_ids         = ["subnet-app-a", "subnet-app-b"]
  source_db_instance_identifier  = "spring-react-msa-learning-postgres"
  source_master_secret_arn       = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
  db_subnet_group_name           = "spring-react-msa-learning-postgres"
  db_parameter_group_name        = "spring-react-msa-learning-postgres16"
  db_name                        = "spring_msa"
  db_instance_class              = "db.t4g.micro"
  restore_identifier             = "spring-react-msa-learning-postgres-restore-drill"
  use_latest_restorable_time     = true
  expires_at_utc                 = "2026-07-23T12:00:00Z"
  validator_image                = "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382"
  common_tags = {
    Environment = "learning"
    ManagedBy   = "terraform"
  }
}

run "foundation_off_creates_nothing" {
  command = plan

  module {
    source = "./modules/rds-restore-drill"
  }

  variables {
    foundation_enabled = false
    restore_enabled    = false
  }

  assert {
    condition = (
      length(aws_cloudwatch_log_group.audit) == 0 &&
      length(aws_db_instance.restore) == 0 &&
      length(aws_ecs_task_definition.validator) == 0
    )
    error_message = "Foundation and Restore OFF must create no resources."
  }
}

run "audit_foundation_contains_only_persistent_log" {
  command = plan

  module {
    source = "./modules/rds-restore-drill"
  }

  variables {
    foundation_enabled = true
    restore_enabled    = false
  }

  assert {
    condition = (
      length(aws_cloudwatch_log_group.audit) == 1 &&
      aws_cloudwatch_log_group.audit["this"].retention_in_days == 7 &&
      aws_cloudwatch_log_group.audit["this"].tags.Lifecycle == "persistent-audit"
    )
    error_message = "The restore drill foundation must contain one seven-day persistent audit log group."
  }

  assert {
    condition = (
      length(aws_db_instance.restore) == 0 &&
      length(aws_security_group.restore_db) == 0 &&
      length(aws_security_group.validator) == 0 &&
      length(aws_iam_role.validator_execution) == 0 &&
      length(aws_ecs_task_definition.validator) == 0
    )
    error_message = "Foundation-only mode must not create temporary or hourly-billed restore resources."
  }
}

run "isolated_restore_and_read_only_validator_contract" {
  command = apply

  module {
    source = "./modules/rds-restore-drill"
  }

  override_resource {
    target = aws_iam_role.validator_execution["this"]
    values = {
      arn = "arn:aws:iam::123456789012:role/rds-restore-validator"
    }
  }

  variables {
    foundation_enabled = true
    restore_enabled    = true
  }

  assert {
    condition = (
      length(aws_db_instance.restore) == 1 &&
      aws_db_instance.restore["this"].identifier == "spring-react-msa-learning-postgres-restore-drill" &&
      aws_db_instance.restore["this"].instance_class == "db.t4g.micro" &&
      aws_db_instance.restore["this"].allocated_storage == 20 &&
      aws_db_instance.restore["this"].storage_type == "gp3" &&
      aws_db_instance.restore["this"].storage_encrypted == true &&
      aws_db_instance.restore["this"].publicly_accessible == false &&
      aws_db_instance.restore["this"].multi_az == false &&
      aws_db_instance.restore["this"].backup_retention_period == 7 &&
      aws_db_instance.restore["this"].deletion_protection == false &&
      aws_db_instance.restore["this"].skip_final_snapshot == true
    )
    error_message = "The temporary restore must remain encrypted, private, Single-AZ, small, and disposable."
  }

  assert {
    condition = (
      aws_db_instance.restore["this"].restore_to_point_in_time[0].source_db_instance_identifier == "spring-react-msa-learning-postgres" &&
      aws_db_instance.restore["this"].restore_to_point_in_time[0].use_latest_restorable_time == true &&
      length(aws_db_instance.restore["this"].vpc_security_group_ids) == 1 &&
      one(aws_db_instance.restore["this"].vpc_security_group_ids) == aws_security_group.restore_db["this"].id
    )
    error_message = "PITR must use the protected source's latest restorable time and only the dedicated restore DB security group."
  }

  assert {
    condition = (
      length(aws_security_group.restore_db) == 1 &&
      length(aws_security_group.validator) == 1 &&
      length(aws_vpc_security_group_ingress_rule.restore_from_validator) == 1 &&
      aws_vpc_security_group_ingress_rule.restore_from_validator["this"].referenced_security_group_id == aws_security_group.validator["this"].id &&
      aws_vpc_security_group_ingress_rule.restore_from_validator["this"].from_port == 5432 &&
      length(aws_vpc_security_group_egress_rule.validator_to_restore) == 1 &&
      aws_vpc_security_group_egress_rule.validator_to_restore["this"].referenced_security_group_id == aws_security_group.restore_db["this"].id
    )
    error_message = "The restored database must accept PostgreSQL only from its dedicated no-ingress validator security group."
  }

  assert {
    condition = (
      length(aws_ecs_task_definition.validator) == 1 &&
      toset(aws_ecs_task_definition.validator["this"].requires_compatibilities) == toset(["FARGATE"]) &&
      aws_ecs_task_definition.validator["this"].network_mode == "awsvpc" &&
      aws_ecs_task_definition.validator["this"].cpu == "256" &&
      aws_ecs_task_definition.validator["this"].memory == "512" &&
      aws_ecs_task_definition.validator["this"].task_role_arn == null &&
      aws_ecs_task_definition.validator["this"].runtime_platform[0].cpu_architecture == "X86_64"
    )
    error_message = "Validation must use one small X86_64 Fargate task without an application Task Role."
  }

  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].image == "public.ecr.aws/docker/library/postgres@sha256:7a396fd264a2067788b6551122b50f162bf6136312c7fc9d74381cb92c648382" &&
      jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].readonlyRootFilesystem == true &&
      jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].user == "70" &&
      jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].linuxParameters.capabilities.drop == ["ALL"] &&
      !contains(keys(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].linuxParameters), "tmpfs") &&
      length(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].secrets) == 2
    )
    error_message = "The validator must use a digest image, read-only non-root execution, no Fargate tmpfs, dropped capabilities, and only the master credential pair."
  }

  assert {
    condition = (
      strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "BEGIN TRANSACTION READ ONLY") &&
      strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "pg_stat_ssl") &&
      strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "application_tables:5") &&
      strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "restore_validation_fingerprint") &&
      strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "<<'SQL'\n") &&
      !strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "<<'SQL' |") &&
      !strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "SELECT login_id") &&
      !strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "SELECT email") &&
      !strcontains(jsondecode(aws_ecs_task_definition.validator["this"].container_definitions)[0].command[0], "SELECT password")
    )
    error_message = "The validator command must use a valid standalone SQL heredoc, enforce TLS, and keep a read-only count-only contract without selecting identity or credential fields."
  }

  assert {
    condition = (
      length(aws_iam_role.validator_execution) == 1 &&
      length(aws_iam_role_policy.validator_execution) == 1 &&
      length(jsondecode(aws_iam_role_policy.validator_execution["this"].policy).Statement) == 2 &&
      jsondecode(aws_iam_role_policy.validator_execution["this"].policy).Statement[1].Action == ["secretsmanager:GetSecretValue"] &&
      jsondecode(aws_iam_role_policy.validator_execution["this"].policy).Statement[1].Resource == "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:rds!db-master"
    )
    error_message = "The execution role may only write the audit log and read the source RDS-managed master secret."
  }

  assert {
    condition = (
      output.validator_run_configuration.launch_type == "FARGATE" &&
      output.validator_run_configuration.platform_version == "1.4.0" &&
      output.validator_run_configuration.assign_public_ip == "DISABLED" &&
      length(output.validator_run_configuration.subnet_ids) == 2 &&
      length(output.validator_run_configuration.security_group_ids) == 1
    )
    error_message = "The run output must keep the validator in private subnets with no public IP and only its dedicated security group."
  }
}

run "restore_rejects_runtime_on" {
  command = plan

  module {
    source = "./modules/rds-restore-drill"
  }

  variables {
    foundation_enabled          = true
    restore_enabled             = true
    application_runtime_enabled = true
  }

  expect_failures = [var.restore_enabled]
}

run "restore_requires_expiry_tag" {
  command = plan

  module {
    source = "./modules/rds-restore-drill"
  }

  variables {
    foundation_enabled = true
    restore_enabled    = true
    expires_at_utc     = null
  }

  expect_failures = [var.expires_at_utc]
}

run "restore_requires_private_api_egress" {
  command = plan

  module {
    source = "./modules/rds-restore-drill"
  }

  variables {
    foundation_enabled  = true
    restore_enabled     = true
    nat_gateway_enabled = false
  }

  expect_failures = [var.restore_enabled]
}
