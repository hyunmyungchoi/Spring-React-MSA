output "operations_sns_topic_arn" {
  description = "SNS topic receiving Learning operational alarms and RDS events."
  value       = aws_sns_topic.operations.arn
}

output "rds_alarm_names" {
  description = "CloudWatch RDS alarm names keyed by contract name."
  value       = { for key, alarm in aws_cloudwatch_metric_alarm.rds : key => alarm.alarm_name }
}

output "rds_event_subscription_name" {
  description = "RDS event subscription name."
  value       = aws_db_event_subscription.rds.name
}

output "runtime_alarm_names" {
  description = "Lifecycle-scoped ECS and ALB alarm names keyed by contract name."
  value       = { for key, alarm in aws_cloudwatch_metric_alarm.runtime : key => alarm.alarm_name }
}

output "runtime_watchdog_function_name" {
  description = "Alert-only Runtime watchdog Lambda function name, or null when disabled."
  value       = try(aws_lambda_function.watchdog["this"].function_name, null)
}

output "runtime_watchdog_alarm_names" {
  description = "Watchdog self-monitoring alarm names, or an empty map when disabled."
  value       = { for key, alarm in aws_cloudwatch_metric_alarm.watchdog : key => alarm.alarm_name }
}

output "runtime_watchdog_schedule_rule_name" {
  description = "EventBridge schedule rule name, or null when the watchdog is disabled."
  value       = try(aws_cloudwatch_event_rule.watchdog["this"].name, null)
}
