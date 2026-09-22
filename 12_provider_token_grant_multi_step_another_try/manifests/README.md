# Generated Manifests

These files are generated from the workflow sources by `kn-workflow` and the
repository build script. Do not edit them manually; regenerate them after
changing the workflow or its resources.

To regenerate the manifests directly:

```bash
cd 12_provider_token_grant_multi_step_another_try/src/main/resources
kn-workflow gen-manifest \
  --skip-namespace \
  --profile=gitops \
  --image=quay.io/orchestrator/demo-provider-token-grant-multi-step-another-try:latest \
  --custom-generated-manifests-dir=../../../manifests
```

For the complete image-build and deployment flow, run this from the repository
root:

```bash
./scripts/build.sh \
  --workflow-directory=./12_provider_token_grant_multi_step_another_try \
  --no-persistence
```
