import type { Hypothesis } from '@/lib/types';

interface HypothesisPanelProps {
  hypothesis: Hypothesis;
}

function formatConfidence(confidence: number | undefined): string {
  if (confidence === undefined || Number.isNaN(confidence)) {
    return 'unknown';
  }
  return `${Math.round(confidence * 100)}%`;
}

export function HypothesisPanel({ hypothesis }: HypothesisPanelProps) {
  return (
    <div className="hypothesis-panel" data-testid="hypothesis-panel">
      <header className="hypothesis-head">
        <h3>Hypothesis</h3>
        {hypothesis.source ? <span className="badge">{hypothesis.source}</span> : null}
      </header>

      <p className="hypothesis-statement" data-testid="hypothesis-statement">
        {hypothesis.statement ?? 'No statement'}
      </p>

      <dl className="hypothesis-meta">
        <div>
          <dt>Affected service</dt>
          <dd>{hypothesis.affectedServiceId ?? 'unknown'}</dd>
        </div>
        {hypothesis.affectedServiceVersion ? (
          <div>
            <dt>Version</dt>
            <dd data-testid="affected-version">{hypothesis.affectedServiceVersion}</dd>
          </div>
        ) : null}
        <div>
          <dt>Confidence</dt>
          <dd>{formatConfidence(hypothesis.confidence)}</dd>
        </div>
      </dl>

      {hypothesis.summary ? <p className="hypothesis-summary">{hypothesis.summary}</p> : null}

      <h4>Evidence</h4>
      {hypothesis.evidence.length === 0 ? (
        <p className="hypothesis-empty">No evidence recorded</p>
      ) : (
        <ul className="hypothesis-evidence">
          {hypothesis.evidence.map((item) => (
            <li key={item.eventId}>
              <span className="badge">{item.type}</span>{' '}
              <span className="meta">
                {item.source} · {item.eventId}
              </span>
              {item.summary ? <span className="summary"> — {item.summary}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}