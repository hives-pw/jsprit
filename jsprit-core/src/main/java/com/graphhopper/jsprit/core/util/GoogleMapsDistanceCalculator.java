/*
 * Licensed to GraphHopper GmbH under one or more contributor
 * license agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * GraphHopper GmbH licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.jsprit.core.util;


import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import com.google.maps.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.concurrent.atomic.AtomicInteger;

public class GoogleMapsDistanceCalculator {

    private final static Logger logger = LoggerFactory.getLogger(GoogleMapsDistanceCalculator.class);

    public static double calculateDistance(Coordinate coord1, Coordinate coord2, GeoApiContext context, Jedis jedis, AtomicInteger c) {

        String coordA = String.format("%f,%f", coord1.getX(), coord1.getY());
        String coordB = String.format("%f,%f", coord2.getX(), coord2.getY());

        if (coordA.equals(coordB)) {
            return 0;
        }

        String cachedResponse = jedis.get("distances/" + coordA + ":" + coordB);
        if (cachedResponse == null) {
            System.out.println("Calculate google");
            DirectionsResult result =
                DirectionsApi.newRequest(context)
                    .mode(TravelMode.DRIVING)
                    .avoid(
                        DirectionsApi.RouteRestriction.FERRIES)
                    .units(Unit.METRIC)
                    .origin(coordA)
                    .destination(coordB)
                    .awaitIgnoreError();
            long duration = result.routes[0].legs[0].duration.inSeconds;
            logger.info("calculate cost [{}] from {} to {}: {}", c.incrementAndGet(), coordA, coordB, duration, c);
            jedis.set("distances/" + coordA + ":" + coordB, Long.toString(duration));
            jedis.expire("distances/" + coordA + ":" + coordB, 900);
            return (double)duration;
        }
        return Double.parseDouble(cachedResponse);
    }

}
