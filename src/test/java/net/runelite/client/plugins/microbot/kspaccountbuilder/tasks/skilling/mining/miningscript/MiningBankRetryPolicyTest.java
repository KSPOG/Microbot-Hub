package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningBankRetryPolicyTest
{
    @Test
    void firstAttemptIsPermitted()
    {
        assertTrue(MiningBankRetryPolicy.canAttempt(10_000L, 0L, 0L));
    }

    @Test
    void successfulAttemptUsesNormalCooldown()
    {
        assertFalse(MiningBankRetryPolicy.canAttempt(10_499L, 10_000L, 0L));
        assertTrue(MiningBankRetryPolicy.canAttempt(10_500L, 10_000L, 0L));
    }

    @Test
    void failedAttemptUsesLongerBackoff()
    {
        assertFalse(MiningBankRetryPolicy.canAttempt(10_500L, 10_000L, 10_000L));
        assertFalse(MiningBankRetryPolicy.canAttempt(14_999L, 10_000L, 10_000L));
        assertTrue(MiningBankRetryPolicy.canAttempt(15_000L, 10_000L, 10_000L));
    }

    @Test
    void successClearsFailureBackoff()
    {
        assertTrue(MiningBankRetryPolicy.canAttempt(20_500L, 20_000L, 0L));
    }

    @Test
    void clockRollbackDoesNotPermitEarlyRetry()
    {
        assertFalse(MiningBankRetryPolicy.canAttempt(9_000L, 10_000L, 0L));
    }
}
