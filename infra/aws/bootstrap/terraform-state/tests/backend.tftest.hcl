mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "111122223333"
      arn        = "arn:aws:iam::111122223333:user/hyun-terraform-admin"
      user_id    = "AIDATEST"
    }
  }

  mock_data "aws_partition" {
    defaults = {
      partition  = "aws"
      dns_suffix = "amazonaws.com"
    }
  }
}

run "backend_security_contract" {
  command = plan

  variables {
    additional_state_keys = ["global/dns/terraform.tfstate"]
  }

  assert {
    condition     = aws_s3_bucket.state.bucket == "spring-react-msa-learning-tfstate-111122223333-ap-northeast-2"
    error_message = "The backend bucket name must be deterministic and account-scoped."
  }


  assert {
    condition = (
      toset(jsondecode(aws_iam_role_policy.state_access.policy).Statement[1].Condition.StringEquals["s3:prefix"]) == toset([
        "global/dns/terraform.tfstate",
        "global/dns/terraform.tfstate.tflock",
        "learning/runtime/terraform.tfstate",
        "learning/runtime/terraform.tfstate.tflock",
      ]) &&
      toset(jsondecode(aws_iam_role_policy.state_access.policy).Statement[2].Resource) == toset([
        "arn:aws:s3:::spring-react-msa-learning-tfstate-111122223333-ap-northeast-2/global/dns/terraform.tfstate",
        "arn:aws:s3:::spring-react-msa-learning-tfstate-111122223333-ap-northeast-2/learning/runtime/terraform.tfstate",
      ]) &&
      toset(jsondecode(aws_iam_role_policy.state_access.policy).Statement[3].Resource) == toset([
        "arn:aws:s3:::spring-react-msa-learning-tfstate-111122223333-ap-northeast-2/global/dns/terraform.tfstate.tflock",
        "arn:aws:s3:::spring-react-msa-learning-tfstate-111122223333-ap-northeast-2/learning/runtime/terraform.tfstate.tflock",
      ])
    )
    error_message = "The state role must grant read/write and lock access to only the runtime and global DNS state keys."
  }

  assert {
    condition     = aws_s3_bucket.state.force_destroy == false
    error_message = "The state bucket must not allow force deletion."
  }

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "S3 Versioning must be enabled."
  }

  assert {
    condition     = one(one(aws_s3_bucket_server_side_encryption_configuration.state.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
    error_message = "The Learning backend must use SSE-S3 AES256 encryption."
  }

  assert {
    condition = (
      aws_s3_bucket_public_access_block.state.block_public_acls &&
      aws_s3_bucket_public_access_block.state.block_public_policy &&
      aws_s3_bucket_public_access_block.state.ignore_public_acls &&
      aws_s3_bucket_public_access_block.state.restrict_public_buckets
    )
    error_message = "All S3 Block Public Access controls must be enabled."
  }

  assert {
    condition = (
      jsondecode(aws_s3_bucket_policy.state.policy).Statement[0].Effect == "Deny" &&
      jsondecode(aws_s3_bucket_policy.state.policy).Statement[0].Condition.Bool["aws:SecureTransport"] == "false"
    )
    error_message = "The bucket policy must deny non-HTTPS access."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role.state_access.assume_role_policy).Statement[0].Principal.AWS == "arn:aws:iam::111122223333:user/hyun-terraform-admin" &&
      jsondecode(aws_iam_role.state_access.assume_role_policy).Statement[0].Action == "sts:AssumeRole"
    )
    error_message = "Only the approved Terraform operator may assume the state role."
  }

  assert {
    condition = (
      contains(jsondecode(aws_iam_role_policy.state_access.policy).Statement[2].Action, "s3:GetObject") &&
      contains(jsondecode(aws_iam_role_policy.state_access.policy).Statement[2].Action, "s3:PutObject") &&
      !contains(jsondecode(aws_iam_role_policy.state_access.policy).Statement[2].Action, "s3:DeleteObject") &&
      contains(jsondecode(aws_iam_role_policy.state_access.policy).Statement[3].Action, "s3:DeleteObject")
    )
    error_message = "State access must be read/write while delete is limited to the lockfile."
  }
}
