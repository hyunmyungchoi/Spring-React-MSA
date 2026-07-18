output "cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "capacity_provider_name" {
  description = "ECS EC2 capacity provider name."
  value       = aws_ecs_capacity_provider.this.name
}

output "autoscaling_group_name" {
  description = "ECS container instance Auto Scaling Group name."
  value       = aws_autoscaling_group.ecs.name
}

output "instance_role_arn" {
  description = "IAM role ARN used only by ECS container instances and SSM."
  value       = aws_iam_role.ecs_instance.arn
}

output "launch_template_id" {
  description = "ECS container instance launch template ID."
  value       = aws_launch_template.ecs.id
}

output "runtime_capacity" {
  description = "Configured Learning ASG min, desired, and max capacity."
  value = {
    min     = local.asg_min_size
    desired = local.asg_desired_capacity
    max     = local.asg_max_size
  }
}

output "awsvpc_trunking" {
  description = "Account-level ECS task ENI trunking setting required to place all eight awsvpc tasks on one Learning instance."
  value       = aws_ecs_account_setting_default.awsvpc_trunking.value
}
