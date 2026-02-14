package net.runelite.client.plugins.microbot.KSPAccountBuilder;

import net.runelite.api.Skill;

public enum KSPAccountBuilderMeleeSkill {
    ANY(null),
    ATTACK(Skill.ATTACK),
    STRENGTH(Skill.STRENGTH),
    DEFENCE(Skill.DEFENCE);

    private final Skill skill;

    KSPAccountBuilderMeleeSkill(Skill skill) {
        this.skill = skill;
    }

    public Skill getSkill() {
        return skill;
    }
}

