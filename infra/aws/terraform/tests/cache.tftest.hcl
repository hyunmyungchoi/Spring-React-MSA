mock_provider "aws" {}

run "runtime_off_creates_no_paid_cache" {
  command = plan

  module {
    source = "./modules/cache"
  }

  variables {
    name_prefix             = "spring-react-msa-learning"
    aws_region              = "ap-northeast-2"
    private_data_subnet_ids = ["subnet-data-a", "subnet-data-b"]
    data_security_group_id  = "sg-data"
    runtime_enabled         = false
  }

  assert {
    condition = (
      length(aws_elasticache_replication_group.this) == 0 &&
      length(aws_elasticache_user.application) == 0 &&
      length(aws_elasticache_user_group.this) == 0 &&
      length(aws_ssm_parameter.redis_host) == 0
    )
    error_message = "Runtime OFF must create no hourly-billed cache and no stale endpoint parameter."
  }
}

run "runtime_on_creates_single_node_encrypted_valkey" {
  command = plan

  module {
    source = "./modules/cache"
  }

  variables {
    name_prefix             = "spring-react-msa-learning"
    aws_region              = "ap-northeast-2"
    private_data_subnet_ids = ["subnet-data-a", "subnet-data-b"]
    data_security_group_id  = "sg-data"
    runtime_enabled         = true
    redis_password          = "0123456789abcdefghijklmnopqrstuvwxyzABCD"
    redis_password_version  = 1
  }

  assert {
    condition = (
      aws_elasticache_replication_group.this["this"].engine == "valkey" &&
      aws_elasticache_replication_group.this["this"].engine_version == "7.2" &&
      aws_elasticache_replication_group.this["this"].node_type == "cache.t4g.micro" &&
      aws_elasticache_replication_group.this["this"].num_cache_clusters == 1 &&
      aws_elasticache_replication_group.this["this"].automatic_failover_enabled == false &&
      aws_elasticache_replication_group.this["this"].multi_az_enabled == false
    )
    error_message = "Learning Runtime ON must use one cache.t4g.micro Valkey 7.2 node without replicas or Multi-AZ."
  }

  assert {
    condition = (
      tobool(aws_elasticache_replication_group.this["this"].at_rest_encryption_enabled) == true &&
      aws_elasticache_replication_group.this["this"].transit_encryption_enabled == true &&
      aws_elasticache_replication_group.this["this"].transit_encryption_mode == "required" &&
      length(aws_elasticache_replication_group.this["this"].user_group_ids) == 1
    )
    error_message = "Valkey must require TLS, encryption at rest, and RBAC authentication."
  }

  assert {
    condition = (
      aws_elasticache_user.application["this"].access_string == "on ~* +@all" &&
      aws_elasticache_user.application["this"].passwords_wo_version == 1 &&
      length(aws_elasticache_user_group.this["this"].user_ids) == 1 &&
      contains(aws_elasticache_user_group.this["this"].user_ids, aws_elasticache_user.application["this"].user_id)
    )
    error_message = "The Valkey user group must contain only the password-authenticated application user using a versioned write-only password."
  }

  assert {
    condition = (
      aws_ssm_parameter.redis_host["this"].type == "String" &&
      aws_ssm_parameter.redis_host["this"].name == "/spring-react-msa/learning/runtime/redis-host"
    )
    error_message = "The disposable endpoint must be published only through the approved non-secret SSM String parameter."
  }
}
