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
/**
 *
 */
package com.graphhopper.jsprit.core.util;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import redis.clients.jedis.Jedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author stefan schroeder
 */
public class GoogleMapsCosts extends AbstractForwardVehicleRoutingTransportCosts {

    public int speed = 1;

    public double detourFactor = 1.0;

    private AtomicInteger c = new AtomicInteger(0);
    private Locations locations;
    private final static Logger logger = LoggerFactory.getLogger(GoogleMapsCosts.class);

    private String redisURI = "redis://localhost:6379";
    private Jedis jedis;

    public GoogleMapsCosts(Locations locations, String redisURI) {
        super();
        this.locations = locations;
        this.redisURI = redisURI;
        this.jedis = new Jedis(this.redisURI);
    }

    @Override
    public String toString() {
        return "[name=googleMapsCosts]";
    }

    @Override
    public double getTransportCost(Location from, Location to, double time, Driver driver, Vehicle vehicle) {
        String cachedResponse = jedis.get("distances/" + from.getCoordinate().toString() + ":" + to.getCoordinate().toString());
        if (cachedResponse == null) {
            double distance = calculateDistance(from, to);
            if (vehicle != null && vehicle.getType() != null) {
                return distance * vehicle.getType().getVehicleCostParams().perDistanceUnit;
            }
            logger.info("calculate cost [{}] from {} to {}: {}", c.incrementAndGet(), from.getCoordinate().toString(), to.getCoordinate().toString(), distance);
            jedis.set("distances/" + from.getCoordinate().toString() + ":" + to.getCoordinate().toString(), Double.toString(distance));
            jedis.expire("distances/" + from.getCoordinate().toString() + ":" + to.getCoordinate().toString(), 900);
            return distance;
        }
        return Double.parseDouble(cachedResponse);
    }

    double calculateDistance(Location fromLocation, Location toLocation) {
        return calculateDistance(fromLocation.getCoordinate(), toLocation.getCoordinate());
    }

    double calculateDistance(Coordinate from, Coordinate to) {
        try {
            return GoogleMapsDistanceCalculator.calculateDistance(from, to) * detourFactor;
        } catch (NullPointerException e) {
            throw new NullPointerException("cannot calculate euclidean distance. coordinates are missing. either add coordinates or use another transport-cost-calculator.");
        }
    }

    @Override
    public double getTransportTime(Location from, Location to, double time, Driver driver, Vehicle vehicle) {
        return calculateDistance(from, to) / speed;
    }

    @Override
    public double getDistance(Location from, Location to, double departureTime, Vehicle vehicle) {
            return calculateDistance(from, to);
    }
}
