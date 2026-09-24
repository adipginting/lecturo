import { splitMath } from './math';

describe('splitMath', () => {
  it('leaves plain prose alone', () => {
    const segments = splitMath('just a sentence');
    expect(segments).toEqual([{ kind: 'text', value: 'just a sentence', display: false }]);
  });

  it('splits inline and display maths', () => {
    const segments = splitMath('see \\(x^2\\) and \\[y = mx + b\\] done');
    expect(segments.map((s) => s.kind)).toEqual(['text', 'math', 'text', 'math', 'text']);
    expect(segments[1]).toEqual({ kind: 'math', value: 'x^2', display: false });
    expect(segments[3]).toEqual({ kind: 'math', value: 'y = mx + b', display: true });
  });

  it('keeps money as prose', () => {
    const segments = splitMath('it costs $5 and $10 today');
    expect(segments).toHaveLength(1);
    expect(segments[0].kind).toBe('text');
  });

  it('accepts an inline dollar formula', () => {
    const segments = splitMath('so $x_i = 2$ holds');
    expect(segments.some((s) => s.kind === 'math' && s.value === 'x_i = 2')).toBe(true);
  });
});
