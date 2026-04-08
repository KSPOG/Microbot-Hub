package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script.MScript;

import javax.inject.Inject;

public class KSPAccountBuilderScript extends Script {

    private final MScript miningScript;

    @Inject
    public KSPAccountBuilderScript(MScript miningScript) {
        this.miningScript = miningScript;
    }

    public boolean run(KSPAccountBuilderConfig config) {
        return miningScript.run();
    }

    @Override
    public void shutdown() {
        miningScript.shutdown();
        super.shutdown();
    }
}
