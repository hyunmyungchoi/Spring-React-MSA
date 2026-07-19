resource "aws_route53_zone" "public" {
  name          = var.root_domain
  comment       = "Public zone for the Spring React MSA learning environment"
  force_destroy = false

  lifecycle {
    prevent_destroy = true
  }

  tags = local.common_tags
}

import {
  to = aws_route53_zone.public
  id = var.hosted_zone_id
}

resource "aws_acm_certificate" "cloudfront" {
  provider = aws.us_east_1

  domain_name               = var.root_domain
  subject_alternative_names = [local.member_domain, local.admin_domain]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(local.common_tags, {
    Name    = "${var.project_name}-${var.environment}-cloudfront"
    Purpose = "cloudfront-viewer-tls"
  })
}

resource "aws_route53_record" "cloudfront_validation" {
  for_each = toset([var.root_domain, local.member_domain, local.admin_domain])

  zone_id = aws_route53_zone.public.zone_id
  name = one([
    for option in aws_acm_certificate.cloudfront.domain_validation_options :
    option.resource_record_name if option.domain_name == each.key
  ])
  type = one([
    for option in aws_acm_certificate.cloudfront.domain_validation_options :
    option.resource_record_type if option.domain_name == each.key
  ])
  ttl = 300
  records = [one([
    for option in aws_acm_certificate.cloudfront.domain_validation_options :
    option.resource_record_value if option.domain_name == each.key
  ])]
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for record in aws_route53_record.cloudfront_validation : record.fqdn]
}

resource "aws_acm_certificate" "origin" {
  domain_name       = local.origin_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(local.common_tags, {
    Name    = "${var.project_name}-${var.environment}-origin"
    Purpose = "alb-origin-tls"
  })
}

resource "aws_route53_record" "origin_validation" {
  zone_id = aws_route53_zone.public.zone_id
  name    = one(aws_acm_certificate.origin.domain_validation_options).resource_record_name
  type    = one(aws_acm_certificate.origin.domain_validation_options).resource_record_type
  ttl     = 300
  records = [one(aws_acm_certificate.origin.domain_validation_options).resource_record_value]
}

resource "aws_acm_certificate_validation" "origin" {
  certificate_arn         = aws_acm_certificate.origin.arn
  validation_record_fqdns = [aws_route53_record.origin_validation.fqdn]
}
