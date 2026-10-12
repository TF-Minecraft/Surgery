package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.character.CharacterIdentity;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SurgeryStateAndRequestTest {
    @Test
    void sessionsAreIsolatedAndCleanupRestoresAllDefaults() {
        SurgeryStateManager state = new SurgeryStateManager();
        UUID surgeon = UUID.randomUUID(), other = UUID.randomUUID(), patient = UUID.randomUUID();
        assertDefaults(state, surgeon);
        assertFalse(state.hasSession(surgeon));
        state.removeCollapseCountdown(surgeon);
        assertFalse(state.hasSession(surgeon));
        state.setPatientName(surgeon, "Patient");
        assertTrue(state.hasSession(surgeon));
        assertEquals("Strong", state.getPulse(surgeon));
        state.setPatientUuid(surgeon, patient);
        Procedure procedure = new Procedure("Fracture", 2, 1, 1, Set.of());
        state.setAilment(surgeon, "broken_leg", "Broken leg", procedure);
        state.setExamined(surgeon, true);
        state.setOperated(surgeon, true);
        state.setPulse(surgeon, "Weak");
        state.setStatus(surgeon, "Collapsed");
        state.setTemperature(surgeon, 104.0);
        state.setOperationSite(surgeon, "Clean");
        state.setIncisions(surgeon, 2);
        state.setSkillFail(surgeon, "Missed");
        state.setBleeding(surgeon, true);
        state.setBrokenBones(surgeon, 3);
        state.setShatteredBones(surgeon, 2);
        state.setRevealedBrokenBones(surgeon, 1);
        state.setRevealedShatteredBones(surgeon, 2);
        state.setCollapseCountdown(surgeon, 3);
        state.setCured(surgeon, true);
        state.setAntisepticProtection(surgeon, true);
        state.setSpongeEffect(surgeon, true);
        state.setMoveCount(surgeon, 7);
        state.setMovesSinceLastSponge(surgeon, 4);
        state.setUnconsciousTimer(surgeon, 5);
        state.setHasRisingTemp(surgeon, true);
        state.setExtremelyWeakCounter(surgeon, 2);
        state.setRedTempCounter(surgeon, 1);

        assertEquals("Patient", state.getPatientName(surgeon));
        assertEquals(patient, state.getPatientUuid(surgeon));
        assertEquals("broken_leg", state.getTraitId(surgeon));
        assertEquals("Broken leg", state.getAilmentName(surgeon));
        assertSame(procedure, state.getProcedure(surgeon));
        assertTrue(state.isExamined(surgeon));
        assertTrue(state.hasOperated(surgeon));
        assertEquals("Weak", state.getPulse(surgeon));
        assertEquals("Collapsed", state.getStatus(surgeon));
        assertEquals(104.0, state.getTemperature(surgeon));
        assertEquals("Clean", state.getOperationSite(surgeon));
        assertEquals(2, state.getIncisions(surgeon));
        assertEquals("Missed", state.getSkillFail(surgeon));
        assertTrue(state.isBleeding(surgeon));
        assertEquals(3, state.getBrokenBones(surgeon));
        assertEquals(2, state.getShatteredBones(surgeon));
        assertEquals(1, state.getRevealedBrokenBones(surgeon));
        assertEquals(2, state.getRevealedShatteredBones(surgeon));
        assertEquals(3, state.getCollapseCountdown(surgeon));
        assertTrue(state.isCured(surgeon));
        assertTrue(state.hasAntisepticProtection(surgeon));
        assertTrue(state.hasSpongeEffect(surgeon));
        assertEquals(7, state.getMoveCount(surgeon));
        assertEquals(4, state.getMovesSinceLastSponge(surgeon));
        assertEquals(5, state.getUnconsciousTimer(surgeon));
        assertTrue(state.hasRisingTemp(surgeon));
        assertEquals(2, state.getExtremelyWeakCounter(surgeon));
        assertEquals(1, state.getRedTempCounter(surgeon));
        assertTrue(state.isPatientInSurgery(patient));
        assertEquals(surgeon, state.findSurgeonForPatient(patient));
        assertFalse(state.isPatientInSurgery(other));
        assertNull(state.findSurgeonForPatient(other));
        assertDefaults(state, other);
        state.removeCollapseCountdown(surgeon);
        assertNull(state.getCollapseCountdown(surgeon));
        state.cleanup(surgeon);
        state.cleanup(surgeon);
        assertDefaults(state, surgeon);
        assertFalse(state.isPatientInSurgery(patient));
    }

    private static void assertDefaults(SurgeryStateManager state, UUID id) {
        assertNull(state.getTraitId(id));
        assertNull(state.getPatientUuid(id));
        assertNull(state.getProcedure(id));
        assertEquals("Unknown", state.getPatientName(id));
        assertEquals("Unknown ailment", state.getAilmentName(id));
        assertEquals("Strong", state.getPulse(id));
        assertEquals("Awake", state.getStatus(id));
        assertEquals(98.6, state.getTemperature(id));
        assertEquals("Not sanitized", state.getOperationSite(id));
        assertEquals("", state.getSkillFail(id));
        assertEquals(0, state.getIncisions(id));
        assertEquals(0, state.getBrokenBones(id));
        assertEquals(0, state.getShatteredBones(id));
        assertEquals(0, state.getRevealedBrokenBones(id));
        assertEquals(0, state.getRevealedShatteredBones(id));
        assertEquals(0, state.getMoveCount(id));
        assertEquals(0, state.getMovesSinceLastSponge(id));
        assertEquals(0, state.getExtremelyWeakCounter(id));
        assertEquals(0, state.getRedTempCounter(id));
        assertNull(state.getCollapseCountdown(id));
        assertNull(state.getUnconsciousTimer(id));
        assertFalse(state.isExamined(id));
        assertFalse(state.hasOperated(id));
        assertFalse(state.isBleeding(id));
        assertFalse(state.isCured(id));
        assertFalse(state.hasAntisepticProtection(id));
        assertFalse(state.hasSpongeEffect(id));
        assertFalse(state.hasRisingTemp(id));
    }

    @Test
    void pendingOffersExpireAreConsumedOnceAndCanBeReplaced() {
        SurgeryRequestManager requests = new SurgeryRequestManager();
        try (ConsentCharacters characters = new ConsentCharacters()) {
            CharacterSession patient = characters.start(), surgeon = characters.start();
            UUID patientId = patient.ref().ownerId();
            assertNull(requests.pending(patientId, 0));
            assertNull(requests.take(patientId, 0));
            requests.offer(patient, surgeon, "leg", 100);
            var request = requests.pending(patientId, 99);
            assertEquals(surgeon.ref().ownerId(), request.surgeonId());
            assertEquals(surgeon, request.surgeonSession());
            assertEquals(patient, request.patientSession());
            assertTrue(request.isCurrent());
            assertEquals("leg", request.traitId());
            assertEquals(100, request.expiresAt());
            assertSame(request, requests.pending(patientId, 100));
            assertSame(request, requests.take(patientId, 100));
            assertNull(requests.take(patientId, 100));
            requests.offer(patient, surgeon, "leg", 100);
            assertNull(requests.pending(patientId, 101));
            assertNull(requests.pending(patientId, 99));
            requests.offer(patient, surgeon, "leg", 100);
            assertNull(requests.take(patientId, 101));
            requests.offer(patient, surgeon, "leg", 100);
            requests.offer(patient, surgeon, "arm", 200);
            assertEquals("arm", requests.pending(patientId, 150).traitId());
        }
    }

    @Test
    void quittingForgetsBothSentAndReceivedOffersOnlyForThatPlayer() {
        SurgeryRequestManager requests = new SurgeryRequestManager();
        try (ConsentCharacters characters = new ConsentCharacters()) {
            CharacterSession quitting = characters.start(), first = characters.start();
            CharacterSession second = characters.start(), unrelated = characters.start();
            requests.offer(quitting, first, "leg", 100);
            requests.offer(first, quitting, "arm", 100);
            requests.offer(second, unrelated, "eye", 100);
            requests.forget(quitting);
            assertNull(requests.pending(quitting.ref().ownerId(), 0));
            assertNull(requests.pending(first.ref().ownerId(), 0));
            assertEquals(unrelated.ref().ownerId(), requests.pending(second.ref().ownerId(), 0).surgeonId());
            requests.clear();
            assertNull(requests.pending(second.ref().ownerId(), 0));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "surgeon", "provider"})
    void pendingAndTakeDiscardConsentWhenEitherCapturedSessionEnds(String changed) {
        try (ConsentCharacters characters = new ConsentCharacters()) {
            SurgeryRequestManager pending = new SurgeryRequestManager();
            SurgeryRequestManager taking = new SurgeryRequestManager();
            CharacterSession patient = characters.start(), surgeon = characters.start();
            UUID patientId = patient.ref().ownerId();
            pending.offer(patient, surgeon, "leg", 100);
            taking.offer(patient, surgeon, "leg", 100);
            var captured = pending.pending(patientId, 0);
            if (changed.equals("provider")) {
                characters.server.getServicesManager().unregister(CharacterIdentity.class, characters.identity);
            } else {
                CharacterSession original = changed.equals("patient") ? patient : surgeon;
                UUID ownerId = original.ref().ownerId();
                characters.sessions.put(ownerId, new CharacterSession(new CharacterRef(ownerId, UUID.randomUUID()), 2));
                characters.sessions.put(ownerId, new CharacterSession(original.ref(), 3));
            }
            assertFalse(captured.isCurrent());
            assertNull(pending.pending(patientId, 0));
            assertNull(taking.take(patientId, 0));
            characters.register();
            characters.sessions.put(patientId, patient);
            characters.sessions.put(surgeon.ref().ownerId(), surgeon);
            assertNull(pending.pending(patientId, 0));
            assertNull(taking.pending(patientId, 0));
        }
    }

    @Test
    void offersRequireBothReadyCurrentSessionsWithoutReplacingValidConsent() {
        try (ConsentCharacters characters = new ConsentCharacters()) {
            SurgeryRequestManager requests = new SurgeryRequestManager();
            CharacterSession patient = characters.start(), surgeon = characters.start();
            UUID patientId = patient.ref().ownerId();
            requests.offer(null, surgeon, "leg", 100);
            requests.offer(patient, null, "leg", 100);
            assertNull(requests.pending(patientId, 0));
            requests.offer(patient, surgeon, "leg", 100);
            CharacterSession stalePatient = new CharacterSession(patient.ref(), 0);
            CharacterSession staleSurgeon = new CharacterSession(surgeon.ref(), 0);
            requests.offer(stalePatient, surgeon, "arm", 100);
            requests.offer(patient, staleSurgeon, "arm", 100);
            assertEquals("leg", requests.pending(patientId, 0).traitId());
            requests.clear();
            characters.server.getServicesManager().unregister(CharacterIdentity.class, characters.identity);
            requests.offer(patient, surgeon, "leg", 100);
            characters.register();
            assertNull(requests.pending(patientId, 0));
        }
    }

    @Test
    void operationTokensRejectReactivatedCharactersAndFinishDetachesOnlyOnce() {
        try (ConsentCharacters characters = new ConsentCharacters()) {
            SurgeryStateManager state = new SurgeryStateManager();
            CharacterSession surgeon = characters.start(), patient = characters.start();
            UUID id = surgeon.ref().ownerId();
            assertNull(state.getSurgeonSession(id)); assertNull(state.getPatientSession(id));
            assertEquals(0, state.getOperationId(id)); assertNull(state.finish(id));
            assertFalse(state.isOriginalSurgeon(id)); assertFalse(state.isOriginalPatient(id));
            state.setSurgeonSession(id, surgeon); state.setPatientSession(id, patient);
            assertTrue(state.isCurrent(id));
            long operation = state.getOperationId(id);
            characters.sessions.put(patient.ref().ownerId(), new CharacterSession(patient.ref(), 2));
            assertFalse(state.isCurrent(id));
            var outcome = state.finish(id);
            assertEquals(operation, outcome.operationId());
            assertEquals(surgeon, outcome.surgeonSession()); assertEquals(patient, outcome.patientSession());
            assertEquals("Unknown", outcome.patientName()); assertEquals("Unknown ailment", outcome.ailmentName());
            assertFalse(outcome.operated()); assertFalse(state.hasSession(id));
            assertNull(state.finish(id));
            state.setPatientName(id, "Replacement");
            assertNotEquals(operation, state.getOperationId(id));
            state.clear(); assertFalse(state.hasSession(id));
        }
    }

    private static final class ConsentCharacters implements AutoCloseable {
        private final ServerMock server = MockBukkit.mock();
        private final CharacterIdentity identity = mock(CharacterIdentity.class);
        private final Map<UUID, CharacterSession> sessions = new HashMap<>();
        private final org.bukkit.plugin.Plugin plugin = MockBukkit.createMockPlugin();

        private ConsentCharacters() {
            when(identity.isCurrent(any())).thenAnswer(call -> {
                CharacterSession session = call.getArgument(0);
                return session.equals(sessions.get(session.ref().ownerId()));
            });
            register();
        }

        private void register() {
            server.getServicesManager().unregister(CharacterIdentity.class, identity);
            server.getServicesManager().register(CharacterIdentity.class, identity, plugin, ServicePriority.Normal);
        }

        private CharacterSession start() {
            CharacterSession session = new CharacterSession(new CharacterRef(UUID.randomUUID(), UUID.randomUUID()), 1);
            sessions.put(session.ref().ownerId(), session);
            return session;
        }

        @Override
        public void close() {
            MockBukkit.unmock();
        }
    }
}
