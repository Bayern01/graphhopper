package com.graphhopper.example;

public class Tuple2<A, B> {
    private A a;
    private B b;

    public static Tuple2<Double, Double> tuple(Double a, Double b) {
        Tuple2 tuple2 = new Tuple2();
        tuple2.setA(a);
        tuple2.setB(b);
        return tuple2;
    }

    private void setA(A a) {
        this.a = a;
    }

    private void setB(B b) {
        this.b = b;
    }

    public A getVal1() {
        return a;
    }

    public B getVal2() {
        return b;
    }

}
