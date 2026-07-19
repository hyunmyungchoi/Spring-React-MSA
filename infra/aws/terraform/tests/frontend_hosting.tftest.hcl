mock_provider "aws" {}

variables {
  name_prefix       = "spring-react-msa-learning"
  account_id        = "123456789012"
  oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  github_repository = "hyunmyungchoi/Spring-React-MSA"
  github_branch_ref = "refs/heads/master"
  common_tags = {
    Environment = "learning"
    ManagedBy   = "terraform"
    Project     = "spring-react-msa"
  }
}

run "independent_frontend_hosting_contract" {
  command = plan

  module {
    source = "./modules/frontend-hosting"
  }

  assert {
    condition = (
      length(aws_s3_bucket.site) == 6 &&
      toset(keys(aws_s3_bucket.site)) == toset([
        "member",
        "community",
        "stock",
        "admin",
        "admin-users",
        "admin-logs",
      ])
    )
    error_message = "Frontend hosting must keep six independent S3 deployment units."
  }

  assert {
    condition = alltrue([
      for bucket in values(aws_s3_bucket_public_access_block.site) :
      bucket.block_public_acls &&
      bucket.block_public_policy &&
      bucket.ignore_public_acls &&
      bucket.restrict_public_buckets
    ])
    error_message = "Every frontend bucket must block all public access."
  }

  assert {
    condition = (
      alltrue([for versioning in values(aws_s3_bucket_versioning.site) : versioning.versioning_configuration[0].status == "Enabled"]) &&
      alltrue([
        for encryption in values(aws_s3_bucket_server_side_encryption_configuration.site) :
        one(one(encryption.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
      ])
    )
    error_message = "Every frontend bucket must use versioning and SSE-S3 encryption."
  }

  assert {
    condition = (
      length(aws_cloudfront_distribution.site) == 2 &&
      length(local.distributions["member"].ordered_sites) == 2 &&
      length(local.distributions["admin"].ordered_sites) == 2 &&
      aws_cloudfront_distribution.site["member"].default_cache_behavior[0].target_origin_id == "s3-member" &&
      aws_cloudfront_distribution.site["admin"].default_cache_behavior[0].target_origin_id == "s3-admin"
    )
    error_message = "Member and Admin CloudFront distributions must each route three S3 origins."
  }

  assert {
    condition = (
      toset([for behavior in aws_cloudfront_distribution.site["member"].ordered_cache_behavior : behavior.path_pattern]) == toset(["/community/*", "/stock/*"]) &&
      toset([for behavior in aws_cloudfront_distribution.site["admin"].ordered_cache_behavior : behavior.path_pattern]) == toset(["/manage/users/*", "/manage/logs/*"])
    )
    error_message = "CloudFront path behaviors must preserve the six existing frontend route boundaries."
  }

  assert {
    condition = (
      aws_cloudfront_origin_access_control.s3.signing_behavior == "always" &&
      aws_cloudfront_origin_access_control.s3.signing_protocol == "sigv4" &&
      aws_cloudfront_origin_access_control.s3.origin_access_control_origin_type == "s3"
    )
    error_message = "Private S3 origins must use always-signed SigV4 OAC requests."
  }

  assert {
    condition = alltrue([
      for function in values(aws_cloudfront_function.spa_router) :
      function.runtime == "cloudfront-js-2.0" && function.publish
    ])
    error_message = "Both route-aware SPA functions must use the current CloudFront JavaScript runtime and be published."
  }

  assert {
    condition = (
      strcontains(aws_cloudfront_function.spa_router["member"].code, "'/stock'") &&
      strcontains(aws_cloudfront_function.spa_router["member"].code, "stock.html") &&
      strcontains(aws_cloudfront_function.spa_router["admin"].code, "'/manage/users'") &&
      strcontains(aws_cloudfront_function.spa_router["admin"].code, "users.html")
    )
    error_message = "SPA functions must rewrite each independently deployed route to its own entry document."
  }

  assert {
    condition = (
      strcontains(aws_iam_role.github_frontend_deploy.assume_role_policy, "repo:hyunmyungchoi/Spring-React-MSA:ref:refs/heads/master") &&
      contains(local.frontend_invalidation_actions, "cloudfront:CreateInvalidation") &&
      contains(local.frontend_object_actions, "s3:PutObject")
    )
    error_message = "The frontend deployment role must trust only master and have scoped S3/invalidation permissions."
  }
}
