package com.rideflow.geospatial;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** A WGS84 coordinate. Latitude first here; remember PostGIS {@code ST_MakePoint} takes longitude first. */
public record GeoPoint(
        @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
}
