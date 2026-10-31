package net.runelite.client.plugins.microbot.ksppetnames;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;

/**
 * Handles pet detection and profile-scoped persistence.
 *
 * Names are intentionally stored through ConfigManager's RuneScape-profile
 * methods. This means Account A and Account B can assign different nicknames
 * to the same OSRS pet without overwriting each other.
 */
@Singleton
public class KspPetNamesService
{
    static final String CONFIG_GROUP = "ksppetnames";
    private static final String NAMES_KEY = "petNamesV1";
    private static final int MAX_NICKNAME_LENGTH = 30;

    private final Client client;
    private final ConfigManager configManager;

    /*
     * Overlay rendering happens every frame. Keep the decoded map cached and
     * invalidate it whenever RuneLite changes the active RuneScape profile.
     */
    private volatile Map<String, PetNameEntry> cachedEntries;

    @Inject
    public KspPetNamesService(Client client, ConfigManager configManager)
    {
        this.client = client;
        this.configManager = configManager;
    }

    public PetSnapshot getCurrentPet()
    {
        NPC follower = client.getFollower();
        if (follower == null)
        {
            return PetSnapshot.none();
        }

        String gameName = follower.getName();
        if (gameName == null || gameName.trim().isEmpty())
        {
            gameName = "Pet";
        }

        String key = canonicalPetKey(gameName);
        PetNameEntry entry = entries().get(key);
        String nickname = entry == null ? "" : entry.getNickname();

        return new PetSnapshot(true, key, gameName, follower.getId(), nickname);
    }

    public String getNicknameFor(NPC follower)
    {
        if (follower == null)
        {
            return "";
        }

        String gameName = follower.getName();
        if (gameName == null || gameName.trim().isEmpty())
        {
            return "";
        }

        PetNameEntry entry = entries().get(canonicalPetKey(gameName));
        return entry == null ? "" : entry.getNickname();
    }

    public SaveResult saveCurrentPetName(String requestedName)
    {
        NPC follower = client.getFollower();
        if (follower == null)
        {
            return SaveResult.failure("No active follower/pet detected.");
        }

        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return SaveResult.failure("Log in before saving an account-bound pet name.");
        }

        String gameName = follower.getName();
        if (gameName == null || gameName.trim().isEmpty())
        {
            return SaveResult.failure("The active follower has no readable name.");
        }

        String nickname = requestedName == null ? "" : requestedName.trim();
        if (nickname.isEmpty())
        {
            return clearCurrentPetName();
        }

        if (nickname.length() > MAX_NICKNAME_LENGTH)
        {
            return SaveResult.failure("Pet names are limited to " + MAX_NICKNAME_LENGTH + " characters.");
        }

        Map<String, PetNameEntry> entries = mutableEntries();
        entries.put(canonicalPetKey(gameName), new PetNameEntry(gameName, nickname));
        persist(entries);

        return SaveResult.success("Saved \"" + nickname + "\" for " + gameName + " on this account.");
    }

    public SaveResult clearCurrentPetName()
    {
        NPC follower = client.getFollower();
        if (follower == null)
        {
            return SaveResult.failure("No active follower/pet detected.");
        }

        String gameName = follower.getName();
        if (gameName == null || gameName.trim().isEmpty())
        {
            return SaveResult.failure("The active follower has no readable name.");
        }

        Map<String, PetNameEntry> entries = mutableEntries();
        PetNameEntry removed = entries.remove(canonicalPetKey(gameName));
        persist(entries);

        return SaveResult.success(removed == null
            ? "This pet did not have a saved name on the active account."
            : "Cleared the saved name for " + gameName + " on this account.");
    }

    public SaveResult deleteSavedPet(String petKey)
    {
        if (petKey == null || petKey.trim().isEmpty())
        {
            return SaveResult.failure("No saved pet selected.");
        }

        Map<String, PetNameEntry> entries = mutableEntries();
        PetNameEntry removed = entries.remove(petKey);
        if (removed == null)
        {
            return SaveResult.failure("That saved pet name is no longer present.");
        }

        persist(entries);
        return SaveResult.success("Deleted " + removed.getGameName() + " -> " + removed.getNickname() + ".");
    }

    public List<SavedPet> getSavedPets()
    {
        List<SavedPet> result = new ArrayList<>();
        for (Map.Entry<String, PetNameEntry> entry : entries().entrySet())
        {
            result.add(new SavedPet(entry.getKey(), entry.getValue().getGameName(), entry.getValue().getNickname()));
        }

        result.sort(Comparator.comparing(SavedPet::getGameName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public void onProfileChanged()
    {
        cachedEntries = null;
    }

    private Map<String, PetNameEntry> mutableEntries()
    {
        return new LinkedHashMap<>(entries());
    }

    private Map<String, PetNameEntry> entries()
    {
        Map<String, PetNameEntry> local = cachedEntries;
        if (local != null)
        {
            return local;
        }

        synchronized (this)
        {
            local = cachedEntries;
            if (local == null)
            {
                local = loadEntriesFromActiveProfile();
                cachedEntries = local;
            }
        }

        return local;
    }

    private Map<String, PetNameEntry> loadEntriesFromActiveProfile()
    {
        String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, NAMES_KEY);
        Map<String, PetNameEntry> entries = new LinkedHashMap<>();

        if (raw == null || raw.isEmpty())
        {
            return entries;
        }

        String[] lines = raw.split("\\n");
        for (String line : lines)
        {
            if (line == null || line.isEmpty())
            {
                continue;
            }

            String[] parts = line.split("\\t", -1);
            if (parts.length != 2)
            {
                continue;
            }

            try
            {
                String gameName = decode(parts[0]);
                String nickname = decode(parts[1]);

                if (!gameName.trim().isEmpty() && !nickname.trim().isEmpty())
                {
                    entries.put(canonicalPetKey(gameName), new PetNameEntry(gameName, nickname));
                }
            }
            catch (IllegalArgumentException ignored)
            {
                // Ignore malformed legacy/corrupt rows instead of breaking the overlay.
            }
        }

        return entries;
    }

    private void persist(Map<String, PetNameEntry> entries)
    {
        if (entries.isEmpty())
        {
            configManager.unsetRSProfileConfiguration(CONFIG_GROUP, NAMES_KEY);
            cachedEntries = new LinkedHashMap<>();
            return;
        }

        StringBuilder out = new StringBuilder();
        for (PetNameEntry entry : entries.values())
        {
            if (out.length() > 0)
            {
                out.append('\n');
            }

            out.append(encode(entry.getGameName()))
                .append('\t')
                .append(encode(entry.getNickname()));
        }

        configManager.setRSProfileConfiguration(CONFIG_GROUP, NAMES_KEY, out.toString());
        cachedEntries = new LinkedHashMap<>(entries);
    }

    static String canonicalPetKey(String gameName)
    {
        if (gameName == null)
        {
            return "";
        }

        return gameName
            .toLowerCase(Locale.ROOT)
            .replace('\u00A0', ' ')
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replace(' ', '_');
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class PetNameEntry
    {
        private final String gameName;
        private final String nickname;

        private PetNameEntry(String gameName, String nickname)
        {
            this.gameName = gameName;
            this.nickname = nickname;
        }

        String getGameName()
        {
            return gameName;
        }

        String getNickname()
        {
            return nickname;
        }
    }

    public static final class PetSnapshot
    {
        private static final PetSnapshot NONE = new PetSnapshot(false, "", "", -1, "");

        private final boolean present;
        private final String petKey;
        private final String gameName;
        private final int npcId;
        private final String nickname;

        private PetSnapshot(boolean present, String petKey, String gameName, int npcId, String nickname)
        {
            this.present = present;
            this.petKey = petKey;
            this.gameName = gameName;
            this.npcId = npcId;
            this.nickname = nickname;
        }

        static PetSnapshot none()
        {
            return NONE;
        }

        public boolean isPresent()
        {
            return present;
        }

        public String getPetKey()
        {
            return petKey;
        }

        public String getGameName()
        {
            return gameName;
        }

        public int getNpcId()
        {
            return npcId;
        }

        public String getNickname()
        {
            return nickname;
        }
    }

    public static final class SavedPet
    {
        private final String petKey;
        private final String gameName;
        private final String nickname;

        SavedPet(String petKey, String gameName, String nickname)
        {
            this.petKey = petKey;
            this.gameName = gameName;
            this.nickname = nickname;
        }

        public String getPetKey()
        {
            return petKey;
        }

        public String getGameName()
        {
            return gameName;
        }

        public String getNickname()
        {
            return nickname;
        }

        @Override
        public String toString()
        {
            return gameName + "  ->  " + nickname;
        }
    }

    public static final class SaveResult
    {
        private final boolean success;
        private final String message;

        private SaveResult(boolean success, String message)
        {
            this.success = success;
            this.message = message;
        }

        static SaveResult success(String message)
        {
            return new SaveResult(true, message);
        }

        static SaveResult failure(String message)
        {
            return new SaveResult(false, message);
        }

        public boolean isSuccess()
        {
            return success;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
