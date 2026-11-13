package org.dr.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Location reported by a device.
 *
 * <p>The x/y pair follows the convention already used by the legacy
 * {@code sessions} table ({@code mapx} / {@code mapy}) and holds latitude
 * and longitude.
 */
public record DeviceLocationRequest(

        
        Double mapX,

     
        Double mapY,

        @Size(max = 120, message = "city must be at most 120 characters")
        String city,

        @Size(max = 120, message = "country must be at most 120 characters")
        String country
) {
}
