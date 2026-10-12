package net.tfminecraft.surgery.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.Characters;

// ==============================================
// Pending surgery offers, keyed by patient. A failed surgery lengthens the
// patient's healing time, so nobody is operated on without agreeing to it
// ==============================================
public class SurgeryRequestManager {

    public record Request(CharacterSession surgeonSession, CharacterSession patientSession,
                          String traitId, long expiresAt) {
        public Request {
            Objects.requireNonNull(surgeonSession, "surgeonSession");
            Objects.requireNonNull(patientSession, "patientSession");
        }

        public UUID surgeonId() {
            return surgeonSession.ref().ownerId();
        }

        public boolean isCurrent() {
            return Characters.isCurrent(surgeonSession) && Characters.isCurrent(patientSession);
        }
    }

    private final Map<UUID, Request> requests = new HashMap<>();

    public void offer(CharacterSession patientSession, CharacterSession surgeonSession,
                      String traitId, long expiresAt) {
        if (patientSession == null || surgeonSession == null) {
            return;
        }
        Request request = new Request(surgeonSession, patientSession, traitId, expiresAt);
        if (request.isCurrent()) {
            requests.put(patientSession.ref().ownerId(), request);
        }
    }

    // ==============================================
    // The patient's current offer, or null when there is none or it has expired
    // ==============================================
    public Request pending(UUID patientId, long now) {
        Request request = requests.get(patientId);
        if (request == null) {
            return null;
        }
        if (request.expiresAt() < now || !request.isCurrent()) {
            requests.remove(patientId);
            return null;
        }
        return request;
    }

    // ==============================================
    // Removes and returns the patient's request if it has not expired
    // ==============================================
    public Request take(UUID patientId, long now) {
        Request request = requests.remove(patientId);
        return request == null || request.expiresAt() < now || !request.isCurrent() ? null : request;
    }

    /** Drop only consent involving the ending session; a late end cannot erase a new offer. */
    public void forget(CharacterSession ending) {
        requests.values().removeIf(request -> ending.equals(request.surgeonSession())
                || ending.equals(request.patientSession()));
    }

    public void clear() {
        requests.clear();
    }
}
