locals {
  config_path = var.config_path == null ? "${path.module}/kind-config.yaml" : var.config_path
}

# Provisions the local kind cluster used by the Phase 7 runbook/demo (control
# plane + 2 labelled workers). The kind binary must already be on PATH. The
# destroy-time provisioner tears the cluster down on `terraform destroy` /
# `terraform apply` when the trigger inputs change.
resource "null_resource" "kind_cluster" {
  triggers = {
    cluster_name    = var.cluster_name
    kind_config_sha = sha256(file(local.config_path))
    kind_wait       = var.kind_wait
    extra_kind_args = var.extra_kind_args
  }

  provisioner "local-exec" {
    command = "kind create cluster --name \"${self.triggers.cluster_name}\" --config \"${local.config_path}\" --wait \"${var.kind_wait}\" ${var.extra_kind_args}"
  }

  provisioner "local-exec" {
    when    = destroy
    command = "kind delete cluster --name \"${self.triggers.cluster_name}\""
  }
}