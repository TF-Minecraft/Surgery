package net.tfminecraft.surgery.procedures;

import java.util.Set;

// ==============================================
// How a healing ailment is operated on
// ==============================================
public record Procedure(String name, int requiredIncisions, int brokenBones, int shatteredBones,
                        Set<Complication> complications) {

    public Procedure {
        complications = Set.copyOf(complications);
    }

    public boolean hasBones() {
        return brokenBones > 0 || shatteredBones > 0;
    }

    public boolean has(Complication complication) {
        return complications.contains(complication);
    }
}
