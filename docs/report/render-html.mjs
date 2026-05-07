import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const reportDir = path.resolve(path.dirname(new URL(import.meta.url).pathname));
const fullMdPath = path.join(reportDir, "full.md");
const cssPath = path.join(reportDir, "gost.css");
const fullHtmlPath = path.join(reportDir, "full.html");
const pdfPath = path.join(reportDir, "HighLoad_Invest_report.pdf");

function escapeHtml(text) {
  return text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function metadataValue(metaBlock, key) {
  const match = metaBlock.match(new RegExp(`^${key}:\\s*['"]?(.+?)['"]?\\s*$`, "m"));
  return match ? match[1] : "";
}

function metadataList(metaBlock, key) {
  const lines = metaBlock.split("\n");
  const start = lines.findIndex((line) => line.trim() === `${key}:`);

  if (start < 0) {
    return [];
  }

  const values = [];
  for (const line of lines.slice(start + 1)) {
    const match = line.match(/^\s+-\s*['"]?(.+?)['"]?\s*$/);
    if (!match) {
      break;
    }
    values.push(match[1]);
  }

  return values;
}

function stripMetadata(markdown) {
  return markdown.replace(/^---\n[\s\S]*?\n---\n/, "");
}

function preprocessMarkdown(markdown) {
  return stripMetadata(markdown).replace(
    /!\[([^\]]+)\]\(([^)]+)\)\{ width=([0-9]+)% \}/g,
    (_match, alt, src, width) =>
      `<figure><img src="${src}" alt="${alt}" style="width:${width}%;"><figcaption>${alt}</figcaption></figure>`
  );
}

async function markdownToHtmlBody(markdown, tempDir) {
  const tempMdPath = path.join(tempDir, "full.tmp.md");
  const tempBodyPath = path.join(tempDir, "full.body.html");

  await writeFile(tempMdPath, preprocessMarkdown(markdown));
  await execFileAsync("npx", ["--yes", "marked", "--gfm", "-i", tempMdPath, "-o", tempBodyPath], {
    cwd: reportDir,
  });

  return readFile(tempBodyPath, "utf8");
}

function addHeadingIds(body) {
  const headings = [];

  const html = body.replace(/<h([1-3])>(.*?)<\/h\1>/g, (_match, level, text) => {
    const plainText = text.replace(/<[^>]+>/g, "");
    const id = `sec-${headings.length + 1}`;
    headings.push({ level: Number(level), text: plainText, id });
    return `<h${level} id="${id}">${text}</h${level}>`;
  });

  return { html, headings };
}

function renderToc(headings, pageNumbers = {}) {
  return headings
    .map((heading) => {
      const title = heading.level === 1 ? heading.text.toUpperCase() : heading.text;
      const page = pageNumbers[heading.id] ?? "";

      return `<a class="toc-row toc-level-${heading.level}" href="#${heading.id}">
<span class="toc-title">${escapeHtml(title)}</span>
<span class="toc-dots"></span>
<span class="toc-page">${escapeHtml(String(page))}</span>
</a>`;
    })
    .join("\n");
}

function renderHtml({ markdown, body, headings, pageNumbers = {} }) {
  const metaBlock = (markdown.match(/^---\n([\s\S]*?)\n---\n/) || [])[1] || "";
  const students = metadataList(metaBlock, "students");
  const fallbackAuthors = metadataList(metaBlock, "author");
  const css = renderHtml.css;

  const title = `<header id="title-block-header">
<div class="title-top">
<p>${escapeHtml(metadataValue(metaBlock, "university") || "Университет ИТМО")}</p>
<p>${escapeHtml(metadataValue(metaBlock, "faculty") || "Факультет программной инженерии и компьютерной техники")}</p>
</div>
<div class="title-main">
<p class="report-type">${escapeHtml(metadataValue(metaBlock, "report_type") || "Отчёт по групповому проекту")}</p>
<p class="course">${escapeHtml(metadataValue(metaBlock, "course") || "по курсу «Разработка мобильных приложений»")}</p>
<p class="project-title">${escapeHtml(metadataValue(metaBlock, "project_title") || "HighLoad Invest Ecosystem")}</p>
<p class="project-subtitle">${escapeHtml(metadataValue(metaBlock, "project_subtitle") || metadataValue(metaBlock, "pagetitle"))}</p>
</div>
<div class="title-people">
<table>
<tbody>
<tr><td>Студенты:</td><td>${(students.length > 0 ? students : fallbackAuthors).map(escapeHtml).join("<br>")}</td></tr>
<tr><td>Преподаватель:</td><td>${escapeHtml(metadataValue(metaBlock, "teacher") || "Ключев А. О.")}</td></tr>
</tbody>
</table>
</div>
<p class="date">${escapeHtml(metadataValue(metaBlock, "city") || "Санкт-Петербург")}<br>${escapeHtml(metadataValue(metaBlock, "year") || "2026")}</p>
</header>`;

  return `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(metadataValue(metaBlock, "pagetitle") || "HighLoad Invest report")}</title>
<style>
${css}
figure{break-inside:avoid;text-align:center;margin:1.2rem 0;}
figure img{max-width:100%;height:auto;}
figcaption{font-size:.95em;margin-top:.35rem;text-align:center;text-indent:0;}
#title-block-header p{text-indent:0;}
</style>
</head>
<body>
${title}
<nav id="TOC">${renderToc(headings, pageNumbers)}</nav>
${body}
</body>
</html>
`;
}

function normalizeText(text) {
  return text.replace(/\s+/g, " ").trim();
}

function headingNeedle(heading) {
  const text = normalizeText(heading.text);
  return heading.level === 1 ? text.toUpperCase() : text;
}

async function printPdf(htmlPath, targetPdfPath) {
  await execFileAsync(
    "chromium",
    [
      "--headless",
      "--no-sandbox",
      "--disable-gpu",
      "--no-pdf-header-footer",
      `--print-to-pdf=${targetPdfPath}`,
      `file://${htmlPath}`,
    ],
    { cwd: reportDir }
  );
}

async function extractPageNumbers(pdfFile, headings, tempDir) {
  const textPath = path.join(tempDir, "layout.txt");
  await execFileAsync("pdftotext", ["-layout", pdfFile, textPath], { cwd: reportDir });

  const pages = (await readFile(textPath, "utf8")).split("\f");
  const bodyStart = pages.findIndex((page) =>
    normalizeText(page).includes("Курсовая работа посвящена разработке")
  );
  const startPage = bodyStart >= 0 ? bodyStart : 0;
  const result = {};

  for (const heading of headings) {
    const needle = headingNeedle(heading);

    for (let pageIndex = startPage; pageIndex < pages.length; pageIndex += 1) {
      const found = pages[pageIndex]
        .split("\n")
        .map(normalizeText)
        .some((line) => line === needle);

      if (found) {
        result[heading.id] = pageIndex + 1;
        break;
      }
    }
  }

  return result;
}

function samePageNumbers(left, right, headings) {
  return headings.every((heading) => left[heading.id] === right[heading.id]);
}

const tempDir = await mkdtemp(path.join(reportDir, ".render-"));

try {
  const markdown = await readFile(fullMdPath, "utf8");
  renderHtml.css = await readFile(cssPath, "utf8");

  const rawBody = await markdownToHtmlBody(markdown, tempDir);
  const { html: body, headings } = addHeadingIds(rawBody);

  const firstPassHtml = path.join(tempDir, "first-pass.html");
  const firstPassPdf = path.join(tempDir, "first-pass.pdf");
  await writeFile(firstPassHtml, renderHtml({ markdown, body, headings }));
  await printPdf(firstPassHtml, firstPassPdf);

  let pageNumbers = await extractPageNumbers(firstPassPdf, headings, tempDir);

  for (let i = 0; i < 3; i += 1) {
    await writeFile(fullHtmlPath, renderHtml({ markdown, body, headings, pageNumbers }));
    await printPdf(fullHtmlPath, pdfPath);

    const nextPageNumbers = await extractPageNumbers(pdfPath, headings, tempDir);
    if (samePageNumbers(pageNumbers, nextPageNumbers, headings)) {
      break;
    }
    pageNumbers = nextPageNumbers;
  }

  await writeFile(fullHtmlPath, renderHtml({ markdown, body, headings, pageNumbers }));
  await printPdf(fullHtmlPath, pdfPath);

  console.log(`Report HTML written: ${path.relative(path.dirname(reportDir), fullHtmlPath)}`);
  console.log(`Report PDF written: ${path.relative(path.dirname(reportDir), pdfPath)}`);
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
