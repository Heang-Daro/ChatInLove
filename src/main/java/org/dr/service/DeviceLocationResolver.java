package org.dr.service;

import com.blueconic.browscap.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dr.dto.request.DeviceLocationRequest;
import org.dr.model.enums.DeviceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds a device location from the request body and fills any missing field
 * (coordinates, city, country) from the client IP using ip-api.com.
 * Also resolves device name/type, using Browscap to parse the User-Agent.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceLocationResolver {

    private static final String CACHE_ATTR = DeviceLocationResolver.class.getName() + ".ipLocation";
    /** Only digits, hex, ':' and '.' - protects the lookup URL from spoofed header values. */
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final RestTemplate restTemplate;

    @Value("${location.ip-api.url:http://ip-api.com/json/}")
    private String apiUrl;

    /**
     * Dev helper: when the client IP is private/loopback, let ip-api use the
     * server's public IP. Keep false in production (it would report the
     * server's location, not the user's).
     */
    @Value("${location.autodetect-private-ip:false}")
    private boolean autodetectPrivateIp;

    private UserAgentParser uaParser;

    @PostConstruct
    void init() {
        try {
            uaParser = new UserAgentService().loadParser(List.of(
                    BrowsCapField.BROWSER,
                    BrowsCapField.DEVICE_TYPE,
                    BrowsCapField.DEVICE_NAME,
                    BrowsCapField.PLATFORM));
        } catch (Exception e) {
            log.warn("Browscap parser unavailable, device detection falls back to caller: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Location
    // ------------------------------------------------------------------

    /**
     * @param body    location from the request body, may be null
     * @param request current HTTP request, may be null
     * @return merged location (body first, then IP lookup), or null if nothing is known
     */
    public DeviceLocationRequest resolve(DeviceLocationRequest body, HttpServletRequest request) {
        Double mapX = body == null ? null : body.mapX();   // latitude
        Double mapY = body == null ? null : body.mapY();   // longitude
        String city = body == null ? null : body.city();
        String country = body == null ? null : body.country();

        boolean missing = mapX == null || mapY == null || isBlank(city) || isBlank(country);

        if (missing) {
            Map<String, Object> net = lookupNetwork(request);
            if (!net.isEmpty()) {
                if (mapX == null) {
                    mapX = toDouble(net.get("lat"));   // mapX = latitude
                }
                if (mapY == null) {
                    mapY = toDouble(net.get("lon"));   // mapY = longitude
                }
                if (isBlank(city)) {
                    city = asString(net.get("city"));
                }
                if (isBlank(country)) {
                    country = asString(net.get("country"));
                }
            }
        }

        if (mapX == null && mapY == null && isBlank(city) && isBlank(country)) {
            return null;
        }
        // adjust argument order/types to match your DeviceLocationRequest record
        return new DeviceLocationRequest(mapX, mapY, city, country);
    }

    /** One lookup per HTTP request, even if resolve() is called several times. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> lookupNetwork(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        Object cached = request.getAttribute(CACHE_ATTR);
        if (cached instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> result = fetchLocation(extractClientIp(request));
        request.setAttribute(CACHE_ATTR, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLocation(String ip) {
        if (ip != null) {
            ip = ip.trim();
            int zone = ip.indexOf('%');                       // IPv6 zone id, e.g. fe80::1%eth0
            if (zone > 0) {
                ip = ip.substring(0, zone);
            }
            if (ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}:\\d+$")) { // IPv4 with port
                ip = ip.substring(0, ip.lastIndexOf(':'));
            }
        }
        log.info("Resolving location for client IP: {}", ip);

        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            log.warn("Client IP '{}' is not a valid IP literal - skipping geolocation", ip);
            return Collections.emptyMap();
        }

        String target = ip;
        if (isPrivateIp(ip)) {
            if (!autodetectPrivateIp) {
                log.warn("Private/local IP {} cannot be geolocated. For local testing set "
                        + "location.autodetect-private-ip=true", ip);
                return Collections.emptyMap();
            }
            log.warn("Private IP ({}) detected - using ip-api auto-detect", ip);
            target = "";
        }

        String url = apiUrl + target + "?fields=status,message,country,city,lat,lon,query";

        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result == null) {
                return Collections.emptyMap();
            }
            if ("fail".equals(result.get("status"))) {
                log.warn("ip-api lookup failed for {}: {}", ip, result.get("message"));
                return Collections.emptyMap();
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch location for {}: {}", ip, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ------------------------------------------------------------------
    // Device info
    // ------------------------------------------------------------------

    /**
     * Device name from the X-Device-Name header, then Browscap, then the fallback.
     */
    public String resolveDeviceName(HttpServletRequest request, String fallback) {
        if (request == null) {
            return fallback;
        }
        String header = request.getHeader("X-Device-Name");
        if (!isBlank(header)) {
            return header.trim();
        }
        UaInfo ua = parseUserAgent(request);
        if (ua != null) {
            String name = known(ua.deviceName());
            if (name == null) {
                name = known(ua.browser());
            }
            String platform = known(ua.platform());
            if (name != null) {
                return platform != null ? name + " on " + platform : name;
            }
        }
        return fallback;
    }

    /**
     * Device type from the X-Device-Type header, then Browscap.
     * Returns null when unknown so the caller can apply its own guess.
     */
    public DeviceType resolveDeviceType(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-Device-Type");
        if (!isBlank(header)) {
            try {
                return DeviceType.valueOf(header.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to User-Agent detection
            }
        }
        UaInfo ua = parseUserAgent(request);
        if (ua == null) {
            return null;
        }
        String platform = String.valueOf(ua.platform()).toLowerCase();
        String type = String.valueOf(ua.deviceType()).toLowerCase();

        if (platform.contains("android")) {
            return DeviceType.ANDROID;
        }
        if (platform.contains("ios")) {
            return DeviceType.IOS;
        }
        if (type.contains("desktop")) {
            return DeviceType.DESKTOP;
        }
        return null;
    }

    /** Plain holder so no Browscap type leaks outside parseUserAgent. */
    private record UaInfo(String browser, String deviceName, String deviceType, String platform) {
    }

    private UaInfo parseUserAgent(HttpServletRequest request) {
        if (uaParser == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (isBlank(userAgent)) {
            return null;
        }
        try {
            var caps = uaParser.parse(userAgent);
            return new UaInfo(
                    asString(caps.getValue(BrowsCapField.BROWSER)),
                    asString(caps.getValue(BrowsCapField.DEVICE_NAME)),
                    asString(caps.getValue(BrowsCapField.DEVICE_TYPE)),
                    asString(caps.getValue(BrowsCapField.PLATFORM)));
        } catch (Exception e) {
            log.debug("User-Agent parse failed: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Client IP, checking common proxy headers first. Only trust these headers
     * when the app sits behind a proxy you control.
     */
    public static String extractClientIp(HttpServletRequest request) {
        for (String header : new String[] {"CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP"}) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip.trim())) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isPrivateIp(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip); // literal only (validated), no DNS
            return a.isLoopbackAddress() || a.isSiteLocalAddress()
                    || a.isLinkLocalAddress() || a.isAnyLocalAddress();
        } catch (Exception e) {
            return true; // unparseable - treat as not geolocatable
        }
    }

    private static Double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Browscap returns "unknown" for unrecognised values. */
    private static String known(String v) {
        return (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v)) ? null : v;
    }
}