# Argo CD

Argo CD watches the Git repository and syncs Kubernetes manifests into the cluster.

Install Argo CD:

```powershell
kubectl apply -f C:\Portfolio\infra\k8s\argocd\00-namespace.yaml
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl patch configmap argocd-cmd-params-cm -n argocd --type merge --patch-file C:\Portfolio\infra\k8s\argocd\10-cmd-params-patch.json
kubectl rollout restart deployment/argocd-server -n argocd
kubectl rollout status deployment/argocd-server -n argocd
kubectl apply -f C:\Portfolio\infra\k8s\argocd\20-ingress.yaml
kubectl apply -f C:\Portfolio\infra\k8s\argocd\30-spring-msa-application.yaml
```

Open Argo CD:

```text
http://argocd.localtest.me
```

Initial password:

```powershell
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}")))
```

## Deployment Flow

GitHub Actions builds and pushes both image tags:

```text
latest
github.sha
```

Kubernetes manifests use the fixed `github.sha` tag, not `latest`.

```text
git push
-> GitHub Actions builds images
-> GHCR receives github.sha images
-> GitHub Actions updates infra/k8s/spring-msa image tags
-> Argo CD detects the Git change
-> Argo CD syncs Kubernetes
-> Deployment rollout starts
```
