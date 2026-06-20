import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

type Props = {
  content: string;
  contentBlocks?: string | null;
};

type AiContentBlock =
  | { type: 'text'; text?: string }
  | { type: 'thinking'; thinking?: string; signature?: string }
  | { type: 'redacted_thinking'; data?: string }
  | { type: 'tool_use'; id?: string; name?: string; input?: unknown }
  | { type: 'tool_result'; tool_use_id?: string; content?: unknown };

/**
 * 渲染 AI 回复 Markdown，并让其中的链接统一在新页面打开。
 */
export function MarkdownMessage({ content, contentBlocks }: Props) {
  const blocks = parseBlocks(contentBlocks);
  if (blocks.length > 0) {
    const text = blocks
      .filter((block): block is { type: 'text'; text?: string } => block.type === 'text')
      .map(block => block.text ?? '')
      .filter(Boolean)
      .join('\n\n')
      .trim();
    const thinkingBlocks = blocks.filter(block => block.type === 'thinking' || block.type === 'redacted_thinking');
    const toolBlocks = blocks.filter(block => block.type === 'tool_use' || block.type === 'tool_result');

    return (
      <div className="space-y-2">
        <MarkdownOnly content={text || content} />
        {thinkingBlocks.length > 0 && (
          <details className="rounded-md border px-2 py-1 text-[12px]" style={{ borderColor: 'var(--border)', color: 'var(--text-muted)' }}>
            <summary className="cursor-pointer select-none">思考过程</summary>
            <div className="mt-1 whitespace-pre-wrap leading-relaxed">
              {thinkingBlocks.map((block, index) => (
                <div key={index}>
                  {block.type === 'thinking' ? block.thinking : '[redacted_thinking]'}
                </div>
              ))}
            </div>
          </details>
        )}
        {toolBlocks.length > 0 && (
          <details className="rounded-md border px-2 py-1 text-[12px]" style={{ borderColor: 'var(--border)', color: 'var(--text-muted)' }}>
            <summary className="cursor-pointer select-none">工具调用</summary>
            <pre className="mt-1 overflow-auto whitespace-pre-wrap">{JSON.stringify(toolBlocks, null, 2)}</pre>
          </details>
        )}
      </div>
    );
  }

  return <MarkdownOnly content={content} />;
}

function MarkdownOnly({ content }: { content: string }) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        a: ({ href, children, ...props }) => (
          <a {...props} href={href} target="_blank" rel="noopener noreferrer">
            {children}
          </a>
        ),
      }}
    >
      {content}
    </ReactMarkdown>
  );
}

function parseBlocks(value?: string | null): AiContentBlock[] {
  if (!value || !value.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.filter(block => block && typeof block === 'object' && typeof block.type === 'string')
      : [];
  } catch {
    return [];
  }
}
