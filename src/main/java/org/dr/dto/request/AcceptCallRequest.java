package org.dr.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class AcceptCallRequest {
    private Map<String, Object> sdp; // รับค่า SDP Map จาก Flutter (type និង sdp string)
    private String deviceId;
}
