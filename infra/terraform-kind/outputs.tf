output "cluster" {
  description = "Name of the provisioned kind cluster."
  value       = var.cluster_name
}

output "kube_context" {
  description = "kubectl context created by kind for this cluster."
  value       = "kind-${var.cluster_name}"
}

output "verify" {
  description = "How to confirm the cluster is reachable."
  value       = "kubectl cluster-info --context kind-${var.cluster_name}"
}

output "teardown" {
  description = "Remove the cluster (also what `terraform destroy` runs)."
  value       = "kind delete cluster --name ${var.cluster_name}"
}