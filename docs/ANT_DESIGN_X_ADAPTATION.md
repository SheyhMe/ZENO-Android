# Ant Design X ideas for ZENO

Reviewed source: https://github.com/ant-design/x

## Compatibility decision

Ant Design X is a React/TypeScript component library, while ZENO's current client is a native Kotlin Android application. Do not add the React package directly to the APK or introduce a WebView solely to reuse its components. Recreate the relevant interaction patterns with native Android views so ZENO remains lightweight, accessible, responsive, and consistent with Android permission and lifecycle behavior.

The package metadata identifies `@ant-design/x` as MIT licensed. If source code or assets are copied in a future change, retain the upstream copyright and MIT license notice in the distributed third-party notices. This change adopts design patterns only; it does not vendor Ant Design X source.

## Patterns worth adapting

| Ant Design X concept | Native ZENO implementation |
| --- | --- |
| Conversations / message list | RecyclerView with distinct user, assistant, system and tool-result rows |
| Bubble | Accessible chat bubbles with timestamps and delivery/error states |
| Sender | Composer with send, voice, attachment and stop-generation controls |
| Prompts | Suggested prompt chips for common tasks |
| Markdown | Safe Markdown renderer for headings, lists, links and code blocks |
| CodeHighlighter | Copyable code blocks with language labels |
| Thought / tool status | Compact status row such as “Searching Google…” or “Opening app…”; never expose hidden chain-of-thought |
| Attachments | Explicit user-selected files only, with preview and remove controls |
| Agent / task cards | Structured, validated action cards with confirmation before side effects |
| Theme tokens | ZENO day/night color tokens, spacing, radius and typography |

## ZENO visual direction

- Keep the existing restrained dark/light palette and soft accent glow; avoid constant high-contrast animation.
- Make the chat the primary workflow, with readable message width and clear speaker separation.
- Show tool activity and failures honestly; never display “done” before Android confirms the action.
- Keep confirmation controls visible for messages, calendar changes, repository publishing and other external side effects.
- Use native system UI for permissions and sensitive operations.

## Implementation sequence

1. Replace the single chat TextView with a RecyclerView and typed ChatMessage rows.
2. Add streaming-safe Markdown rendering and copyable code blocks.
3. Add prompt suggestions and an attachment picker based on Android's system document picker.
4. Add structured tool/action cards with explicit confirmation.
5. Add UI tests for light/dark theme, accessibility labels, long messages, keyboard and rotation.

Source reference: https://github.com/ant-design/x
