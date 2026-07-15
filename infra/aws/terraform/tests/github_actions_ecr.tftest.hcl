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

run "github_actions_ecr_module_contract" {
  command = plan

  module {
    source = "./modules/github-actions-ecr"
  }

  variables {
    name_prefix       = "spring-react-msa-learning"
    oidc_provider_arn = "arn:aws:iam::111122223333:oidc-provider/token.actions.githubusercontent.com"
    github_repository = "hyunmyungchoi/Spring-React-MSA"
    github_branch_ref = "refs/heads/master"
    ecr_repository_arns = toset([
      "arn:aws:ecr:ap-northeast-2:111122223333:repository/spring-react-msa-learning/spring-member-gateway",
      "arn:aws:ecr:ap-northeast-2:111122223333:repository/spring-react-msa-learning/spring-admin-gateway",
    ])
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
      Purpose     = "aws-learning"
    }
  }

  assert {
    condition     = aws_iam_role.this.name == "spring-react-msa-learning-github-ecr-push"
    error_message = "The GitHub Actions role must use the approved name."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role.this.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com" &&
      jsondecode(aws_iam_role.this.assume_role_policy).Statement[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == "repo:hyunmyungchoi/Spring-React-MSA:ref:refs/heads/master"
    )
    error_message = "The trust policy must restrict the STS audience, repository, and master branch exactly."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.ecr_push.policy).Statement[0].Action == "ecr:GetAuthorizationToken" &&
      jsondecode(aws_iam_role_policy.ecr_push.policy).Statement[0].Resource == "*"
    )
    error_message = "Only ECR authorization may use the wildcard resource."
  }

  assert {
    condition = (
      toset(jsondecode(aws_iam_role_policy.ecr_push.policy).Statement[1].Action) == toset([
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:CompleteLayerUpload",
        "ecr:DescribeImages",
        "ecr:GetDownloadUrlForLayer",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart",
      ]) &&
      toset(jsondecode(aws_iam_role_policy.ecr_push.policy).Statement[1].Resource) == toset([
        "arn:aws:ecr:ap-northeast-2:111122223333:repository/spring-react-msa-learning/spring-member-gateway",
        "arn:aws:ecr:ap-northeast-2:111122223333:repository/spring-react-msa-learning/spring-admin-gateway",
      ])
    )
    error_message = "Image publication actions must be scoped to the supplied ECR repository ARNs."
  }
}
