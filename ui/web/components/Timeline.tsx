import type { EvidenceItem, EvidenceRef, IncidentSummary } from '@/lib/types';

interface TimelineProps {
  incident: IncidentSummary;
  from: string;
  to: string;
  items: EvidenceItem[];
}

function fileName(item: EvidenceItem): string | null {
  if (!item.payload) {
    return null;
  }
  try {
    const parsed = JSON.parse(item.payload) as { fileName?: string };
    return parsed.fileName ?? null;
  } catch {
    return null;
  }
}

function refFor(item: EvidenceItem, incidentId: string): EvidenceRef | null {
  const base = `/incidents/${incidentId}/evidence/${item.eventId}`;
  switch (item.type) {
    case 'TRACE':
      return item.traceId
        ? { label: `trace:${item.traceId}`, href: `${base}?type=TRACE&traceId=${encodeURIComponent(item.traceId)}` }
        : null;
    case 'LOG':
      return { label: `log:${item.source}`, href: `${base}?type=LOG` };
    case 'JFR': {
      const name = fileName(item) ?? item.eventId;
      return { label: `jfr:${name}`, href: `${base}?type=JFR&filename=${encodeURIComponent(name)}` };
    }
    default:
      return { label: `${item.type.toLowerCase()}:${item.source}`, href: `${base}?type=${item.type}` };
  }
}

function formatTimestamp(iso: string): string {
  if (Number.isNaN(Date.parse(iso))) {
    return iso;
  }
  return new Date(iso).toISOString().replace(/\.000Z$/, 'Z');
}

export function Timeline({ incident, from, to, items }: TimelineProps) {
  const ordered = [...items].sort((a, b) => a.timestamp.localeCompare(b.timestamp));

  return (
    <section aria-label="Incident timeline" className="timeline">
      <header className="timeline-header">
        <h2>{incident.title ?? incident.incidentId}</h2>
        <p data-testid="incident-id">
          Incident <strong>{incident.incidentId}</strong> — {incident.serviceId} ({incident.severity},{' '}
          {incident.status})
        </p>
        <p data-testid="incident-status">
          Status: <strong>{incident.status}</strong>
        </p>
        <p data-testid="incident-window">
          Window: {formatTimestamp(from)} → {formatTimestamp(to)}
        </p>
      </header>

      {ordered.length === 0 ? (
        <p className="timeline-empty">No evidence in this incident window</p>
      ) : (
        <ul className="timeline-items">
          {ordered.map((item) => {
            const ref = refFor(item, incident.incidentId);
            return (
              <li key={item.eventId} className="timeline-item">
                <time dateTime={item.timestamp}>{formatTimestamp(item.timestamp)}</time>
                <span className="badge">{item.type}</span>
                <span className="meta">
                  {item.serviceId} · {item.env}
                </span>
                {ref ? (
                  <a href={ref.href} className="ref">
                    {ref.label}
                  </a>
                ) : (
                  <span className="ref ref-missing">{item.eventId}</span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}