package com.chinaex123.fox_trot_brew.maid.behavior;

import static com.chinaex123.fox_trot_brew.maid.behavior.TubWorkPolicy.Action.*;

/** No external test framework needed; invoked by Gradle check. */
public final class TubWorkPolicyTest {
    public static void main(String[] args) {
        expect(NONE, TubWorkPolicy.choose(false, false, false, false), "exhausted fruit with residual juice must stop");
        expect(FEED, TubWorkPolicy.choose(false, false, false, true), "residual juice can resume with compatible fruit");
        expect(COLLECT, TubWorkPolicy.choose(true, true, false, false), "full tub without fruit must be collected");
        expect(NONE, TubWorkPolicy.choose(true, false, true, true), "full tub without bucket/space must be skipped");
        expect(PRESS, TubWorkPolicy.choose(false, false, true, true), "existing fruit must be processed before adding more");
        expect(COLLECT, TubWorkPolicy.choose(true, true, true, true), "collection takes precedence over remaining fruit");
        expect(FEED, TubWorkPolicy.choose(false, false, false, true), "empty tub can be supplied");
        if (TubWorkPolicy.expired(239, 79)) throw new AssertionError("visit ended before boundary");
        if (!TubWorkPolicy.expired(240, 0)) throw new AssertionError("productive tub monopolizes maid");
        if (!TubWorkPolicy.expired(80, 80)) throw new AssertionError("stalled pressing never times out");
        System.out.println("10 fruit-tub decision and timeout regression checks passed.");
    }

    private static void expect(Object expected, Object actual, String scenario) {
        if (expected != actual) throw new AssertionError(scenario + ": " + actual);
    }
}
