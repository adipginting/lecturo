# KaTeX

`katex.min.js`, `katex.min.css` and `fonts/` are the KaTeX distribution,
version **0.16.22**, copied verbatim from the sister mobile repository
(`lecturo/app/src/main/assets/katex/`) so both apps typeset formulas with the
same build, offline.

KaTeX is MIT licensed — <https://github.com/KaTeX/KaTeX>. The font files carry
the same licence.

`Services/MathsHtml.cs` builds the page that loads these files; nothing here is
modified, so updating KaTeX means replacing this directory wholesale.
