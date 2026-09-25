package net.tfminecraft.surgery.procedures;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// ==============================================
// Procedures keyed by RPCharacters healing trait ID, with a fallback for
// healing ailments that have no procedure of their own
// ==============================================
public class ProcedureRegistry {

    private static final Procedure BUILT_IN_DEFAULT = new Procedure("Injury", 2, 0, 0, Set.of());

    private final Map<String, Procedure> procedures = new HashMap<>();
    private Procedure defaultProcedure = BUILT_IN_DEFAULT;

    // ==============================================
    // Loads "procedures" and "default-procedure" from the plugin config
    // Unknown complication names are reported through the warning callback
    // ==============================================
    public void load(ConfigurationSection root, Consumer<String> warning) {
        procedures.clear();
        ConfigurationSection defaults = root.getConfigurationSection("default-procedure");
        defaultProcedure = defaults == null ? BUILT_IN_DEFAULT : parse(defaults, BUILT_IN_DEFAULT, warning);

        ConfigurationSection section = root.getConfigurationSection("procedures");
        if (section == null) {
            return;
        }
        for (String traitId : section.getKeys(false)) {
            ConfigurationSection procedure = section.getConfigurationSection(traitId);
            if (procedure != null) {
                procedures.put(traitId.toLowerCase(Locale.ROOT), parse(procedure, defaultProcedure, warning));
            }
        }
    }

    // ==============================================
    // The procedure for a healing trait ID, falling back to the default procedure
    // ==============================================
    public Procedure get(String traitId) {
        if (traitId == null) {
            return defaultProcedure;
        }
        return procedures.getOrDefault(traitId.toLowerCase(Locale.ROOT), defaultProcedure);
    }

    private static Procedure parse(ConfigurationSection section, Procedure fallback, Consumer<String> warning) {
        Set<Complication> complications = EnumSet.noneOf(Complication.class);
        for (String value : section.getStringList("complications")) {
            Complication complication = Complication.fromConfig(value);
            if (complication == null) {
                warning.accept("Unknown complication '" + value + "' in " + section.getCurrentPath());
            } else {
                complications.add(complication);
            }
        }
        return new Procedure(
            section.getString("name", fallback.name()),
            Math.max(0, section.getInt("required-incisions", fallback.requiredIncisions())),
            Math.max(0, section.getInt("broken-bones", fallback.brokenBones())),
            Math.max(0, section.getInt("shattered-bones", fallback.shatteredBones())),
            complications);
    }
}
