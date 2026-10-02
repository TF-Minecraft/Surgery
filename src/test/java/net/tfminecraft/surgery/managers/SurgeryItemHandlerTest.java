package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Complication;
import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SurgeryItemHandlerTest {
    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private YamlConfiguration config;
    private SurgeryStateManager state;
    private SurgeryUIUpdater ui;
    private SurgeryMechanicsManager mechanics;
    private SurgeryCompletionHandler completion;
    private SurgeryItemHandler handler;
    private ItemAPI api;
    private Player player;
    private PlayerInventory inventory;
    private Inventory menu;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        config = new YamlConfiguration();
        config.set("skill-fail.base-chance", 0.0);
        config.set("skill-fail.bleeding-chance", 0.0);
        config.set("skill-fail.with-sponge-chance", 0.0);
        config.set("pulse.scalpel-decrease-chance", 0.0);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        state = new SurgeryStateManager();
        state.setStatus(id, "Unconscious");
        state.setOperationSite(id, "Clean");
        state.setAilment(id, "trait", "Ailment", new Procedure("Procedure", 2, 1, 1, Set.of()));
        ui = mock(SurgeryUIUpdater.class);
        when(ui.getMessage(anyString())).thenAnswer(call -> call.getArgument(0));
        when(ui.getMessageList(anyString())).thenAnswer(call -> List.of(call.getArgument(0, String.class)));
        when(ui.createInfoBlock(any(), anyString(), anyString())).thenReturn(new ItemStack(Material.PAPER));
        mechanics = mock(SurgeryMechanicsManager.class);
        completion = mock(SurgeryCompletionHandler.class);
        api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(new ItemStack(Material.PAPER));
        when(api.getChecker().checkItemWithPath(any(), anyString())).thenReturn(true);
        SurgeryItemsConfig items = mock(SurgeryItemsConfig.class);
        when(items.getItemPath(any())).thenAnswer(call -> ((SurgeryTool) call.getArgument(0)).getDefaultPath());
        menu = server.createInventory(null, 54);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        InventoryView view = mock(InventoryView.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getLocation()).thenReturn(new Location(server.addSimpleWorld("world"), 0, 64, 0));
        when(view.getTopInventory()).thenReturn(menu);
        when(inventory.getSize()).thenReturn(3);
        handler = new SurgeryItemHandler(plugin, api, state, ui, mechanics, completion, items);
        handler.initialize();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void click(SurgeryTool tool) {
        ItemStack stock = new ItemStack(Material.PAPER, 2);
        when(inventory.getItem(0)).thenReturn(stock);
        handler.handleItemClick(player, new ItemStack(Material.PAPER), tool.getSlot());
    }

    @Test
    void spongeProtectsExactlyTheNextAction() {
        state.setBleeding(id, true);
        state.setMovesSinceLastSponge(id, 4);
        click(SurgeryTool.SPONGE);
        assertFalse(state.isBleeding(id));
        assertEquals(0, state.getMovesSinceLastSponge(id));
        assertTrue(state.hasSpongeEffect(id), "A successful sponge must protect the next action");

        config.set("skill-fail.base-chance", 1.0);
        config.set("skill-fail.with-sponge-chance", 0.0);
        state.setTemperature(id, 109.0);
        click(SurgeryTool.TINCTURE);
        assertEquals(103.6, state.getTemperature(id), 0.0001);
        assertEquals("", state.getSkillFail(id));
        assertFalse(state.hasSpongeEffect(id), "The next action must consume the protection");

        click(SurgeryTool.TINCTURE);
        assertEquals(109.0, state.getTemperature(id), 0.0001);
        assertEquals("skill-fail-tincture", state.getSkillFail(id));
    }

    @Test
    void nonToolsAndEmptyClicksHaveNoEffects() {
        handler.handleItemClick(player, null, SurgeryTool.SPONGE.getSlot());
        handler.handleItemClick(player, new ItemStack(Material.AIR), SurgeryTool.SPONGE.getSlot());
        handler.handleItemClick(player, new ItemStack(Material.PAPER), SurgeryConstants.SLOT_DIAGNOSIS);
        verifyNoInteractions(mechanics, completion, inventory);
    }

    @Test
    void cuttingAnAwakePatientFailsBeforeConsumingATool() {
        state.setStatus(id, "Awake");
        click(SurgeryTool.SCALPEL);
        assertTrue(state.hasOperated(id));
        verify(completion).failSurgery(player, "failure-stabbed-awake");
        verify(inventory, never()).getItem(anyInt());
        verifyNoInteractions(mechanics);
    }

    @Test
    void inventoryConsumptionMatchesItemPathAndPreservesUnrelatedItems() {
        ItemStack unrelated = new ItemStack(Material.STONE, 8);
        ItemStack tool = new ItemStack(Material.PAPER, 3);
        when(inventory.getItem(1)).thenReturn(unrelated);
        when(inventory.getItem(2)).thenReturn(tool);
        when(api.getChecker().checkItemWithPath(unrelated, SurgeryTool.SUTURE.getDefaultPath())).thenReturn(false);
        handler.handleItemClick(player, new ItemStack(Material.PAPER), SurgeryTool.SUTURE.getSlot());
        assertEquals(8, unrelated.getAmount());
        assertEquals(2, tool.getAmount());
        verify(mechanics).processMoveEffects(player, SurgeryTool.SUTURE);
        tool.setAmount(1);
        handler.handleItemClick(player, new ItemStack(Material.PAPER), SurgeryTool.SUTURE.getSlot());
        verify(inventory).setItem(2, null);
    }

    @Test
    void missingToolReportsErrorWithoutAdvancingTheOperation() {
        handler.handleItemClick(player, new ItemStack(Material.PAPER), SurgeryTool.SUTURE.getSlot());
        verify(player).sendMessage("item-not-in-inventory");
        verify(player).playSound(any(Location.class), eq(Sound.ENTITY_VILLAGER_NO), eq(1.0f), eq(1.0f));
        verifyNoInteractions(mechanics, completion);
    }

    @Test
    void moveEffectsThatEndTheSessionPreventToolEffectsAndSuccessSounds() {
        doAnswer(call -> { state.cleanup(id); return null; }).when(mechanics).processMoveEffects(player, SurgeryTool.SUTURE);
        state.setIncisions(id, 2);
        click(SurgeryTool.SUTURE);
        assertFalse(state.hasSession(id));
        verify(ui, never()).updateIncisionBlock(any(), any(), anyInt());
        verify(player, never()).playSound(any(Location.class), eq(Sound.ENTITY_PLAYER_LEVELUP), anyFloat(), anyFloat());
    }

    @ParameterizedTest
    @EnumSource(SurgeryTool.class)
    void eachToolHasADeterministicFailureMessageAndBreakSound(SurgeryTool tool) {
        config.set("skill-fail.base-chance", 1.0);
        config.set("skill-fail.bleeding-chance", 1.0);
        config.set("skill-fail.with-sponge-chance", 1.0);
        click(tool);
        assertEquals("skill-fail-" + tool.getConfigKey(), state.getSkillFail(id));
        assertNotNull(menu.getItem(SurgeryConstants.SLOT_SKILL_FAIL));
        verify(player).playSound(any(Location.class), eq(Sound.ENTITY_ITEM_BREAK), eq(1.0f), eq(1.0f));
        verify(player, never()).playSound(any(Location.class), eq(Sound.ENTITY_PLAYER_LEVELUP), anyFloat(), anyFloat());
        switch (tool) {
            case SCALPEL -> { assertEquals("Steady", state.getPulse(id)); assertEquals(0, state.getIncisions(id)); }
            case TINCTURE -> assertEquals(104.0, state.getTemperature(id), 0.0001);
            case TRANSFUSION -> assertEquals("Unsanitary", state.getOperationSite(id));
            case SILVER_WIRE, SPLINT -> {
                assertTrue(state.isBleeding(id));
                verify(ui).sendNumberedMessage(player, "bleeding-warning");
            }
            default -> assertFalse(state.isCured(id));
        }
    }

    @Test
    void absentAndEmptySkillFailureListsUseFallbackText() {
        config.set("skill-fail.base-chance", 1.0);
        when(ui.getMessageList(anyString())).thenReturn(null);
        handler.initialize();
        click(SurgeryTool.SPONGE);
        assertEquals("Something went wrong!", state.getSkillFail(id));
        when(ui.getMessageList(anyString())).thenReturn(List.of());
        handler.initialize();
        click(SurgeryTool.SPONGE);
        assertEquals("Something went wrong!", state.getSkillFail(id));
    }

    @Test
    void thermometerRevealsConfiguredTinctureAndHandlesMissingDefinition() {
        menu.setItem(SurgeryTool.THERMOMETER.getSlot(), new ItemStack(Material.PAPER));
        click(SurgeryTool.THERMOMETER);
        assertNull(menu.getItem(SurgeryTool.THERMOMETER.getSlot()));
        assertNotNull(menu.getItem(SurgeryTool.TINCTURE.getSlot()));
        menu.clear();
        when(api.getCreator().getItemFromPath(anyString())).thenReturn(null);
        click(SurgeryTool.THERMOMETER);
        assertNull(menu.getItem(SurgeryTool.TINCTURE.getSlot()));
    }

    @Test
    void examinationRevealsBonesAndUsesAlreadyMadeIncisions() {
        state.setIncisions(id, 3);
        click(SurgeryTool.STETHOSCOPE);
        assertTrue(state.isExamined(id));
        assertEquals(1, state.getBrokenBones(id));
        assertEquals(1, state.getShatteredBones(id));
        verify(ui).updateDiagnosisBlock(menu, id);
        verify(mechanics).handleBoneReveal(player, menu, id, 3);
        verify(mechanics).checkForDressing(player, menu, id, 3);
    }

    @Test
    void examinationFindsSepsisWithoutReducingAnExistingFever() {
        config.set("complications.sepsis.fever-min", 102.0);
        config.set("complications.sepsis.fever-max", 102.0);
        state.setAilment(id, "sepsis", "Sepsis", new Procedure("Sepsis", 1, 0, 0, Set.of(Complication.SEPSIS)));
        click(SurgeryTool.STETHOSCOPE);
        assertTrue(state.hasRisingTemp(id));
        assertEquals(102.0, state.getTemperature(id));
        state.setTemperature(id, 108.0);
        click(SurgeryTool.STETHOSCOPE);
        assertEquals(108.0, state.getTemperature(id));
        verify(ui, times(2)).sendNumberedMessage(player, "sepsis-found");
    }

    @Test
    void scalpelCreatesDirtyIncisionsWeakensPulseAndRefreshesTools() {
        config.set("pulse.scalpel-decrease-chance", 1.0);
        click(SurgeryTool.SCALPEL);
        assertTrue(state.hasOperated(id));
        assertEquals(1, state.getIncisions(id));
        assertEquals("Unclean", state.getOperationSite(id));
        assertEquals("Steady", state.getPulse(id));
        verify(ui).updateIncisionBlock(menu, id, 1);
        verify(mechanics).handleBoneReveal(player, menu, id, 1);
        verify(mechanics).updateDynamicTools(player, menu, id);
        verify(mechanics).checkForDressing(player, menu, id, 1);
        config.set("pulse.scalpel-decrease-chance", 0.0);
        click(SurgeryTool.SCALPEL);
        assertEquals(2, state.getIncisions(id));
        assertEquals("Steady", state.getPulse(id));
        verify(ui, times(1)).updateOperationSiteBlock(menu, id, "Unclean");
    }

    @Test
    void suturesStopBleedingOnlyWhenLastIncisionClosesAndNeverGoNegative() {
        state.setIncisions(id, 2);
        state.setBleeding(id, true);
        click(SurgeryTool.SUTURE);
        assertEquals(1, state.getIncisions(id));
        assertTrue(state.isBleeding(id));
        click(SurgeryTool.SUTURE);
        assertEquals(0, state.getIncisions(id));
        assertFalse(state.isBleeding(id));
        click(SurgeryTool.SUTURE);
        assertEquals(0, state.getIncisions(id));
    }

    @Test
    void tinctureClampsAtNormalTemperatureAndDeathThreshold() {
        state.setTemperature(id, 100.0);
        click(SurgeryTool.TINCTURE);
        assertEquals(98.6, state.getTemperature(id));
        verify(ui).sendNumberedMessage(player, "temperature-reduced");
        config.set("skill-fail.base-chance", 1.0);
        state.setTemperature(id, 109.0);
        click(SurgeryTool.TINCTURE);
        assertEquals(110.0, state.getTemperature(id));
    }

    @Test
    void transfusionImprovesPulseAndCarbolicAcidProtectsTheCleanSite() {
        state.setPulse(id, "Weak");
        state.setOperationSite(id, "Unsanitary");
        click(SurgeryTool.TRANSFUSION);
        assertEquals("Steady", state.getPulse(id));
        verify(ui).updatePulseBlock(menu, id, "Steady");
        click(SurgeryTool.CARBOLIC_ACID);
        assertEquals("Clean", state.getOperationSite(id));
        assertTrue(state.hasAntisepticProtection(id));
        verify(ui).sendNumberedMessage(player, "operation-clean");
    }

    @Test
    void chloroformSedatesAwakePatientsAndCanBeReusedAfterCooldown() {
        state.setStatus(id, "Awake");
        click(SurgeryTool.CHLOROFORM);
        assertEquals("Unconscious", state.getStatus(id));
        assertEquals(0, state.getUnconsciousTimer(id));
        assertTrue(state.hasOperated(id));
        state.setStatus(id, "Coming to");
        state.setUnconsciousTimer(id, 4);
        click(SurgeryTool.CHLOROFORM);
        assertEquals("Unconscious", state.getStatus(id));
        assertEquals(0, state.getUnconsciousTimer(id));
        verifyNoInteractions(completion);
    }

    @Test
    void chloroformMisuseEndsTheSessionWithoutSuccessFeedback() {
        state.setUnconsciousTimer(id, 1);
        doAnswer(call -> { state.cleanup(id); return null; }).when(completion).failSurgery(player, "failure-anesthetic-misuse");
        click(SurgeryTool.CHLOROFORM);
        verify(completion).failSurgery(player, "failure-anesthetic-misuse");
        assertFalse(state.hasSession(id));
        verify(ui, never()).createInfoBlock(any(), anyString(), anyString());
        verify(player, never()).playSound(any(Location.class), eq(Sound.ENTITY_PLAYER_LEVELUP), anyFloat(), anyFloat());
    }

    @Test
    void smellingSaltsReviveCollapsedPatientsAndRemoveTheirDeadline() {
        state.setStatus(id, "Collapsed");
        state.setCollapseCountdown(id, 1);
        menu.setItem(SurgeryTool.SMELLING_SALTS.getSlot(), new ItemStack(Material.PAPER));
        click(SurgeryTool.SMELLING_SALTS);
        assertEquals("Unconscious", state.getStatus(id));
        assertNull(state.getCollapseCountdown(id));
        assertNull(menu.getItem(SurgeryTool.SMELLING_SALTS.getSlot()));
        click(SurgeryTool.SMELLING_SALTS);
        verify(ui, times(1)).updateStatusBlock(menu, id, "Unconscious");
    }

    @Test
    void silverWireConvertsShatteredBonesAndSplintFinishesRepair() {
        state.setShatteredBones(id, 2);
        state.setRevealedShatteredBones(id, 2);
        click(SurgeryTool.SILVER_WIRE);
        assertEquals(1, state.getShatteredBones(id));
        assertEquals(1, state.getRevealedShatteredBones(id));
        assertEquals(1, state.getBrokenBones(id));
        assertEquals(1, state.getRevealedBrokenBones(id));
        click(SurgeryTool.SPLINT);
        assertEquals(0, state.getBrokenBones(id));
        assertEquals(0, state.getRevealedBrokenBones(id));
        verify(mechanics).checkForDressing(player, menu, id, 0);
        state.setRevealedShatteredBones(id, 0);
        click(SurgeryTool.SILVER_WIRE);
        assertEquals(0, state.getShatteredBones(id));
        assertEquals(0, state.getRevealedShatteredBones(id));
        state.setRevealedBrokenBones(id, 0);
        click(SurgeryTool.SPLINT);
        assertEquals(0, state.getBrokenBones(id));
        assertEquals(0, state.getRevealedBrokenBones(id));
        click(SurgeryTool.SILVER_WIRE);
        click(SurgeryTool.SPLINT);
        assertEquals(0, state.getBrokenBones(id));
        assertEquals(0, state.getShatteredBones(id));
    }

    @Test
    void arteryForcepsRequireAnOpenBleedingWound() {
        state.setBleeding(id, true);
        click(SurgeryTool.ARTERY_FORCEPS);
        assertTrue(state.isBleeding(id));
        state.setIncisions(id, 1);
        click(SurgeryTool.ARTERY_FORCEPS);
        assertFalse(state.isBleeding(id));
        click(SurgeryTool.ARTERY_FORCEPS);
        verify(mechanics, times(1)).updateDynamicTools(player, menu, id);
    }

    @Test
    void dressingTreatsTheConditionAndOnlyCompletesWhenAllChecksPass() {
        menu.setItem(SurgeryTool.DRESSING.getSlot(), new ItemStack(Material.PAPER));
        click(SurgeryTool.DRESSING);
        assertTrue(state.isCured(id));
        assertNull(menu.getItem(SurgeryTool.DRESSING.getSlot()));
        verify(ui).sendNumberedMessage(player, "condition-treated-incomplete");
        verify(ui).sendNumberedMessage(player, "check-remaining");
        verify(completion, never()).handleSuccess(player);
        when(completion.isSurgerySuccessful(id)).thenReturn(true);
        click(SurgeryTool.DRESSING);
        verify(completion).handleSuccess(player);
        verify(ui, times(1)).sendNumberedMessage(player, "condition-treated-incomplete");
    }
}
