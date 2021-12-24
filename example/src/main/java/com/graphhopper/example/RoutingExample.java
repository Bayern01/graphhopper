package com.graphhopper.example;

import com.google.common.collect.Maps;
import com.google.common.geometry.S2;
import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2Cell;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2CellUnion;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.google.common.geometry.S2Loop;
import com.google.common.geometry.S2Point;
import com.google.common.geometry.S2PointRegion;
import com.google.common.geometry.S2Polygon;
import com.google.common.geometry.S2Region;
import com.google.common.geometry.S2RegionCoverer;
import com.google.common.geometry.S2RegionIntersection;
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
import java.util.Date;
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
    static int Distance_Threshold =  800;
    static int Time_Threshold = 60 * 15;
    static int Outdoor_Radius = 800;
    static int Indoor_Radius = 200;
    static int Min_Indoor_Count = 3;
    static double Outdoor_Confidence = 0.7;
    static double EARTH_RADIUS = 6367000.0D;
    static double kEarthCircumferenceMeters = 40075017;
    static int S2_Dim_Level = 18;

    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        String strosm = "./sichuan.osm.pbf";
        String hdfsosm = "hdfs://127.0.0.1:9000/target/sichuan.osm.pbf";
        GraphHopper hopper = createGraphHopperInstance(relDir + strosm);
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
        hopper.setGraphHopperLocation(strtarget);
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

        public static HashMap<Integer, ArrayList<String>> trajAnalysis(ArrayList arrayList) {
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");

            int i = 0;
            int j = 0;
            int stayIndex = 1;
            int routeIndex = -1;
            String[] left = null;
            String[] right = null;
            String[] prev_right = null;
            int diff = 0;
            double dis;
            boolean token = false;

            ArrayList<String> stayPoint = null;
            ArrayList<String> routePoint = null;
            HashMap<Integer, ArrayList<String>> stayroutePointMap = new HashMap<>();

            int pointNum = arrayList.size();

            while (i < pointNum) {
                j = i + 1;
                token = false;
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

                        if (j == pointNum - 1 && dis <= Distance_Threshold) {
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
                                    stayPoint.add((String)arrayList.get(t));
                                }
                                stayroutePointMap.put(stayIndex, stayPoint);
                                stayPoint = null;
                                token = true;
                            }
                            i = j;
                            break;
                        }

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
                                    stayPoint.add((String)arrayList.get(t));
                                }
                                stayroutePointMap.put(stayIndex, stayPoint);
                                stayIndex ++;
                                stayPoint = null;
                                i = j - 1;
                                token = true;
                            }
                            break;
                        }
                        j += 1;
                    }
                    if (!token) {
                        if (routePoint == null) {
                            routePoint = new ArrayList<>();
                        }
                        routePoint.add((String)arrayList.get(i));
                    }
                    else {
                        if (routePoint != null) {
                            stayroutePointMap.put(routeIndex, routePoint);
                            routePoint = null;
                            routeIndex--;
                        }
                    }

                    i += 1;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            if (routePoint != null) {
                stayroutePointMap.put(routeIndex, routePoint);
            }

            return stayroutePointMap;
        }

        public static HashMap<Integer, ArrayList<StayPoint>>trajSegment(HashMap<Integer, ArrayList<String>> stayroutePointMapList) {
            HashMap<Integer, ArrayList<StayPoint>> stayroutePointMap = new HashMap<>();

            for (int key : stayroutePointMapList.keySet()) {
                if (key > 0) {
                    ArrayList<StayPoint> stayPointList = new ArrayList<>();
                    StayPoint sp = getWeightedLocation(stayroutePointMapList.get(key));
                    stayPointList.add(sp);
                    stayroutePointMap.put(key, stayPointList);
                }
            }

            DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");

            for (int key : stayroutePointMapList.keySet()) {
                if (key < 0) {
                    ArrayList<String> obList = stayroutePointMapList.get(key);
                    ArrayList<StayPoint> routePointList = new ArrayList<>();
                    for (String tmp : obList) {
                        double lng = Double.parseDouble(tmp.split(",")[0]);
                        double lat = Double.parseDouble(tmp.split(",")[1]);
                        long st;
                        try {
                            st = df.parse(tmp.split(",")[2]).getTime();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }

                        int radius;
                        if (tmp.split(",")[3].equals("outdoor")) {
                            radius = Outdoor_Radius;
                        }
                        else {
                            radius = Indoor_Radius;
                        }

                        StayPoint sp = new StayPoint(new GHPoint(lat, lng), radius, st, 0);
                        routePointList.add(sp);
                    }

                    stayroutePointMap.put(key, routePointList);
                }
            }

            System.out.println("traj segment size = " + stayroutePointMap.size());

            return stayroutePointMap;
        }

        public static ArrayList<Observation> trajReduce(ArrayList<StayPoint> arrayList) {
            int i = 0;
            int j = 0;
            StayPoint temp1 = null;
            StayPoint temp2 = null;
            int diff = 0;
            double lng,lat;
            long tm;

            ArrayList<Observation> samples = new ArrayList<>();
            ArrayList<Double> lngList = new ArrayList<>();
            ArrayList<Double> latList = new ArrayList<>();
            ArrayList<Long> tmList = new ArrayList<>();

            int pointNum = arrayList.size();

            while (i < pointNum) {
                j = i + 1;

                temp1 = arrayList.get(i);
                while (j < pointNum) {
                    temp2 = arrayList.get(j);
                    diff = (int) (temp2.getStarttime() - temp1.getStarttime()) / 1000;

                    if (diff > Reduce_Seconds) {
                        break;
                    }

                    lngList.add(temp2.getPoint().getLon());
                    latList.add(temp2.getPoint().getLat());
                    tmList.add(temp2.getStarttime());
                    j += 1;
                }

                if (lngList.size() > 0) {
                    lngList.add(temp1.getPoint().getLon());
                    latList.add(temp1.getPoint().getLat());
                    tmList.add(temp1.getStarttime());
                    lng = lngList.stream().collect(Collectors.averagingDouble(x -> x));
                    lat = latList.stream().collect(Collectors.averagingDouble(x -> x));
                    tm = (tmList.stream().collect(Collectors.averagingLong(x -> x))).longValue();
                    Observation sample = new Observation(new GHPoint(lat, lng), tm, Outdoor_Radius);
                    samples.add(sample);
                    lngList.clear();
                    latList.clear();
                }
                else {
                    lng = temp1.getPoint().getLon();
                    lat = temp1.getPoint().getLat();
                    tm = temp1.getStarttime();
                    Observation sample = new Observation(new GHPoint(lat, lng), tm, Outdoor_Radius);
                    samples.add(sample);
                }
                i = j;
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

            reduced = DouglasPeuckerUtil.DouglasPeucker(tmpObservation, 0.1);
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

    public static StayPoint getWeightedLocation(ArrayList<String> origList) {
        ArrayList<Observation> weightedList = new ArrayList<>();

        double lng, lat;
        double radius = 0;
        long st = 0;

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        ArrayList<Observation> obList = new ArrayList<>();
        try {
            for (String tmp : origList) {
                lng = Double.parseDouble(tmp.split(",")[0]);
                lat = Double.parseDouble(tmp.split(",")[1]);
                st = df.parse(tmp.split(",")[2]).getTime();
                if (tmp.split(",")[3].equals("outdoor")) {
                    radius = Outdoor_Radius;
                }
                else {
                    radius = Indoor_Radius;
                }
                obList.add(new Observation(new GHPoint(lat, lng), st, (int) radius));
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        int ct_indoor = 0;
        // outdoor: distinct ; indoor: not distinct
        for (Observation o : obList) {
            if (o.getRadius() == Outdoor_Radius) {
                if (!weightedList.contains(o)) {
                    weightedList.add(o);
                }
            }
            else {
                weightedList.add(o);
                ct_indoor ++;
            }
        }

        boolean isconfidence = false;
        if (ct_indoor >= Min_Indoor_Count) {
            //indoor centroid
            isconfidence = true;
        }

        int fix = 1000;
        double centroidX = 0, centroidY = 0, plusW = 0;

        for (Observation latLng : weightedList) {
            plusW += fix / latLng.getRadius();
        }

        for (Observation latLng: weightedList) {
            if (isconfidence) {
                if (latLng.getRadius() == Indoor_Radius) {
                    centroidX += latLng.getPoint().getLon();
                    centroidY += latLng.getPoint().getLat();
                }
            }
            else {
                centroidX += latLng.getPoint().getLon() * ((fix / latLng.getRadius()) / plusW);
                centroidY += latLng.getPoint().getLat() * ((fix / latLng.getRadius()) / plusW);
            }
        }

        double dis = 0;

        for (Observation latLng : weightedList) {
            dis += getDistance(latLng.getPoint().getLon(), latLng.getPoint().getLat(), centroidX, centroidY);
        }

        if (weightedList.size() == 1) {
            dis = weightedList.get(0).getRadius();
        }

        dis = dis / weightedList.size();

        if (isconfidence) {
            centroidX = centroidX / ct_indoor;
            centroidY = centroidY / ct_indoor;
            radius = Indoor_Radius;
        }
        else {
            radius = dis / Outdoor_Confidence;
        }

        double circleX = 0, circleY = 0;
        double dLat = 0, dLng = 0;
        int Multi_Circle = 32;

        double ratio = radius / EARTH_RADIUS;

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
        System.out.println("***** Stay Circle " + " *****");
        System.out.println(sb_stay.toString());
        System.out.println("centroid coord = " + centroidX + "," + centroidY +
                "  and radius = " + (int)radius +
                "  and confidence = " + isconfidence);

        long s2cell = getStayCell(centroidY, centroidX, radius);
        System.out.println("s2cell = " + s2cell);

        int stayt = (int)(obList.get(obList.size() - 1).getTime() - obList.get(0).getTime()) / 1000 / 60;
        return new StayPoint(new GHPoint(centroidY, centroidX),
                (int)radius, obList.get(0).getTime(), stayt);
    }

    public static long getStayCell(double lat, double lng, double r) {
        int currentLevel = 16;

        S2LatLng s2LatLng = S2LatLng.fromDegrees(lat, lng);
        S2CellId cellId = S2CellId.fromLatLng(s2LatLng).parent(currentLevel);
        System.out.println("Target cellId = " + cellId.id());

        S2LatLng s2out = new S2CellId(cellId.id()).toLatLng();
        double latout = s2out.latDegrees();
        double lngout = s2out.lngDegrees();
        System.out.println("lngout = " + lngout + " latout = " + latout);

        double capHeight = (2 * S2.M_PI) * (r / kEarthCircumferenceMeters);
        S2LatLng s2LatLng1= S2LatLng.fromDegrees(lat, lng);
        S2Cap cap = S2Cap.fromAxisHeight(s2LatLng1.toPoint(),capHeight * capHeight / 2);
        System.out.println("radius = " + r + " area = " + cap.area() * EARTH_RADIUS * EARTH_RADIUS);

        ArrayList<S2Point>  vertices = new ArrayList<>();

        //按照逆时针顺序添加
        vertices.add(S2LatLng.fromDegrees(30.701368518399796,104.02627461190419).toPoint());
        vertices.add(S2LatLng.fromDegrees(30.699328729316257,104.02887101670349).toPoint());
        vertices.add(S2LatLng.fromDegrees(30.701492595650798,104.03252420632933).toPoint());
        vertices.add(S2LatLng.fromDegrees(30.70381116549,104.02985576959131).toPoint());
        vertices.add(S2LatLng.fromDegrees(30.701368518399796,104.02627461190419).toPoint());
        S2Loop s2Loop = new S2Loop(vertices);
        S2Polygon s2polygon = new S2Polygon(s2Loop);
        System.out.println("s2polygon area = " + s2polygon.getArea() * EARTH_RADIUS * EARTH_RADIUS);

        S2RegionCoverer coverer = S2RegionCoverer.builder().
                setLevelMod(1).
                setMaxLevel(14).
                setMinLevel(18).
                setMaxCells(100).
                build();

        ArrayList<S2CellId> arrcell = new ArrayList<>();
        //S2CellUnion covering = coverer.getCovering(s2polygon);
        S2CellUnion covering = coverer.getCovering(cap);

        boolean b = false;
        for (S2CellId s2CellId : covering.cellIds()) {
            b = cap.mayIntersect(new S2Cell(s2CellId));
            if (b) {
                System.out.println("Intersect cellid = " + s2CellId + " id = " + s2CellId.id());
                break;
            }
        }

        if (b)
            System.out.println("Intersect");
        else
            System.out.println("Not Intersect");

        /*for (int i = 0; i < covering.size(); i++) {
            System.out.println("covering cellid " + i + " = " + covering.cellIds().get(i) +
                    " id = " + covering.cellIds().get(i).id());
        }*/

/*        int minLevel = 12;
        int maxLevel = 15;
        S2CellId paCell = null;
        boolean inRegion = false;
        for (int i = minLevel; i < (maxLevel + 1); i++) {
            paCell = cellId.parent(i);
            if (covering.cellIds().contains(paCell)) {
                inRegion = true;
                //System.out.println("Matched paCellid = " + paCell);
                break;
            }
        }*/

        Map<Integer,Integer> sizeCountMap= Maps.newHashMap();
        StringBuilder sb3 = new StringBuilder();
        S2Region s2Region = S2Util.getS2RegionByCircle(lat,lng, r);
        List<S2CellId> cellIdListByPolygon = S2Util.getCompactCellIdList(s2Region);
        cellIdListByPolygon.forEach(s2CellId -> {
            System.out.println("Level:" + s2CellId.level() + ",ID:" + s2CellId.toToken() + ",Min:" + s2CellId.rangeMin().toToken() + ",Max:" + s2CellId.rangeMax().toToken());
            sb3.append(",").append(s2CellId.toToken());
            sizeCountMap.put(s2CellId.level(),sizeCountMap.getOrDefault(s2CellId.level(),0)+1);
        });
        System.out.println(sb3.substring(1));
        System.out.println("totalSize:"+cellIdListByPolygon.size());
        sizeCountMap.entrySet().forEach(integerIntegerEntry -> {
            System.out.printf("level:%d,size:%d\n",integerIntegerEntry.getKey(),integerIntegerEntry.getValue());
        });

        return cellId.id();
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

        HashMap<Integer, ArrayList<String>> stayroutePointMapList = InputFormatMy.trajAnalysis(denoiseList);

        HashMap<Integer, ArrayList<StayPoint>> stayroutePointMap = InputFormatMy.trajSegment(stayroutePointMapList);

        ArrayList<Observation> routePoint = null;
        ArrayList<Observation> sortedPoint = null;

        for (Integer key : stayroutePointMap.keySet()) {
            if (key > 0) {
/*                sortedPoint = multiPointSort(stayroutePointMap.get(key));

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

                Observation centroid = getWeightedLocation(stayroutePointMap.get(key));*/

                continue;
            }

            routePoint = InputFormatMy.trajReduce(stayroutePointMap.get(key));
            ArrayList<StayPoint> sp = stayroutePointMap.get(1);

            if (routePoint.isEmpty())
            {
                continue;
            }
            int startkey, endkey;
            //route points add stay points in head and tail

            if (key != -1) {
                startkey = -key -1;
            }
            else {
                startkey = -key;
            }

            if (key != -1) {
                endkey = -key + 1;
            }
            else {
                endkey = -key + 1;
            }

            ArrayList<StayPoint> head = stayroutePointMap.get(startkey);
            ArrayList<StayPoint> tail = stayroutePointMap.get(endkey);
            if (head != null) {
                java.util.Date dt = new Date(head.get(0).getStarttime() + head.get(0).getStaytime() * 60 * 1000);
                System.out.println(dt.toString());
                routePoint.add(0, new Observation(head.get(0).getPoint(),
                            head.get(0).getStarttime() + head.get(0).getStaytime() * 60 * 1000,
                            head.get(0).getRadius()
                    ));
            }
            if (tail != null) {
                routePoint.add(new Observation(tail.get(0).getPoint(),
                        tail.get(0).getStarttime(), tail.get(0).getRadius()));
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

            ArrayList <S2CellId> routes2cell = new ArrayList<>();

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

                S2LatLng s2LatLng = S2LatLng.fromDegrees(internalNodeId_lat, internalNodeId_lon);
                S2CellId cellId = S2CellId.fromLatLng(s2LatLng).parent(S2_Dim_Level);
                routes2cell.add(cellId);

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

            StringBuilder routes2sb = new StringBuilder();
            double s2celldis = 0;
            routes2sb.append("Route S2CellId : ");
            for (int i = 0; i < routes2cell.size(); i++) {
                routes2sb.append(routes2cell.get(i).id());
                routes2sb.append(", ");
                if (i < routes2cell.size() - 1) {
                    s2celldis += routes2cell.get(i).toPoint().getDistance(
                            routes2cell.get(i+1).toPoint()) * EARTH_RADIUS;
                }
            }
            System.out.println(routes2sb.toString());
            System.out.println("Route S2CellId Distance = " + s2celldis / 1000 + " Km");

            double delta = (routePoint.get(routePoint.size() - 1).getTime() - routePoint.get(0).getTime()) / 1000 / 60;
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            System.out.println("route distance = " +
                    String.format("%.2f", route_dis / 1000) + " Km" +
                    "   route time = " + String.format("%.2f", delta) + " m" +
                    "   speed = " + String.format("%.2f", route_dis * 60 / 1000 / delta ) + " Km/h" +
                    "   route start = " + df.format(routePoint.get(0).getTime()) +
                    "   route end = " + df.format(routePoint.get(routePoint.size() - 1).getTime()));

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
