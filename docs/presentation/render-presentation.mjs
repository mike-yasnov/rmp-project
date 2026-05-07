import { execFile } from "node:child_process";
import { access, mkdir } from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const presentationDir = path.resolve(path.dirname(new URL(import.meta.url).pathname));
const htmlPath = path.join(presentationDir, "presentation.html");
const pdfPath = path.join(presentationDir, "HighLoad_Invest_presentation.pdf");

async function ensureInputExists() {
  await access(htmlPath);
  await mkdir(presentationDir, { recursive: true });
}

async function printPdf() {
  await execFileAsync(
    "chromium",
    [
      "--headless",
      "--no-sandbox",
      "--disable-gpu",
      "--run-all-compositor-stages-before-draw",
      "--virtual-time-budget=1000",
      "--no-pdf-header-footer",
      "--window-size=1920,1080",
      `--print-to-pdf=${pdfPath}`,
      `file://${htmlPath}`,
    ],
    { cwd: presentationDir }
  );
}

await ensureInputExists();
await printPdf();

console.log(`HTML: ${path.relative(process.cwd(), htmlPath)}`);
console.log(`PDF:  ${path.relative(process.cwd(), pdfPath)}`);
