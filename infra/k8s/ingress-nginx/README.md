# ingress-nginx

This cluster add-on installs the NGINX Ingress Controller.

## Install

```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/cloud/deploy.yaml
kubectl patch service ingress-nginx-controller -n ingress-nginx --type merge --patch-file D:\Project\SpringMSA\infra\k8s\ingress-nginx\10-service-external-ips-patch.json
```

## Verify

```powershell
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=180s
kubectl get pods,svc -n ingress-nginx
kubectl get ingressclass
```

Expected ingress class:

```text
nginx
```

On the VMware cluster, the controller answers through worker external IPs
`192.168.147.111` and `192.168.147.112`.
If no application Ingress exists yet, an HTTP 404 from NGINX is normal.

Add these entries to the Windows hosts file for browser access:

```text
192.168.147.111 user.localtest.me admin.localtest.me argocd.localtest.me grafana.localtest.me
```

`ingress-nginx` is retained for this learning cluster. The upstream project is
retiring, so a new production design should evaluate a maintained Gateway API
implementation instead of adopting ingress-nginx as a new long-term dependency.

## Uninstall

```powershell
kubectl delete -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/cloud/deploy.yaml
```
