import type { StyleSpecification } from "maplibre-gl";
import { describe, expect, it } from "vitest";
import { PALETTES, isOpenMapTilesStyle, themeStyle } from "./style";

/** The layer names this module relies on, as the OpenMapTiles-based Positron style uses them. */
function openMapTilesStyle(): StyleSpecification {
  return {
    version: 8,
    sources: { openmaptiles: { type: "vector", url: "https://tiles.example/planet" } },
    layers: [
      { id: "background", type: "background", paint: { "background-color": "rgb(242,243,240)" } },
      { id: "water", type: "fill", source: "openmaptiles", "source-layer": "water", paint: { "fill-color": "rgb(194,200,202)" } },
      { id: "highway_minor", type: "line", source: "openmaptiles", "source-layer": "transportation",
        paint: { "line-color": "hsl(0,0%,88%)", "line-width": 2 } },
      { id: "highway_major_inner", type: "line", source: "openmaptiles", "source-layer": "transportation",
        paint: { "line-color": "#fff", "line-width": 3 } },
      { id: "highway-shield-non-us", type: "symbol", source: "openmaptiles", "source-layer": "transportation_name",
        layout: { "text-field": "{ref}" } },
      { id: "label_city", type: "symbol", source: "openmaptiles", "source-layer": "place",
        layout: { "text-field": "{name}" }, paint: { "text-color": "#000" } },
      { id: "something_custom", type: "line", source: "openmaptiles", "source-layer": "transportation",
        paint: { "line-color": "#123456" } },
    ],
  };
}

const paintOf = (style: StyleSpecification, id: string) =>
  (style.layers.find((layer) => layer.id === id) as { paint?: Record<string, unknown> }).paint ?? {};

describe("themeStyle", () => {
  it("recolours the base style to the dark palette and keeps what it does not know", () => {
    const dark = themeStyle(openMapTilesStyle(), "dark");
    expect(paintOf(dark, "background")["background-color"]).toBe(PALETTES.dark.background);
    expect(paintOf(dark, "water")["fill-color"]).toBe(PALETTES.dark.water);
    expect(paintOf(dark, "highway_major_inner")["line-color"]).toBe(PALETTES.dark.majorRoad);
    expect(paintOf(dark, "highway_major_inner")["line-width"]).toBe(3);
    expect(paintOf(dark, "label_city")["text-halo-color"]).toBe(PALETTES.dark.halo);
    expect(paintOf(dark, "something_custom")["line-color"]).toBe("#123456");
  });

  it("gives the light and dark themes different maps from the same base", () => {
    const base = openMapTilesStyle();
    expect(paintOf(themeStyle(base, "light"), "background")["background-color"])
      .not.toBe(paintOf(themeStyle(base, "dark"), "background")["background-color"]);
  });

  it("hides US highway shields, which mean nothing in the service area", () => {
    const light = themeStyle(openMapTilesStyle(), "light");
    const shield = light.layers.find((layer) => layer.id === "highway-shield-non-us") as { layout?: Record<string, unknown> };
    expect(shield.layout?.visibility).toBe("none");
    expect(shield.layout?.["text-field"]).toBe("{ref}");
  });

  it("does not modify the base style it was given", () => {
    const base = openMapTilesStyle();
    themeStyle(base, "dark");
    expect(paintOf(base, "background")["background-color"]).toBe("rgb(242,243,240)");
  });

  it("leaves a style with other layer names exactly as it is", () => {
    const custom: StyleSpecification = { version: 8, sources: {}, layers: [{ id: "bg", type: "background" }] };
    expect(isOpenMapTilesStyle(custom)).toBe(false);
    expect(themeStyle(custom, "dark")).toBe(custom);
  });
});
