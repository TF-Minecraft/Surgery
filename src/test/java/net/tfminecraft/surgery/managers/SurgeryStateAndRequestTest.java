package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Procedure;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
        UUID patient = UUID.randomUUID(), surgeon = UUID.randomUUID();
        assertNull(requests.pending(patient, 0));
        assertNull(requests.take(patient, 0));
        requests.offer(patient, surgeon, "leg", 100);
        var request = requests.pending(patient, 99);
        assertEquals(surgeon, request.surgeonId());
        assertEquals("leg", request.traitId());
        assertEquals(100, request.expiresAt());
        assertSame(request, requests.pending(patient, 100));
        assertSame(request, requests.take(patient, 100));
        assertNull(requests.take(patient, 100));
        requests.offer(patient, surgeon, "leg", 100);
        assertNull(requests.pending(patient, 101));
        assertNull(requests.pending(patient, 99));
        requests.offer(patient, surgeon, "leg", 100);
        assertNull(requests.take(patient, 101));
        requests.offer(patient, surgeon, "leg", 100);
        requests.offer(patient, surgeon, "arm", 200);
        assertEquals("arm", requests.pending(patient, 150).traitId());
    }

    @Test
    void quittingForgetsBothSentAndReceivedOffersOnlyForThatPlayer() {
        SurgeryRequestManager requests = new SurgeryRequestManager();
        UUID quitting = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        requests.offer(quitting, first, "leg", 100);
        requests.offer(first, quitting, "arm", 100);
        requests.offer(second, unrelated, "eye", 100);
        requests.forget(quitting);
        assertNull(requests.pending(quitting, 0));
        assertNull(requests.pending(first, 0));
        assertEquals(unrelated, requests.pending(second, 0).surgeonId());
    }
}
