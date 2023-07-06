package org.voitac.anticheat.utils.tuple;

public class Triplet<A, B, C> {
    private final A a;

    private final B b;

    private final C c;

    public Triplet(final A a, final B b, final C c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }

    public C getC() {
        return c;
    }
}
