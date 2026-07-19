locals {
  namespace_name = "learning.spring-react-msa.internal"

  secret_names = {
    admin_bff    = "/spring-react-msa/learning/admin-bff"
    auth_server  = "/spring-react-msa/learning/auth-server"
    member_bff   = "/spring-react-msa/learning/member-bff"
    internal_api = "/spring-react-msa/learning/shared/internal-api"
    redis        = "/spring-react-msa/learning/shared/redis"
    stock        = "/spring-react-msa/learning/stock-service"
    user         = "/spring-react-msa/learning/user-service"
  }

  service_dns = {
    admin_bff            = "admin-bff.${local.namespace_name}"
    admin_gateway        = "admin-gateway.${local.namespace_name}"
    authorization_server = "authorization-server.${local.namespace_name}"
    community_service    = "community-service.${local.namespace_name}"
    member_bff           = "member-bff.${local.namespace_name}"
    member_gateway       = "member-gateway.${local.namespace_name}"
    stock_service        = "stock-service.${local.namespace_name}"
    user_service         = "user-service.${local.namespace_name}"
  }

  common_environment = {
    SPRING_PROFILES_ACTIVE                    = "prod"
    JAVA_TOOL_OPTIONS                         = "-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30"
    MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED = "true"
  }

  jwt_environment = {
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI  = var.member_public_origin
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI = "http://${local.service_dns.authorization_server}:9000/oauth2/jwks"
  }

  service_configs = {
    member-gateway = {
      repository_service = "spring-member-gateway"
      port               = 8080
      cpu                = 256
      memory             = 512
      public_host        = trimsuffix(trimprefix(var.member_public_origin, "https://"), "/")
      environment = merge(local.common_environment, {
        GATEWAY_CORS_ALLOWED_ORIGIN      = var.member_public_origin
        GATEWAY_BFF_URI                  = "http://${local.service_dns.member_bff}:8079"
        GATEWAY_BFF_WEBSOCKET_URI        = "ws://${local.service_dns.member_bff}:8079"
        GATEWAY_USER_SERVICE_URI         = "http://${local.service_dns.user_service}:8081"
        GATEWAY_COMMUNITY_SERVICE_URI    = "http://${local.service_dns.community_service}:8083"
        GATEWAY_STOCK_SERVICE_URI        = "http://${local.service_dns.stock_service}:8084"
        GATEWAY_AUTHORIZATION_SERVER_URI = "http://${local.service_dns.authorization_server}:9000"
      })
      secrets        = []
      secret_arns    = []
      parameter_arns = []
    }

    admin-gateway = {
      repository_service = "spring-admin-gateway"
      port               = 8090
      cpu                = 256
      memory             = 512
      public_host        = trimsuffix(trimprefix(var.admin_public_origin, "https://"), "/")
      environment = merge(local.common_environment, {
        ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN      = var.admin_public_origin
        ADMIN_GATEWAY_ADMIN_BFF_URI            = "http://${local.service_dns.admin_bff}:8087"
        ADMIN_GATEWAY_AUTHORIZATION_SERVER_URI = "http://${local.service_dns.authorization_server}:9000"
      })
      secrets        = []
      secret_arns    = []
      parameter_arns = []
    }

    authorization-server = {
      repository_service = "spring-security-authorization-server"
      port               = 9000
      cpu                = 512
      memory             = 1024
      public_host        = null
      environment = merge(local.common_environment, {
        SPRING_DATA_REDIS_PORT                   = "6379"
        SPRING_DATA_REDIS_USERNAME               = "spring-msa"
        SPRING_DATA_REDIS_SSL_ENABLED            = "true"
        AUTH_SERVER_ISSUER                       = var.member_public_origin
        USER_FRONTEND_LOGIN_URI                  = "${var.member_public_origin}/auth"
        ADMIN_FRONTEND_LOGIN_URI                 = "${var.admin_public_origin}/auth"
        USER_SERVICE_BASE_URL                    = "http://${local.service_dns.user_service}:8081"
        BFF_CLIENT_ID                            = var.member_bff_client_id
        BFF_OAUTH2_REDIRECT_URI                  = "${var.member_public_origin}/bff/login/oauth2/code/member-bff"
        BFF_POST_LOGOUT_REDIRECT_URI             = "${var.member_public_origin}/auth"
        ADMIN_BFF_CLIENT_ID                      = var.admin_bff_client_id
        ADMIN_BFF_OAUTH2_REDIRECT_URI            = "${var.admin_public_origin}/admin-bff/login/oauth2/code/admin-bff"
        ADMIN_BFF_POST_LOGOUT_REDIRECT_URI       = "${var.admin_public_origin}/auth"
        ADMIN_BFF_POST_LOGOUT_LOGIN_REDIRECT_URI = "${var.admin_public_origin}/auth"
      })
      secrets = [
        { name = "SPRING_DATA_REDIS_HOST", valueFrom = var.redis_host_parameter_arn },
        { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.redis]}:redis_password::" },
        { name = "SPRING_MSA_INTERNAL_API_TOKEN", valueFrom = "${var.application_secret_arns[local.secret_names.internal_api]}:internal_api_token::" },
        { name = "BFF_CLIENT_SECRET_HASH", valueFrom = "${var.application_secret_arns[local.secret_names.auth_server]}:bff_client_secret_hash::" },
        { name = "ADMIN_BFF_CLIENT_SECRET_HASH", valueFrom = "${var.application_secret_arns[local.secret_names.auth_server]}:admin_bff_client_secret_hash::" },
      ]
      secret_arns = [
        var.application_secret_arns[local.secret_names.redis],
        var.application_secret_arns[local.secret_names.internal_api],
        var.application_secret_arns[local.secret_names.auth_server],
      ]
      parameter_arns = [var.redis_host_parameter_arn]
    }

    user-service = {
      repository_service = "spring-user-service"
      port               = 8081
      cpu                = 512
      memory             = 768
      public_host        = null
      environment = merge(local.common_environment, local.jwt_environment, {
        SPRING_DATASOURCE_URL                          = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}?currentSchema=user_service&sslmode=require"
        SPRING_JPA_HIBERNATE_DDL_AUTO                  = "validate"
        SPRING_SQL_INIT_MODE                           = "never"
        SPRING_FLYWAY_ENABLED                          = "false"
        SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA = "user_service"
        SPRING_FLYWAY_DEFAULT_SCHEMA                   = "user_service"
        SPRING_FLYWAY_SCHEMAS                          = "user_service"
      })
      secrets = [
        { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.application_secret_arns[local.secret_names.user]}:db_username::" },
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.user]}:db_password::" },
        { name = "SPRING_MSA_INTERNAL_API_TOKEN", valueFrom = "${var.application_secret_arns[local.secret_names.internal_api]}:internal_api_token::" },
      ]
      secret_arns = [
        var.application_secret_arns[local.secret_names.user],
        var.application_secret_arns[local.secret_names.internal_api],
      ]
      parameter_arns = []
    }

    community-service = {
      repository_service = "spring-member-community-service"
      port               = 8083
      cpu                = 512
      memory             = 768
      public_host        = null
      environment        = merge(local.common_environment, local.jwt_environment)
      secrets            = []
      secret_arns        = []
      parameter_arns     = []
    }

    stock-service = {
      repository_service = "spring-member-stock-service"
      port               = 8084
      cpu                = 512
      memory             = 1024
      public_host        = null
      environment = merge(local.common_environment, local.jwt_environment, {
        SPRING_DATASOURCE_URL                          = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}?currentSchema=stock_service&sslmode=require"
        SPRING_JPA_HIBERNATE_DDL_AUTO                  = "validate"
        SPRING_SQL_INIT_MODE                           = "never"
        SPRING_FLYWAY_ENABLED                          = "false"
        SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA = "stock_service"
        SPRING_FLYWAY_DEFAULT_SCHEMA                   = "stock_service"
        SPRING_FLYWAY_SCHEMAS                          = "stock_service"
        SPRING_DATA_REDIS_PORT                         = "6379"
        SPRING_DATA_REDIS_USERNAME                     = "spring-msa"
        SPRING_DATA_REDIS_SSL_ENABLED                  = "true"
        TOSS_API_BASE_URL                              = "https://openapi.tossinvest.com"
        TOSS_API_CLIENT_ID                             = var.toss_api_client_id
      })
      secrets = [
        { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.application_secret_arns[local.secret_names.stock]}:db_username::" },
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.stock]}:db_password::" },
        { name = "SPRING_DATA_REDIS_HOST", valueFrom = var.redis_host_parameter_arn },
        { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.redis]}:redis_password::" },
        { name = "TOSS_API_CLIENT_SECRET", valueFrom = "${var.application_secret_arns[local.secret_names.stock]}:toss_api_client_secret::" },
      ]
      secret_arns = [
        var.application_secret_arns[local.secret_names.stock],
        var.application_secret_arns[local.secret_names.redis],
      ]
      parameter_arns = [var.redis_host_parameter_arn]
    }

    member-bff = {
      repository_service = "spring-member-bff-service"
      port               = 8079
      cpu                = 512
      memory             = 1024
      public_host        = null
      environment = merge(local.common_environment, {
        SPRING_DATASOURCE_URL                          = "jdbc:postgresql://${var.db_address}:${var.db_port}/${var.db_name}?currentSchema=member_bff&sslmode=require"
        SPRING_JPA_HIBERNATE_DDL_AUTO                  = "validate"
        SPRING_SQL_INIT_MODE                           = "never"
        SPRING_FLYWAY_ENABLED                          = "false"
        SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA = "member_bff"
        SPRING_FLYWAY_DEFAULT_SCHEMA                   = "member_bff"
        SPRING_FLYWAY_SCHEMAS                          = "member_bff"
        SPRING_DATA_REDIS_PORT                         = "6379"
        SPRING_DATA_REDIS_USERNAME                     = "spring-msa"
        SPRING_DATA_REDIS_SSL_ENABLED                  = "true"
        APP_KAFKA_ENABLED                              = "false"
        SPRING_KAFKA_BOOTSTRAP_SERVERS                 = ""
        BFF_CLIENT_ID                                  = var.member_bff_client_id
        BFF_FRONTEND_REDIRECT_URI                      = var.member_public_origin
        BFF_OAUTH2_AUTHORIZATION_URI                   = "${var.member_public_origin}/oauth2/authorize"
        BFF_OAUTH2_TOKEN_URI                           = "http://${local.service_dns.member_gateway}:8080/oauth2/token"
        BFF_OAUTH2_USERINFO_URI                        = "http://${local.service_dns.member_gateway}:8080/userinfo"
        BFF_OAUTH2_JWK_SET_URI                         = "http://${local.service_dns.authorization_server}:9000/oauth2/jwks"
        BFF_OAUTH2_REDIRECT_URI                        = "${var.member_public_origin}/bff/login/oauth2/code/member-bff"
        BFF_POST_LOGOUT_REDIRECT_URI                   = "${var.member_public_origin}/auth"
        BFF_OAUTH2_END_SESSION_URI                     = "${var.member_public_origin}/connect/logout"
        BFF_OAUTH2_LOGOUT_URI                          = "${var.member_public_origin}/logout"
        BFF_API_USER_API_BASE_URL                      = "http://${local.service_dns.user_service}:8081"
        BFF_API_USER_INTERNAL_BASE_URL                 = "http://${local.service_dns.user_service}:8081"
        BFF_API_COMMUNITY_API_BASE_URL                 = "http://${local.service_dns.community_service}:8083"
        BFF_API_STOCK_API_BASE_URL                     = "http://${local.service_dns.stock_service}:8084"
      })
      secrets = [
        { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.application_secret_arns[local.secret_names.member_bff]}:db_username::" },
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.member_bff]}:db_password::" },
        { name = "SPRING_DATA_REDIS_HOST", valueFrom = var.redis_host_parameter_arn },
        { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.redis]}:redis_password::" },
        { name = "SPRING_MSA_INTERNAL_API_TOKEN", valueFrom = "${var.application_secret_arns[local.secret_names.internal_api]}:internal_api_token::" },
        { name = "BFF_CLIENT_SECRET", valueFrom = "${var.application_secret_arns[local.secret_names.member_bff]}:bff_client_secret::" },
      ]
      secret_arns = [
        var.application_secret_arns[local.secret_names.member_bff],
        var.application_secret_arns[local.secret_names.redis],
        var.application_secret_arns[local.secret_names.internal_api],
      ]
      parameter_arns = [var.redis_host_parameter_arn]
    }

    admin-bff = {
      repository_service = "spring-admin-bff-service"
      port               = 8087
      cpu                = 512
      memory             = 768
      public_host        = null
      environment = merge(local.common_environment, {
        SPRING_DATA_REDIS_PORT                   = "6379"
        SPRING_DATA_REDIS_USERNAME               = "spring-msa"
        SPRING_DATA_REDIS_SSL_ENABLED            = "true"
        ADMIN_BFF_REGISTRATION_ENABLED           = "false"
        ADMIN_BFF_CLIENT_ID                      = var.admin_bff_client_id
        ADMIN_BFF_FRONTEND_REDIRECT_URI          = var.admin_public_origin
        ADMIN_BFF_OAUTH2_AUTHORIZATION_URI       = "${var.admin_public_origin}/oauth2/authorize"
        ADMIN_BFF_OAUTH2_TOKEN_URI               = "http://${local.service_dns.admin_gateway}:8090/oauth2/token"
        ADMIN_BFF_OAUTH2_USERINFO_URI            = "http://${local.service_dns.admin_gateway}:8090/userinfo"
        ADMIN_BFF_OAUTH2_JWK_SET_URI             = "http://${local.service_dns.authorization_server}:9000/oauth2/jwks"
        ADMIN_BFF_OAUTH2_REDIRECT_URI            = "${var.admin_public_origin}/admin-bff/login/oauth2/code/admin-bff"
        ADMIN_BFF_POST_LOGOUT_REDIRECT_URI       = "${var.admin_public_origin}/auth"
        ADMIN_BFF_POST_LOGOUT_LOGIN_REDIRECT_URI = "${var.admin_public_origin}/auth"
        ADMIN_BFF_OAUTH2_END_SESSION_URI         = "${var.admin_public_origin}/connect/logout"
        ADMIN_BFF_OAUTH2_LOGOUT_URI              = "${var.admin_public_origin}/logout"
        ADMIN_BFF_API_USER_API_BASE_URL          = "http://${local.service_dns.user_service}:8081"
        ADMIN_BFF_API_USER_INTERNAL_BASE_URL     = "http://${local.service_dns.user_service}:8081"
      })
      secrets = [
        { name = "SPRING_DATA_REDIS_HOST", valueFrom = var.redis_host_parameter_arn },
        { name = "SPRING_DATA_REDIS_PASSWORD", valueFrom = "${var.application_secret_arns[local.secret_names.redis]}:redis_password::" },
        { name = "SPRING_MSA_INTERNAL_API_TOKEN", valueFrom = "${var.application_secret_arns[local.secret_names.internal_api]}:internal_api_token::" },
        { name = "ADMIN_BFF_CLIENT_SECRET", valueFrom = "${var.application_secret_arns[local.secret_names.admin_bff]}:admin_bff_client_secret::" },
      ]
      secret_arns = [
        var.application_secret_arns[local.secret_names.redis],
        var.application_secret_arns[local.secret_names.internal_api],
        var.application_secret_arns[local.secret_names.admin_bff],
      ]
      parameter_arns = [var.redis_host_parameter_arn]
    }
  }

  public_services = {
    for key, config in local.service_configs : key => config
    if config.public_host != null
  }

  runtime = var.learning_runtime_enabled ? { this = true } : {}
}

resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = local.namespace_name
  description = "Private DNS namespace for the Learning ECS backend services"
  vpc         = var.vpc_id

  tags = merge(var.common_tags, {
    Name = local.namespace_name
  })
}

resource "aws_service_discovery_service" "backend" {
  for_each = local.service_configs

  name = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}"
  })

}

resource "aws_cloudwatch_log_group" "service" {
  for_each = local.service_configs

  name              = "/ecs/${var.name_prefix}/${each.key}"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}"
  })
}

resource "aws_iam_role" "execution" {
  for_each = local.service_configs

  name = "${var.name_prefix}-${each.key}-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy" "execution" {
  for_each = local.service_configs

  name = "runtime-execution"
  role = aws_iam_role.execution[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Sid      = "GetEcrAuthorizationToken"
          Effect   = "Allow"
          Action   = ["ecr:GetAuthorizationToken"]
          Resource = "*"
        },
        {
          Sid    = "PullOnlyServiceImage"
          Effect = "Allow"
          Action = [
            "ecr:BatchCheckLayerAvailability",
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage",
          ]
          Resource = var.ecr_repository_arns[each.value.repository_service]
        },
        {
          Sid    = "WriteOnlyServiceLogs"
          Effect = "Allow"
          Action = [
            "logs:CreateLogStream",
            "logs:PutLogEvents",
          ]
          Resource = "${aws_cloudwatch_log_group.service[each.key].arn}:*"
        },
      ],
      length(each.value.secret_arns) == 0 ? [] : [{
        Sid      = "ReadOnlyServiceSecrets"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = distinct(each.value.secret_arns)
      }],
      length(each.value.parameter_arns) == 0 ? [] : [{
        Sid      = "ReadOnlyRuntimeParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParameters"]
        Resource = distinct(each.value.parameter_arns)
      }],
    )
  })
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.service_configs

  family                   = "${var.name_prefix}-${each.key}"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = tostring(each.value.cpu)
  memory                   = tostring(each.value.memory)
  execution_role_arn       = aws_iam_role.execution[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name                   = each.key
    image                  = var.service_images[each.key]
    essential              = true
    readonlyRootFilesystem = true
    user                   = "65534"
    cpu                    = each.value.cpu
    memory                 = each.value.memory
    portMappings = [{
      name          = "${each.key}-http"
      containerPort = each.value.port
      hostPort      = each.value.port
      protocol      = "tcp"
    }]
    environment = [
      for name in sort(keys(each.value.environment)) : {
        name  = name
        value = each.value.environment[name]
      }
    ]
    secrets = [
      for secret in each.value.secrets : {
        name      = secret.name
        valueFrom = secret.valueFrom
      }
    ]
    healthCheck = {
      command     = ["CMD-SHELL", "curl --fail --silent http://127.0.0.1:${each.value.port}/actuator/health/readiness >/dev/null || exit 1"]
      interval    = 10
      timeout     = 5
      retries     = 6
      startPeriod = 60
    }
    linuxParameters = {
      initProcessEnabled = true
      tmpfs = [{
        containerPath = "/tmp"
        size          = 128
        mountOptions  = ["rw", "nosuid", "nodev", "noexec"]
      }]
    }
    stopTimeout = 30
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.service[each.key].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = each.key
      }
    }
  }])

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}"
  })

  lifecycle {
    precondition {
      condition     = each.key != "stock-service" || !var.learning_runtime_enabled || length(trimspace(var.toss_api_client_id)) > 0
      error_message = "Runtime ON requires a non-empty toss_api_client_id for the stock service."
    }
  }
}

resource "aws_lb_target_group" "gateway" {
  for_each = local.public_services

  name        = "${substr(var.name_prefix, 0, 15)}-${each.key}"
  port        = each.value.port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  deregistration_delay = 15

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
    path                = "/actuator/health/readiness"
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}"
  })
}

resource "aws_lb" "public" {
  for_each = local.runtime

  name               = substr("${var.name_prefix}-alb", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = false
  drop_invalid_header_fields = true
  idle_timeout               = 3600

  tags = merge(var.common_tags, {
    Name    = "${var.name_prefix}-alb"
    Runtime = "disposable"
  })

  lifecycle {
    precondition {
      condition = !var.enable_public_domain_routing || (
        var.public_hosted_zone_id != null &&
        var.origin_certificate_arn != null
      )
      error_message = "Public domain routing requires the existing hosted-zone ID and an issued regional origin certificate."
    }
  }
}

resource "aws_lb_listener" "http" {
  for_each = var.learning_runtime_enabled && !var.enable_public_domain_routing ? local.runtime : {}

  load_balancer_arn = aws_lb.public[each.key].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"message\":\"not found\"}"
      status_code  = "404"
    }
  }

  tags = var.common_tags
}

resource "aws_lb_listener" "https" {
  for_each = var.learning_runtime_enabled && var.enable_public_domain_routing ? local.runtime : {}

  load_balancer_arn = aws_lb.public[each.key].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.origin_certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"message\":\"not found\"}"
      status_code  = "404"
    }
  }

  tags = var.common_tags
}

resource "aws_lb_listener_rule" "gateway" {
  for_each = var.learning_runtime_enabled ? local.public_services : {}

  listener_arn = var.enable_public_domain_routing ? aws_lb_listener.https["this"].arn : aws_lb_listener.http["this"].arn
  priority     = each.key == "member-gateway" ? 100 : 110

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway[each.key].arn
  }

  condition {
    host_header {
      values = [each.value.public_host]
    }
  }

  tags = var.common_tags
}

resource "aws_route53_record" "origin" {
  for_each = var.learning_runtime_enabled && var.enable_public_domain_routing ? local.runtime : {}

  zone_id = var.public_hosted_zone_id
  name    = var.origin_domain
  type    = "A"

  alias {
    name                   = aws_lb.public[each.key].dns_name
    zone_id                = aws_lb.public[each.key].zone_id
    evaluate_target_health = true
  }
}

resource "aws_ecs_service" "backend" {
  for_each = local.service_configs

  name            = "${var.name_prefix}-${each.key}"
  cluster         = var.ecs_cluster_arn
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = var.learning_runtime_enabled ? 1 : 0

  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = var.learning_runtime_enabled && each.value.public_host != null ? 180 : null

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  capacity_provider_strategy {
    capacity_provider = var.capacity_provider_name
    base              = 0
    weight            = 1
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.ecs_security_group_id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.backend[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = var.learning_runtime_enabled && each.value.public_host != null ? [each.value] : []

    content {
      target_group_arn = aws_lb_target_group.gateway[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  depends_on = [aws_lb_listener_rule.gateway]

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-${each.key}"
  })
}
