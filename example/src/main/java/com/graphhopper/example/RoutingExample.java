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
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class RoutingExample {

    static int Interval_Seconds = 60 * 15;
    static int Max_Distance= 1500;
    static int Degree_Threshold = 30;
    static int Reduce_Seconds = 60 * 5;
    static int Distance_Threshold = 800;
    static int Time_Threshold = 60 * 15;
    static int Outdoor_Radius = 800;
    static int Indoor_Radius = 100;

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
                }
                else {
                    jsonsamples = ((JSONArray) jsoninput);
                }

                Set<Long> times = new HashSet<>();
                for (int i = 0; i < jsonsamples.length(); ++i) {
                    Observation sample = new Observation(GHPoint.fromStringLonLat(
                            jsonsamples.getString(i)));
                    samples.add(sample);
                }
            }
            catch (JSONException e) {
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
                }
                else {
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
            }
            catch (JSONException e) {
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
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            try {
                return formatArrayList(new String(filecontent, encoding));
            }
            catch (UnsupportedEncodingException e) {
                System.err.println("The OS does not support " + encoding);
                e.printStackTrace();
                return null;
            }
        }

        public static ArrayList formattxtFile(String filename) {
            try {
                String temp = null;
                File f = new File(filename);
                InputStreamReader read = new InputStreamReader(new FileInputStream(f));
                ArrayList readList = new ArrayList();

                BufferedReader reader = new BufferedReader(read);
                while ((temp = reader.readLine()) != null && !"".equals(temp)) {
                    readList.add(temp);
                }
                read.close();
                return readList;
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        //Clean duplicated in short time
        public static ArrayList<String> trajClean(ArrayList arrayList) {
            ArrayList<String> clean = new ArrayList<>();
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            String[] temp = null;

            HashMap<String, String> distinct_map = new HashMap<>();

            try {
                for (int i = 0; i < arrayList.size(); i++) {
                    temp = ((String)arrayList.get(i)).split(",");

                    long round_interval = (df.parse(temp[2]).getTime() / 1000 / Interval_Seconds);

                    String str_key = temp[0] + "," + temp[1] + "," + round_interval;

                    if (!distinct_map.containsKey(str_key)) {
                        distinct_map.put(str_key, (String)arrayList.get(i));
                        clean.add((String)arrayList.get(i));
                    }
                    else {
                        System.out.println("clean " + arrayList.get(i));
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            return clean;
        }

        public static ArrayList<String> trajClean_dis(ArrayList arrayList) {
            ArrayList<String> clean = new ArrayList<>();
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            String[] temp = null;
            String[] tempn1 = null;

            ArrayList<Double> diff_seq = new ArrayList<>();

            try {
                for (int i = 1; i < arrayList.size(); i++) {
                    temp = ((String)arrayList.get(i)).split(",");
                    tempn1 = ((String)arrayList.get(i - 1)).split(",");

                    int diff = (int) ((df.parse(temp[2]).getTime() - df.parse(tempn1[2]).getTime()) / 1000);

                    double dis = getDistance(Double.parseDouble(temp[0]), Double.parseDouble(temp[1]),
                            Double.parseDouble(tempn1[0]), Double.parseDouble(tempn1[1]));

                    diff_seq.add(dis);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            clean.add((String)arrayList.get(0));
            for (int i = 0; i < diff_seq.size(); i++) {
                if (diff_seq.get(i) > 0.00001) {
                    clean.add((String)arrayList.get(i+1));
                }
            }

            return clean;
        }

        //Filter abnormal speed
        public static ArrayList<String> trajFilter(ArrayList arrayList) {
            ArrayList<String> filter = new ArrayList<>();
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            String[] temp = null;
            String[] tempn1 = null;

            ArrayList<Double> diff_seq = new ArrayList<>();

            try {
                for (int i = 1; i < arrayList.size(); i++) {
                    temp = ((String)arrayList.get(i)).split(",");
                    tempn1 = ((String)arrayList.get(i - 1)).split(",");

                    double diff = (double) ((df.parse(temp[2]).getTime() - df.parse(tempn1[2]).getTime()) / 1000);

                    double dis = getDistance(Double.parseDouble(temp[0]), Double.parseDouble(temp[1]),
                            Double.parseDouble(tempn1[0]), Double.parseDouble(tempn1[1]));

                    diff_seq.add(dis);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            filter.add((String)arrayList.get(0));
            for (int i = 0; i < diff_seq.size(); i++) {
                //if (diff_seq.get(i) > 0 && diff_seq.get(i) <= Speed_Threshold) {
                if (diff_seq.get(i) > 0) {
                    filter.add((String)arrayList.get(i+1));
                }
                else {
                    System.out.println("dis = " + diff_seq.get(i) + " filter " + arrayList.get(i+1));
                }
            }

            return filter;
        }

        public static ArrayList<String> trajDenoise(ArrayList arrayList) {
            ArrayList<String> denoise = new ArrayList<>();
            denoise = (ArrayList<String>) arrayList.clone();

            String[] prev = null;
            String[] curr = null;
            String[] next = null;
            double dis = 0;
            double degree = 0;
            int pointNum = arrayList.size();
            int left = 0;
            int right = 0;

            while (left < pointNum - 2) {
                right = left + 1;
                try {
                    prev = ((String) arrayList.get(left)).split(",");

                    while (right < pointNum - 1) {
                        curr = ((String) arrayList.get(right)).split(",");
                        next = ((String) arrayList.get(right + 1)).split(",");

                        //curr = prev , move the left window one step
                        if (curr[0].equals(prev[0]) && curr[1].equals(prev[1])) {
                            break;
                        }

                        //curr = next
                        if (curr[0].equals(next[0]) && curr[1].equals(next[1])) {
                            dis = getDistance(Double.parseDouble(curr[0]), Double.parseDouble(curr[1]),
                                    Double.parseDouble(prev[0]), Double.parseDouble(prev[1]));
                            if (dis > Max_Distance) {
                                //remove next two, move right window, left window not change
                                System.out.println("dis = " + dis + " denoise " + arrayList.get(right));
                                denoise.remove(arrayList.get(right));
                                denoise.remove(arrayList.get(right+1));
                                right += 2;
                                continue;
                            }
                            else {
                                right += 1;
                                break;
                            }
                        }

                        //prev = next
                        if (prev[0].equals(next[0]) && prev[1].equals(next[1])) {
                            dis = getDistance(Double.parseDouble(curr[0]), Double.parseDouble(curr[1]),
                                    Double.parseDouble(prev[0]), Double.parseDouble(prev[1]));
                            if (dis > Max_Distance) {
                                //remove next two, move right window, left window not change
                                System.out.println("dis = " + dis + " denoise " + arrayList.get(right));
                                denoise.remove(arrayList.get(right));
                                right += 1;
                                continue;
                            }
                            //valid point, move the slide window
                            break;
                        }

                        degree = getDegree(Double.parseDouble(curr[0]), Double.parseDouble(curr[1]),
                                Double.parseDouble(prev[0]), Double.parseDouble(prev[1]),
                                Double.parseDouble(next[0]), Double.parseDouble(next[1]));

                        if (degree < Degree_Threshold) {
                            System.out.println("degree = " + degree + " denoise " + arrayList.get(right));
                            denoise.remove(arrayList.get(right));
                            right += 1;
                            continue;
                        }
                        //valid point, move the slide window
                        break;
                    }
                    //move window
                    left = right;
                }

                catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            try {
                prev = (denoise.get(denoise.size() - 1)).split(",");
                curr = (denoise.get(denoise.size() - 2)).split(",");
                dis = getDistance(Double.parseDouble(curr[0]), Double.parseDouble(curr[1]),
                        Double.parseDouble(prev[0]), Double.parseDouble(prev[1]));

                if (dis > Max_Distance) {
                    //remove the last dispersed point
                    System.out.println("dis = " + dis + " denoise " + denoise.get(denoise.size() - 1));
                    denoise.remove(denoise.size() - 1);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return denoise;
        }

        public static HashMap<Integer, ArrayList<Observation>> StayPoint_Detection(ArrayList arrayList) {
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");

            int i = 0;
            int j = 0;
            int stayIndex = 1;
            int routeIndex = -1;
            String[] left = null;
            String[] right = null;
            String[] prev_right = null;
            int diff = 0;
            double lng,lat;
            int radius;
            double dis;

            ArrayList<Observation> stayPoint = null;
            ArrayList<Observation> routePoint = new ArrayList<>();
            ArrayList<String> routeArray = new ArrayList<>();
            HashMap<Integer, ArrayList<Observation>> stayroutePointMap = new HashMap<>();

            int pointNum = arrayList.size();

            while (i < pointNum) {
                j = i + 1;
                try {
                    left = ((String) arrayList.get(i)).split(",");
                    while (j < pointNum) {
                        right = ((String) arrayList.get(j)).split(",");
                        prev_right = ((String) arrayList.get(j - 1)).split(",");

                        // deal the last point
                        dis = getDistance(Double.parseDouble(left[0]),
                                Double.parseDouble(left[1]),
                                Double.parseDouble(right[0]), Double.parseDouble(right[1])
                        );

                        if (dis <= Distance_Threshold && j == pointNum - 1) {
                            diff = (int) (
                                    (
                                            df.parse(right[2]).getTime() - df.parse(left[2])
                                                    .getTime()
                                    ) / 1000
                            );

                            if (diff >= Time_Threshold) {
                                if (stayPoint == null) {
                                    stayPoint = new ArrayList<>();
                                }
                                for (int t = i; t < j + 1; t++) {
                                    lng = Double.parseDouble(
                                            ((String) arrayList.get(t)).split(",")[0]);
                                    lat = Double.parseDouble(
                                            ((String) arrayList.get(t)).split(",")[1]);
                                    if (((String) arrayList.get(t)).split(",")[3].equals("outdoor")) {
                                        radius = Outdoor_Radius;
                                    } else {
                                        radius = Indoor_Radius;
                                    }
                                    Observation sample = new Observation(new GHPoint(lat, lng), radius);
                                    stayPoint.add(sample);
                                }
                                stayroutePointMap.put(stayIndex, stayPoint);
                                stayPoint = null;
                            }
                            break;
                        }

                        dis = getDistance(Double.parseDouble(left[0]),
                                        Double.parseDouble(left[1]),
                                        Double.parseDouble(right[0]), Double.parseDouble(right[1])
                                );
                        if (dis > Distance_Threshold) {
                            diff = (int) (
                                    (
                                            df.parse(prev_right[2]).getTime() - df.parse(left[2])
                                                    .getTime()
                                    ) / 1000
                            );
                            if (diff >= Time_Threshold) {
                                if (stayPoint == null) {
                                    stayPoint = new ArrayList<>();
                                }
                                for (int t = i; t < j; t++) {
                                    lng = Double.parseDouble(
                                            ((String) arrayList.get(t)).split(",")[0]);
                                    lat = Double.parseDouble(
                                            ((String) arrayList.get(t)).split(",")[1]);
                                    if (((String) arrayList.get(t)).split(",")[3].equals("outdoor")) {
                                        radius = Outdoor_Radius;
                                    } else {
                                        radius = Indoor_Radius;
                                    }
                                    Observation sample = new Observation(new GHPoint(lat, lng), radius);
                                    stayPoint.add(sample);
                                }
                                stayroutePointMap.put(stayIndex, stayPoint);
                                stayIndex ++;
                                stayPoint = null;
                            }
                            break;
                        }
                        j += 1;
                    }

                    routeArray.add((String)arrayList.get(i));

                    i = j;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            routePoint = trajReduce(routeArray);

            System.out.println("routePoint size = " + routePoint.size());

            stayroutePointMap.put(routeIndex, routePoint);

            return stayroutePointMap;
        }

        public static ArrayList<Observation> trajReduce(ArrayList arrayList) {
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");

            int i = 0;
            int j = 0;
            String[] temp1 = null;
            String[] temp2 = null;
            int diff = 0;
            double lng,lat;

            ArrayList<Observation> samples = new ArrayList<>();
            ArrayList<Double> lngList = new ArrayList<>();
            ArrayList<Double> latList = new ArrayList<>();

            int pointNum = arrayList.size();

            try {
                while (i < pointNum) {
                    j = i + 1;

                    temp1 = ((String) arrayList.get(i)).split(",");
                    while (j < pointNum) {
                        temp2 = ((String) arrayList.get(j)).split(",");
                        diff = (int) (
                                (
                                        df.parse(temp2[2]).getTime() - df.parse(temp1[2])
                                                .getTime()
                                ) / 1000
                        );

                        if (diff > Reduce_Seconds) {
                            break;
                        }

                        lngList.add(Double.parseDouble(temp2[0]));
                        latList.add(Double.parseDouble(temp2[1]));
                        j += 1;
                    }

                    if (lngList.size() > 0) {
                        lngList.add(Double.parseDouble(temp1[0]));
                        latList.add(Double.parseDouble(temp1[1]));
                        lng = lngList.stream().collect(Collectors.averagingDouble(x -> x));
                        lat = latList.stream().collect(Collectors.averagingDouble(x -> x));
                        Observation sample = new Observation(new GHPoint(lat, lng));
                        samples.add(sample);
                        lngList.clear();
                        latList.clear();
                    }
                    else {
                        lng = Double.parseDouble(temp1[0]);
                        lat = Double.parseDouble(temp1[1]);
                        Observation sample = new Observation(new GHPoint(lat, lng));
                        samples.add(sample);
                    }
                    i = j;
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            return samples;
        }

        public static List<Observation> trajectoryDPReduce(ArrayList arrayList) {
            ArrayList<Observation> tmpObservation = new ArrayList<>();
            List<Observation> reduced = null;
            String temp = null;

            try {
                for (int i = 0; i < arrayList.size(); i++) {
                    temp = (String) arrayList.get(i);
                    String[] row = temp.split(",");
                    Observation sample = new Observation(
                            new GHPoint(Double.parseDouble(row[1]),
                                    Double.parseDouble(row[0])));
                    tmpObservation.add(sample);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            reduced = DouglasPeuckerUtil.DouglasPeucker(tmpObservation, 0.001);
            return reduced;
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

    public static ArrayList<Observation> multiPointSort(ArrayList<Observation> origList) {
        ArrayList<Observation> distctlist=(ArrayList) origList.stream().distinct().collect(Collectors.toList());

        //not multi polygon
        if (distctlist.size() < 3) {
            return distctlist;
        }

        ArrayList<Observation> sortedPoint = new ArrayList<>();

        double plusX = 0, plusY = 0;
        for (Observation latLng : distctlist) {
            plusX += latLng.getPoint().getLon();
            plusY += latLng.getPoint().getLat();
        }
        Observation center = new Observation(new GHPoint(plusY / distctlist.size(), plusX / distctlist.size()));

        HashMap<Integer, ArrayList<Object>> mapAll = new HashMap<>();
        for (int i = 0; i < distctlist.size(); i++) {
            ArrayList<Object> objList = new ArrayList<>();
            objList.add(distctlist.get(i));
            objList.add(getAngle1(center.getPoint().getLat(), center.getPoint().getLon(),
                    distctlist.get(i).getPoint().getLat(), distctlist.get(i).getPoint().getLon()));
            mapAll.put(i, objList);
        }

        ArrayList<Object> temp = new ArrayList<>();
        int size = mapAll.size();
        for (int i = 0; i < size - 1; i++) {
            for (int j = 0; j < size - 1 - i; j++) {
                if (Double.parseDouble(mapAll.get(j).get(1).toString()) >
                        Double.parseDouble(mapAll.get(j + 1).get(1).toString()))
                {
                    temp = mapAll.get(j);
                    mapAll.put(j, mapAll.get(j + 1));
                    mapAll.put(j + 1, temp);
                }
            }
        }

        for (Integer integer : mapAll.keySet()) {
            if (mapAll.get(integer).get(0) instanceof Observation) {
                sortedPoint.add((Observation) mapAll.get(integer).get(0));
            }
        }

        return sortedPoint;
    }

    public static Observation getWeightedLocation(ArrayList<Observation> origList) {
        ArrayList<Observation> distctlist=(ArrayList) origList.stream().distinct().collect(Collectors.toList());

        int fix = 1000;
        double centroidX = 0, centroidY = 0, plusW = 0;
        for (Observation latLng : distctlist) {
            plusW += fix / latLng.getRadius();
        }

        for (Observation latLng : distctlist) {
            centroidX += latLng.getPoint().getLon() * ((fix / latLng.getRadius())  / plusW);
            centroidY += latLng.getPoint().getLat() * ((fix / latLng.getRadius())  / plusW);
        }

        double dis = 0;
        for (Observation latLng : distctlist) {
            dis += getDistance(latLng.getPoint().getLon(), latLng.getPoint().getLat(), centroidX, centroidY);
        }

        if (distctlist.size() == 1) {
            dis = Outdoor_Radius;
        }

        double circleX = 0, circleY = 0;
        double dLat = 0, dLng = 0;
        int Multi_Circle = 32;
        double ratio = dis / distctlist.size() / 6367000.0;
        StringBuilder sb_stay = new StringBuilder();
        sb_stay.append("{  \"coordinates\": [\n    [\n");
        for (int i = 0; i < Multi_Circle; i++) {
            double angle = Math.toRadians(360.0 * i / Multi_Circle);
            dLat = Math.atan(Math.sin(ratio) * Math.cos(angle) / Math.cos(ratio));
            dLng = Math.asin(Math.sin(ratio) * Math.sin(angle) / Math.cos(Math.toRadians(centroidY) + dLat));
            circleX = centroidX + Math.toDegrees(dLng);
            circleY = centroidY + Math.toDegrees(dLat);
            if (i > 0) {
                sb_stay.append(", ");
            }
            sb_stay.append('[');
            sb_stay.append(String.format("%.6f", circleX));
            sb_stay.append(',');
            sb_stay.append(String.format("%.6f", circleY));
            sb_stay.append(']');
        }
        //closing multi polygon
        sb_stay.append(',');
        sb_stay.append('[');
        dLat = Math.atan(Math.sin(ratio) * Math.cos(0) / Math.cos(ratio));
        dLng = Math.asin(Math.sin(ratio) * Math.sin(0) / Math.cos(Math.toRadians(centroidY) + dLat));
        sb_stay.append(String.format("%.6f", centroidX + Math.toDegrees(dLng)));
        sb_stay.append(',');
        sb_stay.append(String.format("%.6f", centroidY + Math.toDegrees((dLat))));
        sb_stay.append(']');
        sb_stay.append("    ]  ],\n \"type\": \"MultiLineString\" \n}");
        System.out.println("***** Stay Circle " +  " *****");
        System.out.println(sb_stay.toString());

        return new Observation(new GHPoint(centroidY, centroidX), (int)dis / distctlist.size());
    }

    private static double getAngle1(double lat_a, double lng_a, double lat_b, double lng_b) {
        double y = Math.sin(lng_b - lng_a) * Math.cos(lat_b);
        double x = Math.cos(lat_a) * Math.sin(lat_b) - Math.sin(lat_a) * Math.cos(lat_b) * Math.cos(lng_b - lng_a);
        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        if (brng < 0)
            brng = brng + 360;
        return brng;
    }

    private static double getDistance (double lonA1,double latA1,double lonA2,double latA2) {
        double lon1 = lonA1* Math.PI /180;
        double lat1 = latA1* Math.PI /180;
        double lon2 = lonA2* Math.PI /180;
        double lat2 = latA2* Math.PI /180;
        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return c * 6371 * 1000;
    }

    private static double getDegree(double vertexPointX, double vertexPointY, double point0X, double point0Y, double point1X, double point1Y) {
        //向量的点乘
        double vector = (point0X - vertexPointX) * (point1X - vertexPointX) + (point0Y - vertexPointY) * (point1Y - vertexPointY);
        //向量的模乘
        double sqrt = Math.sqrt(
                (Math.abs((point0X - vertexPointX) * (point0X - vertexPointX)) + Math.abs((point0Y - vertexPointY) * (point0Y - vertexPointY)))
                        * (Math.abs((point1X - vertexPointX) * (point1X - vertexPointX)) + Math.abs((point1Y - vertexPointY) * (point1Y - vertexPointY)))
        );
        //反余弦计算弧度
        double radian = Math.acos(vector / sqrt);
        //弧度转角度制
        return 180 * radian / Math.PI;
    }

    public static void matchingTest(GraphHopper hopper) {

        PMap hints = new PMap();
        hints.putObject("profile", "car");

        MapMatching mapMatching = new MapMatching(hopper, hints);

        //List<Observation> samples = InputFormatMy.formatFile("D:\\input\\ods\\zhang-1.json");
        ArrayList origList = InputFormatMy.formattxtFile("D:\\input\\ods\\fang-xiao.txt");
        System.out.println("original size = " + origList.size());

        ArrayList<String> cleanList = InputFormatMy.trajClean(origList);
        System.out.println("clean size = " + cleanList.size());

        /*ArrayList<String> filterList = InputFormatMy.trajFilter(cleanList);
        System.out.println("filter size = " + filterList.size());*/

        ArrayList<String> denoiseList = InputFormatMy.trajDenoise(cleanList);
        System.out.println("denoise size = " + denoiseList.size());

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("D:\\input\\single\\denoise.txt"))) {
            for (String tmp:denoiseList) {
                bufferedWriter.write(tmp);
                bufferedWriter.write("\n");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        /*ArrayList<Observation> samples = InputFormatMy.trajReduce(denoise);
        System.out.println("samples size = " + samples.size());

        HashMap<Integer, ArrayList<Observation>> stayroutePointMap =
                mapMatching.trajectoryCalculate(samples);*/

        HashMap<Integer, ArrayList<Observation>> stayroutePointMap = InputFormatMy.StayPoint_Detection(denoiseList);

        ArrayList<Observation> routePoint = null;
        ArrayList<Observation> sortedPoint = null;

        for (Integer key : stayroutePointMap.keySet()) {
            if (key > 0) {
                sortedPoint = multiPointSort(stayroutePointMap.get(key));

                StringBuilder sb_stay = new StringBuilder();
                sb_stay.append("{  \"coordinates\": [\n    [\n");
                for (int i = 0; i < sortedPoint.size(); i++) {
                    Observation gpx = sortedPoint.get(i);
                    if (i > 0) {
                        sb_stay.append(", ");
                    }
                    sb_stay.append('[');
                    sb_stay.append(String.format("%.6f", gpx.getPoint().getLon()));
                    sb_stay.append(',');
                    sb_stay.append(String.format("%.6f", gpx.getPoint().getLat()));
                    sb_stay.append(']');
                }
                //closing multi polygon
                sb_stay.append(',');
                sb_stay.append('[');
                sb_stay.append(String.format("%.6f", sortedPoint.get(0).getPoint().getLon()));
                sb_stay.append(',');
                sb_stay.append(String.format("%.6f", sortedPoint.get(0).getPoint().getLat()));
                sb_stay.append(']');

                sb_stay.append("    ]  ],\n \"type\": \"MultiLineString\" \n}");
                System.out.println("***** Stay Point " + key + " *****");
                System.out.println(sb_stay.toString());

                Observation centroid = getWeightedLocation(stayroutePointMap.get(key));
                System.out.println("centroid coord = " +
                        centroid.getPoint().getLon() + "," +
                        centroid.getPoint().getLat() + "  and radius = " + centroid.getRadius());

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

            final List<EdgeMatch> matches = mr.getEdgeMatches();

            if (matches.isEmpty()) {
                continue;
            }

            final Map<Long, EdgeMatch> mMatch = new HashMap<>();
            final Map<Long, PointList> mPoints = new HashMap<>();

            for (final EdgeMatch match : matches) {
                final EdgeIteratorState s = match.getEdgeState();
                //final int eId = s.getEdge();
                final int eId = s.getBaseNode();
                long eosmId = hopper.getOSMWay(eId);
                mMatch.put(eosmId, match);
                //mPoints.put(eosmId, s.fetchWayGeometry(ALL));
                PointList tmpPointList = new PointList();
                tmpPointList.add(hopper.getGraphHopperStorage().getBaseGraph().getNodeAccess().getLat(eId),
                        hopper.getGraphHopperStorage().getBaseGraph().getNodeAccess().getLon(eId));
                mPoints.put(eosmId, tmpPointList);
            }

            StringBuilder sb_gpx = new StringBuilder();
            sb_gpx.append("{  \"coordinates\": [\n    [\n");
            EdgeMatch[] samples_result = new EdgeMatch[routePoint.size()];
            for (int i = 0; i < routePoint.size() - 1; i++) {
                Observation gpx = routePoint.get(i);
                if (i > 0) {
                    sb_gpx.append(", ");
                }
                sb_gpx.append('[');
                sb_gpx.append(String.format("%.6f", gpx.getPoint().getLon()));
                sb_gpx.append(',');
                sb_gpx.append(String.format("%.6f", gpx.getPoint().getLat()));
                sb_gpx.append(']');

                double minDistance = Integer.MAX_VALUE;
                long minEdgeId = -1;
                final Set<Entry<Long, PointList>> s = mPoints.entrySet();
                for (final Entry<Long, PointList> e : s) {
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

            sb_gpx.append("    ]  ],\n \"type\": \"MultiLineString\" \n}");

            List<EdgeMatch> list_edge = mr.getEdgeMatches();

            StringBuilder sb = new StringBuilder();

            sb.append("{  \"coordinates\": [\n    [\n");

            //System.out.println("list_edge.size() = " + samples_result.length);

            double route_dis = 0;
            double last_lon = 0;
            double last_lat = 0;

            for (int i = 0; i < samples_result.length - 1; i++) {
            //for (int i = 0; i < list_edge.size() -1; i++) {
                EdgeMatch edge_match = samples_result[i];

                //EdgeMatch edge_match = list_edge.get(i);
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
                        " lon = " + String.format("%.6f", internalNodeId_lon) +
                        " lat = " + String.format("%.6f", internalNodeId_lat));

                if (i > 0) {
                    route_dis += getDistance(internalNodeId_lon, internalNodeId_lat, last_lon, last_lat);
                    sb.append(", ");
                }
                sb.append('[');
                sb.append(String.format("%.6f", internalNodeId_lon));
                sb.append(',');
                sb.append(String.format("%.6f", internalNodeId_lat));
                sb.append(']');

                last_lon = internalNodeId_lon;
                last_lat = internalNodeId_lat;


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

/*            System.out.println("*****Sample Route Point*****");
            System.out.println(sb_gpx.toString());
            System.out.println("");*/
            System.out.println("*****OSM Node Point*****");
            System.out.println(sb.toString());
            System.out.println("route distance = " + String.format("%.2f", route_dis / 1000) + " Km");

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
