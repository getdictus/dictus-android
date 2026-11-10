// Renders the Google Play listing images into fastlane/metadata/android/<locale>/images/.
//
//   npm ci && npx playwright install chromium && ./fetch-icons.sh   once
//   node render.mjs [--proofs <dir>] [--ribbon tiles]   every locale, plus one review board per locale in <dir>
//
// The icon is drawn from the app's own adaptive icon vectors, cut to the 72 dp a launcher shows.
import { chromium } from "playwright";
import sharp from "sharp";
import { existsSync, mkdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, "../..");
const METADATA = join(REPO, "fastlane/metadata/android");
const LOCALES = ["fr-FR", "en-US"];
const SHOT_W = 1440, SHOT_H = 2560, PANEL_W = 1320;
const option = (name) => (process.argv.includes(name) ? process.argv[process.argv.indexOf(name) + 1] : null);
const proofs = option("--proofs");
const ribbon = option("--ribbon") ?? "logos";
if (ribbon === "logos" && !existsSync(join(HERE, ".icons"))) throw new Error("App icons missing: run ./fetch-icons.sh");

// Play wants 24-bit images: no alpha channel.
const opaque = (buffer) => sharp(buffer).flatten({ background: "#ffffff" }).removeAlpha().png({ compressionLevel: 9 });

async function open(browser, viewport, scale, query) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: scale });
  page.on("requestfailed", (r) => { throw new Error(`Failed to load ${r.url()}`); });
  const url = pathToFileURL(join(HERE, "template.html"));
  url.search = query;
  await page.goto(url.href, { waitUntil: "load" });
  await page.evaluate(() => window.whenReady);
  return page;
}

async function screenshots(browser, locale, out) {
  const scale = SHOT_W / PANEL_W;
  const page = await open(browser, { width: PANEL_W * 5, height: 2347 }, scale, `?locale=${locale}&mode=screens&ribbon=${ribbon}`);
  const count = await page.evaluate(() => window.PANELS);
  const files = [];
  for (let i = 0; i < count; i++) {
    const png = await page.screenshot({ clip: { x: i * PANEL_W, y: 0, width: PANEL_W, height: SHOT_H / scale } });
    const file = join(out, `${i + 1}.png`);
    await opaque(await sharp(png).resize(SHOT_W, SHOT_H, { fit: "fill" }).toBuffer()).toFile(file);
    files.push(file);
  }
  await page.close();
  return files;
}

async function featureGraphic(browser, locale, file) {
  const page = await open(browser, { width: 1024, height: 500 }, 1, `?locale=${locale}&mode=feature`);
  await opaque(await page.screenshot({ clip: { x: 0, y: 0, width: 1024, height: 500 } })).toFile(file);
  await page.close();
}

async function icon(browser, file) {
  const drawable = (name) => readFileSync(join(REPO, "app/src/main/res/drawable", name), "utf8");
  const paths = [...drawable("ic_launcher_foreground.xml").matchAll(/android:pathData="([^"]+)"/g)].map((m) => m[1]);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#0D2040"/><stop offset="1" stop-color="#071020"/></linearGradient>
      <linearGradient id="bar" x1="50.29" y1="32.68" x2="57.79" y2="75.33" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#6BA3FF"/><stop offset="1" stop-color="#2563EB"/></linearGradient>
    </defs>
    <rect width="108" height="108" fill="url(#bg)"/>
    <path d="${paths[0]}" fill="#fff" fill-opacity="0.45"/>
    <path d="${paths[1]}" fill="url(#bar)"/>
    <path d="${paths[2]}" fill="#fff" fill-opacity="0.65"/>
  </svg>`;
  const page = await browser.newPage({ viewport: { width: 512, height: 512 } });
  await page.setContent(`<!doctype html><body style="margin:0">${svg}</body>`);
  await opaque(await page.screenshot()).toFile(file);
  await page.close();
}

async function board(files, feature, iconFile, out) {
  const H = 960, W = Math.round(H * SHOT_W / SHOT_H), GAP = 24;
  const fw = Math.round(1024 * 0.75), fh = Math.round(500 * 0.75);
  const size = { width: GAP + files.length * (W + GAP), height: H + 3 * GAP + fh };
  const composite = [];
  for (const [i, f] of files.entries()) composite.push({ input: await sharp(f).resize(W, H).toBuffer(), left: GAP + i * (W + GAP), top: GAP });
  composite.push({ input: await sharp(feature).resize(fw, fh).toBuffer(), left: GAP, top: H + 2 * GAP });
  composite.push({ input: await sharp(iconFile).resize(fh, fh).toBuffer(), left: GAP + fw + GAP, top: H + 2 * GAP });
  await sharp({ create: { ...size, channels: 3, background: "#1C1F26" } }).composite(composite).jpeg({ quality: 92 }).toFile(out);
}

const browser = await chromium.launch({ headless: true });
if (proofs) mkdirSync(proofs, { recursive: true });
for (const locale of LOCALES) {
  const images = join(METADATA, locale, "images");
  const phone = join(images, "phoneScreenshots");
  mkdirSync(phone, { recursive: true });
  const files = await screenshots(browser, locale, phone);
  const feature = join(images, "featureGraphic.png");
  await featureGraphic(browser, locale, feature);
  const iconFile = join(images, "icon.png");
  await icon(browser, iconFile);
  for (const f of [...files, feature, iconFile]) {
    const m = await sharp(f).metadata();
    console.log(`${f.slice(REPO.length + 1)} ${m.width}x${m.height} channels=${m.channels} alpha=${m.hasAlpha} ${(statSync(f).size / 1024).toFixed(0)} KB`);
  }
  if (proofs) await board(files, feature, iconFile, join(proofs, `planche-${locale}.jpg`));
}
await browser.close();
