package net.tfminecraft.surgery;

import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.rpcharacters.lifecycle.CharacterActivatedEvent;
import net.tfminecraft.rpcharacters.objects.RPCharacter;
import net.tfminecraft.rpcharacters.permadeath.CharacterPermakillEvent;
import net.tfminecraft.rpcharacters.permadeath.PermakillCause;
import net.tfminecraft.surgery.listeners.PlayerListener;
import net.tfminecraft.surgery.managers.*;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("TLibs");
        MockBukkit.createMockPlugin("RPCharacters");
        ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.PAPER));
        tlibs = mockStatic(TLibs.class);
        tlibs.when(TLibs::getItemAPI).thenReturn(api);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            // MockBukkit leaves the top inventory null after closeInventory;
            // the live server restores the player's crafting inventory.
            for (Player player : server.getOnlinePlayers()) {
                player.openInventory(server.createInventory(null, 9));
            }
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

    @Test
    void enablesManagersRegistersCommandAndClosesOnlySurgeryMenusOnShutdown() throws Exception {
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
        PlayerMock surgeon = server.addPlayer(), patient = server.addPlayer(), other = server.addPlayer();
        patient.openInventory(server.createInventory(null, 9));
        Inventory ordinary = server.createInventory(null, 9);
        other.openInventory(ordinary);
        menus.openSurgeryMenu(surgeon, patient, new HealingInjury("leg", "Leg", 1_000));
        assertEquals(patient.getUniqueId(), menus.getStateManager().getPatientUuid(surgeon.getUniqueId()));
        assertEquals("leg", menus.getStateManager().getTraitId(surgeon.getUniqueId()));
        assertTrue(menus.isSurgeryMenu(surgeon.getOpenInventory().getTopInventory()));
        assertFalse(menus.isSurgeryMenu(null));
        assertFalse(menus.isSurgeryMenu(ordinary));
        menus.handleItemClick(surgeon, null, 0);
        plugin.onDisable();
        assertFalse(menus.isSurgeryMenu(surgeon.getOpenInventory().getTopInventory()));
        assertSame(ordinary, other.getOpenInventory().getTopInventory());
    }

    @Test
    void missingSharedApiDisablesPluginBeforeCommandRegistration() {
        tlibs.when(TLibs::getItemAPI).thenReturn(null);
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        assertFalse(plugin.isEnabled());
    }

    @Test
    void managerDelegatesAbandonmentAndDisconnectsWithoutLeakingSessions() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        SurgeryStateManager state = menus.getStateManager();
        PlayerMock surgeon = server.addPlayer(), patient = server.addPlayer();
        menus.handlePatientQuit(patient);
        state.setPatientName(surgeon.getUniqueId(), "Patient");
        menus.handleSurgeryAbandonment(surgeon);
        assertFalse(state.hasSession(surgeon.getUniqueId()));
        state.setPatientName(surgeon.getUniqueId(), "Patient");
        menus.handleSurgeonQuit(surgeon);
        assertFalse(state.hasSession(surgeon.getUniqueId()));
        state.setPatientUuid(surgeon.getUniqueId(), patient.getUniqueId());
        menus.handlePatientQuit(patient);
        assertFalse(state.hasSession(surgeon.getUniqueId()));
        UUID missingSurgeon = UUID.randomUUID();
        state.setPatientUuid(missingSurgeon, patient.getUniqueId());
        menus.handlePatientQuit(patient);
        assertFalse(state.hasSession(missingSurgeon));
        server.getScheduler().performOneTick();
    }

    @Test
    void characterChangeEndsTheOperationForSurgeonOrSwitchedPatient() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        SurgeryMenuManager menus = manager(plugin);
        SurgeryStateManager state = menus.getStateManager();
        PlayerMock surgeon = server.addPlayer(), patient = server.addPlayer();
        menus.openSurgeryMenu(surgeon, patient, new HealingInjury("leg", "Leg", 1_000));
        assertNull(state.getPatientCharacterId(surgeon.getUniqueId()));
        menus.handleCharacterActivated(patient.getUniqueId(), null);
        assertTrue(state.hasSession(surgeon.getUniqueId()));
        menus.handleCharacterActivated(patient.getUniqueId(), "other");
        assertFalse(state.hasSession(surgeon.getUniqueId()));
        surgeon.assertSaid(menus.getUiUpdater().getMessage("surgery-failed"));
        server.getScheduler().performOneTick();

        menus.openSurgeryMenu(surgeon, patient, new HealingInjury("leg", "Leg", 1_000));
        menus.handleCharacterActivated(surgeon.getUniqueId(), "other");
        assertFalse(state.hasSession(surgeon.getUniqueId()));
        assertFalse(menus.isSurgeryMenu(surgeon.getOpenInventory().getTopInventory()));
        UUID offlineSurgeon = UUID.randomUUID();
        state.setPatientName(offlineSurgeon, "Patient");
        menus.handleCharacterActivated(offlineSurgeon, "other");
        assertFalse(state.hasSession(offlineSurgeon));
        server.getScheduler().performOneTick();

        SurgeryMenuManager mocked = mock(SurgeryMenuManager.class);
        RPCharacter character = mock(RPCharacter.class);
        when(character.getId()).thenReturn("next");
        new PlayerListener(mocked).onCharacterActivated(
                new CharacterActivatedEvent(patient, patient.getUniqueId(), character, null));
        verify(mocked).handleCharacterActivated(patient.getUniqueId(), "next");
    }

    private CharacterPermakillEvent permakill(Player player, String characterId) {
        RPCharacter character = mock(RPCharacter.class);
        when(character.getId()).thenReturn(characterId);
        return new CharacterPermakillEvent(player, character, PermakillCause.OTHER);
    }

    @Test
    void permakillEndsOnlyTheDyingCharactersOperation() throws Exception {
        SurgeryPlugin plugin = MockBukkit.load(SurgeryPlugin.class);
        plugin.getConfig().set("commands.surgery-failure", "damage %player% 1");
        SurgeryMenuManager menus = manager(plugin);
        SurgeryStateManager state = menus.getStateManager();
        PlayerMock surgeon = server.addPlayer(), patient = server.addPlayer();
        UUID surgeonId = surgeon.getUniqueId();
        HealingInjury leg = new HealingInjury("leg", "Leg", 1_000);
        try (MockedStatic<Ailments> ailments = mockStatic(Ailments.class, CALLS_REAL_METHODS);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            ailments.when(() -> Ailments.characterId(patient)).thenReturn("patient");
            ailments.when(() -> Ailments.characterId(surgeon)).thenReturn("doctor");
            ailments.when(() -> Ailments.extend(any(), anyString(), anyLong())).thenReturn(1L);
            menus.openSurgeryMenu(surgeon, patient, leg);
            assertEquals("doctor", state.getSurgeonCharacterId(surgeonId));
            state.setOperated(surgeonId, true);

            // Another of the patient's characters dying leaves the operation alone
            server.getPluginManager().callEvent(permakill(patient, "spare"));
            CharacterPermakillEvent cancelled = permakill(patient, "patient");
            cancelled.setCancelled(true);
            server.getPluginManager().callEvent(cancelled);
            assertTrue(state.hasSession(surgeonId));

            // The patient's last character dies and nobody takes over
            server.getPluginManager().callEvent(permakill(patient, "patient"));
            assertFalse(state.hasSession(surgeonId));
            surgeon.assertSaid(menus.getUiUpdater().getMessage("surgery-failed"));
            ailments.verify(() -> Ailments.extend(any(), anyString(), anyLong()), never());
            server.getScheduler().performOneTick();

            // The surgeon dies; the unchanged patient still takes the penalty
            menus.openSurgeryMenu(surgeon, patient, leg);
            state.setOperated(surgeonId, true);
            server.getPluginManager().callEvent(permakill(surgeon, "doctor"));
            assertFalse(state.hasSession(surgeonId));
            ailments.verify(() -> Ailments.extend(patient, "leg", 12 * 3_600_000L));
            bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
            server.getScheduler().performOneTick();
        }
    }

    @Test
    void quitRunsBeforeRPCharactersUnloadsTheCharacter() throws Exception {
        EventHandler handler = PlayerListener.class.getMethod("onPlayerQuit", PlayerQuitEvent.class)
                .getAnnotation(EventHandler.class);
        assertEquals(EventPriority.LOW, handler.priority());
    }

    @Test
    void listenersPreventMenuItemMovementAndRouteOnlyTopInventoryActions() {
        SurgeryMenuManager menus = mock(SurgeryMenuManager.class);
        SurgeryRequestManager requests = new SurgeryRequestManager();
        when(menus.getRequestManager()).thenReturn(requests);
        PlayerListener listener = new PlayerListener(menus);
        PlayerMock player = server.addPlayer();
        Inventory top = new SurgeryMenuHolder().getInventory();
        top.setItem(1, new ItemStack(Material.PAPER));
        player.openInventory(top);
        when(menus.isSurgeryMenu(top)).thenReturn(true);
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(click);
        assertTrue(click.isCancelled());
        verify(menus).handleItemClick(player, top.getItem(1), 1);
        InventoryClickEvent bottom = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                54, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(bottom);
        assertTrue(bottom.isCancelled());
        InventoryClickEvent outside = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.OUTSIDE,
                -999, ClickType.LEFT, InventoryAction.NOTHING);
        listener.onInventoryClick(outside);
        assertTrue(outside.isCancelled());
        verify(menus, times(1)).handleItemClick(any(), any(), anyInt());
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getInventory()).thenReturn(top);
        listener.onInventoryDrag(drag);
        verify(drag).setCancelled(true);
        listener.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
        verify(menus).handleSurgeryAbandonment(player);
        requests.offer(player.getUniqueId(), UUID.randomUUID(), "leg", Long.MAX_VALUE);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onPlayerQuit(quit);
        verify(menus).handleSurgeonQuit(player);
        verify(menus).handlePatientQuit(player);
        assertNull(requests.pending(player.getUniqueId(), 0));

        when(menus.isSurgeryMenu(top)).thenReturn(false);
        click.setCancelled(false);
        listener.onInventoryClick(click);
        assertFalse(click.isCancelled());
        listener.onInventoryClose(new InventoryCloseEvent(player.getOpenInventory()));
        verify(menus, times(1)).handleSurgeryAbandonment(player);
        clearInvocations(drag);
        listener.onInventoryDrag(drag);
        verify(drag, never()).setCancelled(anyBoolean());
    }
}
