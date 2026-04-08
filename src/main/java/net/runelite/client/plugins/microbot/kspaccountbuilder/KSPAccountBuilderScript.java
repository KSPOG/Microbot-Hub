package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.kspaccountbuilder.mining.script.MScript;

import javax.inject.Inject;

public class KSPAccountBuilderScript {
    private final MScript miningScript;

    @Inject
    public KSPAccountBuilderScript(MScript miningScript) {
        this.miningScript = miningScript;
    }

    public boolean run(KSPAccountBuilderConfig config) {
        return miningScript.run(config);
    }

    public void shutdown() {
        miningScript.shutdown();
    }
}
