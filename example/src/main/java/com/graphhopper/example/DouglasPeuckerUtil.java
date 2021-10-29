package com.graphhopper.example;

import com.graphhopper.matching.Observation;
import java.util.ArrayList;
import java.util.List;

public class DouglasPeuckerUtil {
    public static List<Observation> DouglasPeucker(List<Observation> points, double epsilon) {
        // 找到最大阈值点，即操作（1）
        double maxH = 0;
        int index = 0;
        int end = points.size();
        for (int i = 1; i < end - 1; i++) {
            double h = H(points.get(i), points.get(0), points.get(end - 1));
            if (h > maxH) {
                maxH = h;
                index = i;
            }
        }

        // 如果存在最大阈值点，就进行递归遍历出所有最大阈值点
        List<Observation> result = new ArrayList<>();
        if (maxH > epsilon) {
            List<Observation> leftPoints = new ArrayList<>();// 左曲线
            List<Observation> rightPoints = new ArrayList<>();// 右曲线
            // 分别提取出左曲线和右曲线的坐标点
            for (int i = 0; i < end; i++) {
                if (i <= index) {
                    leftPoints.add(points.get(i));
                    if (i == index)
                        rightPoints.add(points.get(i));
                } else {
                    rightPoints.add(points.get(i));
                }
            }

            // 分别保存两边遍历的结果
            List<Observation> leftResult = new ArrayList<>();
            List<Observation> rightResult = new ArrayList<>();
            leftResult = DouglasPeucker(leftPoints, epsilon);
            rightResult = DouglasPeucker(rightPoints, epsilon);

            // 将两边的结果整合
            rightResult.remove(0);
            leftResult.addAll(rightResult);
            result = leftResult;
        } else {// 如果不存在最大阈值点则返回当前遍历的子曲线的起始点
            result.add(points.get(0));
            result.add(points.get(end - 1));
        }
        return result;
    }

    /**
     * 计算点到直线的距离
     *
     * @param p
     * @param s
     * @param e
     * @return
     */
    public static double H(Observation p, Observation s, Observation e) {
        double AB = getDistance(s, e);
        double CB = getDistance(p, s);
        double CA = getDistance(p, e);

        double S = helen(CB, CA, AB);
        double H = 2 * S / AB;

        return H;
    }

    /**
     * 海伦公式，已知三边求三角形面积
     *
     * @param CB
     * @param CA
     * @param AB
     * @return 面积
     */
    public static double helen(double CB, double CA, double AB) {
        double p = (CB + CA + AB) / 2;
        double S = Math.sqrt(p * (p - CB) * (p - CA) * (p - AB));
        return S;
    }

    public static double getDistance (Observation a1, Observation a2) {
        double lon1 = a1.getPoint().getLon() * Math.PI /180;
        double lat1 = a1.getPoint().getLat() * Math.PI /180;
        double lon2 = a2.getPoint().getLon() * Math.PI /180;
        double lat2 = a2.getPoint().getLat() * Math.PI /180;
        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return c * 6371 * 1000;
    }
}
