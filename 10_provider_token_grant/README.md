# Provider token grant consumer

This experimental workflow receives an opaque provider grant reference, either
as `{ grantId, provider }` workflow input or as the
`X-Provider-Token-Grant-Github` request header. It retrieves a short-lived
provider token through the secure-token-storage broker and immediately uses it
to call GitHub's `/user` endpoint. The token is never returned in workflow
state, logged, or included in an error message.

The workflow is intentionally stateless: it does not include the Data Index or
embedded Jobs Service, so local dev mode does not start PostgreSQL persistence
services.

## Run locally

The broker must be reachable from the workflow runtime and must allow the
service identity represented by the service credential:

```bash
export SECURE_TOKEN_STORAGE_URL=http://localhost:7007/api/secure-token-storage/token
export SECURE_TOKEN_STORAGE_SERVICE_TOKEN='<Backstage external-access service token>'
kn-workflow quarkus run
```

The generated workflow is available at `http://localhost:8080/provider-token-grant`.
Start an instance with an opaque grant reference:

```bash
curl -X POST http://localhost:8080/provider-token-grant \
  -H 'Content-Type: application/json' \
  -d '{"grantId":"<grant-id>","provider":"github"}'
```

The same workflow can resolve the grant from the provider-specific header:

```bash
curl -X POST http://localhost:8080/provider-token-grant \
  -H 'Content-Type: application/json' \
  -H 'X-Provider-Token-Grant-Github: <grant-id>' \
  -d '{}'
```

When the workflow is started through the Orchestrator backend, provide the
opaque reference in the request's `providerTokenGrants` array. The Orchestrator
backend forwards it as `X-Provider-Token-Grant-Github`; normal workflow input
continues to be supported as well.

## Build and deploy

From the repository root, generate manifests and build the image with the
repository script:

```bash
./scripts/build.sh \
  --workflow-directory=./10_provider_token_grant \
  --no-persistence \
  --image=quay.io/orchestrator/demo-provider-token-grant:latest
```

Add `--deploy` to apply the generated manifests to the current cluster.
Generated manifests are not committed to this demo; the build script creates
them in the requested manifests directory.
