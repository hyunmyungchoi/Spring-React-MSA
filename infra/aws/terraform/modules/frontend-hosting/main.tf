locals {
  sites = {
    member = {
      distribution = "member"
      route_prefix = null
      document     = "index.html"
    }
    community = {
      distribution = "member"
      route_prefix = "/community"
      document     = "community.html"
    }
    stock = {
      distribution = "member"
      route_prefix = "/stock"
      document     = "stock.html"
    }
    admin = {
      distribution = "admin"
      route_prefix = null
      document     = "index.html"
    }
    admin-users = {
      distribution = "admin"
      route_prefix = "/manage/users"
      document     = "users.html"
    }
    admin-logs = {
      distribution = "admin"
      route_prefix = "/manage/logs"
      document     = "logs.html"
    }
  }

  distributions = {
    member = {
      default_site  = "member"
      ordered_sites = ["community", "stock"]
    }
    admin = {
      default_site  = "admin"
      ordered_sites = ["admin-users", "admin-logs"]
    }
  }

  bucket_names = {
    for site_name in keys(local.sites) :
    site_name => "${var.name_prefix}-${var.account_id}-frontend-${site_name}"
  }

  github_subject = "repo:${var.github_repository}:ref:${var.github_branch_ref}"

  # AWS-managed policies. CachingOptimized honors object Cache-Control metadata;
  # SecurityHeadersPolicy adds the standard browser hardening response headers.
  caching_optimized_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  security_headers_policy_id  = "67f7725c-6f97-4210-82d7-5512b31e9d03"

  frontend_bucket_actions = [
    "s3:GetBucketLocation",
    "s3:ListBucket",
  ]
  frontend_object_actions = [
    "s3:DeleteObject",
    "s3:GetObject",
    "s3:PutObject",
  ]
  frontend_invalidation_actions = [
    "cloudfront:CreateInvalidation",
    "cloudfront:GetDistribution",
    "cloudfront:GetInvalidation",
  ]
}

resource "aws_s3_bucket" "site" {
  for_each = local.sites

  bucket        = local.bucket_names[each.key]
  force_destroy = false

  tags = merge(var.common_tags, {
    Name           = local.bucket_names[each.key]
    Frontend       = each.key
    Distribution   = each.value.distribution
    DeploymentUnit = "independent"
  })
}

resource "aws_s3_bucket_ownership_controls" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id

  rule {
    id     = "expire-old-deployments"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 7
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.site]
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${var.name_prefix}-frontend-s3"
  description                       = "Signed CloudFront-only access to the six private frontend buckets."
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_router" {
  for_each = local.distributions

  name    = "${var.name_prefix}-${each.key}-spa-router"
  runtime = "cloudfront-js-2.0"
  comment = "Route ${each.key} SPA paths to independently deployed S3 origins."
  publish = true
  code    = file("${path.module}/functions/${each.key}-router.js")
}

resource "aws_cloudfront_distribution" "site" {
  for_each = local.distributions

  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.name_prefix}-${each.key}-frontend"
  default_root_object = "index.html"
  http_version        = "http2and3"
  price_class         = "PriceClass_200"
  retain_on_delete    = false
  wait_for_deployment = true

  dynamic "origin" {
    for_each = toset(concat([each.value.default_site], each.value.ordered_sites))

    content {
      domain_name              = aws_s3_bucket.site[origin.value].bucket_regional_domain_name
      origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
      origin_id                = "s3-${origin.value}"
    }
  }

  default_cache_behavior {
    target_origin_id           = "s3-${each.value.default_site}"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD", "OPTIONS"]
    compress                   = true
    cache_policy_id            = local.caching_optimized_policy_id
    response_headers_policy_id = local.security_headers_policy_id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_router[each.key].arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = each.value.ordered_sites

    content {
      path_pattern               = "${local.sites[ordered_cache_behavior.value].route_prefix}/*"
      target_origin_id           = "s3-${ordered_cache_behavior.value}"
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ["GET", "HEAD", "OPTIONS"]
      cached_methods             = ["GET", "HEAD", "OPTIONS"]
      compress                   = true
      cache_policy_id            = local.caching_optimized_policy_id
      response_headers_policy_id = local.security_headers_policy_id

      function_association {
        event_type   = "viewer-request"
        function_arn = aws_cloudfront_function.spa_router[each.key].arn
      }
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = merge(var.common_tags, {
    Name         = "${var.name_prefix}-${each.key}-frontend"
    FrontendSite = each.key
  })

  depends_on = [
    aws_s3_bucket_ownership_controls.site,
    aws_s3_bucket_public_access_block.site,
    aws_s3_bucket_server_side_encryption_configuration.site,
  ]
}

resource "aws_s3_bucket_policy" "site" {
  for_each = aws_s3_bucket.site

  bucket = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontReadOnly"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${each.value.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn"     = aws_cloudfront_distribution.site[local.sites[each.key].distribution].arn
            "AWS:SourceAccount" = var.account_id
          }
        }
      },
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          each.value.arn,
          "${each.value.arn}/*",
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      },
    ]
  })
}

resource "aws_iam_role" "github_frontend_deploy" {
  name                 = "${var.name_prefix}-github-frontend-deploy"
  description          = "Allows the master workflow to deploy one frontend unit and invalidate its CloudFront paths."
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
    Name = "${var.name_prefix}-github-frontend-deploy"
  })
}

resource "aws_iam_role_policy" "github_frontend_deploy" {
  name = "${var.name_prefix}-frontend-deploy"
  role = aws_iam_role.github_frontend_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ListFrontendBuckets"
        Effect   = "Allow"
        Action   = local.frontend_bucket_actions
        Resource = sort([for bucket in values(aws_s3_bucket.site) : bucket.arn])
      },
      {
        Sid      = "DeployFrontendObjects"
        Effect   = "Allow"
        Action   = local.frontend_object_actions
        Resource = sort([for bucket in values(aws_s3_bucket.site) : "${bucket.arn}/*"])
      },
      {
        Sid      = "InvalidateFrontendDistributions"
        Effect   = "Allow"
        Action   = local.frontend_invalidation_actions
        Resource = sort([for distribution in values(aws_cloudfront_distribution.site) : distribution.arn])
      },
    ]
  })
}
