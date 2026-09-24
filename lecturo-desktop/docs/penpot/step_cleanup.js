const ROOT = penpot.currentPage.root;
const doomed = ROOT.children.filter(c => c.type === "board" && /\(desktop\)$/.test(c.name));
doomed.forEach(b => b.remove());
return "removed:" + doomed.length;
