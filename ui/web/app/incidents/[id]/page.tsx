import { InvestigatePanel } from '@/components/InvestigatePanel';
import { Timeline } from '@/components/Timeline';
import { ApiError, getEvidence, getIncident } from '@/lib/api';

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
  const { items, from, to } = await getEvidence(incident);

  return (
    <main>
      <InvestigatePanel incident={incident} />
      <Timeline incident={incident} from={from} to={to} items={items} />
    </main>
  );
}