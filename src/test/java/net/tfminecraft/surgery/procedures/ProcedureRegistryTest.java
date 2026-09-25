package net.tfminecraft.surgery.procedures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ProcedureRegistryTest {

    private static ProcedureRegistry load(String yaml, List<String> warnings) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        ProcedureRegistry registry = new ProcedureRegistry();
        registry.load(config, warnings::add);
        return registry;
    }

    @Test
    void loadsProceduresByTraitIdIgnoringCase() throws InvalidConfigurationException {
        ProcedureRegistry registry = load("""
            procedures:
              Broken_Leg:
                name: "Compound Fracture"
                required-incisions: 2
                broken-bones: 2
                shattered-bones: 1
                complications: [haemorrhage, SEPSIS]
            """, new ArrayList<>());

        Procedure leg = registry.get("broken_leg");
        assertEquals("Compound Fracture", leg.name());
        assertEquals(2, leg.requiredIncisions());
        assertEquals(1, leg.shatteredBones());
        assertTrue(leg.hasBones());
        assertTrue(leg.has(Complication.HAEMORRHAGE));
        assertTrue(leg.has(Complication.SEPSIS));
        assertFalse(leg.has(Complication.SHOCK));
    }

    @Test
    void unknownAilmentsUseTheDefaultProcedure() throws InvalidConfigurationException {
        ProcedureRegistry registry = load("""
            default-procedure:
              name: "Wound"
              required-incisions: 3
            """, new ArrayList<>());

        Procedure unknown = registry.get("sprained_wrist");
        assertEquals("Wound", unknown.name());
        assertEquals(3, unknown.requiredIncisions());
        assertFalse(unknown.hasBones());
    }

    @Test
    void procedureFieldsFallBackToTheDefault() throws InvalidConfigurationException {
        ProcedureRegistry registry = load("""
            default-procedure:
              required-incisions: 4
            procedures:
              half_blind:
                name: "Injured Eye"
            """, new ArrayList<>());

        assertEquals(4, registry.get("half_blind").requiredIncisions());
    }

    @Test
    void warnsAboutUnknownComplications() throws InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        ProcedureRegistry registry = load("""
            procedures:
              broken_arm:
                complications: [lupus, shock]
            """, warnings);

        assertEquals(1, warnings.size());
        assertTrue(registry.get("broken_arm").has(Complication.SHOCK));
    }
}
