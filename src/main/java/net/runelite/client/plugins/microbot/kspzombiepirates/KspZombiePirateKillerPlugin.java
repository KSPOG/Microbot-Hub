package net.runelite.client.plugins.microbot.kspzombiepirates;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.UUID;

@PluginDescriptor(
        name = PluginConstants.KSP + "Zombie ¨Pirates",
        description = "Kills Zombie pirates",
        tags = {"microbot", "ksp", "zombie", "pirate", "killer", "hotreload"},
        authors = {"KSP"},
        version = KspZombiePirateKillerPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = false,
        isExternal = true
)
@Slf4j
public class KspZombiePirateKillerPlugin extends Plugin {
    public static final String VERSION = "0.0.1";
    @Inject
    private KspZombiePirateKillerScript script;

    @Inject
    private KspZombiePirateKillerConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    private KspZombiePirateKillerPanel panel;
    private NavigationButton navButton;
    private volatile boolean licenseValidated;

    @Provides
    KspZombiePirateKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KspZombiePirateKillerConfig.class);
    }

    @Override
    protected void startUp() {
        licenseValidated = false;
        panel = new KspZombiePirateKillerPanel(config, configManager, this);

        navButton = NavigationButton.builder()
                .tooltip("KSP Zombie Pirates")
                .icon(createPanelIcon())
                .priority(7)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

        panel.setLicenseState(false, "Validate your license key to unlock settings.");

        if (!isBlank(config.licenseKey())) {
            validateLicense(config.licenseKey());
        }
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        licenseValidated = false;

        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        panel = null;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!licenseValidated) {
            return;
        }

        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
            return;
        }

        final String message = event.getMessage() == null ? "" : event.getMessage().toLowerCase();
        if (message.contains("looting bag") && message.contains("full")) {
            script.markLootingBagFull();
        }
    }

    void validateLicense(String licenseKey) {
        if (isBlank(licenseKey)) {
            setLicenseState(false, "License key is empty.");
            return;
        }

        final String validationUrl = resolveValidationUrl();
        final String deviceId = resolveDeviceId();

        configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, "licenseKey", licenseKey.trim());
        setLicenseState(false, "Validating license...");

        Thread validator = new Thread(() -> {
            KspZombiePirateLicenseClient.LicenseResult result = KspZombiePirateLicenseClient.validate(validationUrl, licenseKey.trim(), deviceId);

            SwingUtilities.invokeLater(() -> {
                setLicenseState(result.isValid(), result.getMessage());
                if (!result.isValid()) {
                    script.shutdown();
                }
            });
        }, "ksp-zombie-pirates-license-validator");
        validator.setDaemon(true);
        validator.start();
    }

    private String resolveValidationUrl() {
        String validationUrl = config.licenseValidationUrl();
        if (isBlank(validationUrl)) {
            validationUrl = "https://zombiepirates-license.ultraflipss500.workers.dev/";
            configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, "licenseValidationUrl", validationUrl);
        }
        return validationUrl.trim();
    }

    private String resolveDeviceId() {
        String deviceId = config.deviceId();
        if (isBlank(deviceId)) {
            deviceId = UUID.randomUUID().toString();
            configManager.setConfiguration(KspZombiePirateKillerConfig.GROUP, "deviceId", deviceId);
        }
        return deviceId.trim();
    }

    boolean isLicenseValidated() {
        return licenseValidated;
    }

    void startScriptFromPanel() {
        if (!licenseValidated) {
            setLicenseState(false, "Validate your license key before starting.");
            return;
        }

        if (script.getState() == KspZombiePirateKillerState.STOPPED) {
            script.run(config);
            setLicenseState(true, "Started.");
        } else {
            setLicenseState(true, "Already running.");
        }
    }

    void stopScriptFromPanel() {
        script.shutdown();
        if (licenseValidated) {
            setLicenseState(true, "Stopped.");
        }
    }

    private void setLicenseState(boolean valid, String message) {
        licenseValidated = valid;
        if (panel != null) {
            panel.setLicenseState(valid, message);
        }
    }

    private BufferedImage createPanelIcon() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        try {
            graphics.setColor(new Color(80, 160, 90));
            graphics.fillOval(1, 1, 14, 14);
            graphics.setColor(Color.BLACK);
            graphics.drawString("Z", 5, 12);
        } finally {
            graphics.dispose();
        }
        return icon;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
