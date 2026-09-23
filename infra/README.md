# MedTrack infrastructure

Deployment configuration is separated by environment:

- `staging/` — synthetic data, Cloud Run staging and test resources
- `production/` — production configuration, enabled only after release gates

Secrets and provider credentials are stored in Google Secret Manager. They do
not belong in this directory or in Git.
