package org.kontalk.xmpp.util;

public class GMStaticUrlBuilder {
    private static final String URL="http://maps.googleapis.com/maps/api/staticmap";

    private String mCenter;
    private int mZoom;
    private String mSize;
    private String mMarker;
    private boolean mSensor;
    private String mType;

    public GMStaticUrlBuilder setCenter (double lat, double lon) {
        mCenter=lat+","+lon;
        return this;
    }

    public GMStaticUrlBuilder setZoom(int zoom) {
       mZoom=zoom;
       return this;
    }

    public GMStaticUrlBuilder setSize(int height, int width) {
        mSize=height+"x"+width;
        return this;
    }

    public GMStaticUrlBuilder setMarker(String color,char label,double lat,double lon) {
        StringBuilder marker = new StringBuilder("color:")
            .append(color);

        if (label != '\0') {
            marker.append("%10label:")
                .append(label);
        }

        marker.append("%7C")
            .append(lat)
            .append(',')
            .append(lon);

        mMarker = marker.toString();

        return this;
    }

    public GMStaticUrlBuilder setSensor(boolean sensor) {
        mSensor=sensor;
        return this;
    }

    public GMStaticUrlBuilder setMapType(String type){
        mType=type;
        return this;
    }

    public String toString() {
        return new StringBuilder()
             .append(URL)
             .append("?center=")
             .append(mCenter)
             .append("&zoom=")
             .append(mZoom)
             .append("&size=")
             .append(mSize)
             .append("&maptype=")
             .append(mType)
             .append("&markers=")
             .append(mMarker)
             .append("&sensor=")
             .append(mSensor)
             .toString();
    }
}
