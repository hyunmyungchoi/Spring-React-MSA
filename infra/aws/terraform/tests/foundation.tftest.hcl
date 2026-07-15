mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["ap-northeast-2a", "ap-northeast-2c"]
    }
  }

  mock_data "aws_iam_openid_connect_provider" {
    defaults = {
      arn = "arn:aws:iam::111122223333:oidc-provider/token.actions.githubusercontent.com"
      url = "token.actions.githubusercontent.com"
    }
  }
}

run "foundation_plan" {
  command = plan

  variables {
    enable_budget      = true
    budget_alert_email = "terraform-test@example.com"
  }

  assert {
    condition     = length(module.network.public_subnet_ids) == 2
    error_message = "Public subnet count must be two."
  }

  assert {
    condition     = length(module.network.private_app_subnet_ids) == 2
    error_message = "Private app subnet count must be two."
  }

  assert {
    condition     = length(module.network.private_data_subnet_ids) == 2
    error_message = "Private data subnet count must be two."
  }

  assert {
    condition     = length(module.network.availability_zones) == 2
    error_message = "Exactly two availability zones must be used."
  }

  assert {
    condition     = aws_budgets_budget.monthly[0].limit_amount == "50"
    error_message = "Monthly budget must be USD 50."
  }

  assert {
    condition     = length(aws_budgets_budget.monthly[0].notification) == 4
    error_message = "Budget must have four notifications."
  }

  assert {
    condition = toset([
      for notification in aws_budgets_budget.monthly[0].notification : notification.threshold
    ]) == toset([10, 30, 40, 50])
    error_message = "Budget thresholds must be USD 10, 30, 40, and 50."
  }

  assert {
    condition = alltrue([
      for notification in aws_budgets_budget.monthly[0].notification :
      notification.notification_type == "ACTUAL" && notification.threshold_type == "ABSOLUTE_VALUE"
    ])
    error_message = "Budget notifications must use actual absolute costs."
  }
}

run "budget_disabled_without_email" {
  command = plan

  variables {
    enable_budget      = false
    budget_alert_email = null
  }

  assert {
    condition     = length(aws_budgets_budget.monthly) == 0
    error_message = "Budget must remain disabled until a real alert email is supplied."
  }
}

run "budget_requires_email" {
  command = plan

  variables {
    enable_budget      = true
    budget_alert_email = null
  }

  expect_failures = [var.budget_alert_email]
}

run "network_contract" {
  command = plan

  module {
    source = "./modules/network"
  }

  variables {
    name_prefix               = "spring-react-msa-learning"
    vpc_cidr                  = "10.20.0.0/16"
    availability_zones        = ["ap-northeast-2a", "ap-northeast-2c"]
    public_subnet_cidrs       = ["10.20.0.0/24", "10.20.1.0/24"]
    private_app_subnet_cidrs  = ["10.20.10.0/24", "10.20.11.0/24"]
    private_data_subnet_cidrs = ["10.20.20.0/24", "10.20.21.0/24"]
    common_tags = {
      Environment = "learning"
      ManagedBy   = "Terraform"
      Project     = "spring-react-msa"
    }
  }

  assert {
    condition     = aws_vpc.this.cidr_block == "10.20.0.0/16"
    error_message = "VPC CIDR must be 10.20.0.0/16."
  }

  assert {
    condition = toset([
      for subnet in values(aws_subnet.this) : subnet.cidr_block
      ]) == toset([
      "10.20.0.0/24",
      "10.20.1.0/24",
      "10.20.10.0/24",
      "10.20.11.0/24",
      "10.20.20.0/24",
      "10.20.21.0/24",
    ])
    error_message = "All six subnet CIDRs must match the foundation design."
  }

  assert {
    condition     = alltrue([for subnet in values(aws_subnet.this) : !subnet.map_public_ip_on_launch])
    error_message = "Automatic public IPv4 assignment must be disabled on every subnet."
  }

  assert {
    condition     = aws_route.public_internet.destination_cidr_block == "0.0.0.0/0"
    error_message = "Only the public route must target the Internet Gateway."
  }

  assert {
    condition = (
      length(aws_route_table_association.public) == 2 &&
      length(aws_route_table_association.private_app) == 2 &&
      length(aws_route_table_association.private_data) == 2
    )
    error_message = "Each route table must be associated with its two tier subnets."
  }

  assert {
    condition = (
      length(aws_vpc_security_group_ingress_rule.alb_public) == 2 &&
      toset([for rule in values(aws_vpc_security_group_ingress_rule.alb_public) : rule.from_port]) == toset([80, 443]) &&
      alltrue([for rule in values(aws_vpc_security_group_ingress_rule.alb_public) : rule.cidr_ipv4 == "0.0.0.0/0"])
    )
    error_message = "The ALB security group must expose only HTTP and HTTPS publicly."
  }

  assert {
    condition = (
      length(aws_vpc_security_group_egress_rule.alb_to_ecs) == 2 &&
      length(aws_vpc_security_group_ingress_rule.ecs_from_alb) == 2 &&
      toset([for rule in values(aws_vpc_security_group_egress_rule.alb_to_ecs) : rule.from_port]) == toset([8080, 8090]) &&
      toset([for rule in values(aws_vpc_security_group_ingress_rule.ecs_from_alb) : rule.from_port]) == toset([8080, 8090])
    )
    error_message = "ALB-to-ECS traffic must be restricted to gateway ports 8080 and 8090."
  }

  assert {
    condition = (
      length(aws_vpc_security_group_ingress_rule.ecs_internal) == 6 &&
      length(aws_vpc_security_group_egress_rule.ecs_internal) == 6 &&
      toset([for rule in values(aws_vpc_security_group_ingress_rule.ecs_internal) : rule.from_port]) == toset([8079, 8087, 9000, 8081, 8083, 8084])
    )
    error_message = "ECS self-referencing rules must contain the six service ports."
  }

  assert {
    condition = (
      length(aws_vpc_security_group_egress_rule.ecs_to_data) == 3 &&
      length(aws_vpc_security_group_ingress_rule.data_from_ecs) == 3 &&
      toset([for rule in values(aws_vpc_security_group_egress_rule.ecs_to_data) : rule.from_port]) == toset([5432, 6379, 9092]) &&
      toset([for rule in values(aws_vpc_security_group_ingress_rule.data_from_ecs) : rule.from_port]) == toset([5432, 6379, 9092])
    )
    error_message = "ECS-to-data traffic must be restricted to PostgreSQL, Redis, and Kafka ports."
  }
}
