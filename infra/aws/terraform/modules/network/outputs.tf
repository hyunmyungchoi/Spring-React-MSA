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

output "nat_gateway_id" {
  description = "ID of the single learning NAT Gateway, or null when disabled."
  value       = one(aws_nat_gateway.this[*].id)
}

output "nat_eip_public_ip" {
  description = "Elastic public IPv4 address retained for the learning NAT Gateway across OFF and ON cycles."
  value       = aws_eip.nat.public_ip
}

output "s3_gateway_endpoint_id" {
  description = "ID of the S3 Gateway VPC Endpoint associated with the Private App Route Table."
  value       = aws_vpc_endpoint.s3.id
}

output "private_app_route_table_id" {
  description = "ID of the route table shared by the two Private App subnets."
  value       = aws_route_table.private_app.id
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
