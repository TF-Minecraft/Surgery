package net.tfminecraft.surgery.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// ==============================================
// Manages all player state for ongoing surgeries
// One SurgerySession per surgeon; cleanup removes the whole session at once
// ==============================================
public class SurgeryStateManager {

    // ==============================================
    // All per-surgery state in one object
    // Boxed types so getters can fall back to defaults when unset
    // ==============================================
    private static class SurgerySession {
        String patientName;
        UUID patientUuid;
        String diagnosis;
        String pulse;
        String status;
        Double temperature;
        String operationSite;
        Integer incisions;
        String skillFail;
        Boolean bleeding;
        Integer brokenBones;
        Integer shatteredBones;
        Integer revealedBrokenBones;
        Integer revealedShatteredBones;
        Integer defibrillatorCountdown;
        Boolean cured;
        Boolean antisepticProtection;
        Boolean spongeEffect;
        Integer moveCount;
        Integer movesSinceLastSponge;
        Integer unconsciousTimer;
        Boolean hasRisingTemp;
        Integer extremelyWeakCounter;
        Integer redTempCounter;
    }

    private final Map<UUID, SurgerySession> sessions = new HashMap<>();

    private SurgerySession session(UUID playerId) {
        return sessions.computeIfAbsent(playerId, k -> new SurgerySession());
    }

    private SurgerySession get(UUID playerId) {
        return sessions.get(playerId);
    }

    // ==============================================
    // Getters with default values
    // ==============================================
    public String getDiagnosis(UUID playerId) { SurgerySession s = get(playerId); return s == null ? null : s.diagnosis; }
    public String getPulse(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.pulse == null ? "Strong" : s.pulse; }
    public String getStatus(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.status == null ? "Awake" : s.status; }
    public double getTemperature(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.temperature == null ? 98.6 : s.temperature; }
    public String getOperationSite(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.operationSite == null ? "Not sanitized" : s.operationSite; }
    public int getIncisions(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.incisions == null ? 0 : s.incisions; }
    public String getSkillFail(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.skillFail == null ? "" : s.skillFail; }
    public boolean isBleeding(UUID playerId) { SurgerySession s = get(playerId); return s != null && Boolean.TRUE.equals(s.bleeding); }
    public int getBrokenBones(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.brokenBones == null ? 0 : s.brokenBones; }
    public int getShatteredBones(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.shatteredBones == null ? 0 : s.shatteredBones; }
    public int getRevealedBrokenBones(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.revealedBrokenBones == null ? 0 : s.revealedBrokenBones; }
    public int getRevealedShatteredBones(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.revealedShatteredBones == null ? 0 : s.revealedShatteredBones; }
    public Integer getDefibrillatorCountdown(UUID playerId) { SurgerySession s = get(playerId); return s == null ? null : s.defibrillatorCountdown; }
    public boolean isCured(UUID playerId) { SurgerySession s = get(playerId); return s != null && Boolean.TRUE.equals(s.cured); }
    public boolean hasAntisepticProtection(UUID playerId) { SurgerySession s = get(playerId); return s != null && Boolean.TRUE.equals(s.antisepticProtection); }
    public boolean hasSpongeEffect(UUID playerId) { SurgerySession s = get(playerId); return s != null && Boolean.TRUE.equals(s.spongeEffect); }
    public int getMoveCount(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.moveCount == null ? 0 : s.moveCount; }
    public int getMovesSinceLastSponge(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.movesSinceLastSponge == null ? 0 : s.movesSinceLastSponge; }
    public Integer getUnconsciousTimer(UUID playerId) { SurgerySession s = get(playerId); return s == null ? null : s.unconsciousTimer; }
    public boolean hasRisingTemp(UUID playerId) { SurgerySession s = get(playerId); return s != null && Boolean.TRUE.equals(s.hasRisingTemp); }
    public int getExtremelyWeakCounter(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.extremelyWeakCounter == null ? 0 : s.extremelyWeakCounter; }
    public int getRedTempCounter(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.redTempCounter == null ? 0 : s.redTempCounter; }
    public String getPatientName(UUID playerId) { SurgerySession s = get(playerId); return s == null || s.patientName == null ? "Unknown" : s.patientName; }
    public UUID getPatientUuid(UUID playerId) { SurgerySession s = get(playerId); return s == null ? null : s.patientUuid; }
    public boolean hasSession(UUID playerId) { return sessions.containsKey(playerId); }
    public boolean hasDiagnosis(UUID playerId) { SurgerySession s = get(playerId); return s != null && s.diagnosis != null; }

    // ==============================================
    // Patient-side lookups (sessions are keyed by surgeon)
    // ==============================================
    public boolean isPatientInSurgery(UUID patientUuid) {
        return sessions.values().stream().anyMatch(s -> patientUuid.equals(s.patientUuid));
    }

    public UUID findSurgeonForPatient(UUID patientUuid) {
        for (Map.Entry<UUID, SurgerySession> entry : sessions.entrySet()) {
            if (patientUuid.equals(entry.getValue().patientUuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ==============================================
    // Setters
    // ==============================================
    public void setDiagnosis(UUID playerId, String diagnosis) { session(playerId).diagnosis = diagnosis; }
    public void setPulse(UUID playerId, String pulse) { session(playerId).pulse = pulse; }
    public void setStatus(UUID playerId, String status) { session(playerId).status = status; }
    public void setTemperature(UUID playerId, double temp) { session(playerId).temperature = temp; }
    public void setOperationSite(UUID playerId, String site) { session(playerId).operationSite = site; }
    public void setIncisions(UUID playerId, int incisions) { session(playerId).incisions = incisions; }
    public void setSkillFail(UUID playerId, String message) { session(playerId).skillFail = message; }
    public void setBleeding(UUID playerId, boolean bleeding) { session(playerId).bleeding = bleeding; }
    public void setBrokenBones(UUID playerId, int count) { session(playerId).brokenBones = count; }
    public void setShatteredBones(UUID playerId, int count) { session(playerId).shatteredBones = count; }
    public void setRevealedBrokenBones(UUID playerId, int count) { session(playerId).revealedBrokenBones = count; }
    public void setRevealedShatteredBones(UUID playerId, int count) { session(playerId).revealedShatteredBones = count; }
    public void setDefibrillatorCountdown(UUID playerId, int countdown) { session(playerId).defibrillatorCountdown = countdown; }
    public void setCured(UUID playerId, boolean cured) { session(playerId).cured = cured; }
    public void setAntisepticProtection(UUID playerId, boolean protected_) { session(playerId).antisepticProtection = protected_; }
    public void setSpongeEffect(UUID playerId, boolean effect) { session(playerId).spongeEffect = effect; }
    public void setMoveCount(UUID playerId, int count) { session(playerId).moveCount = count; }
    public void setMovesSinceLastSponge(UUID playerId, int count) { session(playerId).movesSinceLastSponge = count; }
    public void setUnconsciousTimer(UUID playerId, int timer) { session(playerId).unconsciousTimer = timer; }
    public void setHasRisingTemp(UUID playerId, boolean risingTemp) { session(playerId).hasRisingTemp = risingTemp; }
    public void setExtremelyWeakCounter(UUID playerId, int count) { session(playerId).extremelyWeakCounter = count; }
    public void setRedTempCounter(UUID playerId, int count) { session(playerId).redTempCounter = count; }
    public void setPatientName(UUID playerId, String name) { session(playerId).patientName = name; }
    public void setPatientUuid(UUID playerId, UUID patientUuid) { session(playerId).patientUuid = patientUuid; }
    public void removeDefibrillatorCountdown(UUID playerId) { SurgerySession s = get(playerId); if (s != null) { s.defibrillatorCountdown = null; } }

    // ==============================================
    // Player Data Cleanup
    // ==============================================
    public void cleanup(UUID playerId) {
        sessions.remove(playerId);
    }
}
