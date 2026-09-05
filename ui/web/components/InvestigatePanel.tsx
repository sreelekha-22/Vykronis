'use client';

import { useState } from 'react';

import { HypothesisPanel } from '@/components/HypothesisPanel';
import { ApiError, investigateIncident } from '@/lib/api';
import type { Hypothesis, IncidentSummary } from '@/lib/types';

const INVESTIGABLE = new Set(['OPEN', 'HYPOTHESIS_READY']);

interface InvestigatePanelProps {
  incident: IncidentSummary;
}

export function InvestigatePanel({ incident }: InvestigatePanelProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hypothesis, setHypothesis] = useState<Hypothesis | null>(incident.hypothesis ?? null);

  const actionable = INVESTIGABLE.has(incident.status);

  async function runInvestigation() {
    setBusy(true);
    setError(null);
    try {
      const result = await investigateIncident(incident.incidentId);
      setHypothesis(result);
    } catch (error) {
      setError(
        error instanceof ApiError ? `Investigation failed (${error.status})` : 'Investigation failed',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Investigation" className="investigate">
      {hypothesis ? <HypothesisPanel hypothesis={hypothesis} /> : null}
      {actionable ? (
        <button type="button" onClick={runInvestigation} disabled={busy} className="investigate-action">
          {busy ? 'Investigating…' : 'Investigate'}
        </button>
      ) : !hypothesis ? (
        <p className="investigate-note">No investigation available for this incident status.</p>
      ) : null}
      {error ? (
        <p className="investigate-error" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  );
}