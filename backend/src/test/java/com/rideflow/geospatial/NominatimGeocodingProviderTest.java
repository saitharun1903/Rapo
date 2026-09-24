package com.rideflow.geospatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NominatimGeocodingProviderTest {

    private static final GeoPoint HYDERABAD = new GeoPoint(17.3850, 78.4867);

    private MockRestServiceServer server;
    private NominatimGeocodingProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://nominatim.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new NominatimGeocodingProvider(builder.build(), "in", 0.25);
    }

    @Test
    void searchEncodesTheQueryAndParsesStringCoordinates() {
        server.expect(requestTo(startsWith("http://nominatim.test/search?")))
                .andExpect(queryParam("q", "charminar%20%26%20old%20city"))
                .andExpect(queryParam("countrycodes", "in"))
                // Values are fully percent-encoded, commas included; Nominatim decodes them.
                .andExpect(queryParam("viewbox", "78.2367%2C17.6350%2C78.7367%2C17.1350"))
                .andExpect(queryParam("bounded", "0"))
                .andRespond(withSuccess("""
                        [{"lat":"17.3615636","lon":"78.4746645","name":"Charminar",
                          "display_name":"Charminar, Hyderabad, Telangana, 500002, India","place_id":1},
                         {"lat":"17.36","lon":"78.47","name":"",
                          "display_name":"Old City, Hyderabad, Telangana, India"}]
                        """, MediaType.APPLICATION_JSON));

        List<Place> places = provider.search("charminar & old city", HYDERABAD, 5);

        assertThat(places).hasSize(2);
        assertThat(places.get(0).name()).isEqualTo("Charminar");
        assertThat(places.get(0).point().lat()).isCloseTo(17.3615636, within(1e-9));
        assertThat(places.get(0).point().lng()).isCloseTo(78.4746645, within(1e-9));
        // Places without a name fall back to the first part of the address.
        assertThat(places.get(1).name()).isEqualTo("Old City");
        server.verify();
    }

    @Test
    void reverseReturnsEmptyWhenNominatimFindsNothing() {
        server.expect(requestTo(startsWith("http://nominatim.test/reverse?")))
                .andExpect(queryParam("lat", "17.000000"))
                .andRespond(withSuccess("{\"error\":\"Unable to geocode\"}", MediaType.APPLICATION_JSON));

        assertThat(provider.reverse(new GeoPoint(17.0, 88.0))).isEmpty();
    }

    @Test
    void serverErrorsBecomeUnavailable() {
        server.expect(requestTo(startsWith("http://nominatim.test/search?")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.search("charminar", HYDERABAD, 5))
                .isInstanceOf(GeocodingUnavailableException.class);
    }
}
