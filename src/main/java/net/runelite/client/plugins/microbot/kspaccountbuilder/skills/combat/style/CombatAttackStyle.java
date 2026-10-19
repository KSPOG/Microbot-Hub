package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.style;

import net.runelite.api.Skill;

public enum CombatAttackStyle {
    ACCURATE(Skill.ATTACK),
    AGGRESSIVE(Skill.STRENGTH),
    DEFENSIVE(Skill.DEFENCE),
    CONTROLLED(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
    OTHER();

    private final Skill[] skills;

    CombatAttackStyle(Skill... skills) {
        this.skills = skills;
    }

    public boolean trains(Skill skill) {
        for (Skill value : skills) {
            if (value == skill) {
                return true;
            }
        }
        return false;
    }
}
