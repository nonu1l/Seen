const SECRET_MASK = '************';

interface Props {
  value: string;
  visible: boolean;
  placeholder: string;
  onVisibilityChange: (visible: boolean) => void;
  onChange: (value: string) => void;
}

export function SecretInput({ value, visible, placeholder, onVisibilityChange, onChange }: Props) {
  const hasValue = value.trim() !== '';
  const displayValue = visible || !hasValue ? value : SECRET_MASK;

  return (
    <div className="settings-secret-wrap">
      <input
        className="settings-input"
        type="text"
        value={displayValue}
        placeholder={placeholder}
        readOnly={!visible && hasValue}
        onChange={event => {
          if (!visible) onVisibilityChange(true);
          onChange(event.target.value);
        }}
      />
      <button
        type="button"
        className="btn-icon settings-secret-button"
        title={visible ? '隐藏' : '显示'}
        aria-label={visible ? '隐藏' : '显示'}
        onClick={() => onVisibilityChange(!visible)}
      >
        <SecretVisibilityIcon visible={visible} />
      </button>
    </div>
  );
}

function SecretVisibilityIcon({ visible }: { visible: boolean }) {
  return visible ? (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 3l18 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M10.6 10.7a2 2 0 0 0 2.7 2.7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M7.1 7.5C5.4 8.6 4 10.2 3 12c2 3.5 5.2 5.5 9 5.5 1.3 0 2.6-.3 3.7-.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.2 6.7c.6-.1 1.2-.2 1.8-.2 3.8 0 7 2 9 5.5-.5.9-1.2 1.8-2 2.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ) : (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 12c2-3.5 5.2-5.5 9-5.5s7 2 9 5.5c-2 3.5-5.2 5.5-9 5.5S5 15.5 3 12z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="2.7" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}
