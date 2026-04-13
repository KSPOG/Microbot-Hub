package net.runelite.client.plugins.microbot.ksplamps;

import net.runelite.api.Skill;

public enum LampSkillOption {
    LOWEST("Lowest level skill", null),
    ATTACK(Skill.ATTACK),
    STRENGTH(Skill.STRENGTH),
    DEFENCE(Skill.DEFENCE),
    RANGED(Skill.RANGED),
    PRAYER(Skill.PRAYER),
    MAGIC(Skill.MAGIC),
    RUNECRAFT(Skill.RUNECRAFT),
    CONSTRUCTION(Skill.CONSTRUCTION),
    HITPOINTS(Skill.HITPOINTS),
    AGILITY(Skill.AGILITY),
    HERBLORE(Skill.HERBLORE),
    THIEVING(Skill.THIEVING),
    CRAFTING(Skill.CRAFTING),
    FLETCHING(Skill.FLETCHING),
    SLAYER(Skill.SLAYER),
    HUNTER(Skill.HUNTER),
    MINING(Skill.MINING),
    SMITHING(Skill.SMITHING),
    FISHING(Skill.FISHING),
    COOKING(Skill.COOKING),
    FIREMAKING(Skill.FIREMAKING),
    WOODCUTTING(Skill.WOODCUTTING),
    FARMING(Skill.FARMING);

    private final String displayName;
    private final Skill skill;

    LampSkillOption(String displayName, Skill skill) {
        this.displayName = displayName;
        this.skill = skill;
    }

    LampSkillOption(Skill skill) {
        this(skill.getName(), skill);
    }

    public Skill getSkill() {
        return skill;
    }

    @Override
    public String toString() {
        return displayName;
    }
}