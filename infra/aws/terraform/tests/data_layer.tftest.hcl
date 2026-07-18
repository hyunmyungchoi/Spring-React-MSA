mock_provider "aws" {}

run "enabled_data_layer_contract" {
  command = plan

  module {
    source = "./modules/data-layer"
  }

  variables {
    name_prefix             = "spring-react-msa-learning"
    private_data_subnet_ids = ["subnet-data-a", "subnet-data-c"]
    data_security_group_id  = "sg-data"
    enable_data_layer       = true
    db_engine_version       = "16.14"
    db_instance_class       = "db.t4g.micro"
    application_secret_names = [
      "/spring-react-msa/learning/admin-bff",
      "/spring-react-msa/learning/auth-server",
      "/spring-react-msa/learning/member-bff",
      "/spring-react-msa/learning/shared/internal-api",
      "/spring-react-msa/learning/shared/redis",
      "/spring-react-msa/learning/stock-service",
      "/spring-react-msa/learning/user-service",
    ]
    common_tags = {
      Environment = "learning"
      ManagedBy   = "terraform"
      Project     = "spring-react-msa"
    }
  }

  assert {
    condition = (
      aws_db_instance.this[0].engine == "postgres" &&
      aws_db_instance.this[0].engine_version == "16.14" &&
      aws_db_instance.this[0].instance_class == "db.t4g.micro" &&
      aws_db_instance.this[0].db_name == "spring_msa"
    )
    error_message = "RDS must use the approved PostgreSQL 16 learning configuration."
  }

  assert {
    condition = (
      aws_db_instance.this[0].allocated_storage == 20 &&
      aws_db_instance.this[0].storage_type == "gp3" &&
      aws_db_instance.this[0].storage_encrypted &&
      aws_db_instance.this[0].max_allocated_storage == 0
    )
    error_message = "RDS storage must be encrypted fixed-size 20 GiB gp3 to control learning cost."
  }

  assert {
    condition = (
      aws_db_instance.this[0].manage_master_user_password &&
      !aws_db_instance.this[0].publicly_accessible &&
      !aws_db_instance.this[0].multi_az &&
      aws_db_instance.this[0].deletion_protection &&
      !aws_db_instance.this[0].skip_final_snapshot
    )
    error_message = "RDS must use an RDS-managed password, remain private and Single-AZ, and resist deletion."
  }

  assert {
    condition = (
      aws_db_instance.this[0].backup_retention_period == 7 &&
      aws_db_instance.this[0].delete_automated_backups == false &&
      aws_db_instance.this[0].performance_insights_enabled == false &&
      aws_db_instance.this[0].monitoring_interval == 0
    )
    error_message = "RDS must retain seven days of backups without paid monitoring features."
  }

  assert {
    condition = (
      length(aws_db_subnet_group.this[0].subnet_ids) == 2 &&
      aws_db_parameter_group.this[0].family == "postgres16" &&
      toset(aws_db_instance.this[0].vpc_security_group_ids) == toset(["sg-data"])
    )
    error_message = "RDS must use both Private Data subnets, PostgreSQL 16 parameters, and only the data Security Group."
  }

  assert {
    condition = (
      length(aws_secretsmanager_secret.application) == 7 &&
      toset(keys(aws_secretsmanager_secret.application)) == var.application_secret_names &&
      alltrue([for secret in values(aws_secretsmanager_secret.application) : secret.recovery_window_in_days == 7])
    )
    error_message = "Exactly the seven approved empty secret containers must be retained for seven days on deletion."
  }
}

run "disabled_data_layer_contract" {
  command = plan

  module {
    source = "./modules/data-layer"
  }

  variables {
    name_prefix             = "spring-react-msa-learning"
    private_data_subnet_ids = ["subnet-data-a", "subnet-data-c"]
    data_security_group_id  = "sg-data"
    enable_data_layer       = false
    application_secret_names = [
      "/spring-react-msa/learning/user-service",
    ]
  }

  assert {
    condition = (
      length(aws_db_instance.this) == 0 &&
      length(aws_db_subnet_group.this) == 0 &&
      length(aws_db_parameter_group.this) == 0 &&
      length(aws_secretsmanager_secret.application) == 0
    )
    error_message = "The opt-in data layer must create nothing when disabled."
  }
}
