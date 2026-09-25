package net.tfminecraft.surgery.procedures;

import net.tfminecraft.rpcharacters.api.HealingInjuries;
import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

// ==============================================
// Reads and changes a patient's healing ailments through RPCharacters
// Permanent injuries are never returned, so surgery cannot touch them
// ==============================================
public final class Ailments {

    private Ailments() {
    }

    // ==============================================
    // The ailment surgery treats next: the one with the most healing time left
    // ==============================================
    public static HealingInjury mostSevere(Player patient) {
        List<HealingInjury> injuries = HealingInjuries.list(patient);
        return injuries.stream().max(Comparator.comparingLong(HealingInjury::remainingMs)).orElse(null);
    }

    public static HealingInjury find(Player patient, String traitId) {
        for (HealingInjury injury : HealingInjuries.list(patient)) {
            if (injury.traitId().equalsIgnoreCase(traitId)) {
                return injury;
            }
        }
        return null;
    }

    public static boolean cure(Player patient, String traitId) {
        return HealingInjuries.cure(patient, traitId);
    }

    // Returns the new remaining time, or -1 if the ailment is gone
    public static long extend(Player patient, String traitId, long extraMs) {
        return HealingInjuries.extend(patient, traitId, extraMs);
    }
}
