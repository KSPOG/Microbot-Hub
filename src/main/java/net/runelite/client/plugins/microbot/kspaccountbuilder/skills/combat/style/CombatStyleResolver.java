package net.runelite.client.plugins.microbot.kspaccountbuilder.skills.combat.style;

import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;

public class CombatStyleResolver {

    public Skill getLowestMeleeSkill() {
        int attack = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int strength = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
        int defence = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        Skill target = Skill.STRENGTH;
        if (attack <= strength && attack <= defence) {
            target = Skill.ATTACK;
        } else if (defence <= attack && defence <= strength) {
            target = Skill.DEFENCE;
        }

        return target;
    }

    public int findStyleIndexForSkill(Skill skill) {
        CombatAttackStyle[] styles = getWeaponTypeStyles();
        for (int i = 0; i < styles.length && i < 4; i++) {
            CombatAttackStyle style = styles[i];
            if (style == null || style == CombatAttackStyle.CONTROLLED) {
                continue;
            }
            if (style.trains(skill)) {
                return i;
            }
        }
        return -1;
    }

    private CombatAttackStyle[] getWeaponTypeStyles() {
        int weaponType = Microbot.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
        var weaponStylesEnum = Microbot.getEnum(EnumID.WEAPON_STYLES);
        if (weaponStylesEnum == null) {
            return new CombatAttackStyle[0];
        }

        int styleStructEnumId = weaponStylesEnum.getIntValue(weaponType);
        var styleStructEnum = Microbot.getEnum(styleStructEnumId);
        if (styleStructEnum == null || styleStructEnum.getIntVals() == null) {
            return new CombatAttackStyle[0];
        }

        int[] structs = styleStructEnum.getIntVals();
        CombatAttackStyle[] styles = new CombatAttackStyle[structs.length];
        for (int i = 0; i < structs.length; i++) {
            String styleName = Microbot.getStructComposition(structs[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);
            styles[i] = parseAttackStyle(styleName);
        }

        return styles;
    }

    public WidgetInfo mapStyleWidget(int styleIndex) {
        if (styleIndex == 0) return WidgetInfo.COMBAT_STYLE_ONE;
        if (styleIndex == 1) return WidgetInfo.COMBAT_STYLE_TWO;
        if (styleIndex == 2) return WidgetInfo.COMBAT_STYLE_THREE;
        return WidgetInfo.COMBAT_STYLE_FOUR;
    }

    private CombatAttackStyle parseAttackStyle(String styleName) {
        if (styleName == null || styleName.isBlank()) {
            return CombatAttackStyle.OTHER;
        }

        try {
            return CombatAttackStyle.valueOf(styleName.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return CombatAttackStyle.OTHER;
        }
    }
}
