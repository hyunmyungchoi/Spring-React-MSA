locals {
  subnet_suffixes = ["a", "b"]

  subnets = merge(
    {
      for index, cidr in var.public_subnet_cidrs : "public-${index}" => {
        availability_zone = var.availability_zones[index]
        cidr_block        = cidr
        name              = "${var.name_prefix}-public-${local.subnet_suffixes[index]}"
        tier              = "public"
      }
    },
    {
      for index, cidr in var.private_app_subnet_cidrs : "private-app-${index}" => {
        availability_zone = var.availability_zones[index]
        cidr_block        = cidr
        name              = "${var.name_prefix}-private-app-${local.subnet_suffixes[index]}"
        tier              = "private-app"
      }
    },
    {
      for index, cidr in var.private_data_subnet_cidrs : "private-data-${index}" => {
        availability_zone = var.availability_zones[index]
        cidr_block        = cidr
        name              = "${var.name_prefix}-private-data-${local.subnet_suffixes[index]}"
        tier              = "private-data"
      }
    }
  )

  alb_ingress_ports = {
    http  = 80
    https = 443
  }

  gateway_ports = {
    member-gateway = 8080
    admin-gateway  = 8090
  }

  internal_service_ports = {
    member-gateway       = 8080
    admin-gateway        = 8090
    member-bff           = 8079
    admin-bff            = 8087
    authorization-server = 9000
    user-service         = 8081
    community-service    = 8083
    stock-service        = 8084
  }

  data_ports = {
    postgresql = 5432
    redis      = 6379
    kafka      = 9092
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-vpc"
  })
}

resource "aws_subnet" "this" {
  for_each = local.subnets

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.value.availability_zone
  cidr_block              = each.value.cidr_block
  map_public_ip_on_launch = false

  tags = merge(var.common_tags, {
    Name = each.value.name
    Tier = each.value.tier
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-igw"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-public-rt"
    Tier = "public"
  })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-nat-eip"
  })
}

resource "aws_nat_gateway" "this" {
  count = var.enable_nat_gateway ? 1 : 0

  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.this["public-0"].id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-nat-a"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "private_app" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-private-app-rt"
    Tier = "private-app"
  })
}

resource "aws_route_table" "private_data" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-private-data-rt"
    Tier = "private-data"
  })
}

resource "aws_route" "private_app_internet" {
  count = var.enable_nat_gateway ? 1 : 0

  route_table_id         = aws_route_table.private_app.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[0].id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private_app.id]

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-s3-gateway-endpoint"
  })
}

resource "aws_route_table_association" "public" {
  for_each = {
    for key, subnet in aws_subnet.this : key => subnet
    if startswith(key, "public-")
  }

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private_app" {
  for_each = {
    for key, subnet in aws_subnet.this : key => subnet
    if startswith(key, "private-app-")
  }

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private_app.id
}

resource "aws_route_table_association" "private_data" {
  for_each = {
    for key, subnet in aws_subnet.this : key => subnet
    if startswith(key, "private-data-")
  }

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private_data.id
}

resource "aws_security_group" "alb" {
  name                   = "${var.name_prefix}-alb-sg"
  description            = "Public HTTP and HTTPS entry point for the future ALB"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-alb-sg"
  })
}

resource "aws_security_group" "ecs" {
  name                   = "${var.name_prefix}-ecs-app-sg"
  description            = "Application traffic for future ECS tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-app-sg"
  })
}

resource "aws_security_group" "data" {
  name                   = "${var.name_prefix}-data-sg"
  description            = "Data tier traffic from future ECS tasks"
  vpc_id                 = aws_vpc.this.id
  revoke_rules_on_delete = true

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-data-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "alb_public" {
  for_each = var.cloudfront_origin_prefix_list_id == null ? local.alb_ingress_ports : {}

  security_group_id = aws_security_group.alb.id
  description       = "Allow public ${each.key} traffic to the future ALB"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = each.value
  to_port           = each.value
  ip_protocol       = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-alb-${each.key}-ingress"
  })
}

resource "aws_vpc_security_group_egress_rule" "alb_to_ecs" {
  for_each = local.gateway_ports

  security_group_id            = aws_security_group.alb.id
  description                  = "Allow the future ALB to reach ${each.key}"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-alb-to-${each.key}"
  })
}

resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  for_each = local.gateway_ports

  security_group_id            = aws_security_group.ecs.id
  description                  = "Allow ${each.key} traffic from the future ALB"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-from-${each.key}"
  })
}

resource "aws_vpc_security_group_ingress_rule" "ecs_internal" {
  for_each = local.internal_service_ports

  security_group_id            = aws_security_group.ecs.id
  description                  = "Allow ECS tasks to reach ${each.key}"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-${each.key}-ingress"
  })
}

resource "aws_vpc_security_group_egress_rule" "ecs_internal" {
  for_each = local.internal_service_ports

  security_group_id            = aws_security_group.ecs.id
  description                  = "Allow ECS tasks to call ${each.key}"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-${each.key}-egress"
  })
}

resource "aws_vpc_security_group_egress_rule" "ecs_to_data" {
  for_each = local.data_ports

  security_group_id            = aws_security_group.ecs.id
  description                  = "Allow ECS tasks to reach ${each.key}"
  referenced_security_group_id = aws_security_group.data.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-to-${each.key}"
  })
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  count = var.cloudfront_origin_prefix_list_id == null ? 0 : 1

  security_group_id = aws_security_group.alb.id
  description       = "Allow HTTPS only from the AWS-managed CloudFront origin-facing network"
  prefix_list_id    = var.cloudfront_origin_prefix_list_id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-alb-cloudfront-https-ingress"
  })
}

resource "aws_vpc_security_group_egress_rule" "ecs_external_https" {
  security_group_id = aws_security_group.ecs.id
  description       = "Allow ECS tasks to reach AWS public APIs and external HTTPS services"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-external-https-egress"
  })
}

resource "aws_vpc_security_group_ingress_rule" "data_from_ecs" {
  for_each = local.data_ports

  security_group_id            = aws_security_group.data.id
  description                  = "Allow ${each.key} traffic from ECS tasks"
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = each.value
  to_port                      = each.value
  ip_protocol                  = "tcp"

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-data-${each.key}-ingress"
  })
}
