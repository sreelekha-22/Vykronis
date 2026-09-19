import { InvestigatePanel } from '@/components/InvestigatePanel';
import { RemediationPanel } from '@/components/RemediationPanel';
import { Timeline } from '@/components/Timeline';
import { ApiError, DEMO_OPERATOR, getEvidence, getIncident } from '@/lib/api';
import type { EvidenceItem } from '@/lib/types';

export const dynamic = 'force-dynamic';

export default async function IncidentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  let incident;
  try {
    incident = await getIncident(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return <main className="timeline">Unknown incident {id}</main>;
    }
    throw error;
  }
  let items: EvidenceItem[] = [];
  let from = '';
  let to = '';
  try {
    const evidence = await getEvidence(incident);
    items = evidence.items;
    from = evidence.from;
    to = evidence.to;
  } catch {
    const detected = new Date(incident.detectedAt);
    from = new Date(detected.getTime() - 3600_000).toISOString();
    to = new Date(detected.getTime() + 3600_000).toISOString();
  }

  return (
    <main>
      <InvestigatePanel incident={incident} />
      <RemediationPanel incident={incident} operator={DEMO_OPERATOR} />
      <Timeline incident={incident} from={from} to={to} items={items} />
    </main>
  );
}