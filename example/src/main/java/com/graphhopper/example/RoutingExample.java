package com.graphhopper.example;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.matching.State;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OSMSurfaceParser;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.util.FetchMode.ALL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class RoutingExample {

    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        String strosm = "./sichuan.osm.pbf";
        String hdfsosm = "hdfs://127.0.0.1:9000/target/sichuan.osm.pbf";
        GraphHopper hopper = createGraphHopperInstance(relDir + hdfsosm);
        matchingTest(hopper);
        //routing(hopper);
/*        speedModeVersusFlexibleMode(hopper);
        headingAndAlternativeRoute(hopper);
        customizableRouting(relDir + "./sichuan.osm.pbf");*/

        // release resources to properly shutdown or start a new instance
        hopper.close();
    }

    static GraphHopper createGraphHopperInstance(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        // specify where to store graphhopper files
        String strtarget = "target/routing-graph-cache";
        String hdfshopper = "hdfs://127.0.0.1:9000/hopper";
        hopper.setGraphHopperLocation(hdfshopper);
        hopper.setStoreOnFlush(false);

        // see docs/core/profiles.md to learn more about profiles
        hopper.setProfiles(new Profile("car").
                setVehicle("car").
                setWeighting("fastest").
                setTurnCosts(false));

        // this enables speed mode for the profile we called car
        //hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
        //hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car"));

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
        return hopper;
    }

    public static class InputFormatMy {
        /**
         * Converts a request message into a list of {@link Observation} objects as input for map
         * matching.
         *
         * @param input JSON input format of sample data.
         * @return List of {@link Observation} objects.
         */
        public static List<Observation> format(String input) {
            List<Observation> samples = new LinkedList<>();

            try {
                Object jsoninput = new JSONTokener(input).nextValue();
                JSONArray jsonsamples = null;

                if (jsoninput instanceof JSONObject) {
                    jsonsamples = ((JSONObject) jsoninput).getJSONArray("request");
                } else {
                    jsonsamples = ((JSONArray) jsoninput);
                }

                Set<Long> times = new HashSet<>();
                for (int i = 0; i < jsonsamples.length(); ++i) {
                    Observation sample = new Observation(GHPoint.fromStringLonLat(
                            jsonsamples.getString(i)));
                    samples.add(sample);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException("parsing JSON request: " + e.getMessage());
            }

            return samples;
        }

        public static ArrayList<Observation> formatArrayList(String input) {
            ArrayList<Observation> samples = new ArrayList<>();

            try {
                Object jsoninput = new JSONTokener(input).nextValue();
                JSONArray jsonsamples = null;

                if (jsoninput instanceof JSONObject) {
                    jsonsamples = ((JSONObject) jsoninput).getJSONArray("request");
                } else {
                    jsonsamples = ((JSONArray) jsoninput);
                }

                Set<Long> times = new HashSet<>();

                for (int i = 0; i < jsonsamples.length(); ++i) {
                    JSONObject json_line = (JSONObject)jsonsamples.get(i);
                    String wkt = json_line.getString("point").replace(" ", ",");
                    String xy = wkt.substring(wkt.indexOf("(")+1,wkt.indexOf(")"));
                    Observation sample = new Observation(GHPoint.fromStringLonLat(xy));
                    samples.add(sample);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RuntimeException("parsing JSON request: " + e.getMessage());
            }

            return samples;
        }

        public static ArrayList<Observation> formatFile(String filename) {
            String encoding = "UTF-8";
            File file = new File(filename);
            Long filelength = file.length();
            byte[] filecontent = new byte[filelength.intValue()];
            try {
                FileInputStream in = new FileInputStream(file);
                in.read(filecontent);
                in.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                return formatArrayList(new String(filecontent, encoding));
            } catch (UnsupportedEncodingException e) {
                System.err.println("The OS does not support " + encoding);
                e.printStackTrace();
                return null;
            }
        }

    }

    public static void routing(GraphHopper hopper) {
        // simple configuration of the request object
        GHRequest req = new GHRequest(30.6799,104.03277, 30.7381,103.9614).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.CHINA);
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        String st = pointList.toString();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        // iterate over all turn instructions
        for (Instruction instruction : il) {
             System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
        }
        assert il.size() == 6;
        assert Helper.round(path.getDistance(), -2) == 900;
    }

    public static void matchingTest(GraphHopper hopper) {

        PMap hints = new PMap();
        hints.putObject("profile", "car");

        MapMatching mapMatching = new MapMatching(hopper, hints);

        List<Observation> samples = InputFormatMy.formatFile("D:\\ubuntu\\rootfs\\mnt\\barefoot\\util\\submit\\wu2.json");

        HashMap<Integer, ArrayList<Observation>> stayroutePointMap =
                mapMatching.trajectoryCalculate(samples);

        ArrayList<Observation> routePoint = null;


        for (Integer key : stayroutePointMap.keySet()) {
            if (key > 0) {
                continue;
            }

            routePoint = stayroutePointMap.get(key);

            if (routePoint.isEmpty() || routePoint.size() <= 1) {
                continue;
            }

            StopWatch matchSW = new StopWatch();
            matchSW.start();

            MatchResult mr = mapMatching.match(routePoint);

            matchSW.stop();

            final Map<Integer, EdgeMatch> mMatch = new HashMap<>();
            final Map<Integer, PointList> mPoints = new HashMap<>();
            final List<EdgeMatch> matches = mr.getEdgeMatches();

            for (final EdgeMatch match : matches) {
                final EdgeIteratorState s = match.getEdgeState();
                final int eId = s.getEdge();
                mMatch.put(eId, match);
                mPoints.put(eId, s.fetchWayGeometry(ALL));
            }

            //List<EdgeMatch> samples_result = new ArrayList<>();
            EdgeMatch[] samples_result = new EdgeMatch[routePoint.size()];
            for (int i = 0; i < routePoint.size() - 1; ++i) {
                Observation gpx = routePoint.get(i);

                double minDistance = Integer.MAX_VALUE;
                int minEdgeId = -1;
                final Set<Entry<Integer, PointList>> s = mPoints.entrySet();
                for (final Entry<Integer, PointList> e : s) {
                    final PointList points = e.getValue();
                    for (final GHPoint p : points) {
                        final double d = Math.abs(mapMatching.getDistance(gpx.getPoint().getLat(),
                                gpx.getPoint().getLon(), p.getLat(), p.getLon()
                        ));
                        if (d < minDistance) {
                            minDistance = d;
                            minEdgeId = e.getKey();
                        }
                    }
                }

                if(minEdgeId >= 0) {
                    //samples_result.set(i, mMatch.get(minEdgeId));
                    samples_result[i] = mMatch.get(minEdgeId);
                }
            }



            List<EdgeMatch> list_edge = mr.getEdgeMatches();

            StringBuilder sb = new StringBuilder();

            sb.append("{  \"coordinates\": [\n    [\n");

            System.out.println("list_edge.size() = " + samples_result.length);

            for (int i = 0; i < samples_result.length - 1; i++) {
                //EdgeMatch edge_match = list_edge.get(i);
                EdgeMatch edge_match = samples_result[i];
                List<State> list_state = edge_match.getStates();


                int internalEdgeId =  edge_match.getEdgeState().getEdge();
                System.out.println("EdgeID: " + internalEdgeId +
                        ", OSMWayID: " + hopper.getOSMWay(internalEdgeId) +
                        " EdgeName = " + edge_match.getEdgeState().getName());

                int internalNodeId =  edge_match.getEdgeState().getBaseNode();

                double internalNodeId_lon = hopper.getGraphHopperStorage().getBaseGraph().getNodeAccess().getLon(internalNodeId);
                double internalNodeId_lat = hopper.getGraphHopperStorage().getBaseGraph().getNodeAccess().getLat(internalNodeId);

                System.out.println("basenode NodeID: " + internalNodeId +
                        ", OSMNodeID: " + hopper.getOSMNode(internalNodeId) +
                        " lon = " + String.format("%.5f", internalNodeId_lon) +
                        " lat = " + String.format("%.5f", internalNodeId_lat));

                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('[');
                sb.append(String.format("%.5f", internalNodeId_lon));
                sb.append(',');
                sb.append(String.format("%.5f", internalNodeId_lat));
                sb.append(']');

/*                for (int j = 0; j < list_state.size(); j++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    String str_snap = list_state.get(j).getSnap().getSnappedPoint().toShortString();

                    sb.append('[');
                    String[] xy = str_snap.split(",");
                    sb.append(xy[1]);
                    sb.append(',');
                    sb.append(xy[0]);
                    sb.append(']');
                }*/
            }

            sb.append("    ]  ],\n \"type\": \"MultiLineString\" \n}");

            System.out.println(sb.toString());

            System.out.println("match took: " + matchSW.getMillis() + " ms");
        }


/*        PointList pointList = path.getPoints();
        String st = pointList.toString();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        System.out.println("\tmr = \t" + mr.toString());*/

        //assert Helper.round(path.getDistance(), -2) == 900;
    }

    public static void speedModeVersusFlexibleMode(GraphHopper hopper) {
        GHRequest req = new GHRequest(30.6799, 104.03277, 30.7381, 103.9614).
                setProfile("car").setAlgorithm(Parameters.Algorithms.ASTAR_BI).putHint(Parameters.CH.DISABLE, true);
        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert Helper.round(res.getBest().getDistance(), -2) == 900;
    }

    public static void headingAndAlternativeRoute(GraphHopper hopper) {
        // define a heading (direction) at start and destination
        GHRequest req = new GHRequest().setProfile("car").
                addPoint(new GHPoint(30.6799, 104.03277)).addPoint(new GHPoint(30.7381, 103.9614)).
                setHeadings(Arrays.asList(180d, 90d)).
                // use flexible mode (i.e. disable contraction hierarchies) to make heading and pass_through working
                        putHint(Parameters.CH.DISABLE, true);
        // if you have via points you can avoid U-turns there with
        // req.getHints().putObject(Parameters.Routing.PASS_THROUGH, true);
        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert res.getAll().size() == 1;
        assert Helper.round(res.getBest().getDistance(), -2) == 800;

        // calculate potential alternative routes to the current one (supported with and without CH)
        req = new GHRequest().setProfile("car").
                addPoint(new GHPoint(30.6799, 104.03277)).addPoint(new GHPoint(30.7381, 103.9614)).
                setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
        req.getHints().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3);
        res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());
        assert res.getAll().size() == 2;
        assert Helper.round(res.getBest().getDistance(), -2) == 1600;
    }

    public static void customizableRouting(String ghLoc) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
        hopper.setGraphHopperLocation("target/routing-custom-graph-cache");
        hopper.setProfiles(new CustomProfile("car_custom").setCustomModel(new CustomModel()).setVehicle("car"));

        // The hybrid mode uses the "landmark algorithm" and is up to 15x faster than the flexible mode (Dijkstra).
        // Still it is slower than the speed mode ("contraction hierarchies algorithm") ...
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car_custom"));
        hopper.importOrLoad();

        // ... but for the hybrid mode we can customize the route calculation even at request time:
        // 1. a request with default preferences
        GHRequest req = new GHRequest().setProfile("car_custom").
                addPoint(new GHPoint(30.6799, 104.03277)).addPoint(new GHPoint(30.7381, 103.9614));

        GHResponse res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 96;

        // 2. now avoid primary roads and reduce maximum speed, see docs/core/custom-models.md for an in-depth explanation
        // and also the blog posts https://www.graphhopper.com/?s=customizable+routing
        CustomModel model = new CustomModel();
        model.addToPriority(If("road_class == PRIMARY", MULTIPLY, 0.5));

        // unconditional limit to 100km/h
        model.addToPriority(If("true", LIMIT, 100));

        req.setCustomModel(model);
        res = hopper.route(req);
        if (res.hasErrors())
            throw new RuntimeException(res.getErrors().toString());

        assert Math.round(res.getBest().getTime() / 1000d) == 165;
    }
}
