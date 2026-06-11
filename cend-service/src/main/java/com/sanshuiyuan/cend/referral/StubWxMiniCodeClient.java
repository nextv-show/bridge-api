package com.sanshuiyuan.cend.referral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StubWxMiniCodeClient implements WxMiniCodeClient {

    private static final Logger log = LoggerFactory.getLogger(StubWxMiniCodeClient.class);

    private static final String PLACEHOLDER_DATA_URL = "" +
            "data:image/jpeg;base64,"
                    + "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMCwsK"
                    + "CwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUJBUVFQz/wAALCAABAAEBAREA/8QAFAABAAAAAAA"
                    + "AAAAAAAAAAwAAAAA/9oACAEBAAA/Az/9k=";

    @Override
    public String getUnlimitedWxaCode(String scene, String page, String envVersion) {
        log.info("[stub] wxacode.getUnlimited scene={} page={} envVersion={} -> 占位图", scene, page, envVersion);
        return PLACEHOLDER_DATA_URL;
    }
}
