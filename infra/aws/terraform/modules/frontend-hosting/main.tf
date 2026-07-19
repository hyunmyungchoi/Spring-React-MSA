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

  api_path_patterns = {
    member = [
      "/bff*",
      "/api*",
      "/login*",
      "/oauth2*",
      "/.well-known*",
      "/logout*",
      "/connect*",
      "/userinfo*",
    ]
    admin = [
      "/admin-bff*",
      "/login*",
      "/oauth2*",
      "/.well-known*",
      "/logout*",
      "/connect*",
      "/userinfo*",
    ]
  }

  ordered_behaviors = {
    for distribution_name, distribution in local.distributions :
    distribution_name => concat(
      var.enable_public_domain_routing ? [
        for path_pattern in local.api_path_patterns[distribution_name] : {
          path_pattern = path_pattern
          origin_id    = "custom-api"
          spa_router   = false
        }
      ] : [],
      [
        for site_name in distribution.ordered_sites : {
          path_pattern = "${local.sites[site_name].route_prefix}/*"
          origin_id    = "s3-${site_name}"
          spa_router   = true
        }
      ],
    )
  }

  bucket_names = {
    for site_name in keys(local.sites) :
    site_name => "${var.name_prefix}-${var.account_id}-frontend-${site_name}"
  }

  github_subject = "repo:${var.github_repository}:ref:${var.github_branch_ref}"

  # AWS-managed policies. CachingOptimized honors object Cache-Control metadata;
  # SecurityHeadersPolicy adds the standard browser hardening response headers.
  caching_optimized_policy_id  = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  caching_disabled_policy_id   = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  all_viewer_request_policy_id = "216adef6-5c7f-47e4-b989-5492eafa07d3"
  security_headers_policy_id   = "67f7725c-6f97-4210-82d7-5512b31e9d03"

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

resource "aws_cloudfront_function" "root_redirect" {
  count = var.enable_public_domain_routing ? 1 : 0

  name    = "${var.name_prefix}-root-redirect"
  runtime = "cloudfront-js-2.0"
  comment = "Redirect every root-domain request to the canonical Member app hostname."
  publish = true
  code    = file("${path.module}/functions/root-redirect.js")
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
  aliases = var.enable_public_domain_routing ? (
    each.key == "member" ? [var.root_domain, "app.${var.root_domain}"] : ["admin.${var.root_domain}"]
  ) : []

  dynamic "origin" {
    for_each = toset(concat([each.value.default_site], each.value.ordered_sites))

    content {
      domain_name              = aws_s3_bucket.site[origin.value].bucket_regional_domain_name
      origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
      origin_id                = "s3-${origin.value}"
    }
  }

  dynamic "origin" {
    for_each = var.enable_public_domain_routing ? [var.origin_domain] : []

    content {
      domain_name = origin.value
      origin_id   = "custom-api"

      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
        origin_read_timeout    = 60
      }
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
    for_each = local.ordered_behaviors[each.key]

    content {
      path_pattern               = ordered_cache_behavior.value.path_pattern
      target_origin_id           = ordered_cache_behavior.value.origin_id
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ordered_cache_behavior.value.spa_router ? ["GET", "HEAD", "OPTIONS"] : ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
      cached_methods             = ordered_cache_behavior.value.spa_router ? ["GET", "HEAD", "OPTIONS"] : ["GET", "HEAD"]
      compress                   = true
      cache_policy_id            = ordered_cache_behavior.value.spa_router ? local.caching_optimized_policy_id : local.caching_disabled_policy_id
      origin_request_policy_id   = ordered_cache_behavior.value.spa_router ? null : local.all_viewer_request_policy_id
      response_headers_policy_id = local.security_headers_policy_id

      dynamic "function_association" {
        for_each = ordered_cache_behavior.value.spa_router ? [aws_cloudfront_function.spa_router[each.key].arn] : (
          each.key == "member" ? [aws_cloudfront_function.root_redirect[0].arn] : []
        )

        content {
          event_type   = "viewer-request"
          function_arn = function_association.value
        }
      }
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.enable_public_domain_routing ? false : true
    acm_certificate_arn            = var.enable_public_domain_routing ? var.cloudfront_certificate_arn : null
    ssl_support_method             = var.enable_public_domain_routing ? "sni-only" : null
    minimum_protocol_version       = var.enable_public_domain_routing ? "TLSv1.2_2021" : "TLSv1"
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

  lifecycle {
    precondition {
      condition = !var.enable_public_domain_routing || (
        var.public_hosted_zone_id != null &&
        var.cloudfront_certificate_arn != null
      )
      error_message = "Public domain routing requires the existing hosted-zone ID and issued us-east-1 CloudFront certificate."
    }
  }
}

locals {
  public_records = var.enable_public_domain_routing ? {
    root = {
      name         = var.root_domain
      distribution = "member"
    }
    member = {
      name         = "app.${var.root_domain}"
      distribution = "member"
    }
    admin = {
      name         = "admin.${var.root_domain}"
      distribution = "admin"
    }
  } : {}
}

resource "aws_route53_record" "frontend_ipv4" {
  for_each = local.public_records

  zone_id = var.public_hosted_zone_id
  name    = each.value.name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.site[each.value.distribution].domain_name
    zone_id                = aws_cloudfront_distribution.site[each.value.distribution].hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "frontend_ipv6" {
  for_each = local.public_records

  zone_id = var.public_hosted_zone_id
  name    = each.value.name
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.site[each.value.distribution].domain_name
    zone_id                = aws_cloudfront_distribution.site[each.value.distribution].hosted_zone_id
    evaluate_target_health = false
  }
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
