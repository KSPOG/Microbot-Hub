package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class KspAccountBuilderScript extends Script
{
    private enum BuilderState
    {
        PREPARE,
        TRAIN_STARTER,
        TRAIN_SKILLER,
        RUN_QUEST_ROUTINE,
        COMPLETE
    }

    @Getter
    private BuilderState state = BuilderState.PREPARE;

    private long lastStatusLogAt;

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        state = BuilderState.PREPARE;
        lastStatusLogAt = 0L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn() || !config.enabled())
            {
                return;
            }

            processStateMachine(config);
            maybeLogStatus(config);
        }, 0, config.tickDelayMs(), TimeUnit.MILLISECONDS);

        return true;
    }

    private void processStateMachine(KspAccountBuilderConfig config)
    {
        switch (state)
        {
            case PREPARE:
                transitionState(resolveInitialState(config.mode()), config);
                break;
            case TRAIN_STARTER:
                runStarterRoutine(config);
                break;
            case TRAIN_SKILLER:
                runSkillerRoutine(config);
                break;
            case RUN_QUEST_ROUTINE:
                runQuestRoutine(config);
                break;
            case COMPLETE:
                break;
            default:
                log.warn("Unknown account builder state: {}", state);
                transitionState(BuilderState.COMPLETE, config);
                break;
        }
    }

    private BuilderState resolveInitialState(KspAccountBuilderMode mode)
    {
        switch (mode)
        {
            case STARTER:
                return BuilderState.TRAIN_STARTER;
            case SKILLER:
                return BuilderState.TRAIN_SKILLER;
            case QUESTER:
                return BuilderState.RUN_QUEST_ROUTINE;
            default:
                return BuilderState.COMPLETE;
        }
    }

    private void runStarterRoutine(KspAccountBuilderConfig config)
    {
        // TODO: implement starter progression tasks.
        transitionState(BuilderState.COMPLETE, config);
    }

    private void runSkillerRoutine(KspAccountBuilderConfig config)
    {
        // TODO: implement skiller progression tasks.
        transitionState(BuilderState.COMPLETE, config);
    }

    private void runQuestRoutine(KspAccountBuilderConfig config)
    {
        // TODO: implement quest routine tasks.
        transitionState(BuilderState.COMPLETE, config);
    }

    private void transitionState(BuilderState nextState, KspAccountBuilderConfig config)
    {
        if (state == nextState)
        {
            return;
        }

        if (config.verboseLogging())
        {
            log.info("KSP Account Builder state transition: {} -> {}", state, nextState);
        }

        state = nextState;
    }

    private void maybeLogStatus(KspAccountBuilderConfig config)
    {
        if (!config.verboseLogging())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastStatusLogAt >= 10_000)
        {
            log.info("KSP Account Builder running in mode {} at state {}", config.mode(), state);
            lastStatusLogAt = now;
        }
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
    }
}
