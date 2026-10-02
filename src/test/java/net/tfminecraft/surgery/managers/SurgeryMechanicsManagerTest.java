package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Complication;
import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SurgeryMechanicsManagerTest {
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private YamlConfiguration config;
    private SurgeryStateManager state;
    private SurgeryUIUpdater ui;
    private SurgeryCompletionHandler completion;
    private SurgeryMechanicsManager mechanics;
    private ItemAPI api;
    private Player player;
    private Inventory menu;
    private World world;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        config = new YamlConfiguration();
        config.set("pulse.degradation-chance-bleeding", 0.0);
        config.set("complications.shock.collapse-chance", 0.0);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        state = new SurgeryStateManager();
        state.setStatus(id, "Unconscious");
        state.setOperationSite(id, "Clean");
        ui = mock(SurgeryUIUpdater.class);
        when(ui.getMessage(anyString())).thenAnswer(call -> call.getArgument(0));
        when(ui.getMessage(anyString(), anyString())).thenAnswer(call -> call.getArgument(0));
        completion = mock(SurgeryCompletionHandler.class);
        api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.PAPER));
        SurgeryItemsConfig items = mock(SurgeryItemsConfig.class);
        when(items.getItemPath(any())).thenAnswer(call -> ((SurgeryTool) call.getArgument(0)).getDefaultPath());
        menu = server.createInventory(null, 54);
        world = server.addSimpleWorld("world");
        player = mock(Player.class);
        InventoryView view = mock(InventoryView.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(view.getTopInventory()).thenReturn(menu);
        mechanics = new SurgeryMechanicsManager(plugin, api, state, ui, completion, items);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void move() {
        mechanics.processMoveEffects(player, SurgeryTool.SUTURE);
    }

    private void procedure(int incisions, int broken, int shattered, Complication... complications) {
        state.setAilment(id, "trait", "Ailment", new Procedure("Procedure", incisions, broken, shattered, Set.of(complications)));
    }

    @Test
    void ordinaryMoveAdvancesCountersAndResetsResolvedDangerCounters() {
        state.setMoveCount(id, 2);
        state.setMovesSinceLastSponge(id, 3);
        state.setRedTempCounter(id, 1);
        state.setExtremelyWeakCounter(id, 1);
        move();
        assertEquals(3, state.getMoveCount(id));
        assertEquals(4, state.getMovesSinceLastSponge(id));
        assertEquals(0, state.getRedTempCounter(id));
        assertEquals(0, state.getExtremelyWeakCounter(id));
        assertEquals(98.6, state.getTemperature(id));
        verifyNoInteractions(completion);
    }

    @Test
    void anestheticProgressesThroughComingToAndAwakeAtConfiguredBoundaries() {
        config.set("anesthetic.unconscious-moves", 2);
        config.set("anesthetic.coming-to-moves", 2);
        state.setUnconsciousTimer(id, 0);
        move();
        assertEquals("Unconscious", state.getStatus(id));
        move();
        assertEquals("Coming to", state.getStatus(id));
        move();
        assertEquals("Coming to", state.getStatus(id));
        move();
        assertEquals("Awake", state.getStatus(id));
        assertEquals(4, state.getUnconsciousTimer(id));
        verify(ui).updateStatusBlock(menu, id, "Coming to");
        verify(ui).updateStatusBlock(menu, id, "Awake");
        verify(ui).sendNumberedMessage(player, "patient-coming-to");
        verify(ui).sendNumberedMessage(player, "patient-woke-up");
    }

    @Test
    void collapsedPatientHasAFullResuscitationWindow() {
        state.setStatus(id, "Collapsed");
        state.setCollapseCountdown(id, 2);
        move();
        assertEquals(1, state.getCollapseCountdown(id));
        mechanics.processMoveEffects(player, SurgeryTool.SMELLING_SALTS);
        assertEquals(1, state.getCollapseCountdown(id));
        verifyNoInteractions(completion);
        move();
        verify(completion).failSurgery(player, "failure-not-resuscitated");
    }

    @Test
    void missingCollapseCountdownDoesNotInventADeadline() {
        state.setStatus(id, "Collapsed");
        move();
        assertNull(state.getCollapseCountdown(id));
        verifyNoInteractions(completion);
    }

    @Test
    void temperatureAtInstantDeathThresholdFailsBeforeFurtherEffects() {
        state.setTemperature(id, 110.0);
        move();
        verify(completion).failSurgery(player, "failure-infection");
        verify(ui, never()).updateTemperatureBlock(any(), any(), anyDouble());
    }

    @Test
    void redFeverFailsOnlyAfterTheConfiguredConsecutiveMoves() {
        state.setTemperature(id, 107.0);
        move();
        move();
        verifyNoInteractions(completion);
        assertEquals(2, state.getRedTempCounter(id));
        move();
        verify(completion).failSurgery(player, "failure-high-fever");
    }

    @Test
    void bleedingWeakensPulseAndEventuallyCausesBleedOut() {
        config.set("pulse.degradation-chance-bleeding", 1.0);
        state.setBleeding(id, true);
        move();
        assertEquals("Steady", state.getPulse(id));
        verify(ui).updatePulseBlock(menu, id, "Steady");
        verify(ui).sendNumberedMessage(player, "pulse-weakening");
        state.setPulse(id, "Extremely Weak");
        move();
        verify(completion).failSurgery(player, "failure-bled-out");
    }

    @Test
    void prolongedExtremelyWeakPulseFailsEvenWithoutActiveBleeding() {
        state.setPulse(id, "Extremely Weak");
        move();
        move();
        verifyNoInteractions(completion);
        assertEquals(2, state.getExtremelyWeakCounter(id));
        move();
        verify(completion).failSurgery(player, "failure-weak-pulse");
    }

    @Test
    void dirtyOpenWoundsRaiseFeverAndClampItAtDeathThreshold() {
        state.setOperationSite(id, "Unclean");
        state.setIncisions(id, 1);
        state.setTemperature(id, 109.0);
        move();
        assertEquals(110.0, state.getTemperature(id));
        verify(ui).updateTemperatureBlock(menu, id, 110.0);
        verifyNoInteractions(completion);
        move();
        verify(completion).failSurgery(player, "failure-infection");
    }

    @Test
    void sepsisRaisesTemperatureEvenAtACleanSiteAndProtectionDelaysIt() {
        state.setHasRisingTemp(id, true);
        state.setAntisepticProtection(id, true);
        move();
        assertEquals(98.6, state.getTemperature(id));
        state.setOperationSite(id, "Unclean");
        move();
        assertFalse(state.hasAntisepticProtection(id));
        verify(ui).sendNumberedMessage(player, "protection-lost");
        move();
        assertEquals(100.4, state.getTemperature(id), 0.0001);
    }

    @Test
    void bleedingDirtySiteRaisesFeverWithoutAnIncision() {
        state.setOperationSite(id, "Unclean");
        state.setBleeding(id, true);
        move();
        assertEquals(100.4, state.getTemperature(id), 0.0001);
    }

    @Test
    void patientMustRemainOnlineInTheSameWorldAndInsideConfiguredRange() {
        UUID patientId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        state.setPatientUuid(id, patientId);
        Player patient = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            move();
            verify(completion).failSurgery(player, "failure-patient-left");
            assertEquals(0, state.getMoveCount(id));
            clearInvocations(completion);
            bukkit.when(() -> Bukkit.getPlayer(patientId)).thenReturn(patient);
            move();
            verify(completion).failSurgery(player, "failure-patient-left");
            clearInvocations(completion);
            when(patient.isOnline()).thenReturn(true);
            when(patient.getWorld()).thenReturn(mock(World.class));
            move();
            verify(completion).failSurgery(player, "failure-patient-left");
            clearInvocations(completion);
            when(patient.getWorld()).thenReturn(world);
            when(patient.getLocation()).thenReturn(new Location(world, 10, 64, 0));
            move();
            assertEquals(1, state.getMoveCount(id));
            verifyNoInteractions(completion);
            when(patient.getLocation()).thenReturn(new Location(world, 10.01, 64, 0));
            move();
            verify(completion).failSurgery(player, "failure-patient-left");
        }
    }

    @Test
    void complicationsWaitForExaminationThenHaemorrhageAndShockAddTools() {
        procedure(2, 0, 0, Complication.HAEMORRHAGE, Complication.SHOCK);
        config.set("complications.haemorrhage.bleeding-interval-min", 2);
        config.set("complications.haemorrhage.bleeding-interval-max", 2);
        config.set("complications.shock.collapse-chance", 1.0);
        state.setIncisions(id, 2);
        move();
        assertFalse(state.isBleeding(id));
        state.setExamined(id, true);
        move();
        assertTrue(state.isBleeding(id));
        assertEquals("Collapsed", state.getStatus(id));
        assertEquals(2, state.getCollapseCountdown(id));
        assertNotNull(menu.getItem(SurgeryTool.ARTERY_FORCEPS.getSlot()));
        assertNotNull(menu.getItem(SurgeryTool.SMELLING_SALTS.getSlot()));
        verify(ui).sendNumberedMessage(player, "haemorrhage");
        verify(ui).sendNumberedMessage(player, "shock-collapse");
    }

    @Test
    void haemorrhageDoesNotStartBeforeMinimumInterval() {
        procedure(2, 0, 0, Complication.HAEMORRHAGE);
        state.setExamined(id, true);
        move();
        assertFalse(state.isBleeding(id));
    }

    @Test
    void dressingWaitsForExaminationIncisionsAndAllBoneRepairs() {
        mechanics.checkForDressing(player, menu, id, 2);
        procedure(2, 1, 1);
        mechanics.checkForDressing(player, menu, id, 2);
        state.setExamined(id, true);
        mechanics.checkForDressing(player, menu, id, 1);
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        state.setBrokenBones(id, 1);
        mechanics.checkForDressing(player, menu, id, 2);
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        state.setBrokenBones(id, 0);
        state.setShatteredBones(id, 1);
        mechanics.checkForDressing(player, menu, id, 2);
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        state.setShatteredBones(id, 0);
        mechanics.checkForDressing(player, menu, id, 2);
        assertNotNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        mechanics.checkForDressing(player, menu, id, 2);
        verify(ui, times(1)).sendNumberedMessage(player, "dressing-ready");
        menu.clear();
        state.setCured(id, true);
        mechanics.checkForDressing(player, menu, id, 2);
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
    }

    @Test
    void missingConfiguredDressingDoesNotAnnounceAnUnavailableTool() {
        procedure(1, 0, 0);
        state.setExamined(id, true);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(null);
        mechanics.checkForDressing(player, menu, id, 1);
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        verify(ui, never()).sendNumberedMessage(player, "dressing-ready");
    }

    @Test
    void boneRevealRequiresExamAndEnoughIncisionsAndIsIdempotent() {
        mechanics.handleBoneReveal(player, menu, id, 3);
        procedure(2, 0, 0);
        mechanics.handleBoneReveal(player, menu, id, 3);
        procedure(2, 1, 2);
        state.setBrokenBones(id, 1);
        state.setShatteredBones(id, 2);
        mechanics.handleBoneReveal(player, menu, id, 3);
        assertEquals(0, state.getRevealedBrokenBones(id));
        state.setExamined(id, true);
        mechanics.handleBoneReveal(player, menu, id, 1);
        assertEquals(0, state.getRevealedShatteredBones(id));
        mechanics.handleBoneReveal(player, menu, id, 3);
        assertEquals(1, state.getRevealedBrokenBones(id));
        assertEquals(2, state.getRevealedShatteredBones(id));
        assertNotNull(menu.getItem(SurgeryTool.SPLINT.getSlot()));
        assertNotNull(menu.getItem(SurgeryTool.SILVER_WIRE.getSlot()));
        mechanics.handleBoneReveal(player, menu, id, 3);
        verify(ui, times(1)).sendNumberedMessage(player, "discovered-broken-bone");
        verify(ui, times(1)).sendNumberedMessage(player, "discovered-shattered-bone");
    }

    @Test
    void dynamicToolsDisappearWhenTheirConditionsResolve() {
        state.setStatus(id, "Collapsed");
        state.setRevealedBrokenBones(id, 1);
        state.setRevealedShatteredBones(id, 1);
        state.setIncisions(id, 2);
        state.setBleeding(id, true);
        mechanics.updateDynamicTools(player, menu, id);
        for (SurgeryTool tool : new SurgeryTool[]{SurgeryTool.SMELLING_SALTS, SurgeryTool.SPLINT,
                SurgeryTool.SILVER_WIRE, SurgeryTool.ARTERY_FORCEPS}) {
            assertNotNull(menu.getItem(tool.getSlot()));
        }
        state.setStatus(id, "Unconscious");
        state.setRevealedBrokenBones(id, 0);
        state.setRevealedShatteredBones(id, 0);
        state.setBleeding(id, false);
        mechanics.updateDynamicTools(player, menu, id);
        for (SurgeryTool tool : new SurgeryTool[]{SurgeryTool.SMELLING_SALTS, SurgeryTool.SPLINT,
                SurgeryTool.SILVER_WIRE, SurgeryTool.ARTERY_FORCEPS}) {
            assertNull(menu.getItem(tool.getSlot()));
        }
        state.setStatus(id, "Collapsed");
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(null);
        mechanics.updateDynamicTools(player, menu, id);
        assertNull(menu.getItem(SurgeryTool.SMELLING_SALTS.getSlot()));
    }
}
