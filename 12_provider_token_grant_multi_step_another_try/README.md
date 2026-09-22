# Provider token grant multi-step another try

This experimental workflow receives an opaque provider grant reference, either
as `grantId` workflow input or as the `X-Provider-Token-Grant-Github` request
header. It then performs four read-only GitHub operations: the user profile,
the user's private repositories, organizations, and starred repositories.

Before every GitHub request, the declarative workflow calls the
secure-token-storage broker to obtain a current provider token. It passes the
returned `accessToken` as a dynamic `Authorization: Bearer ...` header to the
GitHub REST function. The token is temporary workflow state for the duration of
each operation and is removed by that operation's `stateDataFilter`; it is not
returned in the final workflow data. The broker service credential remains in
application configuration.

This version intentionally does not retry a GitHub `401`: declarative REST
functions do not reproduce the Java adapter's token-refresh branch without
adding another workflow state.

The workflow does not enable workflow persistence. Its project dependencies
match the copied multi-step demo, including the in-memory Data Index and
embedded Jobs Service add-ons.

## Run locally

The broker must be reachable from the workflow runtime and must allow the
service identity represented by the service credential:

```bash
export SECURE_TOKEN_STORAGE_PROTOCOL=http
export SECURE_TOKEN_STORAGE_HOST=localhost
export SECURE_TOKEN_STORAGE_PORT=7007
export SECURE_TOKEN_STORAGE_SERVICE_TOKEN='<Backstage external-access service token>'
kn-workflow quarkus run
```

The generated workflow is available at `http://localhost:8080/provider-token-grant-multi-step-another-try`.
Start an instance with an opaque grant reference:

```bash
curl -X POST http://localhost:8080/provider-token-grant-multi-step-another-try \
  -H 'Content-Type: application/json' \
  -d '{"grantId":"<grant-id>","provider":"github","page":1,"perPage":100}'
```

The same workflow can resolve the grant from the provider-specific header:

```bash
curl -X POST http://localhost:8080/provider-token-grant-multi-step-another-try \
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
  --workflow-directory=./12_provider_token_grant_multi_step_another_try \
  --no-persistence \
  --image=quay.io/orchestrator/demo-provider-token-grant-multi-step-another-try:latest
```

Add `--deploy` to apply the generated manifests to the current cluster.
Generated manifests are not committed to this demo; the build script creates
them in the requested manifests directory.
