# Generated Manifests

These files are generated from the workflow sources by `kn-workflow` and the
repository build script. Do not edit them manually; regenerate them after
changing the workflow or its resources.

To regenerate the manifests directly:

```bash
cd 10_provider_token_grant/src/main/resources
kn-workflow gen-manifest \
  --skip-namespace \
  --profile=gitops \
  --image=quay.io/orchestrator/demo-provider-token-grant:latest \
  --custom-generated-manifests-dir=../../../manifests
```

For the complete image-build and deployment flow, run this from the repository
root:

```bash
./scripts/build.sh \
  --workflow-directory=./10_provider_token_grant \
  --no-persistence
```
