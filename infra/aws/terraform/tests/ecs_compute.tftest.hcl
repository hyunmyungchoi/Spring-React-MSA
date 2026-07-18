mock_provider "aws" {}

run "runtime_off_contract" {
  command = plan

  module {
    source = "./modules/ecs-compute"
  }

  variables {
    name_prefix              = "spring-react-msa-learning"
    private_app_subnet_ids   = ["subnet-private-a", "subnet-private-b"]
    ecs_security_group_id    = "sg-0123456789abcdef0"
    ecs_optimized_ami_id     = "ami-0123456789abcdef0"
    instance_type            = "m6i.xlarge"
    learning_runtime_enabled = false
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
    }
  }

  assert {
    condition = (
      aws_autoscaling_group.ecs.min_size == 0 &&
      aws_autoscaling_group.ecs.desired_capacity == 0 &&
      aws_autoscaling_group.ecs.max_size == 0
    )
    error_message = "Runtime OFF must keep ECS ASG min, desired, and max capacity at zero."
  }

  assert {
    condition = (
      aws_launch_template.ecs.instance_type == "m6i.xlarge" &&
      tobool(aws_launch_template.ecs.network_interfaces[0].associate_public_ip_address) == false &&
      aws_launch_template.ecs.metadata_options[0].http_tokens == "required" &&
      tobool(aws_launch_template.ecs.block_device_mappings[0].ebs[0].encrypted) == true
    )
    error_message = "The ECS launch template must use the approved instance type, no public IP, IMDSv2, and encrypted storage."
  }

  assert {
    condition = (
      strcontains(base64decode(aws_launch_template.ecs.user_data), "ECS_CLUSTER=spring-react-msa-learning-cluster") &&
      strcontains(base64decode(aws_launch_template.ecs.user_data), "ECS_AWSVPC_BLOCK_IMDS=true")
    )
    error_message = "ECS user data must join the approved cluster and block task access to instance metadata."
  }

  assert {
    condition = (
      aws_autoscaling_group.ecs.protect_from_scale_in == true &&
      aws_ecs_capacity_provider.this.auto_scaling_group_provider[0].managed_termination_protection == "ENABLED" &&
      aws_ecs_capacity_provider.this.auto_scaling_group_provider[0].managed_scaling[0].status == "ENABLED"
    )
    error_message = "Capacity provider managed scaling and termination protection must be enabled together."
  }

  assert {
    condition = one([
      for tag in aws_autoscaling_group.ecs.tag : tag
      if tag.key == "AmazonECSManaged"
    ]).propagate_at_launch == true
    error_message = "The ASG must manage the AmazonECSManaged tag required by the ECS capacity provider."
  }

  assert {
    condition = toset([
      aws_iam_role_policy_attachment.ecs_instance.policy_arn,
      aws_iam_role_policy_attachment.ssm_instance.policy_arn,
      ]) == toset([
      "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role",
      "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore",
    ])
    error_message = "The container instance role must have only the ECS instance and SSM managed policies."
  }
}

run "runtime_on_contract" {
  command = plan

  module {
    source = "./modules/ecs-compute"
  }

  variables {
    name_prefix              = "spring-react-msa-learning"
    private_app_subnet_ids   = ["subnet-private-a", "subnet-private-b"]
    ecs_security_group_id    = "sg-0123456789abcdef0"
    ecs_optimized_ami_id     = "ami-0123456789abcdef0"
    instance_type            = "m6i.xlarge"
    learning_runtime_enabled = true
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
    }
  }

  assert {
    condition = (
      aws_autoscaling_group.ecs.min_size == 1 &&
      aws_autoscaling_group.ecs.desired_capacity == 1 &&
      aws_autoscaling_group.ecs.max_size == 2
    )
    error_message = "Runtime ON must use the approved ASG capacity of min 1, desired 1, and max 2."
  }
}
