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
