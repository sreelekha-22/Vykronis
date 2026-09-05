import type { IncidentSummary } from '@/lib/types';

interface IncidentsResponse extends IncidentSummary {
  incident_id?: string;
  service_id?: string;
  detected_at?: string;
}

const API_URL: string = process.env.VYKRONIS_API_URL ?? 'http://localhost:8080';

async function getIncidents(): Promise<IncidentsResponse[]> {
  const response = await fetch(`${API_URL}/api/incidents`, { headers: { 'Content-Type': 'application/json' }, cache: 'no-store' });
  if (!response.ok) {
    return [];
  }
  return (await response.json()) as IncidentsResponse[];
}

export const dynamic = 'force-dynamic';

export default async function IncidentListPage() {
  const incidents = await getIncidents();

  return (
    <main className="timeline">
      <h1>Vykronis — Incidents</h1>
      {incidents.length === 0 ? (
        <p className="timeline-empty">No incidents reported</p>
      ) : (
        <ul className="incident-list">
          {incidents.map((incident) => {
            const id = incident.incidentId ?? incident.incident_id ?? 'unknown';
            const serviceId = incident.serviceId ?? incident.service_id ?? 'unknown';
            const detectedAt = incident.detectedAt ?? incident.detected_at;
            return (
              <li key={id}>
                <a href={`/incidents/${id}`}>{id}</a> — {incident.title ?? 'Incident'} · {serviceId}
                {detectedAt ? ` · ${new Date(detectedAt).toISOString()}` : ''}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}