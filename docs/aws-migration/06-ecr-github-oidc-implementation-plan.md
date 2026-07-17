# ECR and GitHub OIDC Implementation Plan

> 문서 상태: repository 구현 절차와 승인 gate 기록
>
> 기준일: 2026-07-17
>
> 완료: Foundation 기준선, Terraform module/test, ECR workflow, 검토된 저장 plan Apply, GitHub 변수 등록
>
> 대기: Workflow의 `master` 반영과 ECR 최초 게시
>
> 주의: 아래 checkbox는 당시 실행 절차이며 현재 상태의 기준은 아니다.

현재 운영 상태와 명령은 [`infra/aws/terraform/README.md`](../../infra/aws/terraform/README.md)를 우선한다. 설계 기준은 [ECR/OIDC 설계](05-ecr-github-oidc-design.md), 현재 delivery 경계는 [CI/CD와 배포](../architecture/cicd-deployment.md)에서 관리한다. 이 계획은 특정 skill, plugin 또는 subagent 사용을 요구하지 않는다.

**Goal:** Create eight private backend ECR repositories, a least-privilege GitHub OIDC role, and a separate manual SHA-only image publication workflow without changing the existing GHCR/Kubernetes delivery path.

**Architecture:** Terraform adds an isolated ECR module and a GitHub Actions ECR IAM module to the already-applied learning foundation. GitHub Actions selects all or one backend service, runs tests, assumes the AWS role with OIDC, skips an existing commit SHA, and pushes only a missing SHA-tagged image. Implementation stops for explicit review before any AWS Apply.

**Tech Stack:** Terraform `~> 1.15.0`, HashiCorp AWS Provider `~> 6.0`, Amazon ECR, AWS IAM/STS OIDC, GitHub Actions, Python 3, Java 17/Gradle, Docker Buildx

## Global Constraints

- AWS Region is exactly `ap-northeast-2`.
- Environment is exactly `learning`.
- GitHub repository is exactly `hyunmyungchoi/Spring-React-MSA`.
- The only trusted Git ref is `refs/heads/master`.
- The ECR workflow trigger is manual `workflow_dispatch` only.
- Only the eight backend services in the approved design receive ECR repositories.
- Image tags use the full Git commit SHA; no `latest` tag is published.
- Each repository retains five tagged images and expires untagged images after one day.
- The existing `.github/workflows/ghcr-build-push.yml` file must not change.
- No NAT Gateway, Elastic IP, VPC endpoint, ECS, EC2, ALB, RDS, ElastiCache, MSK, ACM, or Route 53 resource is created.
- No AWS access key, secret key, real account ID, budget email, Terraform state, plan, or real `terraform.tfvars` value is committed. Test fixtures use the AWS documentation example account `111122223333` only.
- Do not run `terraform apply`, set a GitHub repository variable, push a branch, open a PR, or merge without the separately stated gate in Task 6.
- Approved design: `docs/aws-migration/05-ecr-github-oidc-design.md`.

## File Map

- `infra/aws/terraform/modules/ecr/variables.tf`: ECR module input contract.
- `infra/aws/terraform/modules/ecr/main.tf`: Eight repositories and lifecycle policies.
- `infra/aws/terraform/modules/ecr/outputs.tf`: Service-to-name/ARN/URL maps.
- `infra/aws/terraform/modules/github-actions-ecr/variables.tf`: OIDC/IAM module input contract.
- `infra/aws/terraform/modules/github-actions-ecr/main.tf`: Exact GitHub trust and least-privilege ECR push policy.
- `infra/aws/terraform/modules/github-actions-ecr/outputs.tf`: Role name and ARN.
- `infra/aws/terraform/locals.tf`: Approved backend inventory and exact GitHub identity values.
- `infra/aws/terraform/main.tf`: OIDC provider lookup and root module composition.
- `infra/aws/terraform/outputs.tf`: ECR maps and GitHub role outputs.
- `infra/aws/terraform/tests/ecr.tftest.hcl`: Repository and lifecycle contracts.
- `infra/aws/terraform/tests/github_actions_ecr.tftest.hcl`: Trust and permission contracts.
- `infra/aws/terraform/tests/foundation.tftest.hcl`: Existing root tests updated with the OIDC data mock.
- `.github/workflows/ecr-build-push.yml`: Independent manual backend publication workflow.
- `infra/aws/terraform/README.md`: Operator instructions for Plan, Apply gate, GitHub variable, and manual publication.
- `.gitignore`: Ignores the project-local `.worktrees/` directory used after the Foundation baseline commit.

---

### Task 1: Commit the applied AWS Foundation baseline safely

**Files:**
- Modify: `.gitignore`
- Add: `docs/aws-migration/04-aws-foundation-design.md`
- Add: `infra/aws/terraform/.gitignore`
- Add: `infra/aws/terraform/.terraform.lock.hcl`
- Add: `infra/aws/terraform/README.md`
- Add: `infra/aws/terraform/environments/learning/README.md`
- Add: `infra/aws/terraform/locals.tf`
- Add: `infra/aws/terraform/main.tf`
- Add: `infra/aws/terraform/modules/network/main.tf`
- Add: `infra/aws/terraform/modules/network/outputs.tf`
- Add: `infra/aws/terraform/modules/network/variables.tf`
- Add: `infra/aws/terraform/outputs.tf`
- Add: `infra/aws/terraform/providers.tf`
- Add: `infra/aws/terraform/terraform.tfvars.example`
- Add: `infra/aws/terraform/tests/foundation.tftest.hcl`
- Add: `infra/aws/terraform/variables.tf`
- Add: `infra/aws/terraform/versions.tf`

**Interfaces:**
- Consumes: The already-applied local Terraform state and the 16 existing untracked Foundation files.
- Produces: A clean, reviewable Git baseline that later ECR commits can build on without mixing unrelated infrastructure.

- [ ] **Step 1: Confirm the exact untracked baseline and ignored sensitive files**

Run from `C:\Portfolio`:

```powershell
git status --short --untracked-files=all
git check-ignore -v `
  infra/aws/terraform/terraform.tfstate `
  infra/aws/terraform/terraform.tfvars `
  infra/aws/terraform/.terraform `
  infra/aws/terraform/tfplan
```

Expected: exactly the 16 Foundation files listed above are untracked; state, real variables, provider cache, and plan are ignored by `infra/aws/terraform/.gitignore`.

- [ ] **Step 2: Scan the baseline for credential and personal-value leakage**

```powershell
$matches = rg -n `
  "AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|aws_secret_access_key|c\.hyunmyung@gmail\.com" `
  infra/aws `
  docs/aws-migration/04-aws-foundation-design.md

if ($LASTEXITCODE -eq 0) {
  $matches
  throw "Sensitive-looking values found in the Foundation baseline."
}

if ($LASTEXITCODE -ne 1) {
  throw "Secret scan failed to run."
}

Write-Output "No sensitive-looking values found."
```

Expected: `No sensitive-looking values found.`

- [ ] **Step 3: Ignore the project-local worktree directory**

Add this line to the root `.gitignore`:

```gitignore
.worktrees/
```

Verify it before creating any worktree:

```powershell
Set-Location C:\Portfolio
git check-ignore -v .worktrees
```

Expected: the root `.gitignore` rule for `.worktrees/` is reported.

- [ ] **Step 4: Verify that the checked-in Foundation definition matches the applied AWS state**

```powershell
$env:AWS_PROFILE = "spring-react-msa-learning-iam"
$env:AWS_REGION = "ap-northeast-2"

Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan -detailed-exitcode

if ($LASTEXITCODE -ne 0) {
  throw "Foundation must have a no-change plan before it is committed."
}
```

Expected: formatting, validation, and tests pass; Plan prints `No changes` and returns exit code `0`.

- [ ] **Step 5: Stage only the approved Foundation baseline and worktree ignore rule**

```powershell
Set-Location C:\Portfolio
git add -- `
  .gitignore `
  docs/aws-migration/04-aws-foundation-design.md `
  infra/aws/terraform/.gitignore `
  infra/aws/terraform/.terraform.lock.hcl `
  infra/aws/terraform/README.md `
  infra/aws/terraform/environments/learning/README.md `
  infra/aws/terraform/locals.tf `
  infra/aws/terraform/main.tf `
  infra/aws/terraform/modules/network/main.tf `
  infra/aws/terraform/modules/network/outputs.tf `
  infra/aws/terraform/modules/network/variables.tf `
  infra/aws/terraform/outputs.tf `
  infra/aws/terraform/providers.tf `
  infra/aws/terraform/terraform.tfvars.example `
  infra/aws/terraform/tests/foundation.tftest.hcl `
  infra/aws/terraform/variables.tf `
  infra/aws/terraform/versions.tf

git diff --cached --check
git diff --cached --name-only
```

Expected: only the 16 Foundation files and the root `.gitignore` change are staged; `terraform.tfstate`, `terraform.tfvars`, `.terraform`, and `tfplan` are absent.

- [ ] **Step 6: Commit the baseline**

```powershell
git commit -m "feat: add AWS foundation Terraform"
```

Expected: one commit containing the applied Foundation definition and no runtime state or secret values.

After Task 1 passes its task review, the controller creates `.worktrees/codex-ecr-github-oidc` on branch `codex/ecr-github-oidc` from the reviewed Task 1 commit. Tasks 2-5 run only in that worktree. Task 6 remains gated by explicit Apply approval.

---

### Task 2: Build the ECR module with Terraform tests first

**Files:**
- Create: `infra/aws/terraform/tests/ecr.tftest.hcl`
- Create: `infra/aws/terraform/modules/ecr/variables.tf`
- Create: `infra/aws/terraform/modules/ecr/main.tf`
- Create: `infra/aws/terraform/modules/ecr/outputs.tf`
- Modify: `infra/aws/terraform/locals.tf:1`
- Modify: `infra/aws/terraform/main.tf:22`
- Modify: `infra/aws/terraform/outputs.tf:46`

**Interfaces:**
- Consumes: `local.name_prefix`, `local.common_tags`, and the approved eight-service inventory.
- Produces: `module.ecr.repository_names`, `module.ecr.repository_arns`, and `module.ecr.repository_urls`, each typed as `map(string)` keyed by service name.

- [ ] **Step 1: Write the failing ECR contract test**

Create `infra/aws/terraform/tests/ecr.tftest.hcl`:

```hcl
mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["ap-northeast-2a", "ap-northeast-2c"]
    }
  }
}

run "ecr_module_contract" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  variables {
    name_prefix = "spring-react-msa-learning"
    service_names = toset([
      "spring-member-gateway",
      "spring-admin-gateway",
      "spring-security-authorization-server",
      "spring-user-service",
      "spring-member-community-service",
      "spring-member-stock-service",
      "spring-member-bff-service",
      "spring-admin-bff-service",
    ])
    tagged_image_retention_count = 5
    untagged_image_retention_days = 1
    common_tags = {
      Project     = "spring-react-msa"
      Environment = "learning"
      ManagedBy   = "terraform"
      Purpose     = "aws-learning"
    }
  }

  assert {
    condition     = length(aws_ecr_repository.this) == 8
    error_message = "Exactly eight backend ECR repositories must be created."
  }

  assert {
    condition = toset([
      for repository in values(aws_ecr_repository.this) : repository.name
      ]) == toset([
      "spring-react-msa-learning/spring-member-gateway",
      "spring-react-msa-learning/spring-admin-gateway",
      "spring-react-msa-learning/spring-security-authorization-server",
      "spring-react-msa-learning/spring-user-service",
      "spring-react-msa-learning/spring-member-community-service",
      "spring-react-msa-learning/spring-member-stock-service",
      "spring-react-msa-learning/spring-member-bff-service",
      "spring-react-msa-learning/spring-admin-bff-service",
    ])
    error_message = "ECR repository names must use the approved namespace and service names."
  }

  assert {
    condition = alltrue([
      for repository in values(aws_ecr_repository.this) :
      repository.image_tag_mutability == "IMMUTABLE" &&
      repository.force_delete == false &&
      repository.encryption_configuration[0].encryption_type == "AES256" &&
      repository.image_scanning_configuration[0].scan_on_push == true
    ])
    error_message = "Every ECR repository must be immutable, deletion-protected, AES256 encrypted, and scanned on push."
  }

  assert {
    condition = alltrue([
      for lifecycle in values(aws_ecr_lifecycle_policy.this) :
      jsondecode(lifecycle.policy).rules[0].selection.tagStatus == "untagged" &&
      jsondecode(lifecycle.policy).rules[0].selection.countType == "sinceImagePushed" &&
      jsondecode(lifecycle.policy).rules[0].selection.countUnit == "days" &&
      jsondecode(lifecycle.policy).rules[0].selection.countNumber == 1 &&
      jsondecode(lifecycle.policy).rules[1].selection.tagStatus == "tagged" &&
      jsondecode(lifecycle.policy).rules[1].selection.tagPatternList == ["*"] &&
      jsondecode(lifecycle.policy).rules[1].selection.countType == "imageCountMoreThan" &&
      jsondecode(lifecycle.policy).rules[1].selection.countNumber == 5
    ])
    error_message = "Lifecycle policies must expire untagged images after one day and retain five tagged images."
  }
}

run "root_ecr_inventory" {
  command = plan

  variables {
    enable_budget      = false
    budget_alert_email = null
  }

  assert {
    condition     = length(module.ecr.repository_urls) == 8
    error_message = "The root module must expose eight ECR repository URLs."
  }
}
```

- [ ] **Step 2: Run the ECR test and verify the RED state**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform test -filter=tests/ecr.tftest.hcl
```

Expected: FAIL because `./modules/ecr` and `module.ecr` do not exist. The failure must be caused by the missing ECR feature, not by HCL syntax.

- [ ] **Step 3: Implement the ECR module input contract**

Create `infra/aws/terraform/modules/ecr/variables.tf`:

```hcl
variable "name_prefix" {
  description = "Prefix used for ECR repository namespaces."
  type        = string
}

variable "service_names" {
  description = "Backend service names that receive private ECR repositories."
  type        = set(string)
}

variable "tagged_image_retention_count" {
  description = "Number of tagged images retained in each repository."
  type        = number

  validation {
    condition     = var.tagged_image_retention_count > 0
    error_message = "tagged_image_retention_count must be greater than zero."
  }
}

variable "untagged_image_retention_days" {
  description = "Number of days untagged images are retained."
  type        = number

  validation {
    condition     = var.untagged_image_retention_days > 0
    error_message = "untagged_image_retention_days must be greater than zero."
  }
}

variable "common_tags" {
  description = "Tags applied to every managed ECR repository."
  type        = map(string)
}
```

- [ ] **Step 4: Implement repositories and lifecycle policies**

Create `infra/aws/terraform/modules/ecr/main.tf`:

```hcl
resource "aws_ecr_repository" "this" {
  for_each = var.service_names

  name                 = "${var.name_prefix}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  encryption_configuration {
    encryption_type = "AES256"
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}/${each.key}"
  })
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.untagged_image_retention_days} day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_image_retention_days
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Retain the ${var.tagged_image_retention_count} newest tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPatternList = ["*"]
          countType     = "imageCountMoreThan"
          countNumber   = var.tagged_image_retention_count
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}
```

- [ ] **Step 5: Implement the ECR module outputs**

Create `infra/aws/terraform/modules/ecr/outputs.tf`:

```hcl
output "repository_names" {
  description = "Map of backend service names to ECR repository names."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.name }
}

output "repository_arns" {
  description = "Map of backend service names to ECR repository ARNs."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.arn }
}

output "repository_urls" {
  description = "Map of backend service names to ECR repository URLs."
  value       = { for service, repository in aws_ecr_repository.this : service => repository.repository_url }
}
```

- [ ] **Step 6: Wire the approved service inventory into the root module**

Add this inside the existing `locals` block in `infra/aws/terraform/locals.tf`:

```hcl
  backend_service_names = toset([
    "spring-member-gateway",
    "spring-admin-gateway",
    "spring-security-authorization-server",
    "spring-user-service",
    "spring-member-community-service",
    "spring-member-stock-service",
    "spring-member-bff-service",
    "spring-admin-bff-service",
  ])
```

Add this module after `module "network"` in `infra/aws/terraform/main.tf`:

```hcl
module "ecr" {
  source = "./modules/ecr"

  name_prefix                  = local.name_prefix
  service_names                = local.backend_service_names
  tagged_image_retention_count = 5
  untagged_image_retention_days = 1
  common_tags                  = local.common_tags
}
```

Append these outputs to `infra/aws/terraform/outputs.tf`:

```hcl
output "ecr_repository_names" {
  description = "Map of backend service names to ECR repository names."
  value       = module.ecr.repository_names
}

output "ecr_repository_arns" {
  description = "Map of backend service names to ECR repository ARNs."
  value       = module.ecr.repository_arns
}

output "ecr_repository_urls" {
  description = "Map of backend service names to ECR repository URLs."
  value       = module.ecr.repository_urls
}
```

- [ ] **Step 7: Run the ECR test and verify the GREEN state**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -recursive
terraform test -filter=tests/ecr.tftest.hcl
```

Expected: both `ecr_module_contract` and `root_ecr_inventory` pass.

- [ ] **Step 8: Commit the ECR module**

```powershell
Set-Location C:\Portfolio
git add -- `
  infra/aws/terraform/tests/ecr.tftest.hcl `
  infra/aws/terraform/modules/ecr/variables.tf `
  infra/aws/terraform/modules/ecr/main.tf `
  infra/aws/terraform/modules/ecr/outputs.tf `
  infra/aws/terraform/locals.tf `
  infra/aws/terraform/main.tf `
  infra/aws/terraform/outputs.tf
git diff --cached --check
git commit -m "feat: add private backend ECR repositories"
```

---

### Task 3: Build the GitHub OIDC ECR role with Terraform tests first

**Files:**
- Create: `infra/aws/terraform/tests/github_actions_ecr.tftest.hcl`
- Create: `infra/aws/terraform/modules/github-actions-ecr/variables.tf`
- Create: `infra/aws/terraform/modules/github-actions-ecr/main.tf`
- Create: `infra/aws/terraform/modules/github-actions-ecr/outputs.tf`
- Modify: `infra/aws/terraform/locals.tf:1`
- Modify: `infra/aws/terraform/main.tf:1`
- Modify: `infra/aws/terraform/outputs.tf`
- Modify: `infra/aws/terraform/tests/foundation.tftest.hcl:1`
- Modify: `infra/aws/terraform/tests/ecr.tftest.hcl:1`

**Interfaces:**
- Consumes: Existing GitHub OIDC provider ARN and `module.ecr.repository_arns`.
- Produces: `module.github_actions_ecr.role_name` and `module.github_actions_ecr.role_arn` for the manual workflow.

- [ ] **Step 1: Write the failing OIDC/IAM contract test**

Create `infra/aws/terraform/tests/github_actions_ecr.tftest.hcl`:

```hcl
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
```

- [ ] **Step 2: Run the IAM test and verify the RED state**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform test -filter=tests/github_actions_ecr.tftest.hcl
```

Expected: FAIL because `./modules/github-actions-ecr` does not exist.

- [ ] **Step 3: Implement the IAM module input contract**

Create `infra/aws/terraform/modules/github-actions-ecr/variables.tf`:

```hcl
variable "name_prefix" {
  description = "Prefix used for the GitHub Actions IAM role and policy."
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the existing GitHub Actions OIDC provider."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository in owner/name format."
  type        = string
}

variable "github_branch_ref" {
  description = "Full Git ref allowed to assume the role."
  type        = string
}

variable "ecr_repository_arns" {
  description = "ECR repository ARNs that GitHub Actions may publish to."
  type        = set(string)
}

variable "common_tags" {
  description = "Tags applied to the GitHub Actions IAM role."
  type        = map(string)
}
```

- [ ] **Step 4: Implement the exact GitHub trust and ECR push permissions**

Create `infra/aws/terraform/modules/github-actions-ecr/main.tf`:

```hcl
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
```

- [ ] **Step 5: Implement IAM module outputs**

Create `infra/aws/terraform/modules/github-actions-ecr/outputs.tf`:

```hcl
output "role_name" {
  description = "Name of the GitHub Actions ECR publication role."
  value       = aws_iam_role.this.name
}

output "role_arn" {
  description = "ARN of the GitHub Actions ECR publication role."
  value       = aws_iam_role.this.arn
}
```

- [ ] **Step 6: Reuse the existing OIDC provider and compose the IAM module at root**

Add these values inside the existing `locals` block in `infra/aws/terraform/locals.tf`:

```hcl
  github_repository = "hyunmyungchoi/Spring-React-MSA"
  github_branch_ref = "refs/heads/master"
```

Add this data source to `infra/aws/terraform/main.tf` after the availability-zone data source:

```hcl
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}
```

Add this module after `module "ecr"`:

```hcl
module "github_actions_ecr" {
  source = "./modules/github-actions-ecr"

  name_prefix         = local.name_prefix
  oidc_provider_arn   = data.aws_iam_openid_connect_provider.github.arn
  github_repository   = local.github_repository
  github_branch_ref   = local.github_branch_ref
  ecr_repository_arns = toset(values(module.ecr.repository_arns))
  common_tags         = local.common_tags
}
```

Append these outputs to `infra/aws/terraform/outputs.tf`:

```hcl
output "github_actions_ecr_role_name" {
  description = "Name of the GitHub Actions ECR publication role."
  value       = module.github_actions_ecr.role_name
}

output "github_actions_ecr_role_arn" {
  description = "ARN registered as the GitHub AWS_ECR_PUSH_ROLE_ARN repository variable."
  value       = module.github_actions_ecr.role_arn
}
```

- [ ] **Step 7: Add the OIDC provider mock to every root Terraform test**

Inside each existing `mock_provider "aws"` block in `infra/aws/terraform/tests/foundation.tftest.hcl` and `infra/aws/terraform/tests/ecr.tftest.hcl`, add:

```hcl
  mock_data "aws_iam_openid_connect_provider" {
    defaults = {
      arn = "arn:aws:iam::111122223333:oidc-provider/token.actions.githubusercontent.com"
      url = "token.actions.githubusercontent.com"
    }
  }
```

- [ ] **Step 8: Run focused and full Terraform tests**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -recursive
terraform test -filter=tests/github_actions_ecr.tftest.hcl
terraform test
```

Expected: the focused IAM test and all Foundation/ECR tests pass.

- [ ] **Step 9: Commit the OIDC/IAM module**

```powershell
Set-Location C:\Portfolio
git add -- `
  infra/aws/terraform/tests/github_actions_ecr.tftest.hcl `
  infra/aws/terraform/modules/github-actions-ecr/variables.tf `
  infra/aws/terraform/modules/github-actions-ecr/main.tf `
  infra/aws/terraform/modules/github-actions-ecr/outputs.tf `
  infra/aws/terraform/locals.tf `
  infra/aws/terraform/main.tf `
  infra/aws/terraform/outputs.tf `
  infra/aws/terraform/tests/foundation.tftest.hcl `
  infra/aws/terraform/tests/ecr.tftest.hcl
git diff --cached --check
git commit -m "feat: add GitHub OIDC role for ECR publishing"
```

---

### Task 4: Add the independent manual ECR publication workflow

**Files:**
- Create: `.github/workflows/ecr-build-push.yml`
- Modify: `infra/aws/terraform/README.md`
- Verify unchanged: `.github/workflows/ghcr-build-push.yml`

**Interfaces:**
- Consumes: Existing `infra/ci/select-build-matrix.py`, GitHub variable `AWS_ECR_PUSH_ROLE_ARN`, Terraform-created ECR repository names, and OIDC role.
- Produces: A manual workflow that publishes all or one backend image under the full `github.sha` tag.

The YAML workflow is configuration rather than executable application logic. Per the approved design, validate it immediately with `actionlint` and validate its reused selector with the existing Python unit tests.

- [ ] **Step 1: Confirm the existing GHCR workflow is unchanged before editing**

```powershell
git diff --quiet -- .github/workflows/ghcr-build-push.yml

if ($LASTEXITCODE -ne 0) {
  throw "The existing GHCR workflow was already modified before the ECR task."
}
```

Expected: exit code `0`; the existing GHCR workflow has no uncommitted change.

- [ ] **Step 2: Create the manual ECR workflow**

Create `.github/workflows/ecr-build-push.yml`:

```yaml
name: Build and Push Backend Images to Amazon ECR

on:
  workflow_dispatch:
    inputs:
      deploy_target:
        description: Backend service to build and publish
        required: true
        default: all
        type: choice
        options:
          - all
          - spring-member-gateway
          - spring-admin-gateway
          - spring-security-authorization-server
          - spring-user-service
          - spring-member-community-service
          - spring-member-stock-service
          - spring-member-bff-service
          - spring-admin-bff-service

permissions:
  contents: read

concurrency:
  group: ecr-publish-${{ github.ref }}
  cancel-in-progress: false

env:
  AWS_REGION: ap-northeast-2
  ECR_NAMESPACE: spring-react-msa-learning
  DEPLOY_TARGET: ${{ inputs.deploy_target }}

jobs:
  prepare-build-matrix:
    name: Prepare backend build matrix
    runs-on: ubuntu-latest
    outputs:
      backend_matrix: ${{ steps.matrix.outputs.backend_matrix }}
      has_backend: ${{ steps.matrix.outputs.has_backend }}

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Select backend target
        id: matrix
        run: python infra/ci/select-build-matrix.py

  ci-matrix-test:
    name: CI matrix unit test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Run CI matrix tests
        run: python -m unittest infra/ci/test_select_build_matrix.py

  test-backend:
    name: Test backend (${{ matrix.service }})
    runs-on: ubuntu-latest
    needs:
      - prepare-build-matrix
    if: needs.prepare-build-matrix.outputs.has_backend == 'true'

    strategy:
      fail-fast: false
      matrix: ${{ fromJSON(needs.prepare-build-matrix.outputs.backend_matrix) }}

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Make Gradle wrapper executable
        working-directory: BackEnd/${{ matrix.service }}
        run: chmod +x ./gradlew

      - name: Run Gradle tests
        working-directory: BackEnd/${{ matrix.service }}
        run: ./gradlew test

  build-backend:
    name: Publish backend image (${{ matrix.service }})
    runs-on: ubuntu-latest
    needs:
      - prepare-build-matrix
      - ci-matrix-test
      - test-backend
    if: >
      ${{
        needs.prepare-build-matrix.outputs.has_backend == 'true'
        && needs.ci-matrix-test.result == 'success'
        && needs.test-backend.result == 'success'
      }}

    permissions:
      contents: read
      id-token: write

    strategy:
      fail-fast: false
      matrix: ${{ fromJSON(needs.prepare-build-matrix.outputs.backend_matrix) }}

    env:
      AWS_ECR_PUSH_ROLE_ARN: ${{ vars.AWS_ECR_PUSH_ROLE_ARN }}

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Validate publication prerequisites
        shell: bash
        run: |
          if [[ "${GITHUB_REF}" != "refs/heads/master" ]]; then
            echo "ECR publication is restricted to refs/heads/master; received ${GITHUB_REF}."
            exit 1
          fi

          if [[ -z "${AWS_ECR_PUSH_ROLE_ARN}" ]]; then
            echo "Repository variable AWS_ECR_PUSH_ROLE_ARN is required."
            exit 1
          fi

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v6
        with:
          role-to-assume: ${{ env.AWS_ECR_PUSH_ROLE_ARN }}
          aws-region: ${{ env.AWS_REGION }}
          role-session-name: GitHubActionsEcr-${{ github.run_id }}
          role-duration-seconds: 3600

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Check whether the SHA image already exists
        id: image
        shell: bash
        run: |
          repository="${ECR_NAMESPACE}/${{ matrix.service }}"

          set +e
          lookup_output=$(aws ecr describe-images \
            --region "${AWS_REGION}" \
            --repository-name "${repository}" \
            --image-ids "imageTag=${GITHUB_SHA}" 2>&1)
          lookup_status=$?
          set -e

          if [[ ${lookup_status} -eq 0 ]]; then
            echo "exists=true" >> "${GITHUB_OUTPUT}"
            echo "${repository}:${GITHUB_SHA} already exists; skipping publication."
          elif grep -q "ImageNotFoundException" <<< "${lookup_output}"; then
            echo "exists=false" >> "${GITHUB_OUTPUT}"
            echo "${repository}:${GITHUB_SHA} is missing; publication will continue."
          else
            echo "${lookup_output}"
            exit "${lookup_status}"
          fi

      - name: Build and push SHA-tagged image
        if: steps.image.outputs.exists == 'false'
        uses: docker/build-push-action@v7
        with:
          context: ${{ matrix.context }}
          file: ${{ matrix.dockerfile }}
          push: true
          tags: ${{ steps.login-ecr.outputs.registry }}/${{ env.ECR_NAMESPACE }}/${{ matrix.service }}:${{ github.sha }}
```

- [ ] **Step 3: Validate the reused selector and workflow syntax**

```powershell
Set-Location C:\Portfolio
python -m unittest infra/ci/test_select_build_matrix.py
docker run --rm `
  -v "${PWD}:/repo" `
  -w /repo `
  rhysd/actionlint:1.7.12 `
  .github/workflows/ecr-build-push.yml
```

Expected: Python reports three passing tests; `actionlint` exits `0` with no findings.

- [ ] **Step 4: Verify the GHCR workflow is byte-for-byte unchanged**

```powershell
git diff --quiet -- .github/workflows/ghcr-build-push.yml

if ($LASTEXITCODE -ne 0) {
  throw "The existing GHCR workflow changed unexpectedly."
}

Write-Output "GHCR workflow unchanged."
```

Expected: `GHCR workflow unchanged.`

- [ ] **Step 5: Add the operator instructions to the Terraform README**

Append this section to `infra/aws/terraform/README.md`:

````markdown
## Backend ECR and GitHub OIDC

This phase adds eight backend ECR repositories and one GitHub OIDC role. It does not create ECS, EC2, ALB, NAT, RDS, or frontend repositories.

The ECR repositories use immutable full-commit-SHA tags. Each repository retains five tagged images and expires untagged images after one day. The existing GHCR workflow remains independent.

After an approved Terraform Apply, register the role ARN as a GitHub repository variable:

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
$roleArn = terraform output -raw github_actions_ecr_role_arn

Set-Location C:\Portfolio
gh variable set AWS_ECR_PUSH_ROLE_ARN `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $roleArn
```

The manual workflow must exist on the default branch. After the change is reviewed and merged to `master`, publish one service first:

```powershell
gh workflow run ecr-build-push.yml `
  --repo hyunmyungchoi/Spring-React-MSA `
  --ref master `
  -f deploy_target=spring-user-service
```

Watch the run:

```powershell
gh run watch --repo hyunmyungchoi/Spring-React-MSA
```

Do not publish `all` until the single-service run, SHA tag, digest, and scan result have been verified.
````

- [ ] **Step 6: Commit the workflow and operator documentation**

```powershell
Set-Location C:\Portfolio
git add -- `
  .github/workflows/ecr-build-push.yml `
  infra/aws/terraform/README.md
git diff --cached --check
git diff --cached --name-only
git commit -m "ci: add manual ECR image publishing workflow"
```

Expected: the new workflow and Terraform README are committed; `ghcr-build-push.yml` is absent from the commit.

---

### Task 5: Run the full verification suite and produce the authenticated AWS plan

**Files:**
- Read: all implementation files from Tasks 1-4
- Create locally but never commit: `infra/aws/terraform/tfplan.ecr`

**Interfaces:**
- Consumes: Committed Foundation, ECR, IAM, workflow, local Terraform state, and the `spring-react-msa-learning-iam` AWS CLI login profile.
- Produces: Reproducible verification evidence and an exact saved plan for the explicit Apply gate.

- [ ] **Step 1: Run formatting, validation, Terraform tests, Python tests, and workflow lint**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test

Set-Location C:\Portfolio
python -m unittest infra/ci/test_select_build_matrix.py
docker run --rm `
  -v "${PWD}:/repo" `
  -w /repo `
  rhysd/actionlint:1.7.12 `
  .github/workflows/ecr-build-push.yml
```

Expected: every command exits `0`; no formatting or workflow-lint findings are printed.

- [ ] **Step 2: Verify the authenticated AWS identity and Region without exposing credentials**

```powershell
$env:AWS_PROFILE = "spring-react-msa-learning-iam"
$env:AWS_REGION = "ap-northeast-2"

aws sts get-caller-identity --query Arn --output text
aws configure get region --profile spring-react-msa-learning-iam
```

Expected: the IAM principal ends in `user/hyun-terraform-admin`; the intended Region is `ap-northeast-2`. Do not copy access tokens or cache files into reports.

- [ ] **Step 3: Create an exact saved Terraform plan**

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform plan -out=tfplan.ecr
terraform show -no-color tfplan.ecr
```

Expected resource summary:

```text
Plan: 18 to add, 0 to change, 0 to destroy.
```

Expected additions:

- `module.ecr.aws_ecr_repository.this`: 8
- `module.ecr.aws_ecr_lifecycle_policy.this`: 8
- `module.github_actions_ecr.aws_iam_role.this`: 1
- `module.github_actions_ecr.aws_iam_role_policy.ecr_push`: 1

There must be no NAT Gateway, Elastic IP, VPC endpoint, ECS, EC2, ALB, RDS, ElastiCache, MSK, ACM, Route 53, Foundation replacement, or destroy action.

- [ ] **Step 4: Review the plan for IAM and repository safety invariants**

Inspect `terraform show -no-color tfplan.ecr` and confirm all of the following:

```text
ECR names start with spring-react-msa-learning/
image_tag_mutability = IMMUTABLE
force_delete = false
encryption_type = AES256
scan_on_push = true
GitHub subject = repo:hyunmyungchoi/Spring-React-MSA:ref:refs/heads/master
Only ecr:GetAuthorizationToken has Resource = *
All image push actions use the eight planned ECR repository ARNs
```

Expected: every invariant is visible in the saved plan.

- [ ] **Step 5: Confirm the repository is clean and the saved plan is ignored**

```powershell
Set-Location C:\Portfolio
git status --short
git check-ignore -v infra/aws/terraform/tfplan.ecr
```

Expected: no implementation file is modified or untracked; `tfplan.ecr` is ignored by `infra/aws/terraform/.gitignore`.

- [ ] **Step 6: Stop and report the Apply gate**

Report:

```text
Verification results
Terraform plan resource counts
Add/change/destroy summary
IAM trust and permission scope
ECR storage/scanning cost drivers
Confirmation that existing Foundation and GHCR paths are unchanged
```

Do not continue to Task 6 until the user explicitly approves the saved plan Apply.

---

### Task 6: Apply and connect GitHub only after explicit approval

**Files:**
- Consume locally: `infra/aws/terraform/tfplan.ecr`
- External configuration: GitHub repository variable `AWS_ECR_PUSH_ROLE_ARN`

**Interfaces:**
- Consumes: The exact approved saved plan from Task 5.
- Produces: Eighteen AWS resources and the GitHub variable required by the manual workflow.

- [ ] **Step 1: Confirm the approval applies to the unchanged saved plan**

Immediately before Apply:

```powershell
Set-Location C:\Portfolio
git status --short

Set-Location C:\Portfolio\infra\aws\terraform
terraform show -no-color tfplan.ecr | Select-String "Plan:"
```

Expected: clean Git status and `Plan: 18 to add, 0 to change, 0 to destroy.` If code, state, credentials, or the plan has changed, discard the plan and repeat Task 5 instead of applying it.

- [ ] **Step 2: Apply the exact approved plan**

```powershell
$env:AWS_PROFILE = "spring-react-msa-learning-iam"
$env:AWS_REGION = "ap-northeast-2"

Set-Location C:\Portfolio\infra\aws\terraform
terraform apply tfplan.ecr
```

Expected: `Apply complete! Resources: 18 added, 0 changed, 0 destroyed.`

- [ ] **Step 3: Verify Terraform convergence and AWS resources**

```powershell
terraform plan -detailed-exitcode

if ($LASTEXITCODE -ne 0) {
  throw "Post-Apply Terraform plan must report no changes."
}

aws ecr describe-repositories `
  --profile spring-react-msa-learning-iam `
  --region ap-northeast-2 `
  --query "length(repositories[?starts_with(repositoryName, 'spring-react-msa-learning/')])" `
  --output text

terraform output -raw github_actions_ecr_role_name
```

Expected: no Terraform changes, ECR count `8`, and role name `spring-react-msa-learning-github-ecr-push`.

- [ ] **Step 4: Register the non-secret role ARN as a GitHub repository variable**

```powershell
$roleArn = terraform output -raw github_actions_ecr_role_arn

gh variable set AWS_ECR_PUSH_ROLE_ARN `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $roleArn

gh variable get AWS_ECR_PUSH_ROLE_ARN `
  --repo hyunmyungchoi/Spring-React-MSA
```

Expected: GitHub returns the same IAM role ARN. Do not store the ARN in a tracked workflow or Terraform variable file.

- [ ] **Step 5: Stop before publishing or merging**

Report that AWS and the GitHub variable are ready. The workflow cannot be manually run from the GitHub Actions UI until `.github/workflows/ecr-build-push.yml` is reviewed and merged to `master`.

Do not push the branch, open a pull request, merge, or run the workflow without separate user authorization. After that authorization, publish `spring-user-service` first and verify its immutable SHA tag, digest, and scan result before publishing `all`.

## Plan Completion Checklist

- [ ] Every approved design requirement maps to a task above.
- [ ] Terraform behavior is specified test-first.
- [ ] Workflow configuration has an exact linter command and reuses tested selection logic.
- [ ] Existing Foundation files are committed separately before feature work.
- [ ] Existing GHCR workflow preservation is verified by a clean targeted Git diff before and after the task.
- [ ] Apply and GitHub mutations have explicit gates.
- [ ] No placeholder, wildcard trust, secret, real account ID, `latest` tag, or unrelated AWS resource is introduced.
