export interface Segment {
  kind: 'text' | 'math';
  value: string;
  display: boolean;
}

/**
 * Splits a reply into prose and maths so the maths can be typeset.
 *
 * Delimiters: `\[ … \]` and `$$ … $$` display; `\( … \)` inline. A bare
 * `$…$` counts only when it is on one line, has no space just inside the
 * delimiters, and is not obviously money — which keeps "$5 and $10" as prose.
 */
export function splitMath(input: string): Segment[] {
  const segments: Segment[] = [];
  let text = '';
  let i = 0;

  const flushText = () => {
    if (text) segments.push({ kind: 'text', value: text, display: false });
    text = '';
  };

  while (i < input.length) {
    const displayOpen = matchAt(input, i, ['\\[', '$$']);
    if (displayOpen) {
      const close = displayOpen === '$$' ? '$$' : '\\]';
      const end = input.indexOf(close, i + displayOpen.length);
      if (end >= 0) {
        flushText();
        segments.push({
          kind: 'math',
          value: input.slice(i + displayOpen.length, end),
          display: true,
        });
        i = end + close.length;
        continue;
      }
    }

    const inlineOpen = matchAt(input, i, ['\\(', '$$']);
    if (inlineOpen === '\\(') {
      const end = input.indexOf('\\)', i + 2);
      if (end >= 0) {
        flushText();
        segments.push({ kind: 'math', value: input.slice(i + 2, end), display: false });
        i = end + 2;
        continue;
      }
    }

    if (input.startsWith('$', i) && isInlineDollar(input, i)) {
      const end = input.indexOf('$', i + 1);
      if (end > i + 1) {
        flushText();
        segments.push({ kind: 'math', value: input.slice(i + 1, end), display: false });
        i = end + 1;
        continue;
      }
    }

    text += input[i];
    i += 1;
  }

  flushText();
  return segments;
}

function matchAt(input: string, index: number, tokens: string[]): string | null {
  for (const token of tokens) {
    if (input.startsWith(token, index)) return token;
  }
  return null;
}

/** A conservative test for "$…$" being maths rather than money. */
function isInlineDollar(input: string, index: number): boolean {
  const next = input[index + 1];
  if (!next || next === ' ' || next === '\n') return false;

  const end = input.indexOf('$', index + 1);
  if (end < 0) return false;

  const inner = input.slice(index + 1, end);
  if (inner.includes('\n') || inner.endsWith(' ')) return false;
  // Must look mathematical: a backslash, an operator, or a caret/underscore.
  return /[\\^_{}=+\-*/]/.test(inner);
}
