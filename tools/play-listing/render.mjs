// Renders the Google Play listing images into fastlane/metadata/android/<locale>/images/.
//
//   npm ci && npx playwright install chromium && ./fetch-icons.sh   once
//   node render.mjs [--proofs <dir>] [--ribbon tiles]   every locale, plus one review board per locale in <dir>
//   node render.mjs --only icon                         the Play icon and the legacy launcher PNGs only
//
// The icon is drawn from the app's own adaptive icon vectors, cut to the 72 dp a launcher shows.
// The same drawing gives the legacy mipmap-*/ic_launcher.png.
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
const only = option("--only");
if (only && only !== "icon") throw new Error(`Unknown --only ${only}: the only one is icon`);
if (!only && ribbon === "logos" && !existsSync(join(HERE, ".icons"))) throw new Error("App icons missing: run ./fetch-icons.sh");

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

// An Android colour (#RRGGBB or #AARRGGBB) as an SVG colour and opacity.
function androidColor(value) {
  const hex = value.slice(1);
  return hex.length === 8 ? { color: `#${hex.slice(2)}`, opacity: parseInt(hex.slice(0, 2), 16) / 255 } : { color: `#${hex}`, opacity: 1 };
}

// The paths of a vector drawable as SVG, with their solid or linear gradient fills. Reading the
// launcher's own layers keeps the listing icon in step with the app: no coordinate is copied.
function vectorToSvg(name) {
  const xml = readFileSync(join(REPO, "app/src/main/res/drawable", name), "utf8");
  const attr = (source, key) => source.match(new RegExp(`android:${key}="([^"]+)"`))?.[1];
  const defs = [];
  const paths = [...xml.matchAll(/<path\b([^>]*?)(?:\/>|>([\s\S]*?)<\/path>)/g)].map(([, attrs, body = ""], i) => {
    const alpha = attr(attrs, "fillAlpha") ?? "1";
    const solid = attr(attrs, "fillColor");
    if (solid) {
      const { color, opacity } = androidColor(solid);
      return `<path d="${attr(attrs, "pathData")}" fill="${color}" fill-opacity="${opacity * alpha}"/>`;
    }
    const gradient = body.match(/<gradient\b([^>]*)>([\s\S]*?)<\/gradient>/);
    if (!gradient || attr(gradient[1], "type") !== "linear") throw new Error(`${name}: path ${i} has no solid or linear fill`);
    const id = `${name.replace(".xml", "")}-${i}`;
    const stops = [...gradient[2].matchAll(/<item\b([^>]*)\/>/g)].map(([, item]) => {
      const { color, opacity } = androidColor(attr(item, "color"));
      return `<stop offset="${attr(item, "offset")}" stop-color="${color}" stop-opacity="${opacity}"/>`;
    });
    const g = gradient[1];
    defs.push(`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${attr(g, "startX")}" y1="${attr(g, "startY")}" x2="${attr(g, "endX")}" y2="${attr(g, "endY")}">${stops.join("")}</linearGradient>`);
    return `<path d="${attr(attrs, "pathData")}" fill="url(#${id})" fill-opacity="${alpha}"/>`;
  });
  return { defs: defs.join(""), paths: paths.join("") };
}

// The adaptive icon as a launcher shows it: the middle 72 dp of the 108 dp layers. That square
// is the iOS tile (80 units in `assets/brand/appicon-dark.svg`), so `clipRadius` 16.2 is its
// 18-unit corner (22.5 % of the side).
async function renderIcon(browser, size, { clipRadius = 0 } = {}) {
  const layers = ["ic_launcher_background.xml", "ic_launcher_foreground.xml"].map(vectorToSvg);
  const clip = clipRadius ? `<clipPath id="tile"><rect x="18" y="18" width="72" height="72" rx="${clipRadius}"/></clipPath>` : "";
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="18 18 72 72">
    <defs>${clip}${layers.map((l) => l.defs).join("")}</defs>
    <g${clipRadius ? ' clip-path="url(#tile)"' : ""}>${layers.map((l) => l.paths).join("")}</g>
  </svg>`;
  const page = await browser.newPage({ viewport: { width: size, height: size } });
  await page.setContent(`<!doctype html><body style="margin:0;background:transparent">${svg}</body>`);
  const png = await page.screenshot({ omitBackground: true });
  await page.close();
  return png;
}

// The Play icon: full bleed and square, Google applies its own mask.
async function icon(browser, file) {
  await opaque(await renderIcon(browser, 512)).toFile(file);
}

// The launcher icon of Android 7.1 and older, where adaptive icons do not exist: the iOS tile,
// corners transparent. minSdk 29 never shows them; they are kept in step for completeness.
const LEGACY_LAUNCHER = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
async function legacyLauncherIcons(browser) {
  for (const [density, size] of Object.entries(LEGACY_LAUNCHER)) {
    const file = join(REPO, `app/src/main/res/mipmap-${density}/ic_launcher.png`);
    await sharp(await renderIcon(browser, size, { clipRadius: 16.2 })).png({ compressionLevel: 9 }).toFile(file);
    console.log(`${file.slice(REPO.length + 1)} ${size}x${size}`);
  }
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
if (only === "icon") {
  for (const locale of LOCALES) await icon(browser, join(METADATA, locale, "images", "icon.png"));
  await legacyLauncherIcons(browser);
  await browser.close();
  process.exit(0);
}
if (proofs) mkdirSync(proofs, { recursive: true });
await legacyLauncherIcons(browser);
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
