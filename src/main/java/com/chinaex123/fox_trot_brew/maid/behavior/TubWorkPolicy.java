package com.chinaex123.fox_trot_brew.maid.behavior;

/** Pure decision rules shared by the behavior and offline regression checks. */
public final class TubWorkPolicy {
    public enum Action { NONE, COLLECT, PRESS, FEED }
    private TubWorkPolicy() {}

    public static Action choose(boolean full, boolean canCollect, boolean canPress, boolean canFeed) {
        if (full) return canCollect ? Action.COLLECT : Action.NONE;
        if (canPress) return Action.PRESS;
        return canFeed ? Action.FEED : Action.NONE;
    }

    public static boolean expired(long visitTicks, long idleTicks) {
        return visitTicks >= 240 || idleTicks >= 80;
    }
}
