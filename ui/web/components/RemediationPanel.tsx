'use client';

import { useState } from 'react';

import { ApiError, approveRemediation, requestRemediation } from '@/lib/api';
import type { IncidentSummary, OperatorSubject } from '@/lib/types';

interface RemediationPanelProps {
  incident: IncidentSummary;
  operator?: OperatorSubject;
}

export function RemediationPanel({ incident, operator }: RemediationPanelProps) {
  const [summary, setSummary] = useState(incident);
  const [busy, setBusy] = useState<'request' | 'approve' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const requestable = summary.status === 'HYPOTHESIS_READY';
  const approvable = summary.status === 'AWAITING_APPROVAL';
  const approved = summary.status === 'AUTO_APPROVED';

  async function run(action: 'request' | 'approve') {
    setBusy(action);
    setError(null);
    try {
      const next =
        action === 'request'
          ? await requestRemediation(summary.incidentId, operator)
          : await approveRemediation(summary.incidentId, operator);
      setSummary(next);
    } catch (reason) {
      const label = action === 'request' ? 'Remediation request' : 'Approval';
      setError(reason instanceof ApiError ? `${label} failed (${reason.status})` : `${label} failed`);
    } finally {
      setBusy(null);
    }
  }

  return (
    <section aria-label="Remediation" className="remediation">
      <header className="remediation-head">
        <h3>Remediation</h3>
        {summary.env ? (
          <span className="badge" data-testid="remediation-env">
            {summary.env}
          </span>
        ) : null}
        {summary.policyDecision ? (
          <span className="badge" data-testid="remediation-policy">
            {summary.policyDecision}
          </span>
        ) : null}
      </header>

      {requestable ? (
        <button
          type="button"
          onClick={() => run('request')}
          disabled={busy !== null}
          className="remediation-request"
        >
          {busy === 'request' ? 'Requesting…' : 'Request remediation'}
        </button>
      ) : null}

      {approvable ? (
        <button
          type="button"
          onClick={() => run('approve')}
          disabled={busy !== null}
          className="remediation-approve"
        >
          {busy === 'approve' ? 'Approving…' : 'Approve'}
        </button>
      ) : null}

      {approved ? (
        <p className="remediation-note" data-testid="remediation-status">
          {`Remediation auto-approved${summary.approvedBy ? ` by ${summary.approvedBy}` : ''}`}
        </p>
      ) : null}

      {error ? (
        <p className="remediation-error" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  );
}