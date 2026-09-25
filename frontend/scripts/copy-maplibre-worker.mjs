// Copies MapLibre's web worker, and the shared module it imports, into public/maplibre, where MapView points
// MapLibre (setWorkerUrl). Bundled into a Next chunk, MapLibre would look for its worker next to that chunk,
// which the build never emits: the worker fails to load, no tile is ever parsed, and every map stays blank.
// Copied at build time so the files always match the installed version.
import { copyFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

const FILES = ["maplibre-gl-worker.mjs", "maplibre-gl-shared.mjs"];
const require = createRequire(import.meta.url);
const dist = path.join(path.dirname(require.resolve("maplibre-gl/package.json")), "dist");
const target = path.join(import.meta.dirname, "..", "public", "maplibre");

mkdirSync(target, { recursive: true });
for (const file of FILES) {
  copyFileSync(path.join(dist, file), path.join(target, file));
}
console.log(`Copied MapLibre's worker to ${path.relative(process.cwd(), target)}`);
