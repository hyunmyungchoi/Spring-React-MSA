output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs in availability-zone order."
  value = [
    for index in range(length(var.public_subnet_cidrs)) : aws_subnet.this["public-${index}"].id
  ]
}

output "private_app_subnet_ids" {
  description = "Private application subnet IDs in availability-zone order."
  value = [
    for index in range(length(var.private_app_subnet_cidrs)) : aws_subnet.this["private-app-${index}"].id
  ]
}

output "private_data_subnet_ids" {
  description = "Private data subnet IDs in availability-zone order."
  value = [
    for index in range(length(var.private_data_subnet_cidrs)) : aws_subnet.this["private-data-${index}"].id
  ]
}

output "internet_gateway_id" {
  description = "ID of the Internet Gateway."
  value       = aws_internet_gateway.this.id
}

output "alb_security_group_id" {
  description = "ID of the ALB Security Group."
  value       = aws_security_group.alb.id
}

output "ecs_security_group_id" {
  description = "ID of the ECS application Security Group."
  value       = aws_security_group.ecs.id
}

output "data_security_group_id" {
  description = "ID of the data tier Security Group."
  value       = aws_security_group.data.id
}

output "availability_zones" {
  description = "Availability zones used by the network."
  value       = var.availability_zones
}
