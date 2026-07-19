mock_provider "aws" {}

mock_provider "aws" {
  alias = "us_east_1"
}

variables {
  hosted_zone_id = "Z0123456789EXAMPLE"
}

run "global_dns_and_certificate_contract" {
  command = plan

  override_resource {
    target = aws_route53_zone.public
    values = {
      zone_id = "Z0123456789EXAMPLE"
      name    = "hyuncloudlab.com"
    }
  }

  override_resource {
    target = aws_acm_certificate.cloudfront
    values = {
      domain_validation_options = [
        {
          domain_name           = "hyuncloudlab.com"
          resource_record_name  = "_root.hyuncloudlab.com"
          resource_record_type  = "CNAME"
          resource_record_value = "_root.acm-validations.aws"
        },
        {
          domain_name           = "app.hyuncloudlab.com"
          resource_record_name  = "_app.hyuncloudlab.com"
          resource_record_type  = "CNAME"
          resource_record_value = "_app.acm-validations.aws"
        },
        {
          domain_name           = "admin.hyuncloudlab.com"
          resource_record_name  = "_admin.hyuncloudlab.com"
          resource_record_type  = "CNAME"
          resource_record_value = "_admin.acm-validations.aws"
        },
      ]
    }
  }

  override_resource {
    target = aws_acm_certificate.origin
    values = {
      domain_validation_options = [
        {
          domain_name           = "origin.hyuncloudlab.com"
          resource_record_name  = "_origin.hyuncloudlab.com"
          resource_record_type  = "CNAME"
          resource_record_value = "_origin.acm-validations.aws"
        },
      ]
    }
  }

  assert {
    condition = (
      aws_route53_zone.public.name == "hyuncloudlab.com" &&
      aws_route53_zone.public.force_destroy == false
    )
    error_message = "The existing public zone must be imported and protected from forced deletion."
  }

  assert {
    condition = (
      aws_acm_certificate.cloudfront.domain_name == "hyuncloudlab.com" &&
      toset(aws_acm_certificate.cloudfront.subject_alternative_names) == toset([
        "app.hyuncloudlab.com",
        "admin.hyuncloudlab.com",
      ]) &&
      aws_acm_certificate.cloudfront.validation_method == "DNS"
    )
    error_message = "The CloudFront certificate must cover root, Member, and Admin through DNS validation."
  }

  assert {
    condition = (
      aws_acm_certificate.origin.domain_name == "origin.hyuncloudlab.com" &&
      aws_acm_certificate.origin.validation_method == "DNS"
    )
    error_message = "The regional origin certificate must cover only origin.hyuncloudlab.com."
  }

  assert {
    condition = (
      alltrue([for record in values(aws_route53_record.cloudfront_validation) : record.ttl == 300]) &&
      aws_route53_record.origin_validation.ttl == 300
    )
    error_message = "All ACM DNS validation records must be persistent five-minute Route 53 records."
  }
}
