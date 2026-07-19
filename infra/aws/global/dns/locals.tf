locals {
  member_domain = "app.${var.root_domain}"
  admin_domain  = "admin.${var.root_domain}"
  origin_domain = "origin.${var.root_domain}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Scope       = "global-dns"
  }
}
