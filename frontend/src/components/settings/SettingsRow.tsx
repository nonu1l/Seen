import type { ReactNode } from 'react';

interface Props {
  title: string;
  description?: string;
  children: ReactNode;
}

export function SettingsRow({ title, description, children }: Props) {
  return (
    <div className="settings-row">
      <div className="settings-row-meta">
        <div className="settings-row-title"><span>{title}</span></div>
        {description && <p>{description}</p>}
      </div>
      <div className="settings-row-control">
        {children}
      </div>
    </div>
  );
}
