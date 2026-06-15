import { SettingsRow } from './SettingsRow';

interface Props {
  title: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function ToggleRow({ title, checked, onChange }: Props) {
  return (
    <SettingsRow title={title}>
      <button
        type="button"
        className={`settings-toggle ${checked ? 'is-on' : ''}`}
        aria-pressed={checked}
        onClick={() => onChange(!checked)}
      >
        <span />
      </button>
    </SettingsRow>
  );
}
