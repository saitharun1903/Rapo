import type { LayerSpecification, StyleSpecification } from "maplibre-gl";

export type MapTheme = "light" | "dark";

/**
 * Raido's map colours per theme (docs/design-system.md). Roads carry the hierarchy: minor streets are quiet,
 * arterials stand out, motorways get a faint warm tint; water, parks and buildings are there but never louder
 * than the route drawn on top.
 */
export type MapPalette = {
  background: string;
  residential: string;
  park: string;
  wood: string;
  water: string;
  waterLabel: string;
  building: string;
  buildingOutline: string;
  path: string;
  minorRoad: string;
  majorRoad: string;
  majorCasing: string;
  motorway: string;
  motorwayCasing: string;
  rail: string;
  railDash: string;
  boundary: string;
  label: string;
  labelMuted: string;
  halo: string;
};

export const PALETTES: Record<MapTheme, MapPalette> = {
  light: {
    background: "#f1f1ed",
    residential: "#ebebe6",
    park: "#e0e8da",
    wood: "#dbe4d5",
    water: "#c8d7de",
    waterLabel: "#557082",
    building: "#e4e3dd",
    buildingOutline: "#d6d5ce",
    path: "#e6e5df",
    minorRoad: "#ffffff",
    majorRoad: "#ffffff",
    majorCasing: "#d3d2ca",
    motorway: "#fbf3e8",
    motorwayCasing: "#d8ccba",
    rail: "#d2d1ca",
    railDash: "#f6f6f2",
    boundary: "#b7b6ae",
    label: "#272825",
    labelMuted: "#6b6c65",
    halo: "#f6f6f2",
  },
  dark: {
    background: "#121311",
    residential: "#151614",
    park: "#162019",
    wood: "#17211a",
    water: "#16222a",
    waterLabel: "#7f9aab",
    building: "#1b1c19",
    buildingOutline: "#242521",
    path: "#1f201c",
    minorRoad: "#282925",
    majorRoad: "#363732",
    majorCasing: "#1a1b18",
    motorway: "#45423a",
    motorwayCasing: "#1f1e1a",
    rail: "#2a2b27",
    railDash: "#171816",
    boundary: "#4a4b45",
    label: "#d8d9d2",
    labelMuted: "#8b8c84",
    halo: "#121311",
  },
};

/** Layers that add noise at city scale or assume another country (US highway shields). */
const HIDDEN = new Set(["highway-shield-non-us", "highway-shield-us-interstate", "road_shield_us", "label_country_3",
  "label_country_2", "label_country_1", "boundary_disputed"]);

type Paint = Record<string, unknown>;

/** Paint overrides by layer id, for styles built on the OpenMapTiles schema (OpenFreeMap Positron and kin). */
function overrides(id: string, palette: MapPalette): Paint | null {
  switch (id) {
    case "background": return { "background-color": palette.background };
    case "park": case "landuse_park": return { "fill-color": palette.park };
    case "water": return { "fill-color": palette.water };
    case "landuse_residential": return { "fill-color": palette.residential };
    case "landcover_wood": return { "fill-color": palette.wood };
    case "landcover_ice_shelf": case "landcover_glacier": return { "fill-color": palette.residential };
    case "waterway": return { "line-color": palette.water };
    case "building": return { "fill-color": palette.building, "fill-outline-color": palette.buildingOutline };
    case "road_area_pier": return { "fill-color": palette.background };
    case "road_pier": return { "line-color": palette.background };
    case "aeroway-area": case "aeroway-runway": return { [id === "aeroway-area" ? "fill-color" : "line-color"]: palette.path };
    case "aeroway-taxiway": case "aeroway-runway-casing": return { "line-color": palette.majorCasing };
    case "highway_path": return { "line-color": palette.path };
    case "highway_minor": return { "line-color": palette.minorRoad, "line-opacity": 1 };
    case "highway_major_casing": return { "line-color": palette.majorCasing };
    case "highway_major_inner": return { "line-color": palette.majorRoad };
    case "highway_major_subtle": return { "line-color": palette.majorCasing };
    case "highway_motorway_casing": case "tunnel_motorway_casing": case "highway_motorway_bridge_casing":
      return { "line-color": palette.motorwayCasing };
    case "highway_motorway_inner": case "tunnel_motorway_inner": case "highway_motorway_bridge_inner":
      return { "line-color": palette.motorway };
    case "highway_motorway_subtle": return { "line-color": palette.motorwayCasing };
    case "railway": case "railway_transit": case "railway_service": case "railway_minor": return { "line-color": palette.rail };
    case "railway_dashline": case "railway_transit_dashline": case "railway_service_dashline": case "railway_minor_dashline":
      return { "line-color": palette.railDash };
    case "boundary_2": case "boundary_3": case "boundary_state": return { "line-color": palette.boundary };
    case "waterway_line_label": case "water_name_point_label": case "water_name_line_label": case "water_name":
      return { "text-color": palette.waterLabel, "text-halo-color": palette.halo };
    case "highway-name-path": case "highway-name-minor": case "highway-name-major": case "highway_name_other":
    case "highway_name_motorway": case "airport":
      return { "text-color": palette.labelMuted, "text-halo-color": palette.halo, "text-halo-width": 1.2 };
    case "label_other": case "label_state": case "place_other": case "place_suburb":
      return { "text-color": palette.labelMuted, "text-halo-color": palette.halo, "text-halo-width": 1.2 };
    case "label_village": case "label_town": case "label_city": case "label_city_capital":
    case "place_village": case "place_town": case "place_city": case "place_city_large":
      return { "text-color": palette.label, "text-halo-color": palette.halo, "text-halo-width": 1.5 };
    default: return null;
  }
}

/** Whether a style follows the OpenMapTiles layer naming this module knows how to restyle. */
export function isOpenMapTilesStyle(style: StyleSpecification): boolean {
  const ids = new Set(style.layers.map((layer) => layer.id));
  return ids.has("background") && ids.has("water") && ids.has("highway_minor");
}

/**
 * The style recoloured to Raido's palette for {@code theme}. Styles that do not follow the OpenMapTiles naming
 * are returned unchanged, so a custom NEXT_PUBLIC_MAP_STYLE_URL keeps working as it was designed.
 */
export function themeStyle(style: StyleSpecification, theme: MapTheme): StyleSpecification {
  if (!isOpenMapTilesStyle(style)) {
    return style;
  }
  const palette = PALETTES[theme];
  const layers = style.layers.map((layer): LayerSpecification => {
    if (HIDDEN.has(layer.id)) {
      return { ...layer, layout: { ...layer.layout, visibility: "none" } } as LayerSpecification;
    }
    const paint = overrides(layer.id, palette);
    return paint ? ({ ...layer, paint: { ...layer.paint, ...paint } } as LayerSpecification) : layer;
  });
  return { ...style, layers };
}
