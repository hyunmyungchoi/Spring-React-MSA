locals {
  asg_min_size         = var.learning_runtime_enabled ? 1 : 0
  asg_desired_capacity = var.learning_runtime_enabled ? 1 : 0
  asg_max_size         = var.learning_runtime_enabled ? 2 : 0

  instance_tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs"
  })

  asg_tags = merge(local.instance_tags, {
    AmazonECSManaged = ""
  })
}

resource "aws_ecs_account_setting_default" "awsvpc_trunking" {
  name  = "awsvpcTrunking"
  value = "enabled"
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-cluster"
  })
}

resource "aws_iam_role" "ecs_instance" {
  name = "${var.name_prefix}-ecs-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "ecs_instance" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "ssm_instance" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ecs" {
  name = "${var.name_prefix}-ecs-instance"
  role = aws_iam_role.ecs_instance.name

  tags = var.common_tags
}

resource "aws_launch_template" "ecs" {
  name_prefix   = "${var.name_prefix}-ecs-"
  image_id      = var.ecs_optimized_ami_id
  instance_type = var.instance_type

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = true
      iops                  = 3000
      throughput            = 125
      volume_size           = 30
      volume_type           = "gp3"
    }
  }

  iam_instance_profile {
    name = aws_iam_instance_profile.ecs.name
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_protocol_ipv6          = "disabled"
    http_put_response_hop_limit = 2
    http_tokens                 = "required"
    instance_metadata_tags      = "disabled"
  }

  monitoring {
    enabled = false
  }

  network_interfaces {
    associate_public_ip_address = false
    delete_on_termination       = true
    device_index                = 0
    security_groups             = [var.ecs_security_group_id]
  }

  tag_specifications {
    resource_type = "instance"
    tags          = local.instance_tags
  }

  tag_specifications {
    resource_type = "volume"
    tags          = local.instance_tags
  }

  user_data = base64encode(<<-EOT
    #!/bin/bash
    cat <<'EOF' >> /etc/ecs/ecs.config
    ECS_CLUSTER=${aws_ecs_cluster.this.name}
    ECS_ENABLE_TASK_IAM_ROLE=true
    ECS_ENABLE_TASK_IAM_ROLE_NETWORK_HOST=true
    ECS_AWSVPC_BLOCK_IMDS=true
    ECS_LOGLEVEL=info
    EOF
  EOT
  )

  update_default_version = true

  tags = var.common_tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "ecs" {
  name = "${var.name_prefix}-ecs"

  min_size         = local.asg_min_size
  desired_capacity = local.asg_desired_capacity
  max_size         = local.asg_max_size

  vpc_zone_identifier = var.private_app_subnet_ids

  health_check_type         = "EC2"
  health_check_grace_period = 300
  default_instance_warmup   = 300
  protect_from_scale_in     = true

  launch_template {
    id      = aws_launch_template.ecs.id
    version = "$Latest"
  }

  dynamic "tag" {
    for_each = local.asg_tags

    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }

  lifecycle {
    ignore_changes = [desired_capacity]
  }

  depends_on = [
    aws_ecs_account_setting_default.awsvpc_trunking,
    aws_iam_role_policy_attachment.ecs_instance,
    aws_iam_role_policy_attachment.ssm_instance,
  ]
}

resource "aws_ecs_capacity_provider" "this" {
  name = "${var.name_prefix}-ec2"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.ecs.arn
    managed_draining               = "ENABLED"
    managed_termination_protection = "ENABLED"

    managed_scaling {
      instance_warmup_period    = 300
      maximum_scaling_step_size = 1
      minimum_scaling_step_size = 1
      status                    = "ENABLED"
      target_capacity           = 100
    }
  }

  tags = var.common_tags
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = [aws_ecs_capacity_provider.this.name]

  default_capacity_provider_strategy {
    base              = 0
    capacity_provider = aws_ecs_capacity_provider.this.name
    weight            = 1
  }
}
