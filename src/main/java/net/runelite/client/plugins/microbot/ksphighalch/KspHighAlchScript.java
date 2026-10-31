package net.runelite.client.plugins.microbot.ksphighalch;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspHighAlchScript extends Script
{
    private static final String NATURE_RUNE = "Nature rune";
    private static final String FIRE_RUNE = "Fire rune";
    private static final String STAFF_OF_FIRE = "Staff of fire";
    private static final String BRYOPHYTAS_STAFF = "Bryophyta's staff";

    private KspHighAlchConfig config;
    private KspHighAlchState state = KspHighAlchState.VALIDATE_CONFIG;

    public boolean run(KspHighAlchConfig config)
    {
        this.config = config;
        this.state = KspHighAlchState.VALIDATE_CONFIG;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn())
                {
                    return;
                }

                switch (state)
                {
                    case VALIDATE_CONFIG:
                        validateConfig();
                        break;
                    case BANKING:
                        doBanking();
                        break;
                    case ALCHING:
                        doAlching();
                        break;
                    case STOPPED:
                    default:
                        shutdown();
                        break;
                }
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace("KspHighAlchScript", ex);
                state = KspHighAlchState.STOPPED;
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown()
    {
        state = KspHighAlchState.STOPPED;
        super.shutdown();
    }

    private void validateConfig()
    {
        if (configuredItems().isEmpty())
        {
            status("No high alch items configured. Shift-right-click an inventory item and select Set High Alch Item.");
            state = KspHighAlchState.STOPPED;
            return;
        }

        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < 55)
        {
            status("Magic level is below 55; High Level Alchemy is unavailable.");
            state = KspHighAlchState.STOPPED;
            return;
        }

        state = hasAlchItemInInventory() && hasNatureRunesForAtLeastOneCast() && hasFireSourceForAtLeastOneCast()
                ? KspHighAlchState.ALCHING
                : KspHighAlchState.BANKING;
    }

    private void doAlching()
    {
        Rs2ItemModel target = firstInventoryAlchItem();
        if (target == null)
        {
            state = config.bankWhenOut() ? KspHighAlchState.BANKING : KspHighAlchState.STOPPED;
            return;
        }

        if (!hasNatureRunesForAtLeastOneCast() || !hasFireSourceForAtLeastOneCast())
        {
            state = KspHighAlchState.BANKING;
            return;
        }

        int min = Math.min(config.delayMin(), config.delayMax());
        int max = Math.max(config.delayMin(), config.delayMax());
        status("High alching " + target.getName());
        Rs2Magic.alch(target, min, max);
        Rs2Player.waitForAnimation();
    }

    private void doBanking()
    {
        if (!config.bankWhenOut())
        {
            state = KspHighAlchState.STOPPED;
            return;
        }

        if (!Rs2Bank.isOpen())
        {
            BankLocation nearestBank = Rs2Bank.getNearestBank(Rs2Player.getWorldLocation());
            if (nearestBank != null && nearestBank.getWorldPoint() != null)
            {
                status("Walking to nearest bank: " + nearestBank.name());
                Rs2Walker.walkTo(nearestBank.getWorldPoint(), 5);
            }

            if (!Rs2Bank.openBank())
            {
                status("Unable to open nearest bank.");
                return;
            }

            if (!sleepUntil(Rs2Bank::isOpen, 5000))
            {
                return;
            }
        }

        status("Preparing inventory");
        prepareRunesAndStaff();
        withdrawConfiguredAlchItems();

        if (!hasAlchItemInInventory())
        {
            status("No configured alch items found in bank/inventory.");
            state = KspHighAlchState.STOPPED;
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        state = KspHighAlchState.ALCHING;
    }

    private void prepareRunesAndStaff()
    {
        // Prefer a useful staff if available. Staff of fire removes the Fire rune requirement.
        // Bryophyta's staff is equipped when available, but Fire runes are still kept unless another source supplies Fire.
        if (!hasEquippedFireStaff() && Rs2Bank.hasBankItem(STAFF_OF_FIRE, 1, true))
        {
            status("Withdrawing/equipping Staff of fire");
            Rs2Bank.withdrawAndEquip(STAFF_OF_FIRE);
            sleepUntil(() -> Rs2Equipment.isWearing(STAFF_OF_FIRE), 2500);
        }
        else if (!Rs2Equipment.isWearing(BRYOPHYTAS_STAFF) && Rs2Bank.hasBankItem(BRYOPHYTAS_STAFF, 1, true))
        {
            status("Withdrawing/equipping Bryophyta's staff");
            Rs2Bank.withdrawAndEquip(BRYOPHYTAS_STAFF);
            sleepUntil(() -> Rs2Equipment.isWearing(BRYOPHYTAS_STAFF), 2500);
        }

        withdrawRuneIfNeeded(NATURE_RUNE, config.withdrawNatureRunes(), false);
        if (!hasEquippedFireStaff())
        {
            withdrawRuneIfNeeded(FIRE_RUNE, config.withdrawFireRunes(), false);
        }
    }

    private void withdrawRuneIfNeeded(String runeName, int targetAmount, boolean exact)
    {
        if (targetAmount <= 0)
        {
            if (!Rs2Inventory.hasItem(runeName) && Rs2Bank.hasBankItem(runeName, 1, exact))
            {
                status("Withdrawing all " + runeName + "s");
                Rs2Bank.withdrawAll(runeName, exact);
            }
            return;
        }

        int current = Rs2Inventory.itemQuantity(runeName);
        if (current >= targetAmount)
        {
            return;
        }

        int deficit = targetAmount - current;
        if (Rs2Bank.hasBankItem(runeName, deficit, exact))
        {
            status("Withdrawing " + deficit + " x " + runeName);
            Rs2Bank.withdrawX(runeName, deficit, exact);
            sleepUntil(() -> Rs2Inventory.itemQuantity(runeName) >= targetAmount, 2500);
        }
    }

    private void withdrawConfiguredAlchItems()
    {
        for (String itemName : configuredItems())
        {
            if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(itemName, true))
            {
                return;
            }

            if (Rs2Bank.hasBankItem(itemName, 1, true))
            {
                status("Withdrawing alch item: " + itemName);
                Rs2Bank.withdrawAll(itemName, true);
                sleepUntil(() -> Rs2Inventory.hasItem(itemName, true) || Rs2Inventory.isFull(), 2500);
            }
        }
    }

    private Rs2ItemModel firstInventoryAlchItem()
    {
        Set<String> configured = configuredItemsLowercase();
        return Rs2Inventory.items(item -> configured.contains(item.getName().toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAlchItemInInventory()
    {
        return firstInventoryAlchItem() != null;
    }

    private boolean hasNatureRunesForAtLeastOneCast()
    {
        return Rs2Inventory.itemQuantity(NATURE_RUNE) > 0;
    }

    private boolean hasFireSourceForAtLeastOneCast()
    {
        return hasEquippedFireStaff() || Rs2Inventory.itemQuantity(FIRE_RUNE) >= 5;
    }

    private boolean hasEquippedFireStaff()
    {
        return Rs2Equipment.isWearing(STAFF_OF_FIRE);
    }

    private List<String> configuredItems()
    {
        return Arrays.stream(config.alchItems().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<String> configuredItemsLowercase()
    {
        return configuredItems().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void status(String message)
    {
        Microbot.status = message;
        if (config != null && config.debugLogging())
        {
            Microbot.log("[KspHighAlch] " + message);
        }
    }
}
