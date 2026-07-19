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

run "ecr_module_contract" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  variables {
    name_prefix = "spring-react-msa-learning"
    service_names = toset([
      "spring-member-gateway",
      "spring-admin-gateway",
      "spring-security-authorization-server",
      "spring-user-service",
      "spring-member-community-service",
      "spring-member-stock-service",
      "spring-member-bff-service",
      "spring-admin-bff-service",
    ])
    tagged_image_retention_count  = 5
    untagged_image_retention_days = 1
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
      Purpose     = "aws-learning"
    }
  }

  assert {
    condition     = length(aws_ecr_repository.this) == 8
    error_message = "Exactly eight backend ECR repositories must be created."
  }

  assert {
    condition = toset([
      for repository in values(aws_ecr_repository.this) : repository.name
      ]) == toset([
      "spring-react-msa-learning/spring-member-gateway",
      "spring-react-msa-learning/spring-admin-gateway",
      "spring-react-msa-learning/spring-security-authorization-server",
      "spring-react-msa-learning/spring-user-service",
      "spring-react-msa-learning/spring-member-community-service",
      "spring-react-msa-learning/spring-member-stock-service",
      "spring-react-msa-learning/spring-member-bff-service",
      "spring-react-msa-learning/spring-admin-bff-service",
    ])
    error_message = "ECR repository names must use the approved namespace and service names."
  }

  assert {
    condition = alltrue([
      for repository in values(aws_ecr_repository.this) :
      repository.image_tag_mutability == "IMMUTABLE" &&
      repository.force_delete == false &&
      repository.encryption_configuration[0].encryption_type == "AES256" &&
      repository.image_scanning_configuration[0].scan_on_push == true
    ])
    error_message = "Every ECR repository must be immutable, deletion-protected, AES256 encrypted, and scanned on push."
  }

  assert {
    condition = alltrue([
      for lifecycle in values(aws_ecr_lifecycle_policy.this) :
      jsondecode(lifecycle.policy).rules[0].selection.tagStatus == "untagged" &&
      jsondecode(lifecycle.policy).rules[0].selection.countType == "sinceImagePushed" &&
      jsondecode(lifecycle.policy).rules[0].selection.countUnit == "days" &&
      jsondecode(lifecycle.policy).rules[0].selection.countNumber == 1 &&
      jsondecode(lifecycle.policy).rules[1].selection.tagStatus == "tagged" &&
      jsondecode(lifecycle.policy).rules[1].selection.tagPatternList == ["*"] &&
      jsondecode(lifecycle.policy).rules[1].selection.countType == "imageCountMoreThan" &&
      jsondecode(lifecycle.policy).rules[1].selection.countNumber == 5
    ])
    error_message = "Lifecycle policies must expire untagged images after one day and retain five tagged images."
  }
}

run "root_ecr_inventory" {
  command = plan

  variables {
    enable_budget                   = false
    enable_observability_foundation = false
    budget_alert_email              = null
  }

  assert {
    condition     = length(module.ecr.repository_urls) == 8
    error_message = "The root module must expose eight ECR repository URLs."
  }
}
