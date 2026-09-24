---
status: accepted
---

# Render documents natively instead of in a WebView

The reading surface is built from native Avalonia controls — selectable Markdown
in a `ScrollViewer` for EPUB and text PDFs, page images for scans — rather than
from HTML rendered in a WebView. A WebView survives only for the rare PDF that
has neither a text layer nor extractable page images. Both GTK WebView adapters
(the native child, and the experimental offscreen one) proved unreliable for
wheel and keyboard input under Wayland/XWayland while spamming `Gdk-WARNING:
Event with type 11 not holding a GdkDevice`; native controls also made text
selection and screenshot capture tractable, which the WebView never did.

## Considered options

- **WebView + pdf.js / EPUB.js** (tried first): faithful page layout and the
  obvious approach, but scrolling and keys were broken under Wayland, and
  selection, the selection pill, and drag-to-crop screenshots all had to cross
  into a document the app does not control.
- **Native Markdown surface** (chosen): reliable scroll and selection, and a
  surface the app can render to a bitmap for screenshots.

## Consequences

- Text PDFs are **reflowed, not reproduced**: a page becomes Markdown, so the
  original layout and page images are gone. One chapter equals one page.
- Scans and text both work, but a PDF with neither a text layer nor extractable
  images still hands off to WebKitGTK's own viewer, where selection and
  screenshots do not work.
- EPUB is unzipped and converted to Markdown, so the original HTML's styling,
  layout, and images are dropped.
- Fonts must exist on disk for the WebView fallback, which is why
  `Assets/Fonts/*.ttf` is copied to the output directory as well as embedded.
