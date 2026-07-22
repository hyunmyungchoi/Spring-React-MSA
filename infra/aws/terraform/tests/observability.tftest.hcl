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
    condition = alltrue(flatten([
      for statement in data.aws_iam_policy_document.operations.statement : [
        for action in statement.actions : action == "sns:Publish"
      ]
    ]))
    error_message = "The SNS topic policy must grant only sns:Publish and must not contain an out-of-scope wildcard action."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.rds) == 3
    error_message = "Exactly three persistent RDS alarms must be configured."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.runtime) == 0
    error_message = "Runtime OFF must not create ECS or ALB alarms."
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

run "runtime_observability_contract" {
  command = plan

  module {
    source = "./modules/observability"
  }

  variables {
    name_prefix                   = "spring-react-msa-learning"
    aws_region                    = "ap-northeast-2"
    alert_email                   = "terraform-test@example.com"
    db_instance_identifier        = "spring-react-msa-learning-postgres"
    db_instance_arn               = "arn:aws:rds:ap-northeast-2:111122223333:db:spring-react-msa-learning-postgres"
    runtime_observability_enabled = true
    runtime_enabled               = true
    ecs_cluster_name              = "spring-react-msa-learning-cluster"
    ecs_service_names = {
      "member-gateway"       = "spring-react-msa-learning-member-gateway"
      "admin-gateway"        = "spring-react-msa-learning-admin-gateway"
      "authorization-server" = "spring-react-msa-learning-authorization-server"
      "user-service"         = "spring-react-msa-learning-user-service"
      "community-service"    = "spring-react-msa-learning-community-service"
      "stock-service"        = "spring-react-msa-learning-stock-service"
      "member-bff"           = "spring-react-msa-learning-member-bff"
      "admin-bff"            = "spring-react-msa-learning-admin-bff"
    }
    load_balancer_arn_suffix = "app/spring-react-msa-learning-alb/0123456789abcdef"
    target_group_arn_suffixes = {
      "member-gateway" = "targetgroup/spring-react-msa-member/0123456789abcdef"
      "admin-gateway"  = "targetgroup/spring-react-msa-admin/fedcba9876543210"
    }
    common_tags = {
      Environment = "learning"
      ManagedBy   = "Terraform"
      Project     = "spring-react-msa"
    }
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.runtime) == 29
    error_message = "Runtime ON must create 24 ECS alarms and five ALB alarms."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-cpu-high"].namespace == "AWS/ECS" &&
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-cpu-high"].threshold == 80 &&
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-memory-high"].threshold == 85
    )
    error_message = "Each ECS service must use the approved CPU and memory thresholds."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-task-count-low"].namespace == "ECS/ContainerInsights" &&
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-task-count-low"].metric_name == "RunningTaskCount" &&
      aws_cloudwatch_metric_alarm.runtime["ecs-member-gateway-task-count-low"].treat_missing_data == "breaching"
    )
    error_message = "Task-count alarms must use the Container Insights RunningTaskCount metric and treat missing data as breaching."
  }

  assert {
    condition = (
      aws_cloudwatch_metric_alarm.runtime["alb-load-balancer-5xx"].metric_name == "HTTPCode_ELB_5XX_Count" &&
      aws_cloudwatch_metric_alarm.runtime["alb-member-gateway-target-5xx"].metric_name == "HTTPCode_Target_5XX_Count" &&
      aws_cloudwatch_metric_alarm.runtime["alb-admin-gateway-unhealthy-host"].metric_name == "UnHealthyHostCount"
    )
    error_message = "Runtime ON must monitor ALB-generated 5xx, target 5xx, and unhealthy public gateway targets."
  }

  assert {
    condition = alltrue([
      for alarm in values(aws_cloudwatch_metric_alarm.runtime) :
      alarm.actions_enabled &&
      length(alarm.alarm_actions) == 1 &&
      length(alarm.ok_actions) == 1
    ])
    error_message = "Every Runtime alarm must enable exactly one alarm action and one recovery action."
  }
}

run "runtime_watchdog_contract" {
  command = plan

  module {
    source = "./modules/observability"
  }

  variables {
    name_prefix                   = "spring-react-msa-learning"
    aws_region                    = "ap-northeast-2"
    alert_email                   = "terraform-test@example.com"
    db_instance_identifier        = "spring-react-msa-learning-postgres"
    db_instance_arn               = "arn:aws:rds:ap-northeast-2:111122223333:db:spring-react-msa-learning-postgres"
    runtime_observability_enabled = true
    runtime_enabled               = false
    watchdog_enabled              = true
    ecs_cluster_name              = "spring-react-msa-learning-cluster"
    ecs_autoscaling_group_name    = "spring-react-msa-learning-ecs"
    ecs_service_names = {
      "member-gateway"       = "spring-react-msa-learning-member-gateway"
      "admin-gateway"        = "spring-react-msa-learning-admin-gateway"
      "authorization-server" = "spring-react-msa-learning-authorization-server"
      "user-service"         = "spring-react-msa-learning-user-service"
      "community-service"    = "spring-react-msa-learning-community-service"
      "stock-service"        = "spring-react-msa-learning-stock-service"
      "member-bff"           = "spring-react-msa-learning-member-bff"
      "admin-bff"            = "spring-react-msa-learning-admin-bff"
    }
    common_tags = {
      Environment = "learning"
      ManagedBy   = "Terraform"
      Project     = "spring-react-msa"
    }
  }

  assert {
    condition = (
      aws_lambda_function.watchdog["this"].function_name == "spring-react-msa-learning-runtime-watchdog" &&
      aws_lambda_function.watchdog["this"].runtime == "python3.12" &&
      aws_lambda_function.watchdog["this"].architectures == tolist(["arm64"]) &&
      one(aws_lambda_function.watchdog["this"].environment).variables["RUNTIME_MAX_HOURS"] == "6" &&
      one(aws_lambda_function.watchdog["this"].environment).variables["RDS_RESTART_WARNING_HOURS"] == "24"
    )
    error_message = "The watchdog Lambda must use the approved runtime, architecture, and lifecycle thresholds."
  }

  assert {
    condition = (
      aws_cloudwatch_event_rule.watchdog["this"].schedule_expression == "rate(15 minutes)" &&
      aws_cloudwatch_event_target.watchdog["this"].target_id == "RuntimeWatchdog"
    )
    error_message = "The alert-only watchdog must run every 15 minutes through EventBridge."
  }

  assert {
    condition = (
      aws_dynamodb_table.watchdog_state["this"].billing_mode == "PAY_PER_REQUEST" &&
      aws_dynamodb_table.watchdog_state["this"].deletion_protection_enabled
    )
    error_message = "The notification state table must use on-demand billing and deletion protection."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.watchdog) == 3
    error_message = "The watchdog must monitor heartbeat, Lambda errors, and EventBridge failed invocations."
  }

  assert {
    condition = toset(local.watchdog_runtime_read_actions) == toset([
      "autoscaling:DescribeAutoScalingGroups",
      "ec2:DescribeInstances",
      "ecs:DescribeServices",
      "rds:DescribeDBInstances",
    ])
    error_message = "The alert-only watchdog role must not receive Runtime mutation permissions."
  }
}
