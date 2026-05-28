/**
 * 固定右下角的机器人吉祥物，点击展开 AI 对话页面。
 */
export function RobotLogo() {
  return (
    <button type="button" className="robot-fab group" aria-label="打开 AI 对话" title="ask assistant">
      <span className="robot-fab-face">
        <span className="robot-fab-eyes">
          <span className="robot-fab-eye robot-fab-eye--left" />
          <span className="robot-fab-eye robot-fab-eye--right" />
        </span>
        <span className="robot-fab-mouth" />
      </span>
    </button>
  );
}
