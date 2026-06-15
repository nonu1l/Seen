import type { SettingsTestResult } from '../../api/types';

interface Props {
  result: SettingsTestResult | null;
}

export function TestResult({ result }: Props) {
  if (!result) return null;
  const detailEntries = Object.entries(result.details ?? {});

  return (
    <div className={`settings-test-result ${result.ok ? 'is-ok' : 'is-error'}`}>
      <span>{result.message}</span>
      {typeof result.elapsedMs === 'number' && <span>{result.elapsedMs}ms</span>}
      {detailEntries.length > 0 && (
        <div className="settings-test-details">
          {detailEntries.map(([detailKey, detailValue]) => (
            <span key={detailKey}>{detailKey}: {renderDetailValue(detailValue)}</span>
          ))}
        </div>
      )}
    </div>
  );
}

function renderDetailValue(value: unknown) {
  if (value == null) return '';
  if (Array.isArray(value)) return value.join('、');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
