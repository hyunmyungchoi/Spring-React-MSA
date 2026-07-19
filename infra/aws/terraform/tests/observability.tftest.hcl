mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "111122223333"
      arn        = "arn:aws:iam::111122223333:root"
      id         = "111122223333"
    }
  }

  mock_data "aws_partition" {
    defaults = {
      partition  = "aws"
      dns_suffix = "amazonaws.com"
    }
  }
}

run "observability_contract" {
  command = plan

  module {
    source = "./modules/observability"
  }

  variables {
    name_prefix            = "spring-react-msa-learning"
    aws_region             = "ap-northeast-2"
    alert_email            = "terraform-test@example.com"
    db_instance_identifier = "spring-react-msa-learning-postgres"
    db_instance_arn        = "arn:aws:rds:ap-northeast-2:111122223333:db:spring-react-msa-learning-postgres"
    common_tags = {
      Environment = "learning"
      ManagedBy   = "Terraform"
      Project     = "spring-react-msa"
    }
  }

  assert {
    condition = (
      aws_sns_topic.operations.name == "spring-react-msa-learning-operations" &&
      aws_sns_topic_subscription.email.protocol == "email"
    )
    error_message = "The operations channel must use one standard SNS topic with an email subscription."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.rds) == 3
    error_message = "Exactly three persistent RDS alarms must be configured."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds["cpu_high"].metric_name == "CPUUtilization" &&
      aws_cloudwatch_metric_alarm.rds["cpu_high"].comparison_operator == "GreaterThanOrEqualToThreshold" &&
      aws_cloudwatch_metric_alarm.rds["cpu_high"].threshold == 80
    )
    error_message = "RDS CPU must alarm at 80 percent or higher."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.rds["freeable_memory_low"].metric_name == "FreeableMemory" &&
      aws_cloudwatch_metric_alarm.rds["freeable_memory_low"].threshold == 268435456 &&
      aws_cloudwatch_metric_alarm.rds["free_storage_low"].metric_name == "FreeStorageSpace" &&
      aws_cloudwatch_metric_alarm.rds["free_storage_low"].threshold == 5368709120
    )
    error_message = "RDS memory and storage thresholds must remain at 256 MiB and 5 GiB."
  }

  assert {
    condition = alltrue([
      for alarm in values(aws_cloudwatch_metric_alarm.rds) :
      alarm.period == 300 &&
      alarm.evaluation_periods == 3 &&
      alarm.datapoints_to_alarm == 3 &&
      alarm.treat_missing_data == "notBreaching"
    ])
    error_message = "Every RDS alarm must require three consecutive five-minute datapoints and tolerate stopped-RDS missing data."
  }

  assert {
    condition = toset(aws_db_event_subscription.rds.event_categories) == toset([
      "availability",
      "backup",
      "failure",
      "low storage",
      "maintenance",
      "notification",
    ])
    error_message = "The RDS event subscription must include availability, backup, failure, low storage, maintenance, and notification events."
  }
}
