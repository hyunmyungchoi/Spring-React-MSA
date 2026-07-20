data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  runtime_active = var.runtime_observability_enabled && var.runtime_enabled

  rds_alarms = {
    cpu_high = {
      metric_name         = "CPUUtilization"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 80
      statistic           = "Average"
      unit                = "Percent"
      description         = "RDS CPU utilization is at least 80 percent for 15 minutes."
    }
    freeable_memory_low = {
      metric_name         = "FreeableMemory"
      comparison_operator = "LessThanOrEqualToThreshold"
      threshold           = 268435456
      statistic           = "Minimum"
      unit                = "Bytes"
      description         = "RDS freeable memory is at or below 256 MiB for 15 minutes."
    }
    free_storage_low = {
      metric_name         = "FreeStorageSpace"
      comparison_operator = "LessThanOrEqualToThreshold"
      threshold           = 5368709120
      statistic           = "Minimum"
      unit                = "Bytes"
      description         = "RDS free storage is at or below 5 GiB for 15 minutes."
    }
  }

  ecs_utilization_alarms = local.runtime_active ? merge({}, [
    for service_key, service_name in var.ecs_service_names : {
      "ecs-${service_key}-cpu-high" = {
        metric_name         = "CPUUtilization"
        namespace           = "AWS/ECS"
        comparison_operator = "GreaterThanOrEqualToThreshold"
        threshold           = 80
        statistic           = "Average"
        unit                = "Percent"
        period              = 300
        evaluation_periods  = 3
        datapoints_to_alarm = 3
        treat_missing_data  = "notBreaching"
        description         = "ECS service CPU utilization is at least 80 percent for 15 minutes."
        dimensions = {
          ClusterName = var.ecs_cluster_name
          ServiceName = service_name
        }
      }
      "ecs-${service_key}-memory-high" = {
        metric_name         = "MemoryUtilization"
        namespace           = "AWS/ECS"
        comparison_operator = "GreaterThanOrEqualToThreshold"
        threshold           = 85
        statistic           = "Average"
        unit                = "Percent"
        period              = 300
        evaluation_periods  = 3
        datapoints_to_alarm = 3
        treat_missing_data  = "notBreaching"
        description         = "ECS service memory utilization is at least 85 percent for 15 minutes."
        dimensions = {
          ClusterName = var.ecs_cluster_name
          ServiceName = service_name
        }
      }
    }
  ]...) : {}

  ecs_task_count_alarms = local.runtime_active ? {
    for service_key, service_name in var.ecs_service_names : "ecs-${service_key}-task-count-low" => {
      metric_name         = "RunningTaskCount"
      namespace           = "ECS/ContainerInsights"
      comparison_operator = "LessThanThreshold"
      threshold           = 1
      statistic           = "Minimum"
      unit                = "Count"
      period              = 60
      evaluation_periods  = 5
      datapoints_to_alarm = 3
      treat_missing_data  = "breaching"
      description         = "ECS service has fewer than one running task for three of five minutes."
      dimensions = {
        ClusterName = var.ecs_cluster_name
        ServiceName = service_name
      }
    }
  } : {}

  alb_alarms = local.runtime_active ? {
    "alb-load-balancer-5xx" = {
      metric_name         = "HTTPCode_ELB_5XX_Count"
      namespace           = "AWS/ApplicationELB"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 5
      statistic           = "Sum"
      unit                = "Count"
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      description         = "Public ALB generated at least five 5xx responses in two consecutive five-minute periods."
      dimensions = {
        LoadBalancer = coalesce(var.load_balancer_arn_suffix, "missing")
      }
    }
  } : {}

  target_group_alarms = local.runtime_active ? merge({}, [
    for service_key, target_group_suffix in var.target_group_arn_suffixes : {
      "alb-${service_key}-target-5xx" = {
        metric_name         = "HTTPCode_Target_5XX_Count"
        namespace           = "AWS/ApplicationELB"
        comparison_operator = "GreaterThanOrEqualToThreshold"
        threshold           = 5
        statistic           = "Sum"
        unit                = "Count"
        period              = 300
        evaluation_periods  = 2
        datapoints_to_alarm = 2
        treat_missing_data  = "notBreaching"
        description         = "Public gateway targets returned at least five 5xx responses in two consecutive five-minute periods."
        dimensions = {
          LoadBalancer = coalesce(var.load_balancer_arn_suffix, "missing")
          TargetGroup  = target_group_suffix
        }
      }
      "alb-${service_key}-unhealthy-host" = {
        metric_name         = "UnHealthyHostCount"
        namespace           = "AWS/ApplicationELB"
        comparison_operator = "GreaterThanOrEqualToThreshold"
        threshold           = 1
        statistic           = "Maximum"
        unit                = "Count"
        period              = 60
        evaluation_periods  = 3
        datapoints_to_alarm = 2
        treat_missing_data  = "notBreaching"
        description         = "Public gateway target group reported an unhealthy host for two of three minutes."
        dimensions = {
          LoadBalancer = coalesce(var.load_balancer_arn_suffix, "missing")
          TargetGroup  = target_group_suffix
        }
      }
    }
  ]...) : {}

  runtime_alarms = merge(
    local.ecs_utilization_alarms,
    local.ecs_task_count_alarms,
    local.alb_alarms,
    local.target_group_alarms,
  )
}

resource "aws_sns_topic" "operations" {
  name = "${var.name_prefix}-operations"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-operations"
  })

  lifecycle {
    precondition {
      condition = !local.runtime_active || (
        length(trimspace(var.ecs_cluster_name)) > 0 &&
        length(var.ecs_service_names) == 8 &&
        var.load_balancer_arn_suffix != null &&
        length(var.target_group_arn_suffixes) == 2
      )
      error_message = "Runtime observability requires one ECS cluster, eight ECS services, one ALB, and two public gateway target groups."
    }
  }
}

data "aws_iam_policy_document" "operations" {
  statement {
    sid       = "RdsEventPublish"
    effect    = "Allow"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.operations.arn]

    principals {
      type        = "Service"
      identifiers = ["events.rds.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [var.db_instance_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }

  statement {
    sid       = "CloudWatchAlarmPublish"
    effect    = "Allow"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.operations.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:${data.aws_partition.current.partition}:cloudwatch:${var.aws_region}:${data.aws_caller_identity.current.account_id}:alarm:${var.name_prefix}-*"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "operations" {
  arn    = aws_sns_topic.operations.arn
  policy = data.aws_iam_policy_document.operations.json
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.operations.arn
  protocol  = "email"
  endpoint  = var.alert_email

  depends_on = [aws_sns_topic_policy.operations]
}

resource "aws_cloudwatch_metric_alarm" "rds" {
  for_each = local.rds_alarms

  alarm_name          = "${var.name_prefix}-rds-${replace(each.key, "_", "-")}"
  alarm_description   = "${each.value.description} Runbook: docs/runbooks/aws-observability.md"
  comparison_operator = each.value.comparison_operator
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = each.value.threshold
  metric_name         = each.value.metric_name
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = each.value.statistic
  unit                = each.value.unit
  treat_missing_data  = "notBreaching"
  actions_enabled     = true

  dimensions = {
    DBInstanceIdentifier = var.db_instance_identifier
  }

  alarm_actions = [aws_sns_topic.operations.arn]
  ok_actions    = [aws_sns_topic.operations.arn]

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-rds-${replace(each.key, "_", "-")}"
  })

  depends_on = [aws_sns_topic_policy.operations]
}

resource "aws_cloudwatch_metric_alarm" "runtime" {
  for_each = local.runtime_alarms

  alarm_name          = "${var.name_prefix}-${each.key}"
  alarm_description   = "${each.value.description} Runbook: docs/runbooks/aws-observability.md"
  comparison_operator = each.value.comparison_operator
  evaluation_periods  = each.value.evaluation_periods
  datapoints_to_alarm = each.value.datapoints_to_alarm
  threshold           = each.value.threshold
  metric_name         = each.value.metric_name
  namespace           = each.value.namespace
  period              = each.value.period
  statistic           = each.value.statistic
  unit                = each.value.unit
  treat_missing_data  = each.value.treat_missing_data
  actions_enabled     = true
  dimensions          = each.value.dimensions

  alarm_actions = [aws_sns_topic.operations.arn]
  ok_actions    = [aws_sns_topic.operations.arn]

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-${each.key}"
    Runtime = "disposable"
  })

  depends_on = [aws_sns_topic_policy.operations]
}

resource "aws_db_event_subscription" "rds" {
  name      = "${var.name_prefix}-rds-events"
  sns_topic = aws_sns_topic.operations.arn

  source_type = "db-instance"
  source_ids  = [var.db_instance_identifier]
  event_categories = [
    "availability",
    "backup",
    "failure",
    "low storage",
    "maintenance",
    "notification",
  ]

  enabled = true

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-rds-events"
  })

  depends_on = [aws_sns_topic_policy.operations]
}
