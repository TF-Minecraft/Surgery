package net.tfminecraft.surgery.commands;

import net.kyori.adventure.text.Component;
import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.SurgeryPlugin;
import net.tfminecraft.surgery.managers.SurgeryMenuManager;
import net.tfminecraft.surgery.managers.SurgeryRequestManager;
import net.tfminecraft.surgery.managers.SurgeryStateManager;
import net.tfminecraft.surgery.managers.SurgeryUIUpdater;
import net.tfminecraft.surgery.procedures.Ailments;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryCommandTest {
    private Player surgeon, patient;
    private SurgeryMenuManager menus;
    private SurgeryStateManager state;
    private SurgeryRequestManager requests;
    private YamlConfiguration config;
    private SurgeryCommand command;
    private Command bukkitCommand;
    private HealingInjury injury;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Ailments> ailments;
    private World world;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        surgeon = player("Surgeon");
        patient = player("Patient");
        menus = mock(SurgeryMenuManager.class);
        state = new SurgeryStateManager();
        requests = new SurgeryRequestManager();
        SurgeryUIUpdater ui = mock(SurgeryUIUpdater.class);
        when(ui.getMessage(anyString())).thenAnswer(c -> c.getArgument(0) + " %patient% %surgeon% %ailment% %remaining% %extra% %seconds%");
        when(ui.getMessage(anyString(), anyString())).thenAnswer(c -> c.getArgument(0) + " %distance%");
        when(menus.getStateManager()).thenReturn(state);
        when(menus.getRequestManager()).thenReturn(requests);
        when(menus.getUiUpdater()).thenReturn(ui);
        SurgeryPlugin plugin = mock(SurgeryPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        command = new SurgeryCommand(menus, plugin);
        bukkitCommand = mock(Command.class);
        injury = new HealingInjury("leg", "§cBroken leg", 7_200_000L);
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS);
        bukkit.when(() -> Bukkit.getPlayerExact("Patient")).thenReturn(patient);
        bukkit.when(() -> Bukkit.getPlayer(surgeon.getUniqueId())).thenReturn(surgeon);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(surgeon, patient));
        ailments = mockStatic(Ailments.class);
        ailments.when(() -> Ailments.mostSevere(patient)).thenReturn(injury);
        ailments.when(() -> Ailments.find(patient, "leg")).thenReturn(injury);
    }

    @AfterEach
    void tearDown() {
        ailments.close();
        bukkit.close();
        MockBukkit.unmock();
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.canSee(any(Player.class))).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return player;
    }

    private void run(CommandSender sender, String... args) {
        assertTrue(command.onCommand(sender, bukkitCommand, "surgery", args));
    }

    private void message(Player player, String prefix) {
        verify(player).sendMessage(startsWith(prefix));
    }

    private void offerRequest() {
        requests.offer(patient.getUniqueId(), surgeon.getUniqueId(), "leg", Long.MAX_VALUE);
    }

    @Test
    void consoleUsagePermissionsAndMissingPatientsProduceFeedback() {
        CommandSender console = mock(CommandSender.class);
        run(console, "Patient");
        verify(console).sendMessage(startsWith("command-console"));
        run(surgeon);
        message(surgeon, "command-usage");
        when(surgeon.hasPermission(anyString())).thenReturn(false);
        run(surgeon, "Patient");
        message(surgeon, "command-no-permission");
        when(surgeon.hasPermission(anyString())).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayerExact("Patient")).thenReturn(null);
        run(surgeon, "Patient");
        message(surgeon, "command-player-not-found");
        bukkit.when(() -> Bukkit.getPlayerExact("Patient")).thenReturn(patient);
        when(patient.isOnline()).thenReturn(false);
        run(surgeon, "Patient");
        verify(surgeon, times(2)).sendMessage(startsWith("command-player-not-found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"surgeon-busy", "self", "patient-busy", "patient-operating", "other-world", "too-far", "fractional-range"})
    void offersRejectConflictingSessionsAndDistantPatients(String reason) {
        String feedback;
        switch (reason) {
            case "surgeon-busy" -> { state.setPatientName(surgeon.getUniqueId(), "Other"); feedback = "command-already-in-surgery"; }
            case "self" -> { doReturn(surgeon.getUniqueId()).when(patient).getUniqueId(); feedback = "command-self-surgery"; }
            case "patient-busy" -> { state.setPatientUuid(UUID.randomUUID(), patient.getUniqueId()); feedback = "command-patient-in-surgery"; }
            case "patient-operating" -> { state.setPatientName(patient.getUniqueId(), "Other"); feedback = "command-patient-is-operating"; }
            case "other-world" -> { when(patient.getWorld()).thenReturn(mock(World.class)); feedback = "command-too-far"; }
            default -> {
                config.set("max-surgery-distance", reason.equals("fractional-range") ? 7.5 : 5.0);
                when(patient.getLocation()).thenReturn(new Location(world, 10, 64, 0));
                feedback = "command-too-far";
            }
        }
        run(surgeon, "Patient");
        message(surgeon, feedback);
        assertNull(requests.pending(patient.getUniqueId(), 0));
        verify(menus, never()).openSurgeryMenu(any(), any(), any());
    }

    @Test
    void offersRequireAnAilmentAndDoNotReplacePendingConsent() {
        ailments.when(() -> Ailments.mostSevere(patient)).thenReturn(null);
        run(surgeon, "Patient");
        message(surgeon, "command-no-ailment");
        ailments.when(() -> Ailments.mostSevere(patient)).thenReturn(injury);
        offerRequest();
        var old = requests.pending(patient.getUniqueId(), 0);
        run(surgeon, "Patient");
        message(surgeon, "request-already-pending");
        assertSame(old, requests.pending(patient.getUniqueId(), 0));
    }

    @Test
    void offersStripFormattingDescribePenaltyAndProvideConsentButtons() {
        long before = System.currentTimeMillis();
        run(surgeon, "Patient");
        var request = requests.pending(patient.getUniqueId(), before);
        assertEquals(surgeon.getUniqueId(), request.surgeonId());
        assertEquals("leg", request.traitId());
        assertTrue(request.expiresAt() >= before + 60_000);
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(patient).sendMessage(messages.capture());
        assertTrue(messages.getValue().contains("Broken leg 2h 12h 60"));
        assertFalse(messages.getValue().contains("§c"));
        ArgumentCaptor<Component> buttons = ArgumentCaptor.forClass(Component.class);
        verify(patient).sendMessage(buttons.capture());
        assertEquals("/surgery accept", buttons.getValue().clickEvent().value());
        assertTrue(buttons.getValue().children().stream().anyMatch(c -> c.clickEvent() != null && c.clickEvent().value().equals("/surgery deny")));
    }

    @Test
    void blankPermissionClampedTimeoutAndInvalidPenaltyAreHandled() {
        config.set("permission", " ");
        config.set("request-timeout-seconds", 1);
        config.set("failure-healing-penalty", "invalid");
        when(surgeon.hasPermission(anyString())).thenReturn(false);
        long before = System.currentTimeMillis();
        run(surgeon, "Patient");
        assertTrue(requests.pending(patient.getUniqueId(), before).expiresAt() >= before + 10_000);
        verify(patient).sendMessage(contains("invalid 10"));
    }

    @Test
    void acceptsOnlyFreshConsentFromAnAvailablePermittedSurgeon() {
        run(patient, "AcCePt");
        message(patient, "request-none");
        offerRequest();
        bukkit.when(() -> Bukkit.getPlayer(surgeon.getUniqueId())).thenReturn(null);
        run(patient, "accept");
        message(patient, "request-surgeon-gone");
        offerRequest();
        bukkit.when(() -> Bukkit.getPlayer(surgeon.getUniqueId())).thenReturn(surgeon);
        when(surgeon.isOnline()).thenReturn(false);
        run(patient, "accept");
        when(surgeon.isOnline()).thenReturn(true);
        when(surgeon.hasPermission(anyString())).thenReturn(false);
        offerRequest();
        run(patient, "accept");
        verify(patient, times(3)).sendMessage(startsWith("request-surgeon-gone"));
        verify(menus, never()).openSurgeryMenu(any(), any(), any());
    }

    @Test
    void acceptingRechecksStateAndInjuryThenOpensTheMatchingProcedure() {
        offerRequest();
        state.setPatientName(surgeon.getUniqueId(), "Other");
        run(patient, "accept");
        message(patient, "request-cannot-start");
        state.cleanup(surgeon.getUniqueId());
        offerRequest();
        ailments.when(() -> Ailments.find(patient, "leg")).thenReturn(null);
        run(patient, "accept");
        message(patient, "request-ailment-gone");
        ailments.when(() -> Ailments.find(patient, "leg")).thenReturn(injury);
        offerRequest();
        run(patient, "accept");
        verify(menus).openSurgeryMenu(surgeon, patient, new HealingInjury("leg", "Broken leg", 7_200_000L));
        message(surgeon, "request-accepted-surgeon");
        message(patient, "request-accepted-patient");
        assertNull(requests.pending(patient.getUniqueId(), 0));
    }

    @Test
    void denyingNotifiesTheSurgeonWhenStillAvailable() {
        run(patient, "DeNy");
        message(patient, "request-none");
        offerRequest();
        run(patient, "deny");
        message(patient, "request-denied-patient");
        message(surgeon, "request-denied-surgeon");
        bukkit.when(() -> Bukkit.getPlayer(surgeon.getUniqueId())).thenReturn(null);
        offerRequest();
        run(patient, "deny");
        verify(surgeon, times(1)).sendMessage(startsWith("request-denied-surgeon"));
    }

    @Test
    void completionFiltersByPermissionVisibilityPrefixAndArgumentPosition() {
        assertTrue(command.onTabComplete(surgeon, bukkitCommand, "surgery", new String[0]).isEmpty());
        assertEquals(List.of("accept", "deny", "Patient"), command.onTabComplete(surgeon, bukkitCommand, "surgery", new String[]{""}));
        assertEquals(List.of("Patient"), command.onTabComplete(surgeon, bukkitCommand, "surgery", new String[]{"PA"}));
        when(surgeon.canSee(patient)).thenReturn(false);
        assertTrue(command.onTabComplete(surgeon, bukkitCommand, "surgery", new String[]{"PA"}).isEmpty());
        when(surgeon.hasPermission(anyString())).thenReturn(false);
        assertEquals(List.of("accept", "deny"), command.onTabComplete(surgeon, bukkitCommand, "surgery", new String[]{""}));
        assertEquals(List.of("accept"), command.onTabComplete(mock(CommandSender.class), bukkitCommand, "surgery", new String[]{"A"}));
    }
}
