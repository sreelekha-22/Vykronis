{{/* Shared labels for every vykronis object. */}}
{{- define "vykronis.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/* Per-service selector labels. Expects dict with "service" key. */}}
{{- define "vykronis.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
vykronis.io/service: {{ .service }}
{{- end }}
