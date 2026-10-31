package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

final class MiningBankRetryPolicy
{
    static final long NORMAL_COOLDOWN_MS = 500L;
    static final long FAILURE_COOLDOWN_MS = 5_000L;

    private MiningBankRetryPolicy() {}

    static boolean canAttempt(long nowMs, long lastActionAtMs, long lastFailureAtMs)
    {
        if (nowMs < lastActionAtMs || nowMs - lastActionAtMs < NORMAL_COOLDOWN_MS) return false;
        return lastFailureAtMs <= 0L || (nowMs >= lastFailureAtMs
                && nowMs - lastFailureAtMs >= FAILURE_COOLDOWN_MS);
    }
}
