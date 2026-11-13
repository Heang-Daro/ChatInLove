package org.dr.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Names of the HTTP headers a client may use to report its location.
 *
 * <p>Mobile and desktop clients can attach the device position to every
 * request as headers, which is handy when the JSON body cannot carry it.
 * Each list is tried in order; the first header that is present wins.
 */
@Component
@ConfigurationProperties(prefix = "app.location")
@Data
public class DeviceLocationProperties {

    /** Headers that may carry the latitude (mapX). */
    private List<String> latitudeHeaders =
            List.of("X-Device-Latitude", "X-Latitude", "X-Geo-Latitude", "X-Client-Latitude");

    /** Headers that may carry the longitude (mapY). */
    private List<String> longitudeHeaders =
            List.of("X-Device-Longitude", "X-Longitude", "X-Geo-Longitude", "X-Client-Longitude");

    /** Headers that may carry the city name. */
    private List<String> cityHeaders = List.of("X-Device-City", "X-City");

    /** Headers that may carry the country name. */
    private List<String> countryHeaders = List.of("X-Device-Country", "X-Country");

    /** Header that may carry a human readable device name. */
    private String deviceNameHeader = "X-Device-Name";

    /** Header that may carry the device type (WEB, ANDROID, IOS, DESKTOP). */
    private String deviceTypeHeader = "X-Device-Type";
}
