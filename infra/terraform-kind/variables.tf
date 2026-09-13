variable "cluster_name" {
  type        = string
  default     = "vykronis"
  description = "Name of the kind cluster (becomes kube context kind-<cluster_name>)."
}

variable "config_path" {
  type        = string
  default     = null
  description = "Path to the kind cluster config; defaults to ${path.module}/kind-config.yaml."
}

variable "kind_wait" {
  type        = string
  default     = "120s"
  description = "How long kind waits for control-plane ready before finishing (kind --wait)."
}

variable "extra_kind_args" {
  type        = string
  default     = ""
  description = "Extra flags to append to `kind create cluster` (e.g. verbosity or node images)."
}