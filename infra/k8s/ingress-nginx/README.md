# ingress-nginx

This cluster add-on installs the NGINX Ingress Controller.

## Install

```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/cloud/deploy.yaml
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

On the local desktop cluster, the controller should answer on `http://localhost`.
If no application Ingress exists yet, an HTTP 404 from NGINX is normal.

## Uninstall

```powershell
kubectl delete -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/cloud/deploy.yaml
```
