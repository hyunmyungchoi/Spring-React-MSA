locals {
  github_subject = "repo:${var.github_repository}:ref:${var.github_branch_ref}"
}

resource "aws_iam_role" "this" {
  name                 = "${var.name_prefix}-github-ecr-push"
  description          = "Allows the Spring-React-MSA master workflow to publish backend images to ECR."
  max_session_duration = 3600

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = var.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
            "token.actions.githubusercontent.com:sub" = local.github_subject
          }
        }
      },
    ]
  })

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-github-ecr-push"
  })
}

resource "aws_iam_role_policy" "ecr_push" {
  name = "${var.name_prefix}-ecr-push"
  role = aws_iam_role.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuthorization"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "EcrImagePush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = sort(tolist(var.ecr_repository_arns))
      },
    ]
  })
}
