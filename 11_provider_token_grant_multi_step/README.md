# Provider token grant multi-step consumer

This experimental workflow receives an opaque provider grant reference, either
as `grantId` workflow input or as the `X-Provider-Token-Grant-Github` request
header. It then performs four read-only GitHub operations: the user profile,
the user's private repositories, organizations, and starred repositories.

Before every GitHub request, the workflow calls the secure-token-storage broker
to obtain a current provider token. The token is never returned in workflow
state, logged, or included in an error message. A GitHub `401` causes one
additional token acquisition and retry; other failures are returned without
including the provider response body.

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

The generated workflow is available at `http://localhost:8080/provider-token-grant-multi-step`.
Start an instance with an opaque grant reference:

```bash
curl -X POST http://localhost:8080/provider-token-grant-multi-step \
  -H 'Content-Type: application/json' \
  -d '{"grantId":"<grant-id>","provider":"github","page":1,"perPage":100}'
```

The same workflow can resolve the grant from the provider-specific header:

```bash
curl -X POST http://localhost:8080/provider-token-grant-multi-step \
  -H 'Content-Type: application/json' \
  -H 'X-Provider-Token-Grant-Github: <grant-id>' \
  -d '{"page":1,"perPage":100}'
```

When the workflow is started through the Orchestrator backend, provide the
opaque reference in the request's `providerTokenGrants` array. The Orchestrator
backend forwards it as `X-Provider-Token-Grant-Github`; provide any optional
pagination fields in the normal workflow input.

The repository-listing step uses GitHub's `visibility=private` filter and the
`owner,collaborator,organization_member` affiliations. Each list operation
returns one page; set `page` and `perPage` in the input to retrieve another
page.

## Build and deploy

From the repository root, generate manifests and build the image with the
repository script:

```bash
./scripts/build.sh \
  --workflow-directory=./11_provider_token_grant_multi_step \
  --no-persistence \
  --image=quay.io/orchestrator/demo-provider-token-grant-multi-step:latest
```

Add `--deploy` to apply the generated manifests to the current cluster.
Generated manifests are not committed to this demo; the build script creates
them in the requested manifests directory.
