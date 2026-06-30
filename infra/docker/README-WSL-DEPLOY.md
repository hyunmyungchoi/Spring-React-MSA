# WSL Docker Compose Deploy Rehearsal

This document describes how to rehearse the initial deployment flow locally using WSL as a temporary Linux server.

## Deployment Flow

```text
GitHub Actions
→ Build Docker images
→ Push images to GHCR
→ WSL pulls images from GHCR
→ Docker Compose runs services
```



infra/docker/docker-compose.prod.yml
infra/docker/docker-compose.wsl.yml
infra/docker/.env.prod
infra/docker/deploy-wsl.sh


Settings
→ Secrets and variables
→ Actions
→ Variables


PUBLIC_GATEWAY_URL=http://localhost:8080
PUBLIC_ADMIN_GATEWAY_URL=http://localhost:8090


read -s GHCR_PAT
echo "$GHCR_PAT" | docker login ghcr.io -u hyunmyungchoi --password-stdin
unset GHCR_PAT

read:packages

## WSL Deploy Directory

mkdir -p ~/spring-msa-deploy
cd ~/spring-msa-deploy

cp /mnt/c/Portfolio/infra/docker/docker-compose.prod.yml .
cp /mnt/c/Portfolio/infra/docker/docker-compose.wsl.yml .
cp -r /mnt/c/Portfolio/infra/docker/nginx .
cp /mnt/c/Portfolio/infra/docker/.env.prod .
cp /mnt/c/Portfolio/infra/docker/deploy-wsl.sh .
chmod +x deploy-wsl.sh


## Run Deployment

./deploy-wsl.sh
