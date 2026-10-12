package net.tfminecraft.surgery.managers;

import net.tfminecraft.rpcharacters.api.HealingInjuries;
import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.TestCharacters;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.Durations;
import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryCompletionTest {
    private final UUID surgeonId = UUID.randomUUID(), patientId = UUID.randomUUID();
    private Player surgeon, patient;
    private JavaPlugin plugin;
    private YamlConfiguration config;
    private SurgeryStateManager state;
    private SurgeryUIUpdater ui;
    private SurgeryCompletionHandler completion;
    private BukkitScheduler scheduler;
    private ConsoleCommandSender console;
    private InventoryView view;
    private Inventory menu;
    private TestCharacters characters;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<HealingInjuries> injuries;
    private final List<Runnable> closes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        characters = new TestCharacters();
        surgeon = mock(Player.class);
        patient = mock(Player.class);
        when(surgeon.getUniqueId()).thenReturn(surgeonId);
        when(patient.getUniqueId()).thenReturn(patientId);
        when(surgeon.getName()).thenReturn("Surgeon");
        when(patient.getName()).thenReturn("Patient");
        when(surgeon.isOnline()).thenReturn(true);
        when(patient.isOnline()).thenReturn(true);
        characters.activate(surgeon);
        characters.activate(patient);
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.isEnabled()).thenReturn(true);
        state = new SurgeryStateManager();
        ui = mock(SurgeryUIUpdater.class);
        when(ui.getMessage(anyString())).thenAnswer(c -> c.getArgument(0));
        completion = new SurgeryCompletionHandler(plugin, state, ui);
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            closes.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        console = mock(ConsoleCommandSender.class);
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(() -> Bukkit.getPlayer(patientId)).thenReturn(patient);
        bukkit.when(() -> Bukkit.getPlayer(surgeonId)).thenReturn(surgeon);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(() -> Bukkit.dispatchCommand(eq(console), anyString())).thenReturn(true);
        view = mock(InventoryView.class);
        when(surgeon.getOpenInventory()).thenReturn(view);
        beginOperation("leg", "Broken leg");
        injuries = mockStatic(HealingInjuries.class);
    }

    private void beginOperation(String traitId, String ailment) {
        state.setPatientUuid(surgeonId, patientId);
        state.setPatientName(surgeonId, "Patient");
        state.setSurgeonSession(surgeonId, characters.currentSession(surgeon).orElseThrow());
        state.setPatientSession(surgeonId, characters.currentSession(patient).orElseThrow());
        state.setAilment(surgeonId, traitId, ailment, new Procedure("Fracture", 2, 0, 0, Set.of()));
        menu = new SurgeryMenuHolder(surgeonId, state.getOperationId(surgeonId)).getInventory();
        when(view.getTopInventory()).thenReturn(menu);
    }

    @AfterEach
    void tearDown() {
        injuries.close();
        bukkit.close();
        characters.close();
        MockBukkit.unmock();
    }

    @Test
    void successRequiresEveryClinicalCondition() {
        state.setStatus(surgeonId, "Unconscious");
        state.setOperationSite(surgeonId, "Clean");
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setCured(surgeonId, true);
        assertTrue(completion.isSurgerySuccessful(surgeonId));
        state.setPulse(surgeonId, "Weak");
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setPulse(surgeonId, "Strong");
        state.setStatus(surgeonId, "Awake");
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setStatus(surgeonId, "Unconscious");
        state.setTemperature(surgeonId, 101);
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setTemperature(surgeonId, 100);
        state.setOperationSite(surgeonId, "Unclean");
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setOperationSite(surgeonId, "Clean");
        state.setIncisions(surgeonId, 1);
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setIncisions(surgeonId, 0);
        state.setBrokenBones(surgeonId, 1);
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setBrokenBones(surgeonId, 0);
        state.setShatteredBones(surgeonId, 1);
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setShatteredBones(surgeonId, 0);
        state.setBleeding(surgeonId, true);
        assertFalse(completion.isSurgerySuccessful(surgeonId));
        state.setBleeding(surgeonId, false);
        assertTrue(completion.isSurgerySuccessful(surgeonId));
    }

    @Test
    void successfulCureRunsRewardOnceAndClosesOnNextTick() {
        injuries.when(() -> HealingInjuries.cure(patient, "leg")).thenReturn(true);
        config.set("commands.surgery-success", "reward %surgeon% %player% %ailment%");
        completion.handleSuccess(surgeon);
        completion.handleSuccess(surgeon);
        injuries.verify(() -> HealingInjuries.cure(patient, "leg"));
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "reward Surgeon Patient Broken leg"));
        assertFalse(state.hasSession(surgeonId));
        verify(surgeon).sendMessage("surgery-successful");
        verify(patient).sendMessage("patient-surgery-successful");
        verify(surgeon).playSound(surgeon.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        verifyDeferredClose();
    }

    @Test
    void missingPatientOrAlreadyHealedAilmentDoesNotRewardSuccess() {
        completion.handleSuccess(surgeon);
        verify(surgeon).sendMessage("ailment-already-healed");
        verify(surgeon, never()).sendMessage("surgery-successful");
        verifyDeferredClose();
        beginOperation("leg", "Leg");
        state.setPatientSession(surgeonId, null);
        completion.handleSuccess(surgeon);
        assertFalse(state.hasSession(surgeonId));
        injuries.verify(() -> HealingInjuries.cure(patient, "leg"), times(1));
    }

    @Test
    void failureDetachesBeforePenaltyAndRunsCommandAfterward() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "90m");
        config.set("commands.surgery-failure", "penalty %player% %surgeon% %ailment%");
        List<String> effects = new ArrayList<>();
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 5_400_000L)).thenAnswer(call -> {
            assertFalse(state.hasSession(surgeonId));
            effects.add("penalty");
            return 7_200_000L;
        });
        bukkit.when(() -> Bukkit.dispatchCommand(console, "penalty Patient Surgeon Broken leg")).thenAnswer(call -> {
            assertFalse(state.hasSession(surgeonId));
            effects.add("command");
            return true;
        });
        completion.failSurgery(surgeon, "failure reason");
        assertEquals(List.of("penalty", "command"), effects);
        verify(surgeon).sendMessage("failure reason");
        verify(surgeon).sendMessage("surgery-failed-penalty");
        verify(patient).sendMessage("patient-surgery-failed");
        assertFalse(state.hasSession(surgeonId));
        completion.failSurgery(surgeon, "duplicate");
        verify(surgeon, never()).sendMessage("duplicate");
        verifyDeferredClose();
    }

    @Test
    void invalidPenaltyUsesDefaultAndHarmlessFailuresDoNotExtendInjuries() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "invalid");
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 43_200_000L)).thenReturn(-1L);
        completion.failSurgery(surgeon, "failed");
        verify(plugin.getLogger()).warning("Invalid failure-healing-penalty, using 12h");
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 43_200_000L));
        verify(surgeon, never()).sendMessage("surgery-failed-penalty");
        beginOperation("leg", "Leg");
        completion.failSurgery(surgeon, "not operated");
        injuries.verifyNoMoreInteractions();
    }

    @Test
    void zeroPenaltyMissingPatientAndMissingTraitAreSafe() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "0h");
        completion.failSurgery(surgeon, "zero penalty");
        config.set("failure-healing-penalty", "1h");
        beginOperation(null, "Unknown");
        state.setOperated(surgeonId, true);
        completion.failSurgery(surgeon, "no trait");
        beginOperation("leg", "Leg");
        state.setOperated(surgeonId, true);
        bukkit.when(() -> Bukkit.getPlayer(patientId)).thenReturn(null);
        completion.failSurgery(surgeon, "no patient");
        assertFalse(state.hasSession(surgeonId));
        injuries.verifyNoInteractions();
    }

    @Test
    void abandonmentBeforeTreatmentCancelsAndAfterTreatmentFails() {
        completion.handleAbandonment(surgeon);
        assertFalse(state.hasSession(surgeonId));
        verify(surgeon).sendMessage("surgery-cancelled");
        completion.handleAbandonment(surgeon);
        verify(surgeon, times(1)).sendMessage("surgery-cancelled");
        beginOperation("leg", "Broken leg");
        state.setOperated(surgeonId, true);
        completion.handleAbandonment(surgeon);
        verify(surgeon).sendMessage("failure-gave-up");
        assertFalse(state.hasSession(surgeonId));
    }

    @Test
    void failureOnAPatientWhoSwitchedAwayAndBackSkipsCommandAndPenalty() {
        UUID original = state.getPatientSession(surgeonId).ref().characterId();
        characters.activate(patient);
        characters.activate(patient, original);
        state.setOperated(surgeonId, true);
        config.set("commands.surgery-failure", "damage %player% 1");
        completion.failSurgery(surgeon, "failed");
        verifyNoCommand();
        injuries.verifyNoInteractions();
        verify(surgeon).sendMessage("failed");
        verify(patient, never()).sendMessage(anyString());
        assertFalse(state.hasSession(surgeonId));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cureRequiresBothOriginalSessionsEvenAfterSwitchingBack(boolean patientChanges) {
        Player changing = patientChanges ? patient : surgeon;
        CharacterSession original = characters.currentSession(changing).orElseThrow();
        characters.activate(changing);
        characters.activate(changing, original.ref().characterId());
        config.set("commands.surgery-success", "reward %player%");
        completion.handleSuccess(surgeon);
        injuries.verifyNoInteractions();
        verifyNoCommand();
        assertFalse(state.hasSession(surgeonId));
    }

    @Test
    void switchedSurgeonSkipsCommandButUnchangedPatientTakesPenalty() {
        characters.activate(surgeon);
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "1h");
        config.set("commands.surgery-failure", "damage %surgeon% 1");
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 3_600_000L)).thenReturn(3_600_000L);
        completion.handleAbandonment(surgeon);
        verifyNoCommand();
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 3_600_000L));
        verify(patient).sendMessage("patient-surgery-failed");
        verify(surgeon, never()).sendMessage(anyString());
        assertTrue(closes.isEmpty());
    }

    @Test
    void providerLossCannotApplyPenaltyCommandOrCloseToAccounts() {
        state.setOperated(surgeonId, true);
        config.set("commands.surgery-failure", "damage %player% 1");
        characters.unregister();
        completion.failSurgery(surgeon, "provider gone");
        assertFalse(state.hasSession(surgeonId));
        injuries.verifyNoInteractions();
        verifyNoCommand();
        assertTrue(closes.isEmpty());
    }

    @Test
    void patientQuitPenalizesTheInvalidatedOutgoingBodyOnceWithoutCommands() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "1h");
        config.set("commands.surgery-failure", "damage %player% 1");
        CharacterSession outgoing = characters.invalidate(patient);
        assertFalse(characters.isCurrent(outgoing));
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 3_600_000L)).thenReturn(7_200_000L);
        completion.handleEnd(surgeon, outgoing, Reason.QUIT);
        completion.handleEnd(surgeon, outgoing, Reason.QUIT);
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 3_600_000L));
        verifyNoCommand();
        assertFalse(state.hasSession(surgeonId));
        verify(patient, never()).sendMessage(anyString());
        verifyDeferredClose();
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"SWITCH", "DEATH", "SHUTDOWN"})
    void otherPatientEndReasonsNeverPenalizeTheAccount(Reason reason) {
        state.setOperated(surgeonId, true);
        config.set("commands.surgery-failure", "damage %player% 1");
        completion.handleEnd(surgeon, characters.invalidate(patient), reason);
        injuries.verifyNoInteractions();
        verifyNoCommand();
        assertFalse(state.hasSession(surgeonId));
        if (reason == Reason.SHUTDOWN) {
            assertTrue(closes.isEmpty());
            verify(surgeon, never()).sendMessage(anyString());
        } else {
            verifyDeferredClose();
        }
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void surgeonEndPenalizesTheCurrentPatientExceptDuringShutdown(Reason reason) {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "1h");
        config.set("commands.surgery-failure", "damage %surgeon% 1");
        completion.handleEnd(surgeon, characters.invalidate(surgeon), reason);
        if (reason == Reason.SHUTDOWN) {
            injuries.verifyNoInteractions();
        } else {
            injuries.verify(() -> HealingInjuries.extend(patient, "leg", 3_600_000L));
        }
        verifyNoCommand();
        assertFalse(state.hasSession(surgeonId));
        verify(surgeon, never()).sendMessage(anyString());
        assertTrue(closes.isEmpty());
    }

    @Test
    void outgoingPatientQuitCannotPenalizeAReplacementSession() {
        state.setOperated(surgeonId, true);
        CharacterSession outgoing = characters.invalidate(patient);
        characters.activate(patient);
        completion.handleEnd(surgeon, outgoing, Reason.QUIT);
        injuries.verifyNoInteractions();
        assertFalse(state.hasSession(surgeonId));
    }

    @Test
    void oldEndEventCannotDetachANewerOperation() {
        CharacterSession old = characters.invalidate(patient);
        state.finish(surgeonId);
        characters.activate(patient);
        beginOperation("arm", "Broken arm");
        long operation = state.getOperationId(surgeonId);
        completion.handleEnd(surgeon, old, Reason.QUIT);
        assertEquals(operation, state.getOperationId(surgeonId));
        assertEquals("arm", state.getTraitId(surgeonId));
        injuries.verifyNoInteractions();
        assertTrue(closes.isEmpty());
    }

    @Test
    void reentrantCureCannotRepeatOrEraseAndCloseANewOperation() {
        long original = state.getOperationId(surgeonId);
        Inventory oldMenu = menu;
        injuries.when(() -> HealingInjuries.cure(patient, "leg")).thenAnswer(call -> {
            assertFalse(state.hasSession(surgeonId));
            completion.handleSuccess(surgeon);
            beginOperation("arm", "Broken arm");
            return true;
        });
        completion.handleSuccess(surgeon);
        injuries.verify(() -> HealingInjuries.cure(patient, "leg"));
        assertNotEquals(original, state.getOperationId(surgeonId));
        assertEquals("arm", state.getTraitId(surgeonId));
        assertNotSame(oldMenu, menu);
        assertEquals(1, closes.size());
        closes.removeFirst().run();
        verify(surgeon, never()).closeInventory();
        assertTrue(state.hasSession(surgeonId));
    }

    @Test
    void reentrantFailureCommandCannotRepeatPenaltyOrClearTheNextOperation() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "1h");
        config.set("commands.surgery-failure", "penalty %player% %ailment%");
        List<String> effects = new ArrayList<>();
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 3_600_000L)).thenAnswer(call -> {
            assertFalse(state.hasSession(surgeonId));
            effects.add("penalty");
            return 3_600_000L;
        });
        bukkit.when(() -> Bukkit.dispatchCommand(console, "penalty Patient Broken leg")).thenAnswer(call -> {
            effects.add("command");
            completion.failSurgery(surgeon, "duplicate");
            beginOperation("arm", "Broken arm");
            return true;
        });
        completion.failSurgery(surgeon, "failed");
        assertEquals(List.of("penalty", "command"), effects);
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 3_600_000L));
        assertEquals("arm", state.getTraitId(surgeonId));
        verify(surgeon, never()).sendMessage("duplicate");
        closes.removeFirst().run();
        verify(surgeon, never()).closeInventory();
        assertTrue(state.hasSession(surgeonId));
    }

    @Test
    void reentrantPenaltySwitchSuppressesTheFollowingAccountCommand() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "1h");
        config.set("commands.surgery-failure", "damage %player% 1");
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 3_600_000L)).thenAnswer(call -> {
            completion.failSurgery(surgeon, "duplicate");
            characters.activate(patient);
            return 3_600_000L;
        });
        completion.failSurgery(surgeon, "failed");
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 3_600_000L));
        verifyNoCommand();
        verify(patient, never()).sendMessage(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"switch-back", "offline", "reconnect", "new-menu", "disabled"})
    void deferredCloseRejectsChangedActorsAndInventories(String change) {
        injuries.when(() -> HealingInjuries.cure(patient, "leg")).thenReturn(true);
        completion.handleSuccess(surgeon);
        assertEquals(1, closes.size());
        switch (change) {
            case "switch-back" -> {
                UUID character = characters.currentSession(surgeon).orElseThrow().ref().characterId();
                characters.activate(surgeon);
                characters.activate(surgeon, character);
            }
            case "offline" -> when(surgeon.isOnline()).thenReturn(false);
            case "reconnect" -> bukkit.when(() -> Bukkit.getPlayer(surgeonId)).thenReturn(mock(Player.class));
            case "new-menu" -> {
                Inventory other = Bukkit.createInventory(null, 9, "Other menu");
                when(view.getTopInventory()).thenReturn(other);
            }
            case "disabled" -> when(plugin.isEnabled()).thenReturn(false);
            default -> fail(change);
        }
        closes.removeFirst().run();
        verify(surgeon, never()).closeInventory();
    }

    @Test
    void unrelatedMenuAndDisabledPluginDoNotScheduleAClosure() {
        Inventory unrelated = new SurgeryMenuHolder(surgeonId, state.getOperationId(surgeonId) + 1).getInventory();
        when(view.getTopInventory()).thenReturn(unrelated);
        completion.failSurgery(surgeon, "failed");
        assertTrue(closes.isEmpty());
        beginOperation("leg", "Leg");
        when(plugin.isEnabled()).thenReturn(false);
        completion.failSurgery(surgeon, "disabled");
        assertTrue(closes.isEmpty());
    }

    @Test
    void ailmentQueriesSelectWorstAndFindCaseInsensitively() throws Exception {
        HealingInjury shortInjury = new HealingInjury("arm", "Arm", 1_000);
        HealingInjury worst = new HealingInjury("leg", "Leg", 2_000);
        injuries.when(() -> HealingInjuries.list(patient)).thenReturn(List.of(shortInjury, worst));
        assertSame(worst, Ailments.mostSevere(patient));
        assertSame(worst, Ailments.find(patient, "LEG"));
        assertNull(Ailments.find(patient, "missing"));
        injuries.when(() -> HealingInjuries.list(patient)).thenReturn(List.of());
        assertNull(Ailments.mostSevere(patient));
        for (Class<?> utility : List.of(Ailments.class, Durations.class)) {
            var constructor = utility.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            constructor.setAccessible(true);
            assertNotNull(constructor.newInstance());
        }
    }

    private void verifyNoCommand() {
        bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), anyString()), never());
    }

    private void verifyDeferredClose() {
        verify(surgeon, never()).closeInventory();
        assertEquals(1, closes.size());
        closes.removeFirst().run();
        verify(surgeon).closeInventory();
    }
}
