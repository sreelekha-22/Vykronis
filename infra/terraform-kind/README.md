# Terraform IaC for the local Vykronis kind demo cluster
# -------------------------------------------------------
# Provisions the exact Phase 7 demo cluster (control plane + 2 labelled
# workers: `vykronis.io/node-type`) as versioned, repeatable infrastructure.
# Requires:
#   * Terraform >= 1.4.0          (https://developer.hashicorp.com/terraform/downloads)
#   * kind on PATH                (https://kind.sigs.k8s.io)
#   * kubectl (only to verify / to run the demo afterwards)

## Usage

    # 1. Plan & inspect
    terraform -chdir=infra/terraform-kind init
    terraform -chdir=infra/terraform-kind plan

    # 2. Create the cluster (kind is stateful; triggers re-create if config changes)
    terraform -chdir=infra/terraform-kind apply -auto-approve

    # 3. Verify, then continue the demo with helm install (phase7-runbook.md)
    kubectl cluster-info --context kind-vykronis
    kubectl get nodes -L vykronis.io/node-type

    # 4. Tear down
    terraform -chdir=infra/terraform-kind destroy -auto-approve

## What it manages

* `infra/terraform-kind/kind-config.yaml` is the **single source of truth** for
  the cluster shape (also consumable via `kind create cluster --config
  infra/terraform-kind/kind-config.yaml`, which the runbook quickstart now
  references).
* `null_resource.kind_cluster` creates on apply and deletes on destroy; the
  trigger inputs snapshot the config hash so `apply` re-creates the cluster if
  the node shape changes.
* Variables: `cluster_name` (default `vykronis`), `config_path`, `kind_wait`
  (default `120s`), `extra_kind_args`.

## Notes

* This runs `kind` locally — it is NOT a cloud IaC layer. It makes the laptop
  demo cluster declarative and reproducible, matching the "kind + Helm" scope;
  there is no EKS/AKS/GKE provisioning in the project.
* `terraform destroy` deletes the cluster; `kind delete cluster --name vykronis`
  is equivalent.
* CI runs `terraform fmt -check` + `terraform init -backend=false` + `terraform
  validate` on every push (see `.github/workflows/ci.yml`), so the HCL stays
  syntactically and semantically valid without needing Terraform installed on
  the dev machine.