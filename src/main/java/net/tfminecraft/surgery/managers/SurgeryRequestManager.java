package net.tfminecraft.surgery.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// ==============================================
// Pending surgery offers, keyed by patient. A failed surgery lengthens the
// patient's healing time, so nobody is operated on without agreeing to it
// ==============================================
public class SurgeryRequestManager {

    public record Request(UUID surgeonId, String traitId, long expiresAt) {
    }

    private final Map<UUID, Request> requests = new HashMap<>();

    public void offer(UUID patientId, UUID surgeonId, String traitId, long expiresAt) {
        requests.put(patientId, new Request(surgeonId, traitId, expiresAt));
    }

    // ==============================================
    // Removes and returns the patient's request if it has not expired
    // ==============================================
    public Request take(UUID patientId, long now) {
        Request request = requests.remove(patientId);
        return request == null || request.expiresAt() < now ? null : request;
    }

    // ==============================================
    // Drops every request the player sent or received (used on quit)
    // ==============================================
    public void forget(UUID playerId) {
        requests.remove(playerId);
        requests.values().removeIf(request -> request.surgeonId().equals(playerId));
    }
}
