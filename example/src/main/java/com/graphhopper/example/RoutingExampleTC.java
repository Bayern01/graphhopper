package com.graphhopper.example;


import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;

import java.util.Arrays;

import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT;

/**
 * Routing with turn costs. Also see {@link RoutingExample} for more details.
 */
public class RoutingExampleTC {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        GraphHopper hopper = createGraphHopperInstance(relDir + "./sichuan.osm.pbf");
        routeWithTurnCosts(hopper);
        routeWithTurnCostsAndCurbsides(hopper);
        routeWithTurnCostsAndOtherUTurnCosts(hopper);
    }

    public static void routeWithTurnCosts(GraphHopper hopper) {
        GHRequest req = new GHRequest(30.6799,104.03277, 30.7381,103.9614).
                setProfile("car");
        route(hopper, req, 1038, 63_000);
        GHResponse rsp = hopper.route(req);
        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        String st = st = pointList.toString();
        double distance = path.getDistance();
        long timeInMs = path.getTime();
    }

    public static void routeWithTurnCostsAndCurbsides(GraphHopper hopper) {
        GHRequest req = new GHRequest(30.5326,104.0598, 30.7342,103.9686).
                setCurbsides(Arrays.asList(CURBSIDE_ANY, CURBSIDE_RIGHT)).
                setProfile("car");
        route(hopper, req, 1729, 112_000);
    }

    public static void routeWithTurnCostsAndOtherUTurnCosts(GraphHopper hopper) {
        GHRequest req = new GHRequest(30.5326,104.0598, 30.7342,103.9686)
                .setCurbsides(Arrays.asList(CURBSIDE_ANY, CURBSIDE_RIGHT))
                // to change u-turn costs per request we have to disable CH. otherwise the u-turn costs we set per request
                // will be ignored and those set for our profile will be used.
                .putHint(Parameters.CH.DISABLE, true)
                .setProfile("car");
        route(hopper, req.putHint(Parameters.Routing.U_TURN_COSTS, 10), 1370, 100_000);
        route(hopper, req.putHint(Parameters.Routing.U_TURN_COSTS, 15), 1370, 105_000);
    }

    private static void route(GraphHopper hopper, GHRequest req, int expectedDistance, int expectedTime) {
        GHResponse rsp = hopper.route(req);
        // handle errors
        if (rsp.hasErrors())
            // if you get: Impossible curbside constraint: 'curbside=right'
            // you either specify 'curbside=any' or Parameters.Routing.FORCE_CURBSIDE=false to ignore this situation
            throw new RuntimeException(rsp.getErrors().toString());
        ResponsePath path = rsp.getBest();
        assert Math.abs(expectedDistance - path.getDistance()) < 1 : "unexpected distance : " + path.getDistance() + " vs. " + expectedDistance;
        assert Math.abs(expectedTime - path.getTime()) < 1000 : "unexpected time : " + path.getTime() + " vs. " + expectedTime;
    }

    // see RoutingExample for more details
    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-tc-graph-cache");
        // by enabling turn costs for the FlagEncoder, turn restriction constraints like 'no_left_turn' will be taken
        // from OSM
        Profile profile = new Profile("car").setVehicle("car").setWeighting("fastest")
                // to actually use the turn restrictions when routing we have to enable turn costs for our routing profile
                .setTurnCosts(true)
                // we can also set u_turn_costs (in seconds). by default no u-turns are allowed, but with this setting
                // we will consider u-turns at all junctions with a 40s time penalty
                .putHint("u_turn_costs", 40);
        hopper.setProfiles(profile);
        // enable CH for our profile. since turn costs are enabled this will take more time and memory to prepare than
        // without turn costs.
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile.getName()));
        hopper.importOrLoad();
        return hopper;
    }
}
