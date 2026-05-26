interface Props {
  src: string | null | undefined;
  alt: string;
  className?: string;
}

export function Cover({ src, alt, className }: Props) {
  return (
    <div className={`cover-placeholder relative w-full h-full ${className ?? ''}`}>
      {src ? (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          className="h-full w-full object-cover"
          onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
        />
      ) : (
        <svg className="h-7 w-7 m-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.2} opacity={0.3}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0L16.5 16.5M14.25 13.5l1.409-1.409a2.25 2.25 0 013.182 0L21 14.25M3.75 21h16.5A2.25 2.25 0 0022.5 18.75V5.25A2.25 2.25 0 0020.25 3H3.75A2.25 2.25 0 001.5 5.25v13.5A2.25 2.25 0 003.75 21z" />
        </svg>
      )}
    </div>
  );
}
