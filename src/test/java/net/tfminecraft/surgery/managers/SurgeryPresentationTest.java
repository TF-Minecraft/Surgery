package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryPresentationTest {
    @TempDir Path directory;
    private ServerMock server;
    private JavaPlugin plugin;
    private YamlConfiguration config;
    private Logger logger;
    private SurgeryStateManager state;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getResourceAsStream("/" + call.getArgument(0)));
        doAnswer(call -> {
            String resource = call.getArgument(0);
            try (InputStream in = plugin.getResource(resource)) {
                Files.copy(in, directory.resolve(resource));
            }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        state = new SurgeryStateManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void statusColorsAndProgressionDescribeAllClinicalStates() {
        new SurgeryConstants();
        String[] pulses = {"Strong", "Steady", "Weak", "Extremely Weak", "unknown"};
        Material[] colors = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
                Material.ORANGE_CONCRETE, Material.RED_CONCRETE, Material.GRAY_CONCRETE};
        String[] improved = {"Strong", "Strong", "Steady", "Weak", "Strong"};
        String[] worsened = {"Steady", "Weak", "Extremely Weak", "Extremely Weak", "Extremely Weak"};
        for (int i = 0; i < pulses.length; i++) {
            assertEquals(colors[i], SurgeryConstants.getPulseColor(pulses[i]));
            assertEquals(improved[i], SurgeryConstants.improvePulse(pulses[i]));
            assertEquals(worsened[i], SurgeryConstants.worsenPulse(pulses[i]));
        }
        String[] statuses = {"Unconscious", "Awake", "Coming to", "Collapsed", "unknown"};
        String[] sites = {"Clean", "Not sanitized", "Unclean", "Unsanitary", "unknown"};
        for (int i = 0; i < statuses.length; i++) {
            assertEquals(colors[i], SurgeryConstants.getStatusColor(statuses[i]));
            assertEquals(colors[i], SurgeryConstants.getOperationSiteColor(sites[i]));
        }
        double[] temperatures = {100, 100.1, 104, 104.1, 106, 106.1};
        Material[] temperatureColors = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE,
                Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.ORANGE_CONCRETE, Material.RED_CONCRETE};
        for (int i = 0; i < temperatures.length; i++) {
            assertEquals(temperatureColors[i], SurgeryConstants.getTemperatureColor(temperatures[i]));
        }
        assertEquals(Material.LIME_CONCRETE, SurgeryConstants.getIncisionColor(0));
        assertEquals(Material.YELLOW_CONCRETE, SurgeryConstants.getIncisionColor(1));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            assertEquals("98.6°F / 37.0°C", SurgeryConstants.formatTemperature(98.6));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void itemConfigCreatesDefaultsAndPreservesCustomPaths() throws Exception {
        SurgeryItemsConfig items = new SurgeryItemsConfig(plugin);
        assertTrue(Files.isRegularFile(directory.resolve("surgeryItemsConfig.yml")));
        for (SurgeryTool tool : SurgeryTool.values()) {
            assertEquals(tool.getDefaultPath(), items.getItemPath(tool));
            assertEquals(tool, SurgeryTool.fromSlot(tool.getSlot()));
        }
        assertNull(SurgeryTool.fromSlot(-1));
        assertNull(SurgeryTool.fromSlot(SurgeryConstants.SLOT_PULSE));
        Files.writeString(directory.resolve("surgeryItemsConfig.yml"), "items:\n  sponge: custom.sponge\n");
        items = new SurgeryItemsConfig(plugin);
        assertEquals("custom.sponge", items.getItemPath(SurgeryTool.SPONGE));
        assertEquals(SurgeryTool.SCALPEL.getDefaultPath(), items.getItemPath(SurgeryTool.SCALPEL));
    }

    @Test
    void itemConfigFallsBackIfBundledResourceIsMissingOrUnreadable() throws Exception {
        doReturn(null).when(plugin).getResource("surgeryItemsConfig.yml");
        assertEquals(SurgeryTool.SPONGE.getDefaultPath(), new SurgeryItemsConfig(plugin).getItemPath(SurgeryTool.SPONGE));
        InputStream broken = mock(InputStream.class);
        when(broken.transferTo(any())).thenThrow(new IOException("broken resource"));
        doReturn(broken).when(plugin).getResource("surgeryItemsConfig.yml");
        assertEquals(SurgeryTool.SPONGE.getDefaultPath(), new SurgeryItemsConfig(plugin).getItemPath(SurgeryTool.SPONGE));
        verify(logger).severe("Could not create surgeryItemsConfig.yml: broken resource");
        verify(broken).close();
    }

    @Test
    void messagesAndInfoBlocksShowTheCurrentState() throws Exception {
        SurgeryUIUpdater ui = new SurgeryUIUpdater(plugin, state);
        assertTrue(Files.exists(directory.resolve("messages.yml")));
        Files.writeString(directory.resolve("messages.yml"), "hello: '&aHello'\nlist: [one, two]\n");
        ui = new SurgeryUIUpdater(plugin, state);
        assertEquals("§aHello", ui.getMessage("hello"));
        assertEquals("", ui.getMessage("missing"));
        assertEquals("§cFallback", ui.getMessage("missing", "&cFallback"));
        assertEquals(List.of("one", "two"), ui.getMessageList("list"));
        ItemStack info = ui.createInfoBlock(Material.PAPER, "Name", "Description");
        assertEquals("Name", info.getItemMeta().getDisplayName());
        assertEquals(List.of("Description"), info.getItemMeta().getLore());
        assertEquals(Material.AIR, ui.createInfoBlock(Material.AIR, "Name", "Description").getType());
        assertEquals(Material.AIR, ui.createInfoBlock(Material.AIR, "Name", List.of("Line")).getType());
        PlayerMock player = server.addPlayer();
        UUID id = player.getUniqueId();
        Inventory menu = server.createInventory(null, 54);
        state.setAilment(id, "leg", "Broken leg", new Procedure("Bone repair", 2, 1, 2, Set.of()));
        ui.updateDiagnosisBlock(menu, id);
        assertEquals(Material.RED_CONCRETE, menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getType());
        state.setExamined(id, true);
        state.setRevealedBrokenBones(id, 1);
        state.setRevealedShatteredBones(id, 2);
        ui.updateDiagnosisBlock(menu, id);
        assertEquals(Material.YELLOW_CONCRETE, menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getType());
        assertTrue(menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getItemMeta().getLore().stream().anyMatch(s -> s.contains("Shattered Bones")));
        state.setCured(id, true);
        ui.updateDiagnosisBlock(menu, id);
        assertEquals(Material.LIME_CONCRETE, menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getType());
        assertTrue(menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getItemMeta().getLore().contains("§aTreated and dressed"));
        state.setAilment(id, "eye", "Eye", new Procedure("Eye repair", 1, 0, 0, Set.of()));
        ui.updateDiagnosisBlock(menu, id);
        assertFalse(menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getItemMeta().getLore().stream().anyMatch(s -> s.contains("Bones")));
        state.setAilment(id, "unknown", "Unknown", null);
        ui.updateDiagnosisBlock(menu, id);
        assertEquals(Material.RED_CONCRETE, menu.getItem(SurgeryConstants.SLOT_DIAGNOSIS).getType());
        ui.updateIncisionBlock(menu, id, 2);
        ui.updateTemperatureBlock(menu, id, 107);
        ui.updateOperationSiteBlock(menu, id, "Clean");
        ui.updateStatusBlock(menu, id, "Unconscious");
        ui.updatePulseBlock(menu, id, "Weak");
        assertEquals(Material.YELLOW_CONCRETE, menu.getItem(SurgeryConstants.SLOT_INCISIONS).getType());
        assertEquals(Material.RED_CONCRETE, menu.getItem(SurgeryConstants.SLOT_TEMPERATURE).getType());
        assertEquals(Material.LIME_CONCRETE, menu.getItem(SurgeryConstants.SLOT_OPERATION_SITE).getType());
        assertEquals(Material.LIME_CONCRETE, menu.getItem(SurgeryConstants.SLOT_STATUS).getType());
        assertEquals(Material.ORANGE_CONCRETE, menu.getItem(SurgeryConstants.SLOT_PULSE).getType());
        state.setMoveCount(id, 2);
        ui.sendNumberedMessage(player, "Message");
        assertEquals("§7§l[Move 3] §rMessage", player.nextMessage());
    }

    @Test
    void menusInitializePatientStateAndOnlyStartingTools() {
        SurgeryItemsConfig items = mock(SurgeryItemsConfig.class);
        when(items.getItemPath(any())).thenAnswer(c -> ((SurgeryTool) c.getArgument(0)).getDefaultPath());
        when(items.getItemPath(SurgeryTool.SPONGE)).thenReturn(null);
        ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.PAPER));
        when(api.getCreator().getItemFromPath(SurgeryTool.SCALPEL.getDefaultPath())).thenReturn(null);
        SurgeryUIUpdater ui = new SurgeryUIUpdater(plugin, state);
        SurgeryMenuBuilder builder = new SurgeryMenuBuilder(plugin, api, state, ui, items);
        PlayerMock player = server.addPlayer();
        ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        when(random.nextBoolean()).thenReturn(true, false);
        when(random.nextDouble()).thenReturn(0.5);
        config.set("temperature.rising-temp-min", 100);
        config.set("temperature.rising-temp-max", 104);
        try (MockedStatic<ThreadLocalRandom> randomness = mockStatic(ThreadLocalRandom.class)) {
            randomness.when(ThreadLocalRandom::current).thenReturn(random);
            builder.buildAndOpenMenu(player);
            Inventory menu = player.getOpenInventory().getTopInventory();
            assertInstanceOf(SurgeryMenuHolder.class, menu.getHolder());
            assertSame(menu, menu.getHolder().getInventory());
            assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
            assertNull(menu.getItem(SurgeryTool.SPONGE.getSlot()));
            assertEquals(Material.PAPER, menu.getItem(SurgeryTool.SUTURE.getSlot()).getType());
            assertEquals(102.0, state.getTemperature(player.getUniqueId()));
            assertTrue(state.hasRisingTemp(player.getUniqueId()));
            assertEquals("Awake", state.getStatus(player.getUniqueId()));
            assertFalse(state.isBleeding(player.getUniqueId()));
            assertFalse(state.isCured(player.getUniqueId()));
            assertEquals(0, state.getIncisions(player.getUniqueId()));
            assertEquals(0, state.getBrokenBones(player.getUniqueId()));
            assertEquals(0, state.getShatteredBones(player.getUniqueId()));
            builder.buildAndOpenMenu(player);
            assertFalse(state.hasRisingTemp(player.getUniqueId()));
            assertEquals(98.6, state.getTemperature(player.getUniqueId()));
        }
        verify(logger, times(2)).warning("[Surgery] Could not load item: null");
        verify(logger, times(2)).warning("[Surgery] Could not load item: " + SurgeryTool.SCALPEL.getDefaultPath());
    }
}
