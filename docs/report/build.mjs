import { deflateSync } from "node:zlib";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const reportDir = path.join(root, "docs", "report");
const diagramsDir = path.join(reportDir, "diagrams");

const inputs = [
  "docs/report/01-title.md",
  "docs/report/02-main.md",
  "docs/report/99-conclusion.md",
  "docs/report/9A-references.md",
];

const essayInput = "docs/report/essay/essay.md";

function plantUmlUrl(source) {
  const encoded = deflateSync(Buffer.from(source, "utf8"))
    .toString("base64")
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");

  return `https://kroki.io/plantuml/svg/${encoded}`;
}

async function renderPlantUml(source, index) {
  const url = plantUmlUrl(source);
  const response = await fetch(url);

  if (!response.ok) {
    const details = await response.text();
    throw new Error(
      `PlantUML render failed for diagram ${index}: ${response.status} ${response.statusText}\n${details}\n\n${source}`
    );
  }

  const svg = await response.text();
  const filename = `diagram-${String(index).padStart(2, "0")}.svg`;
  await writeFile(path.join(diagramsDir, filename), svg);

  return `![Диаграмма ${index}](diagrams/${filename}){ width=100% }`;
}

async function renderDiagrams(markdown) {
  await rm(diagramsDir, { recursive: true, force: true });
  await mkdir(diagramsDir, { recursive: true });

  const blocks = [...markdown.matchAll(/```plantuml\s*\n([\s\S]*?)\n```/g)];
  let rendered = markdown;

  for (let i = blocks.length - 1; i >= 0; i -= 1) {
    const block = blocks[i];
    const replacement = await renderPlantUml(block[1], i + 1);
    rendered = `${rendered.slice(0, block.index)}${replacement}${rendered.slice(block.index + block[0].length)}`;
  }

  return rendered;
}

function renderEssayAppendix(source) {
  const essay = source.trim();
  const normalizedEssay = essay.replace(/^(#{1,5})\s+/gm, (_match, hashes) => `${hashes}# `);

  return `# Приложение А. Эссе\n\n${normalizedEssay}`.trimEnd();
}

const parts = [];

for (const input of inputs) {
  const text = await readFile(path.join(root, input), "utf8");
  parts.push(text.trimEnd());
}

const essay = await readFile(path.join(root, essayInput), "utf8");
parts.push(renderEssayAppendix(essay));

const combined = `${parts.join("\n\n")}\n`;
const rendered = await renderDiagrams(combined);

await writeFile(path.join(reportDir, "full.md"), rendered);

console.log(`Report markdown written: ${path.relative(root, path.join(reportDir, "full.md"))}`);
console.log(`Rendered ${[...combined.matchAll(/```plantuml\s*\n/g)].length} PlantUML diagrams`);
