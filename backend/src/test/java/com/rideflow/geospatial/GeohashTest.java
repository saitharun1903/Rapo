package com.rideflow.geospatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeohashTest {

    private static final GeoPoint HITECH_CITY = new GeoPoint(17.4435, 78.3772);

    @Test
    void encodesTheReferenceExample() {
        // The worked example from the geohash specification (Jutland, Denmark).
        assertThat(Geohash.encode(new GeoPoint(57.64911, 10.40744), 11)).isEqualTo("u4pruydqqvj");
    }

    @Test
    void centreOfACellLiesWithinHalfACellOfEveryPointInIt() {
        String cell = Geohash.encode(HITECH_CITY, 6);
        GeoPoint centre = Geohash.center(cell);

        // Precision 6 cells are about 1.2 km x 0.6 km: the centre is at most ~0.7 km from any point inside.
        assertThat(GeoMath.haversineMeters(centre, HITECH_CITY)).isLessThan(700);
        assertThat(Geohash.encode(centre, 6)).isEqualTo(cell);
    }

    @Test
    void nearbyPointsShareACellAndDistantOnesDoNot() {
        GeoPoint fiftyMetresAway = new GeoPoint(HITECH_CITY.lat() + 0.00045, HITECH_CITY.lng());
        GeoPoint tenKilometresAway = new GeoPoint(17.4239, 78.4738);

        // Shared prefix = same larger cell (precision 3 is ~156 km x 156 km).
        assertThat(Geohash.encode(HITECH_CITY, 3)).isEqualTo(Geohash.encode(tenKilometresAway, 3));
        assertThat(Geohash.encode(HITECH_CITY, 6)).isNotEqualTo(Geohash.encode(tenKilometresAway, 6));
        assertThat(Geohash.center(Geohash.encode(fiftyMetresAway, 6)).lat())
                .isCloseTo(Geohash.center(Geohash.encode(HITECH_CITY, 6)).lat(), within(0.006));
    }

    @Test
    void rejectsCharactersOutsideTheAlphabet() {
        assertThatThrownBy(() -> Geohash.center("tepa")).isInstanceOf(IllegalArgumentException.class);
    }
}
