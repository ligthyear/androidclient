/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmpp.message;

import android.content.Context;
import android.database.Cursor;


/**
 * A location message.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 * @version 1.0
 */
public class LocationMessage extends PlainTextMessage {

    private double mLongitude;
    private double mLatitude;

    protected LocationMessage(Context context) {
        super(context);
    }

    public LocationMessage(Context context, String id, long timestamp, String sender, byte[] content, double lat, double lon) {
        super(context, id, timestamp, sender, content, false, null);

        mLatitude = lat;
        mLongitude = lon;
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        mLatitude = c.getDouble(COLUMN_GEO_LAT);
        mLongitude = c.getDouble(COLUMN_GEO_LON);
    }

    @Override
    public byte[] getBinaryContent() {
        return content;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }
}
