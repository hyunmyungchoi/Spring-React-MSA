mock_provider "aws" {}

variables {
  name_prefix              = "spring-react-msa-learning"
  aws_region               = "ap-northeast-2"
  vpc_id                   = "vpc-learning"
  public_subnet_ids        = ["subnet-public-a", "subnet-public-b"]
  private_app_subnet_ids   = ["subnet-app-a", "subnet-app-b"]
  alb_security_group_id    = "sg-alb"
  ecs_security_group_id    = "sg-ecs"
  ecs_cluster_arn          = "arn:aws:ecs:ap-northeast-2:123456789012:cluster/spring-react-msa-learning-cluster"
  capacity_provider_name   = "spring-react-msa-learning-ec2"
  redis_host_parameter_arn = "arn:aws:ssm:ap-northeast-2:123456789012:parameter/spring-react-msa/learning/runtime/redis-host"
  db_address               = "postgres.learning.internal"
  db_port                  = 5432
  db_name                  = "spring_msa"

  service_images = {
    member-gateway       = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/member-gateway@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    admin-gateway        = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/admin-gateway@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    authorization-server = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/authorization-server@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    user-service         = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/user-service@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    community-service    = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/community-service@sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    stock-service        = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/stock-service@sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    member-bff           = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/member-bff@sha256:1111111111111111111111111111111111111111111111111111111111111111"
    admin-bff            = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/admin-bff@sha256:2222222222222222222222222222222222222222222222222222222222222222"
  }

  ecr_repository_arns = {
    spring-member-gateway                = "arn:aws:ecr:ap-northeast-2:123456789012:repository/member-gateway"
    spring-admin-gateway                 = "arn:aws:ecr:ap-northeast-2:123456789012:repository/admin-gateway"
    spring-security-authorization-server = "arn:aws:ecr:ap-northeast-2:123456789012:repository/authorization-server"
    spring-user-service                  = "arn:aws:ecr:ap-northeast-2:123456789012:repository/user-service"
    spring-member-community-service      = "arn:aws:ecr:ap-northeast-2:123456789012:repository/community-service"
    spring-member-stock-service          = "arn:aws:ecr:ap-northeast-2:123456789012:repository/stock-service"
    spring-member-bff-service            = "arn:aws:ecr:ap-northeast-2:123456789012:repository/member-bff"
    spring-admin-bff-service             = "arn:aws:ecr:ap-northeast-2:123456789012:repository/admin-bff"
  }

  application_secret_arns = {
    "/spring-react-msa/learning/admin-bff"           = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:admin-bff"
    "/spring-react-msa/learning/auth-server"         = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:auth-server"
    "/spring-react-msa/learning/member-bff"          = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:member-bff"
    "/spring-react-msa/learning/shared/internal-api" = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:internal-api"
    "/spring-react-msa/learning/shared/redis"        = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:redis"
    "/spring-react-msa/learning/stock-service"       = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:stock"
    "/spring-react-msa/learning/user-service"        = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user"
  }
}

run "runtime_off_keeps_foundation_at_zero_tasks" {
  command = plan

  module {
    source = "./modules/application-runtime"
  }

  variables {
    learning_runtime_enabled = false
  }

  assert {
    condition = (
      length(aws_ecs_task_definition.service) == 8 &&
      length(aws_ecs_service.backend) == 8 &&
      length(aws_service_discovery_service.backend) == 8 &&
      length(aws_cloudwatch_log_group.service) == 8 &&
      alltrue([for service in values(aws_ecs_service.backend) : service.desired_count == 0])
    )
    error_message = "Runtime OFF must retain eight task/service contracts with every desired count at zero."
  }

  assert {
    condition = (
      length(aws_lb.public) == 0 &&
      length(aws_lb_listener.http) == 0 &&
      length(aws_lb_listener.https) == 0 &&
      length(aws_route53_record.origin) == 0 &&
      length(aws_lb_listener_rule.gateway) == 0 &&
      length(aws_lb_target_group.gateway) == 2
    )
    error_message = "Runtime OFF must remove the paid ALB while retaining the two free target-group contracts."
  }

  assert {
    condition = alltrue([
      for task in values(aws_ecs_task_definition.service) :
      task.network_mode == "awsvpc" &&
      toset(task.requires_compatibilities) == toset(["EC2"]) &&
      strcontains(jsondecode(task.container_definitions)[0].image, "@sha256:") &&
      jsondecode(task.container_definitions)[0].readonlyRootFilesystem == true
    ])
    error_message = "Every backend task must use EC2 awsvpc, an immutable digest, and a read-only root filesystem."
  }

  assert {
    condition = (
      length(jsondecode(aws_ecs_task_definition.service["member-gateway"].container_definitions)[0].secrets) == 0 &&
      length(jsondecode(aws_ecs_task_definition.service["member-bff"].container_definitions)[0].secrets) == 6 &&
      length(jsondecode(aws_ecs_task_definition.service["authorization-server"].container_definitions)[0].secrets) == 5
    )
    error_message = "Each task definition must receive only the secret keys required by that service."
  }

  assert {
    condition = alltrue([
      for service_name in ["user-service", "stock-service", "member-bff"] :
      lookup({
        for entry in jsondecode(aws_ecs_task_definition.service[service_name].container_definitions)[0].environment :
        entry.name => entry.value
      }, "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", null) == "5" &&
      lookup({
        for entry in jsondecode(aws_ecs_task_definition.service[service_name].container_definitions)[0].environment :
        entry.name => entry.value
      }, "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE", null) == "1"
    ])
    error_message = "The three RDS clients must use the bounded AWS Learning Hikari pool contract."
  }

  assert {
    condition = lookup({
      for entry in jsondecode(aws_ecs_task_definition.service["member-gateway"].container_definitions)[0].environment :
      entry.name => entry.value
    }, "GATEWAY_BFF_WEBSOCKET_URI", null) == "ws://member-bff.learning.spring-react-msa.internal:8079"
    error_message = "The member gateway must route chat upgrades to the Member BFF with a WebSocket URI."
  }

  assert {
    condition = lookup({
      for entry in jsondecode(aws_ecs_task_definition.service["member-bff"].container_definitions)[0].environment :
      entry.name => entry.value
    }, "BFF_CHAT_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS", null) == "https://app.hyuncloudlab.com"
    error_message = "The Member BFF must allow WebSocket handshakes from the configured member public origin."
  }

  assert {
    condition = (
      aws_service_discovery_private_dns_namespace.this.name == "learning.spring-react-msa.internal" &&
      aws_service_discovery_service.backend["user-service"].dns_config[0].dns_records[0].type == "A" &&
      aws_service_discovery_service.backend["user-service"].health_check_custom_config[0].failure_threshold == 1
    )
    error_message = "Cloud Map must provide private A records and ECS-managed custom health checks."
  }
}

run "runtime_on_uses_cloudfront_origin_tls" {
  command = plan

  module {
    source = "./modules/application-runtime"
  }

  variables {
    learning_runtime_enabled     = true
    enable_public_domain_routing = true
    public_hosted_zone_id        = "Z0123456789EXAMPLE"
    origin_domain                = "origin.hyuncloudlab.com"
    origin_certificate_arn       = "arn:aws:acm:ap-northeast-2:123456789012:certificate/00000000-0000-0000-0000-000000000000"
    toss_api_client_id           = "test-toss-client-id"
  }

  assert {
    condition = (
      length(aws_lb_listener.http) == 0 &&
      length(aws_lb_listener.https) == 1 &&
      aws_lb_listener.https["this"].port == 443 &&
      aws_lb_listener.https["this"].protocol == "HTTPS" &&
      aws_lb_listener.https["this"].ssl_policy == "ELBSecurityPolicy-TLS13-1-2-2021-06" &&
      aws_lb.public["this"].idle_timeout == 3600
    )
    error_message = "CloudFront origin mode must expose only a modern TLS listener and retain long-lived connections."
  }

  assert {
    condition = (
      length(aws_route53_record.origin) == 1 &&
      aws_route53_record.origin["this"].name == "origin.hyuncloudlab.com" &&
      aws_route53_record.origin["this"].type == "A"
    )
    error_message = "Runtime ON must create the disposable origin Route 53 alias."
  }

}

run "runtime_on_creates_one_task_each_and_disposable_alb" {
  command = plan

  module {
    source = "./modules/application-runtime"
  }

  variables {
    learning_runtime_enabled = true
    toss_api_client_id       = "test-toss-client-id"
  }

  assert {
    condition = (
      alltrue([for service in values(aws_ecs_service.backend) : service.desired_count == 1]) &&
      length(aws_lb.public) == 1 &&
      length(aws_lb_listener.http) == 1 &&
      length(aws_lb_listener_rule.gateway) == 2
    )
    error_message = "Runtime ON must run one task per backend service and create one two-host public ALB."
  }

  assert {
    condition = (
      length(aws_ecs_service.backend["member-gateway"].load_balancer) == 1 &&
      length(aws_ecs_service.backend["admin-gateway"].load_balancer) == 1 &&
      length(aws_ecs_service.backend["user-service"].load_balancer) == 0
    )
    error_message = "Only the two gateway services may register with the public ALB."
  }

  assert {
    condition = alltrue([
      for service_name in ["authorization-server", "stock-service", "member-bff", "admin-bff"] :
      lookup({
        for entry in jsondecode(aws_ecs_task_definition.service[service_name].container_definitions)[0].environment :
        entry.name => entry.value
      }, "SPRING_DATA_REDIS_USERNAME", null) == "spring-msa"
    ])
    error_message = "Every Valkey client task must use the password-authenticated spring-msa RBAC username."
  }

  assert {
    condition = lookup({
      for entry in jsondecode(aws_ecs_task_definition.service["stock-service"].container_definitions)[0].environment :
      entry.name => entry.value
    }, "TOSS_API_CLIENT_ID", null) == "test-toss-client-id"
    error_message = "Runtime ON must inject a non-empty Toss API client ID into the stock service."
  }

  assert {
    condition = lookup({
      for entry in jsondecode(aws_ecs_task_definition.service["admin-bff"].container_definitions)[0].environment :
      entry.name => entry.value
    }, "ADMIN_BFF_REGISTRATION_ENABLED", null) == "false"
    error_message = "The AWS Admin BFF task must keep public administrator registration disabled."
  }
}

run "runtime_on_rejects_missing_toss_client_id" {
  command = plan

  module {
    source = "./modules/application-runtime"
  }

  variables {
    learning_runtime_enabled = true
    toss_api_client_id       = ""
  }

  expect_failures = [aws_ecs_task_definition.service["stock-service"]]
}
