// Desktop UI boards for the Lecturo Penpot file.
//
// Run it through the Penpot MCP plugin (execute_code), or with
//   python3 docs/penpot/create_desktop_boards.py
// Idempotent: every board whose name ends with "(desktop)" is removed first.
//
// Fonts: the file only has "Plus Jakarta Sans" Regular, so the mock uses the
// file's "Inter Tight" (full weights, same as the mobile boards) for UI text and
// "Source Serif 4" for the reading surface. The shipped Avalonia app uses Plus
// Jakarta Sans.

const ROOT = penpot.currentPage.root;

// ---- palette (sampled from the existing Lecturo UI boards) -------------------
const C = {
  surface: "#FFFBFE", surfaceLow: "#F7F2FA", surfaceContainer: "#F3EDF7", surfaceHigh: "#ECE6F0",
  onSurface: "#1C1B1F", onSurfaceVariant: "#49454F", primary: "#6650A4",
  primaryContainer: "#EADDFF", onPrimaryContainer: "#21005D",
  secondaryContainer: "#E8DEF8", onSecondaryContainer: "#1D192B",
  outline: "#79747E", outlineVariant: "#CAC4D0", selection: "#D0BCFF", white: "#FFFFFF",
  covers: ["#FFD8E4", "#E8DEF8", "#E6E0E9", "#EADDFF"],
  coverFg: ["#31111D", "#1D192B", "#1C1B1F", "#21005D"],
};
const UI = "Inter Tight";
const READ = "Source Serif 4";
const ICONS = {
  book: "M18,2H6C4.9,2 4,2.9 4,4V20C4,21.1 4.9,22 6,22H18C19.1,22 20,21.1 20,20V4C20,2.9 19.1,2 18,2ZM6,4H11V12L8.5,10.5L6,12V4Z",
  bookmark: "M17,3H7C5.9,3 5,3.9 5,5V21L12,18L19,21V5C19,3.9 18.1,3 17,3Z",
  chat: "M20,2H4C2.9,2 2,2.9 2,4V22L6,18H20C21.1,18 22,17.1 22,16V4C22,2.9 21.1,2 20,2Z",
  settings: "M19.14,12.94C19.18,12.64 19.2,12.33 19.2,12C19.2,11.68 19.18,11.36 19.13,11.06L21.16,9.48C21.34,9.34 21.39,9.07 21.28,8.87L19.36,5.55C19.24,5.33 18.99,5.26 18.77,5.33L16.38,6.29C15.88,5.91 15.35,5.59 14.76,5.35L14.4,2.81C14.36,2.57 14.16,2.4 13.92,2.4H10.08C9.84,2.4 9.65,2.57 9.61,2.81L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33C5.02,5.25 4.77,5.33 4.65,5.55L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48L4.89,11.06C4.84,11.36 4.8,11.69 4.8,12C4.8,12.31 4.82,12.64 4.87,12.94L2.84,14.52C2.66,14.66 2.61,14.93 2.72,15.13L4.64,18.45C4.76,18.67 5.01,18.74 5.23,18.67L7.62,17.71C8.12,18.09 8.65,18.41 9.24,18.65L9.6,21.19C9.65,21.43 9.84,21.6 10.08,21.6H13.92C14.16,21.6 14.36,21.43 14.4,21.19L14.76,18.65C15.35,18.41 15.88,18.09 16.38,17.71L18.77,18.67C18.99,18.75 19.24,18.67 19.36,18.45L21.28,15.13C21.39,14.91 21.34,14.66 21.16,14.52L19.14,12.94ZM12,15.6C10.02,15.6 8.4,13.98 8.4,12C8.4,10.02 10.02,8.4 12,8.4C13.98,8.4 15.6,10.02 15.6,12C15.6,13.98 13.98,15.6 12,15.6Z",
  trash: "M6,19C6,20.1 6.9,21 8,21H16C17.1,21 18,20.1 18,19V7H6V19ZM19,4H15.5L14.5,3H9.5L8.5,4H5V6H19V4Z",
  edit: "M3,17.25V21H6.75L17.81,9.94L14.06,6.19L3,17.25ZM20.71,7.04C21.1,6.65 21.1,6.02 20.71,5.63L18.37,3.29C17.98,2.9 17.35,2.9 16.96,3.29L15.13,5.12L18.88,8.87L20.71,7.04Z",
  add: "M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z",
  back: "M20,11H7.83L13.42,5.41L12,4L4,12L12,20L13.41,18.59L7.83,13H20V11Z",
  send: "M2.01,21L23,12L2.01,3L2,10L17,12L2,14L2.01,21Z",
  download: "M5,20H19V18H5V20ZM12,15L17,10H14V4H10V10H7L12,15Z",
  expandMore: "M16.59,8.59L12,13.17L7.41,8.59L6,10L12,16L18,10L16.59,8.59Z",
};

const fill = (c) => [{ fillColor: c, fillOpacity: 1 }];
const ox = (parent) => (parent && typeof parent.x === "number" ? parent.x : 0);
const oy = (parent) => (parent && typeof parent.y === "number" ? parent.y : 0);

const rect = (parent, x, y, w, h, color, r, stroke, sw) => {
  const s = penpot.createRectangle();
  parent.appendChild(s);
  s.resize(w, h);
  s.x = ox(parent) + x; s.y = oy(parent) + y;
  s.fills = color ? fill(color) : [];
  if (r) s.borderRadius = r;
  if (stroke) s.strokes = [{ strokeColor: stroke, strokeOpacity: 1, strokeWidth: sw || 1 }];
  return s;
};

const text = (parent, x, y, opts) => {
  const o = Object.assign(
    { size: 14, weight: "400", color: C.onSurface, font: UI, align: "left", lineHeight: null, width: null, wrap: false },
    opts);
  const t = penpot.createText(o.chars);
  parent.appendChild(t);
  t.fontFamily = o.font;
  t.fontWeight = o.weight;
  t.fontSize = String(o.size);
  t.fills = fill(o.color);
  if (o.align) t.align = o.align;
  if (o.lineHeight) t.lineHeight = String(o.lineHeight);
  if (o.width != null) { t.resize(o.width, t.height); t.growType = o.wrap ? "auto-height" : "fixed"; }
  else { t.growType = "auto-width"; }
  t.x = ox(parent) + x; t.y = oy(parent) + y;
  return t;
};

const icon = (parent, x, y, size, d, color) => {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="' + size +
    '" height="' + size + '"><path d="' + d + '" fill="' + color + '"/></svg>';
  const g = penpot.createShapeFromSvg(svg);
  if (!g) return null;
  parent.appendChild(g);
  g.resize(size, size);
  g.x = ox(parent) + x; g.y = oy(parent) + y;
  return g;
};

const board = (name, x, y, w, h, color) => {
  const b = penpot.createBoard();
  ROOT.appendChild(b);
  b.name = name;
  b.resize(w, h);
  b.x = x; b.y = y;
  b.fills = fill(color || C.surface);
  return b;
};

const ellipse = (parent, x, y, w, h, color, stroke, sw) => {
  const e = penpot.createEllipse();
  parent.appendChild(e);
  e.resize(w, h);
  e.x = ox(parent) + x; e.y = oy(parent) + y;
  e.fills = color ? fill(color) : [];
  if (stroke) e.strokes = [{ strokeColor: stroke, strokeOpacity: 1, strokeWidth: sw || 1 }];
  return e;
};

const pill = (parent, x, y, w, h, label, bg, fg) => {
  rect(parent, x, y, w, h, bg, h / 2);
  text(parent, x, y + (h - 17) / 2, { chars: label, size: 14, weight: "600", color: fg, align: "center", width: w });
};

const field = (parent, x, y, w, label, value) => {
  rect(parent, x, y, w, 56, C.surface, 4, C.outline, 1);
  text(parent, x + 14, y + 9, { chars: label, size: 12, color: C.primary });
  text(parent, x + 14, y + 28, { chars: value, size: 14 });
};

const navRail = (b, selected) => {
  const items = [["Library", "book", 120], ["Saved", "bookmark", 172], ["Chats", "chat", 224], ["Settings", "settings", 276]];
  rect(b, 0, 0, 248, 900, C.surfaceLow);
  rect(b, 247, 0, 1, 900, C.outlineVariant);
  text(b, 24, 28, { chars: "Lecturo", size: 24, weight: "600" });
  text(b, 24, 62, { chars: "Read · Save · Ask", size: 12, color: C.onSurfaceVariant });
  for (const [key, glyph, iy] of items) {
    const sel = key === selected;
    if (sel) rect(b, 8, iy, 232, 44, C.secondaryContainer, 22);
    const fg = sel ? C.onSecondaryContainer : C.onSurfaceVariant;
    icon(b, 24, iy + 12, 20, ICONS[glyph], fg);
    text(b, 60, iy + 13, { chars: key, size: 14, weight: "500", color: fg });
  }
  rect(b, 16, 820, 216, 64, C.surfaceContainer, 12, C.outlineVariant, 1);
  text(b, 30, 832, { chars: "CHAT PROVIDER", size: 11, color: C.onSurfaceVariant });
  text(b, 30, 850, { chars: "DeepSeek", size: 14, weight: "500" });
};

const docCard = (b, x, y, title, meta, initial, coverIndex, w) => {
  w = w || 546;
  rect(b, x, y, w, 88, C.surfaceLow, 12);
  rect(b, x + 16, y + 16, 40, 56, C.covers[coverIndex], 4);
  text(b, x + 16, y + 34, { chars: initial, size: 20, weight: "500", color: C.coverFg[coverIndex], align: "center", width: 40 });
  text(b, x + 72, y + 20, { chars: title, size: 16, weight: "500" });
  text(b, x + 72, y + 46, { chars: meta, size: 12, color: C.onSurfaceVariant });
  icon(b, x + w - 34, y + 35, 18, ICONS.trash, C.onSurfaceVariant);
};

const fab = (b, x, y) => { rect(b, x, y, 56, 56, C.primaryContainer, 16); icon(b, x + 16, y + 16, 24, ICONS.add, C.onPrimaryContainer); };

// ============================ D1 · Library =================================
const d1 = board("D1 · Library (desktop)", 160, 2200, 1440, 900);
navRail(d1, "Library");
text(d1, 284, 28, { chars: "Library", size: 22 });
rect(d1, 284, 80, 440, 48, C.surface, 4, C.outline, 1);
text(d1, 300, 95, { chars: "https://example.com/book.epub", size: 14, color: C.onSurfaceVariant });
pill(d1, 740, 80, 116, 48, "Download", C.secondaryContainer, C.onSecondaryContainer);
const tabW = [374, 375, 375];
const tabX = [284, 658, 1033];
["Local", "Calibre", "Zotero"].forEach((name, i) => {
  text(d1, tabX[i], 150, { chars: name, size: 14, weight: "500", color: i === 0 ? C.primary : C.onSurfaceVariant, align: "center", width: tabW[i] });
});
rect(d1, 284, 192, 374, 3, C.primary);
rect(d1, 284, 195, 1124, 1, C.outlineVariant);
docCard(d1, 284, 224, "Inference Engineering", "EPUB · chapter 3", "I", 0);
docCard(d1, 846, 224, "Edmund Burke: Reflections", "EPUB · chapter 1", "E", 1);
docCard(d1, 284, 328, "multipage", "PDF · page 15", "M", 2);
fab(d1, 1344, 812);

// ============================ D2 · Reader ==================================
const d2 = board("D2 · Reader (desktop)", 1720, 2200, 1440, 900);
navRail(d2, "Library");
const readerChrome = (b, topW) => {
  rect(b, 248, 0, topW, 900, C.white);
  rect(b, 248, 0, topW, 64, C.surface);
  rect(b, 248, 63, topW, 1, C.outlineVariant);
  icon(b, 264, 22, 20, ICONS.back, C.onSurface);
  text(b, 296, 12, { chars: "marcus-aurelius_meditations_george-long", size: 15, weight: "500", width: 380 });
  text(b, 296, 38, { chars: "Book II", size: 11, color: C.onSurfaceVariant });
  icon(b, 248 + topW - 128, 22, 20, ICONS.bookmark, C.onSurfaceVariant);
  icon(b, 248 + topW - 92, 22, 20, ICONS.chat, C.onSurfaceVariant);
};
readerChrome(d2, 1192);
const colX = 514;
const colW = 660;
const body1 = "Begin the morning by saying to thyself, I shall meet with the busybody, the ungrateful, arrogant, deceitful, envious, unsocial. All these things happen to them by reason of their ignorance of what is good and evil. But I who have seen the nature of the good that it is beautiful, and of the bad that it is ugly, and the nature of him who does wrong, that it is akin to me, not only of the same blood or seed, but that it participates in the same intelligence and the same portion of the divinity.";
const body2 = "To act against one another then is contrary to nature; and it is acting against one another to be vexed and to turn away.";
const body3 = "Whatever this is that I am, it is a little flesh and breath, and the ruling part. Throw away thy books; no longer distract thyself: it is not allowed; but as if thou wast now dying, despise the flesh; it is blood and bones and a network, a contexture of nerves, veins, and arteries.";
text(d2, colX, 112, { chars: "Book II", size: 26, weight: "700", font: READ, align: "center", width: colW });
text(d2, colX, 162, { chars: body1, size: 18, font: READ, lineHeight: "1.65", width: colW, wrap: true });
text(d2, colX, 470, { chars: body2, size: 18, font: READ, lineHeight: "1.65", width: colW, wrap: true });
text(d2, colX, 580, { chars: body3, size: 18, font: READ, lineHeight: "1.65", width: colW, wrap: true });
rect(d2, 248, 820, 1192, 80, C.surface);
rect(d2, 248, 820, 1192, 1, C.outlineVariant);
pill(d2, 284, 840, 180, 40, "Previous", C.secondaryContainer, C.onSecondaryContainer);
pill(d2, 1228, 840, 180, 40, "Next", C.secondaryContainer, C.onSecondaryContainer);
rect(d2, 674, 840, 320, 40, C.secondaryContainer, 20);
text(d2, 674, 851, { chars: "Book II", size: 14, weight: "600", color: C.onSecondaryContainer, align: "center", width: 320 });
icon(d2, 960, 852, 16, ICONS.expandMore, C.onSecondaryContainer);

// ============================ D3 · Reader · Chat ===========================
const d3 = board("D3 · Reader · Chat (desktop)", 3280, 2200, 1440, 900);
navRail(d3, "Library");
readerChrome(d3, 792);
const ccx = 344;
const ccw = 600;
text(d3, ccx, 112, { chars: "Book II", size: 26, weight: "700", font: READ, align: "center", width: ccw });
text(d3, ccx, 162, { chars: body1, size: 16, font: READ, lineHeight: "1.6", width: ccw, wrap: true });
text(d3, ccx, 470, { chars: body2, size: 16, font: READ, lineHeight: "1.6", width: ccw, wrap: true });
rect(d3, 1040, 0, 400, 900, C.surface);
rect(d3, 1040, 0, 1, 900, C.outlineVariant);
icon(d3, 1056, 20, 16, ICONS.back, C.onSurfaceVariant);
text(d3, 1090, 14, { chars: "New chat", size: 16, weight: "500", width: 150 });
text(d3, 1090, 40, { chars: "sample · chapter 1", size: 11, color: C.onSurfaceVariant, width: 150 });
text(d3, 1250, 22, { chars: "~28 / 8,000", size: 11, color: C.onSurfaceVariant });
text(d3, 1350, 20, { chars: "DeepSeek", size: 12, weight: "500", color: C.primary });
rect(d3, 1056, 84, 368, 124, C.secondaryContainer, 12);
text(d3, 1070, 96, { chars: "Original excerpt", size: 12, weight: "500", color: C.onSecondaryContainer });
text(d3, 1070, 118, { chars: "Chapter One paragraph 3: the quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs.", size: 14, color: C.onSecondaryContainer, lineHeight: "1.35", width: 340, wrap: true });
text(d3, 1070, 184, { chars: "sample · chapter 1", size: 11, color: C.onSurfaceVariant });
const chip = (x, w, label) => {
  rect(d3, x, 224, w, 32, null, 8, C.outlineVariant, 1);
  text(d3, x, 233, { chars: label, size: 12, color: C.onSurfaceVariant, align: "center", width: w });
};
chip(1056, 96, "Summarize");
chip(1160, 118, "Explain simply");
chip(1286, 98, "Translate");
rect(d3, 1040, 780, 400, 1, C.outlineVariant);
rect(d3, 1056, 800, 300, 56, C.surface, 4, C.outline, 1);
text(d3, 1070, 818, { chars: "Ask about this excerpt", size: 12, color: C.primary });
icon(d3, 1372, 818, 20, ICONS.send, C.primary);

return ROOT.children.filter(c => c.type === "board" && /\(desktop\)$/.test(c.name)).map(c => c.name + ":" + (c.children || []).length);
