package com.rideflow.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.rideflow.entity.PaymentMethod;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.support.RideFixtures.Actor;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Thin HTTP helpers so ride integration tests read like the user journey. */
public class RideApi {

    private final MockMvc mvc;

    public RideApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    public MvcResult call(Actor actor, String method, String path, String json) throws Exception {
        var request = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            default -> throw new IllegalArgumentException(method);
        };
        request.header(HttpHeaders.AUTHORIZATION, actor.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request).andReturn();
    }

    public MvcResult goOnline(Actor driver, GeoPoint at) throws Exception {
        return call(driver, "POST", "/api/drivers/online", "{\"location\":%s}".formatted(point(at)));
    }

    public MvcResult reportLocation(Actor driver, GeoPoint at, Instant recordedAt) throws Exception {
        return call(driver, "POST", "/api/drivers/location",
                "{\"location\":%s,\"recordedAt\":\"%s\"}".formatted(point(at), recordedAt));
    }

    /** Estimates and books an ECONOMY ride paid in cash; returns the ride id. */
    public UUID book(Actor passenger, GeoPoint pickup, GeoPoint dropoff) throws Exception {
        return book(passenger, pickup, dropoff, PaymentMethod.CASH);
    }

    /** Estimates and books an ECONOMY ride; returns the ride id. */
    public UUID book(Actor passenger, GeoPoint pickup, GeoPoint dropoff, PaymentMethod paymentMethod) throws Exception {
        MvcResult estimate = call(passenger, "POST", "/api/fares/estimate",
                "{\"pickup\":%s,\"dropoff\":%s}".formatted(point(pickup), point(dropoff)));
        List<String> quoteIds = JsonPath.read(body(estimate), "$.quotes[?(@.vehicleCategory == 'ECONOMY')].quoteId");
        String quoteId = quoteIds.getFirst();
        MvcResult booked = call(passenger, "POST", "/api/rides", """
                {"quoteId":"%s","pickup":{"point":%s,"address":"Pickup"},
                 "dropoff":{"point":%s,"address":"Dropoff"},"paymentMethod":"%s"}
                """.formatted(quoteId, point(pickup), point(dropoff), paymentMethod));
        if (booked.getResponse().getStatus() != 201) {
            throw new AssertionError("Booking failed: " + booked.getResponse().getStatus() + " " + body(booked));
        }
        return UUID.fromString(JsonPath.read(body(booked), "$.id"));
    }

    public int openOfferCount(Actor driver) throws Exception {
        return JsonPath.<Integer>read(body(call(driver, "GET", "/api/drivers/me/offers", null)), "$.length()");
    }

    public static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    public static String point(GeoPoint point) {
        return String.format(Locale.ROOT, "{\"lat\":%.7f,\"lng\":%.7f}", point.lat(), point.lng());
    }
}
