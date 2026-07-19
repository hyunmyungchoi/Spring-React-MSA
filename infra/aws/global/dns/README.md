# Global DNS and TLS

이 스택은 기존 `hyuncloudlab.com` Route 53 Public Hosted Zone과 영구 ACM 검증 레코드를 별도 State로 관리한다. Hosted Zone을 새로 만들지 않으며, 최초 Plan에서 `hosted_zone_id`로 기존 Zone을 import한다. `prevent_destroy`가 설정되어 있으므로 삭제·대체 Plan은 적용하지 않는다.

관리 범위는 다음과 같다.

- 기존 Public Hosted Zone의 Terraform 관리 관계와 태그
- `us-east-1`: Root, `app`, `admin` CloudFront Viewer 인증서
- `ap-northeast-2`: `origin` ALB 인증서
- 두 인증서의 DNS 검증 CNAME

NS/SOA 레코드, Registrar, 결제 및 연락처는 이 스택이 관리하지 않는다. `app`, `admin`, Root Alias와 Runtime 전용 `origin` Alias는 Runtime State가 실제 배포 리소스와 함께 관리한다.

최초 실행 전 Bootstrap State Role이 `global/dns/terraform.tfstate`와 Lockfile에 접근할 수 있어야 한다. 실제 `backend.s3.hcl`과 `terraform.tfvars`는 Git에 커밋하지 않는다.

```powershell
Set-Location C:\Portfolio\infra\aws\global\dns
terraform init -backend-config=backend.s3.hcl
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan -out=tfplan-global-dns
terraform show -no-color tfplan-global-dns
```

Plan에는 기존 Hosted Zone import, 인증서 2개, ACM 검증 CNAME과 검증 완료 대기만 있어야 한다. 정확한 Plan 파일 SHA-256을 승인받은 뒤 저장된 Plan만 적용한다.

2026-07-19 Gate B Saved Plan을 생성·검토했다.

- 파일: `tfplan-global-dns`
- 크기: 8,483 bytes
- SHA-256: `ce6c35edd8f4a156c6e506cfab8856f218ed5652273b1e44a65701df70a49517`
- 요약: `8 add, 1 change, 0 destroy`
- Import: 기존 Public Hosted Zone 1개
- Zone In-place Update: 관리 Comment, `force_destroy=false`, 공통 Tag
- 생성: `us-east-1` CloudFront 인증서·검증 5개, `ap-northeast-2` Origin 인증서·검증 3개
- NS/SOA 삭제·교체, 새 Hosted Zone, Application Alias: 0개

이 Plan은 승인된 SHA-256을 다시 검증한 뒤 그대로 Apply했다. 결과는 `8 added, 1 changed, 0 destroyed`다. Global DNS State 9개 주소, 두 인증서 `ISSUED`, DNS 검증 `3+1`, Hosted Zone의 NS 1·SOA 1·검증 CNAME 4·Application Alias 0과 재계획 `No changes`를 확인했다. 적용 Plan은 기록 후 로컬 파일을 삭제했다.
