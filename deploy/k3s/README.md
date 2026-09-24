# Deploying Lecturo to k3s

Two images (`lecturo-web`, `lecturo-relay`) are built in CI, pushed to Docker
Hub, and rolled out to a k3s cluster with kustomize. The cluster runs its own
Postgres and an S3-compatible SeaweedFS for document bytes.

## Prerequisites

- A k3s cluster (its bundled Traefik is the ingress controller).
- [cert-manager](https://cert-manager.io/) installed, with a `ClusterIssuer`
  named `letsencrypt-prod`.
- DNS for the two hostnames pointing at the cluster.
- These values, as GitHub Actions secrets and/or GitLab CI/CD variables
  (masked):

  | Name | Purpose |
  |---|---|
  | `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` | Push images |
  | `KUBE_CONFIG` | base64 of a kubeconfig with access to the cluster |
  | `POSTGRES_PASSWORD` | Postgres password |
  | `BLOB_ACCESS_KEY` / `BLOB_SECRET_KEY` | SeaweedFS S3 credentials |
  | `LECTURO_ENCRYPTION_KEY` | 32-byte key (`openssl rand -base64 32`) |
  | `UPSTREAM_BASE_URL` / `UPSTREAM_API_KEY` / `UPSTREAM_MODEL` | Relay upstream |

## Layout

```
deploy/k3s/
  base/                 namespace, postgres, seaweedfs, web, relay, ingress
  overlays/prod/        image names/tags and the public hostnames
```

Secrets are **not** in git: the deploy pipeline
(`.github/workflows/deploy.yml`, or `.gitlab-ci.yml` on GitLab) creates the
`lecturo-postgres`, `lecturo-web-secrets`, `lecturo-relay-secrets` and
`seaweedfs-s3` Secrets from those values on every deploy, then applies the
kustomize overlay and waits for the rollouts.

## First deploy

1. Set the public hostnames in
   [`overlays/prod/patch-ingress.yaml`](overlays/prod/patch-ingress.yaml) and,
   if your Docker Hub user differs, the `newName` values in
   [`overlays/prod/kustomization.yaml`](overlays/prod/kustomization.yaml).
2. Add the values above — as **Settings → Secrets and variables → Actions** on
   GitHub, or **Settings → CI/CD → Variables** (masked) on GitLab.
3. Push to `main`. The test stage runs first; when it passes, the pipeline
   builds, pushes and rolls out.

## Manual apply (no CI)

With a configured `kubectl` and kustomize:

```sh
kubectl apply -f deploy/k3s/base/namespace.yaml   # plus the Secrets, see above
kubectl apply -k deploy/k3s/overlays/prod
kubectl -n lecturo get pods
```

## Notes

- The web app applies its embedded migrations on start; there is no separate
  migration step. Keep `web` at one replica: concurrent migration runs are not
  coordinated.
- The relay is a `Recreate` Deployment because its quota volume is
  `ReadWriteOnce`; a rolling update would leave two pods sharing it.
- The quota file is the relay's only state; losing the PVC merely resets the
  day's counters.
- On a single-node k3s, the default `local-path` StorageClass provides the
  volumes.
