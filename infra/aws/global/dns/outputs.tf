output "hosted_zone_id" {
  description = "Imported public hosted-zone ID."
  value       = aws_route53_zone.public.zone_id
}

output "cloudfront_certificate_arn" {
  description = "Issued us-east-1 certificate for the root, Member, and Admin CloudFront aliases."
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
}

output "origin_certificate_arn" {
  description = "Issued ap-northeast-2 certificate for the disposable ALB origin hostname."
  value       = aws_acm_certificate_validation.origin.certificate_arn
}

output "public_domains" {
  description = "Stable public hostname contract."
  value = {
    root   = var.root_domain
    member = local.member_domain
    admin  = local.admin_domain
    origin = local.origin_domain
  }
}
