import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { HypothesisPanel } from '@/components/HypothesisPanel';
import type { Hypothesis } from '@/lib/types';

const hypothesis: Hypothesis = {
  incidentId: 'inc-1',
  statement: 'Elevated error/latency most plausibly originating from payment-service',
  summary: 'rule-based over 2 evidence items',
  confidence: 0.5,
  affectedServiceId: 'payment-service',
  affectedServiceVersion: '1.2.3',
  source: 'fallback',
  evidence: [
    { eventId: 'ev-1', type: 'LOG', source: 'payment', summary: 'latency_ms=1200' },
    { eventId: 'ev-2', type: 'TRACE', source: 'payment', summary: 'latency_ms=2400' },
  ],
};

describe('HypothesisPanel', () => {
  it('renders statement, affected version, source badge and evidence', () => {
    render(<HypothesisPanel hypothesis={hypothesis} />);

    expect(screen.getByTestId('hypothesis-statement')).toHaveTextContent('payment-service');
    expect(screen.getByTestId('affected-version')).toHaveTextContent('1.2.3');
    expect(screen.getByText('fallback')).toBeInTheDocument();
    expect(screen.getByText(/Evidence/)).toBeInTheDocument();
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent(/ev-1/);
    expect(items[0]).toHaveTextContent(/latency_ms=1200/);
    expect(items[1]).toHaveTextContent(/ev-2/);
    expect(items[1]).toHaveTextContent(/latency_ms=2400/);
  });

  it('omits the version row when the hypothesis carries none', () => {
    const { affectedServiceVersion: _ignored, ...noVersion } = hypothesis;
    render(<HypothesisPanel hypothesis={noVersion} />);

    expect(screen.queryByTestId('affected-version')).not.toBeInTheDocument();
  });
});