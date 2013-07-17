package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.PacketExtension;


/**
 * XEP-0080: User Location
 * http://xmpp.org/extensions/xep-0080.html
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public class UserLocation implements PacketExtension {
    public static final String NAMESPACE = "http://jabber.org/protocol/geoloc";
    public static final String ELEMENT_NAME = "geoloc";

    private float mLatitude;
    private float mLongitude;

    public UserLocation(float latitude, float longitude) {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public float getLatitude() {
        return mLatitude;
    }

    public float getLongitude() {
        return mLongitude;
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return new StringBuilder("<")
            .append(ELEMENT_NAME)
            .append(" xmlns='")
            .append(NAMESPACE)
            .append("'><lat>")
            .append(mLatitude)
            .append("</lat><lon>")
            .append(mLongitude)
            .append("</lon></")
            .append(ELEMENT_NAME)
            .append('>')
            .toString();
    }

}
