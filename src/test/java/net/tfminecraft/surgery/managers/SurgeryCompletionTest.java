package net.tfminecraft.surgery.managers;

import net.tfminecraft.rpcharacters.api.HealingInjuries;
import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.Durations;
import net.tfminecraft.surgery.procedures.Procedure;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Modifier;
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
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<HealingInjuries> injuries;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        surgeon = mock(Player.class);
        patient = mock(Player.class);
        when(surgeon.getUniqueId()).thenReturn(surgeonId);
        when(patient.getUniqueId()).thenReturn(patientId);
        when(surgeon.getName()).thenReturn("Surgeon");
        when(patient.getName()).thenReturn("Patient");
        plugin = mock(JavaPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        state = new SurgeryStateManager();
        state.setPatientUuid(surgeonId, patientId);
        state.setPatientName(surgeonId, "Patient");
        state.setAilment(surgeonId, "leg", "Broken leg", new Procedure("Fracture", 2, 0, 0, Set.of()));
        ui = mock(SurgeryUIUpdater.class);
        when(ui.getMessage(anyString())).thenAnswer(c -> c.getArgument(0));
        completion = new SurgeryCompletionHandler(plugin, state, ui);
        scheduler = mock(BukkitScheduler.class);
        console = mock(ConsoleCommandSender.class);
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(() -> Bukkit.getPlayer(patientId)).thenReturn(patient);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(() -> Bukkit.dispatchCommand(eq(console), anyString())).thenReturn(true);
        injuries = mockStatic(HealingInjuries.class);
    }

    @AfterEach
    void tearDown() {
        injuries.close();
        bukkit.close();
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
        state.setPatientUuid(surgeonId, null);
        state.setAilment(surgeonId, "leg", "Leg", null);
        completion.handleSuccess(surgeon);
        assertFalse(state.hasSession(surgeonId));
        injuries.verify(() -> HealingInjuries.cure(patient, "leg"), times(1));
    }

    @Test
    void failureExtendsHealingAndRunsCommandBeforeCleaningSession() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "90m");
        config.set("commands.surgery-failure", "penalty %player% %surgeon% %ailment%");
        injuries.when(() -> HealingInjuries.extend(patient, "leg", 5_400_000L)).thenReturn(7_200_000L);
        completion.failSurgery(surgeon, "failure reason");
        injuries.verify(() -> HealingInjuries.extend(patient, "leg", 5_400_000L));
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "penalty Patient Surgeon Broken leg"));
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
        state.setPatientUuid(surgeonId, patientId);
        state.setAilment(surgeonId, "leg", "Leg", null);
        completion.failSurgery(surgeon, "not operated");
        injuries.verifyNoMoreInteractions();
    }

    @Test
    void zeroPenaltyMissingPatientAndMissingTraitAreSafe() {
        state.setOperated(surgeonId, true);
        config.set("failure-healing-penalty", "0h");
        completion.handleQuit(surgeon);
        state.setOperated(surgeonId, true);
        state.setPatientUuid(surgeonId, patientId);
        completion.handleQuit(surgeon);
        state.setOperated(surgeonId, true);
        state.setAilment(surgeonId, "leg", "Leg", null);
        completion.handleQuit(surgeon);
        completion.handleQuit(surgeon);
        assertFalse(state.hasSession(surgeonId));
        injuries.verifyNoInteractions();
        verifyNoInteractions(scheduler);
    }

    @Test
    void abandonmentBeforeTreatmentCancelsAndAfterTreatmentFails() {
        completion.handleAbandonment(surgeon);
        assertFalse(state.hasSession(surgeonId));
        verify(surgeon).sendMessage("surgery-cancelled");
        completion.handleAbandonment(surgeon);
        verify(surgeon, times(1)).sendMessage("surgery-cancelled");
        state.setOperated(surgeonId, true);
        completion.handleAbandonment(surgeon);
        verify(surgeon).sendMessage("failure-gave-up");
        assertFalse(state.hasSession(surgeonId));
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

    private void verifyDeferredClose() {
        verify(surgeon, never()).closeInventory();
        ArgumentCaptor<Runnable> close = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), close.capture());
        close.getValue().run();
        verify(surgeon).closeInventory();
    }
}
