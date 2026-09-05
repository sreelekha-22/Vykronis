import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Timeline } from '@/components/Timeline';
import type { EvidenceItem, IncidentSummary } from '@/lib/types';

const incident: IncidentSummary = {
  incidentId: 'inc-1',
  serviceId: 'payment-service',
  severity: 'critical',
  status: 'OPEN',
  title: 'Error rate above threshold',
  detectedAt: '2026-09-03T10:00:00Z',
};

const trace: EvidenceItem = {
  eventId: 'evt-trace-1',
  source: 'payment-service',
  serviceId: 'payment-service',
  env: 'PROD',
  type: 'TRACE',
  payload: '{"spanId":"s1"}',
  traceId: 'abc123',
  timestamp: '2026-09-03T10:02:00Z',
};

const log: EvidenceItem = {
  eventId: 'evt-log-1',
  source: 'payment-service',
  serviceId: 'payment-service',
  env: 'PROD',
  type: 'LOG',
  payload: '{"message":"NPE on check"}',
  traceId: 'abc123',
  timestamp: '2026-09-03T10:01:00Z',
};

const jfr: EvidenceItem = {
  eventId: 'evt-jfr-1',
  source: 'payment-service',
  serviceId: 'payment-service',
  env: 'PROD',
  type: 'JFR',
  payload: '{"fileName":"allocated-20260903-100100.jfr"}',
  timestamp: '2026-09-03T10:01:30Z',
};

describe('Timeline', () => {
  it('renders trace, log and JFR evidence refs on the timeline', () => {
    render(
      <Timeline incident={incident} from="2026-09-03T10:00:00Z" to="2026-09-03T11:00:00Z" items={[jfr, trace, log]} />,
    );

    expect(screen.getByText(/inc-1/)).toBeInTheDocument();
    expect(screen.getByText(/2026-09-03T10:00:00Z/)).toBeInTheDocument();
    expect(screen.getByText(/2026-09-03T11:00:00Z/)).toBeInTheDocument();

    const traceRef = screen.getByRole('link', { name: /^trace:abc123$/ });
    expect(traceRef).toHaveAttribute(
      'href',
      '/incidents/inc-1/evidence/evt-trace-1?type=TRACE&traceId=abc123',
    );

    const logRef = screen.getByRole('link', { name: /^log:payment-service$/ });
    expect(logRef).toHaveAttribute('href', '/incidents/inc-1/evidence/evt-log-1?type=LOG');

    const jfrRef = screen.getByRole('link', { name: /^jfr:allocated-20260903-100100\.jfr$/ });
    expect(jfrRef).toHaveAttribute(
      'href',
      '/incidents/inc-1/evidence/evt-jfr-1?type=JFR&filename=allocated-20260903-100100.jfr',
    );

    const timestamps = screen.getAllByRole('listitem');
    expect(timestamps[0]).toHaveTextContent('2026-09-03T10:01:00Z');
    expect(timestamps[1]).toHaveTextContent('2026-09-03T10:01:30Z');
    expect(timestamps[2]).toHaveTextContent('2026-09-03T10:02:00Z');
  });

  it('shows an empty state when no evidence matches the window', () => {
    render(<Timeline incident={incident} from="2026-09-03T10:00:00Z" to="2026-09-03T11:00:00Z" items={[]} />);

    expect(screen.getByText(/No evidence in this incident window/)).toBeInTheDocument();
  });

  it('does not render a trace ref when the item has no traceId', () => {
    render(
      <Timeline
        incident={incident}
        from="2026-09-03T10:00:00Z"
        to="2026-09-03T11:00:00Z"
        items={[{ ...jfr, type: 'METRIC' }]}
      />,
    );

    expect(screen.queryByText(/^trace:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^jfr:/)).not.toBeInTheDocument();
  });
});