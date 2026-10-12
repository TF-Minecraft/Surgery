package net.tfminecraft.surgery;

import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.listeners.PlayerListener;
import net.tfminecraft.surgery.managers.*;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryLifecycleTest {
    private ServerMock server;
    private MockedStatic<TLibs> tlibs;
    private TestCharacters characters;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("TLibs");
        MockBukkit.createMockPlugin("RPCharacters");
        characters = new TestCharacters();
        ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.PAPER));
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (SurgeryPlugin.getInstance() != null) HandlerList.unregisterAll(SurgeryPlugin.getInstance());
            // Restore MockBukkit's null post-close top inventories before plugin shutdown.
            for (Player player : server.getOnlinePlayers()) {
                player.openInventory(server.createInventory(null, 9));
            }
            characters.close();
            MockBukkit.unmock();
        } finally {
            if (tlibs != null) tlibs.close();
            var instance = SurgeryPlugin.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        }
    }

    private SurgeryMenuManager manager(SurgeryPlugin plugin) throws Exception {
        var field = SurgeryPlugin.class.getDeclaredField("surgeryMenuManager");
        field.setAccessible(true);
        return (SurgeryMenuManager) field.get(plugin);
    }

    private PlayerMock readyPlayer() {
        PlayerMock player = server.addPlayer();
        characters.activate(player);
        return player;
    }

    private void open(SurgeryMenuManager menus, Player surgeon, Player patient) {
        menus.openSurgeryMenu(surgeon, patient, new HealingInjury("leg", "Leg", 1_000));
    }

    @Test
    void enablesManagersRegistersCommandAndClearsOperationsAndOffersOnShutdown() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        assertSame(plugin, SurgeryPlugin.getInstance());
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("surgery").getExecutor());
        assertSame(plugin.getCommand("surgery").getExecutor(), plugin.getCommand("surgery").getTabCompleter());
        SurgeryMenuManager menus = manager(plugin);
        assertNotNull(menus.getUiUpdater());
        assertNotNull(menus.getStateManager());
        assertNotNull(menus.getRequestManager());
        assertNotNull(menus.getMenuBuilder());
        assertNotNull(menus.getCompletionHandler());
        assertNotNull(menus.getMechanicsManager());
        assertNotNull(menus.getItemHandler());
        PlayerMock surgeon = readyPlayer(), patient = readyPlayer(), other = readyPlayer();
        patient.openInventory(server.createInventory(null, 9));
        Inventory ordinary = server.createInventory(null, 9);
        other.openInventory(ordinary);
        open(menus, surgeon, patient);
        CharacterSession surgeonSession = Characters.currentSession(surgeon).orElseThrow();
        CharacterSession otherSession = Characters.currentSession(other).orElseThrow();
        menus.getRequestManager().offer(otherSession, surgeonSession, "arm", Long.MAX_VALUE);
        assertEquals(patient.getUniqueId(), menus.getStateManager().getPatientUuid(surgeon.getUniqueId()));
        assertEquals("leg", menus.getStateManager().getTraitId(surgeon.getUniqueId()));
        Inventory surgery = surgeon.getOpenInventory().getTopInventory();
        assertTrue(menus.isSurgeryMenu(surgery));
        assertFalse(menus.isSurgeryMenu(null));
        assertFalse(menus.isSurgeryMenu(ordinary));
        menus.handleItemClick(surgeon, surgery, null, 0);
        plugin.onDisable();
        assertFalse(menus.isSurgeryMenu(surgeon.getOpenInventory().getTopInventory()));
        assertSame(ordinary, other.getOpenInventory().getTopInventory());
        assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
        assertNull(menus.getRequestManager().pending(other.getUniqueId(), 0));
    }

    @Test
    void missingCharacterApiDisablesBeforeInitializationWithAnUpgradeMessage() {
        SurgeryPlugin startup = mock(SurgeryPlugin.class);
        org.bukkit.Server startupServer = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        when(startup.getServer()).thenReturn(startupServer);
        when(startupServer.getPluginManager()).thenReturn(plugins);
        when(startup.getLogger()).thenReturn(logger);
        doCallRealMethod().when(startup).onEnable();
        try (var unavailable = mockStatic(Characters.class)) {
            unavailable.when(Characters::available).thenThrow(new NoClassDefFoundError("CharacterIdentity"));
            startup.onEnable();
            verify(plugins).disablePlugin(startup);
            verify(logger).severe("Surgery requires TLibs 2.3.0 or newer. Update TLibs and restart the server.");
            verify(startup, never()).saveDefaultConfig();
            verify(startup, never()).getCommand(anyString());
        }
    }

    @Test
    void warnsAfterStartupOnlyWhenEnabledRPCharactersProvidesNoIdentity() {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        plugin.getLogger().addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel() == java.util.logging.Level.WARNING) warnings.add(record.getMessage());
            }
            @Override public void flush() {}
            @Override public void close() {}
        });
        server.getScheduler().performOneTick();
        assertTrue(warnings.isEmpty());
        characters.unregister();
        plugin.warnIfNoCharacterProvider();
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("RPCharacters 2.16.0 or newer"));
        server.getPluginManager().disablePlugin(server.getPluginManager().getPlugin("RPCharacters"));
        plugin.warnIfNoCharacterProvider();
        assertEquals(1, warnings.size());
    }

    @Test
    void missingSharedItemApiDisablesPluginBeforeCommandRegistration() {
        tlibs.when(TLibs::getItemAPI).thenReturn(null);
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        assertFalse(plugin.isEnabled());
    }

    @Test
    void menusWaitForBothReadyCharactersAndNeverUseProviderAbsenceAsReadiness() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        PlayerMock surgeon = server.addPlayer(), patient = server.addPlayer();
        assertTrue(plugin.isEnabled());
        open(menus, surgeon, patient);
        assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
        characters.activate(surgeon);
        open(menus, surgeon, patient);
        assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
        characters.activate(patient);
        characters.unregister();
        open(menus, surgeon, patient);
        assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
    }

    @ParameterizedTest
    @CsvSource({"patient,QUIT,true", "patient,SWITCH,true", "patient,DEATH,false", "patient,SHUTDOWN,false",
            "surgeon,QUIT,true", "surgeon,SWITCH,true", "surgeon,DEATH,true", "surgeon,SHUTDOWN,false"})
    void sessionEndUsesOutgoingIdentityAndAppliesOnlyTheAllowedPenalty(String participant, Reason reason,
                                                                      boolean penalized) throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        plugin.getConfig().set("commands.surgery-failure", "damage %player% 1");
        SurgeryMenuManager menus = manager(plugin);
        PlayerMock surgeon = readyPlayer(), patient = readyPlayer();
        try (MockedStatic<Ailments> ailments = mockStatic(Ailments.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            ailments.when(() -> Ailments.extend(any(), anyString(), anyLong())).thenReturn(1L);
            open(menus, surgeon, patient);
            menus.getStateManager().setOperated(surgeon.getUniqueId(), true);
            Player endingPlayer = participant.equals("patient") ? patient : surgeon;
            CharacterSession ending = characters.invalidate(endingPlayer);
            assertFalse(Characters.isCurrent(ending));
            CharacterSessionEndEvent event = new CharacterSessionEndEvent(endingPlayer, ending, reason);
            server.getPluginManager().callEvent(event);
            assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
            assertFalse(menus.isSurgeryMenu(surgeon.getOpenInventory().getTopInventory()));
            // Duplicate delivery must not apply another penalty.
            server.getPluginManager().callEvent(event);
            ailments.verify(() -> Ailments.extend(patient, "leg", 12 * 3_600_000L), times(penalized ? 1 : 0));
            bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
            server.getScheduler().performOneTick();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "surgeon"})
    void lateOldEndCannotRemoveNewOperationOrNewConsentAfterSwitchingBack(String participant) throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        PlayerMock surgeon = readyPlayer(), patient = readyPlayer();
        open(menus, surgeon, patient);
        Player actor = participant.equals("patient") ? patient : surgeon;
        CharacterSession original = characters.invalidate(actor);
        CharacterSessionEndEvent oldEnd = new CharacterSessionEndEvent(actor, original, Reason.SWITCH);
        server.getPluginManager().callEvent(oldEnd);
        characters.activate(actor);
        characters.activate(actor, original.ref().characterId());
        open(menus, surgeon, patient);
        long operation = menus.getStateManager().getOperationId(surgeon.getUniqueId());
        Inventory currentMenu = surgeon.getOpenInventory().getTopInventory();
        menus.getRequestManager().offer(Characters.currentSession(patient).orElseThrow(),
                Characters.currentSession(surgeon).orElseThrow(), "arm", Long.MAX_VALUE);
        SurgeryRequestManager.Request request = menus.getRequestManager().pending(patient.getUniqueId(), 0);
        assertNotNull(request);
        server.getPluginManager().callEvent(oldEnd);
        server.getScheduler().performOneTick();
        assertEquals(operation, menus.getStateManager().getOperationId(surgeon.getUniqueId()));
        assertSame(currentMenu, surgeon.getOpenInventory().getTopInventory());
        assertSame(request, menus.getRequestManager().pending(patient.getUniqueId(), 0));
    }

    @Test
    void staleOrForeignMenusCannotClickOrAbandonTheReplacementOperation() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        PlayerMock surgeon = readyPlayer(), patient = readyPlayer(), stranger = readyPlayer();
        open(menus, surgeon, patient);
        Inventory oldMenu = surgeon.getOpenInventory().getTopInventory();
        menus.handleSurgeryAbandonment(surgeon, oldMenu);
        open(menus, surgeon, patient);
        Inventory currentMenu = surgeon.getOpenInventory().getTopInventory();
        long operation = menus.getStateManager().getOperationId(surgeon.getUniqueId());
        menus.handleSurgeryAbandonment(surgeon, oldMenu);
        menus.handleItemClick(surgeon, oldMenu, new ItemStack(Material.PAPER), SurgeryTool.SCALPEL.getSlot());
        menus.handleSurgeryAbandonment(stranger, currentMenu);
        menus.handleItemClick(surgeon, server.createInventory(null, 9), new ItemStack(Material.PAPER), 0);
        assertEquals(operation, menus.getStateManager().getOperationId(surgeon.getUniqueId()));
        assertFalse(menus.getStateManager().hasOperated(surgeon.getUniqueId()));
        assertSame(currentMenu, surgeon.getOpenInventory().getTopInventory());
        // Neither duplicate opens nor a second surgeon may replace the operation.
        open(menus, surgeon, patient);
        open(menus, stranger, patient);
        assertEquals(operation, menus.getStateManager().getOperationId(surgeon.getUniqueId()));
        assertFalse(menus.getStateManager().hasSession(stranger.getUniqueId()));
        menus.handleSurgeryAbandonment(surgeon, currentMenu);
        assertFalse(menus.getStateManager().hasSession(surgeon.getUniqueId()));
    }

    @Test
    void patientEndCleansAnOperationWhoseSurgeonIsAlreadyAbsent() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        PlayerMock patient = readyPlayer();
        UUID surgeonId = UUID.randomUUID();
        CharacterSession ending = characters.invalidate(patient);
        menus.getStateManager().setPatientUuid(surgeonId, patient.getUniqueId());
        menus.getStateManager().setPatientSession(surgeonId, ending);
        server.getPluginManager().callEvent(new CharacterSessionEndEvent(patient, ending, Reason.QUIT));
        assertFalse(menus.getStateManager().hasSession(surgeonId));
    }

    @Test
    void sessionEndListenerRunsBeforeOtherCleanupAndRoutesTheExactEvent() throws Exception {
        EventHandler handler = PlayerListener.class.getMethod("onSessionEnd", CharacterSessionEndEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.LOWEST, handler.priority());
        SurgeryMenuManager menus = mock(SurgeryMenuManager.class);
        PlayerMock player = readyPlayer();
        CharacterSessionEndEvent event = new CharacterSessionEndEvent(player, characters.invalidate(player), Reason.QUIT);
        new PlayerListener(menus).onSessionEnd(event);
        verify(menus).handleSessionEnd(event);
    }

    @Test
    void listenersPreventMenuItemMovementAndRouteOnlyTopInventoryActions() {
        SurgeryMenuManager menus = mock(SurgeryMenuManager.class);
        PlayerListener listener = new PlayerListener(menus);
        PlayerMock player = readyPlayer();
        Inventory top = new SurgeryMenuHolder(player.getUniqueId(), 1).getInventory();
        top.setItem(1, new ItemStack(Material.PAPER));
        player.openInventory(top);
        when(menus.isSurgeryMenu(top)).thenReturn(true);
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(click);
        assertTrue(click.isCancelled());
        verify(menus).handleItemClick(player, top, top.getItem(1), 1);
        InventoryClickEvent bottom = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                54, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(bottom);
        assertTrue(bottom.isCancelled());
        InventoryClickEvent outside = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.OUTSIDE,
                -999, ClickType.LEFT, InventoryAction.NOTHING);
        listener.onInventoryClick(outside);
        assertTrue(outside.isCancelled());
        verify(menus, times(1)).handleItemClick(any(), any(), any(), anyInt());
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getInventory()).thenReturn(top);
        listener.onInventoryDrag(drag);
        verify(drag).setCancelled(true);
        listener.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
        verify(menus).handleSurgeryAbandonment(player, top);
        when(menus.isSurgeryMenu(top)).thenReturn(false);
        click.setCancelled(false);
        listener.onInventoryClick(click);
        assertFalse(click.isCancelled());
        listener.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
        verify(menus, times(1)).handleSurgeryAbandonment(player, top);
        clearInvocations(drag);
        listener.onInventoryDrag(drag);
        verify(drag, never()).setCancelled(anyBoolean());
    }
}
