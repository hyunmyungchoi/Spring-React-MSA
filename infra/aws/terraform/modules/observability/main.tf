data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
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
}

resource "aws_sns_topic" "operations" {
  name = "${var.name_prefix}-operations"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-operations"
  })
}

data "aws_iam_policy_document" "operations" {
  statement {
    sid       = "AccountAdministration"
    effect    = "Allow"
    actions   = ["SNS:*"]
    resources = [aws_sns_topic.operations.arn]

    principals {
      type        = "AWS"
      identifiers = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  statement {
    sid       = "RdsEventPublish"
    effect    = "Allow"
    actions   = ["SNS:Publish"]
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
    actions   = ["SNS:Publish"]
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
