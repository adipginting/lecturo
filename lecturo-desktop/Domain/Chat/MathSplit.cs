namespace Lecturo.Domain.Chat;

/// <summary>A stretch of a model's reply: either prose or a formula.</summary>
public abstract record MathPart
{
    public sealed record Text(string Value) : MathPart;

    public sealed record Math(string Tex, bool Display) : MathPart;
}

/// <summary>
/// Splits Markdown into prose and maths. Display delimiters (<c>$$…$$</c>,
/// <c>\[…\]</c>) are unambiguous; <c>\(…\)</c> is inline. A <c>$…$</c> pair counts
/// only when it sits on one line with no space just inside it and carries a
/// backslash or a maths-only character, which keeps prices out of the renderer.
/// Backtick code, fenced or inline, is never maths.
/// </summary>
public static class MathSplit
{
    /// <summary>Whether a reply carries anything that wants typesetting.</summary>
    public static bool HasMath(string? markdown) =>
        !string.IsNullOrEmpty(markdown) && Split(markdown).Any(part => part is MathPart.Math);

    public static IReadOnlyList<MathPart> Split(string markdown)
    {
        var parts = new List<MathPart>();
        var text = new System.Text.StringBuilder();

        void Flush()
        {
            if (text.Length > 0)
            {
                parts.Add(new MathPart.Text(text.ToString()));
                text.Clear();
            }
        }

        var i = 0;
        while (i < markdown.Length)
        {
            var c = markdown[i];

            if (c == '`')
            {
                var run = RunLength(markdown, i, '`');
                var close = FindRun(markdown, i + run, '`', run);
                if (run >= 3 || close >= 0)
                {
                    var end = close < 0 ? markdown.Length : close + run;
                    text.Append(markdown, i, end - i);
                    i = end;
                }
                else
                {
                    text.Append(c);
                    i++;
                }
                continue;
            }

            if (c == '\\' && i + 1 < markdown.Length)
            {
                var next = markdown[i + 1];
                if (next is '(' or '[')
                {
                    var closeTag = next == '(' ? "\\)" : "\\]";
                    var end = markdown.IndexOf(closeTag, i + 2, StringComparison.Ordinal);
                    if (end >= 0)
                    {
                        Flush();
                        parts.Add(new MathPart.Math(markdown[(i + 2)..end].Trim(), Display: next == '['));
                        i = end + 2;
                        continue;
                    }
                }
                text.Append(c);
                i++;
                continue;
            }

            if (c == '$')
            {
                if (i + 1 < markdown.Length && markdown[i + 1] == '$')
                {
                    var end = markdown.IndexOf("$$", i + 2, StringComparison.Ordinal);
                    if (end >= 0)
                    {
                        Flush();
                        parts.Add(new MathPart.Math(markdown[(i + 2)..end].Trim(), Display: true));
                        i = end + 2;
                        continue;
                    }
                }

                var lineEnd = markdown.IndexOf('\n', i);
                var endDollar = markdown.IndexOf('$', i + 1);
                if (endDollar >= 0 && (lineEnd < 0 || endDollar < lineEnd))
                {
                    var body = markdown[(i + 1)..endDollar];
                    if (IsInlineMath(body))
                    {
                        Flush();
                        parts.Add(new MathPart.Math(body, Display: false));
                        i = endDollar + 1;
                        continue;
                    }
                }
            }

            text.Append(c);
            i++;
        }

        Flush();
        return parts;
    }

    /// <summary>
    /// A single <c>$…$</c> is the one ambiguous delimiter, so it asks for a
    /// backslash or a maths-only character inside before it counts.
    /// </summary>
    private static bool IsInlineMath(string body) =>
        body.Length > 0
        && !char.IsWhiteSpace(body[0])
        && !char.IsWhiteSpace(body[^1])
        && body.Any(ch => ch == '\\' || ch is '^' or '_' or '=' or '+' or '/');

    private static int RunLength(string text, int from, char ch)
    {
        var i = from;
        while (i < text.Length && text[i] == ch)
            i++;
        return i - from;
    }

    private static int FindRun(string text, int from, char ch, int min)
    {
        var i = from;
        while (i < text.Length)
        {
            if (text[i] == ch)
            {
                var run = RunLength(text, i, ch);
                if (run >= min)
                    return i;
                i += run;
            }
            else
            {
                i++;
            }
        }
        return -1;
    }
}
