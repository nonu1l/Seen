import { Eye, EyeOff } from 'lucide-react';

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
        {visible ? <EyeOff size={18} strokeWidth={1.8} /> : <Eye size={18} strokeWidth={1.8} />}
      </button>
    </div>
  );
}
