package com.sanshuiyuan.matching.geo;

/**
 * 标准 base32 geohash 编码（取前6位 ~1.2km 精度）+ Haversine 距离。纯函数，无状态。
 * nearby 半径查询用 bbox + Haversine 精算，不用 geohash 邻居网格（geohash6 覆盖不了 50–200km）。
 */
public final class GeoHash {

    private GeoHash() {}

    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** 编码 lat/lng 为 6 位 geohash。 */
    public static String encode6(double lat, double lng) {
        return encode(lat, lng, 6);
    }

    public static String encode(double lat, double lng, int precision) {
        double latMin = -90, latMax = 90;
        double lngMin = -180, lngMax = 180;
        StringBuilder hash = new StringBuilder();
        boolean even = true;
        int bit = 0;
        int ch = 0;
        while (hash.length() < precision) {
            if (even) {
                double mid = (lngMin + lngMax) / 2;
                if (lng >= mid) {
                    ch |= (1 << (4 - bit));
                    lngMin = mid;
                } else {
                    lngMax = mid;
                }
            } else {
                double mid = (latMin + latMax) / 2;
                if (lat >= mid) {
                    ch |= (1 << (4 - bit));
                    latMin = mid;
                } else {
                    latMax = mid;
                }
            }
            even = !even;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    /** 两点间大圆距离（km）。 */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
