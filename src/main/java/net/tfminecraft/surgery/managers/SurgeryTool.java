package net.tfminecraft.surgery.managers;

// ==============================================
// Victorian surgical instruments: config key, default TLibs item path, and menu slot
// Default paths keep the original MMOItems IDs so existing crafted tools keep working
// ==============================================
public enum SurgeryTool {

    SPONGE("sponge", "m.surgery.sponge", 28),
    SCALPEL("scalpel", "m.surgery.scalpel", 29),
    SUTURE("suture", "m.surgery.stitches", 30),
    TINCTURE("tincture", "m.surgery.antibiotics", 31),
    CARBOLIC_ACID("carbolic-acid", "m.surgery.antiseptic", 32),
    DRESSING("dressing", "m.surgery.surgical_glove", 33),
    STETHOSCOPE("stethoscope", "m.surgery.ultrasound", 34),
    THERMOMETER("thermometer", "m.surgery.lab_kit", 37),
    CHLOROFORM("chloroform", "m.surgery.anesthetic", 38),
    SMELLING_SALTS("smelling-salts", "m.surgery.defibrillator", 39),
    SILVER_WIRE("silver-wire", "m.surgery.pins", 40),
    SPLINT("splint", "m.surgery.splint", 41),
    ARTERY_FORCEPS("artery-forceps", "m.surgery.clamp", 42),
    TRANSFUSION("transfusion", "m.surgery.transfusion", 43);

    private final String configKey;
    private final String defaultPath;
    private final int slot;

    SurgeryTool(String configKey, String defaultPath, int slot) {
        this.configKey = configKey;
        this.defaultPath = defaultPath;
        this.slot = slot;
    }

    public String getConfigKey() { return configKey; }
    public String getDefaultPath() { return defaultPath; }
    public int getSlot() { return slot; }

    // ==============================================
    // Finds the tool shown in a menu slot, or null for info blocks and empty slots
    // ==============================================
    public static SurgeryTool fromSlot(int slot) {
        for (SurgeryTool tool : values()) {
            if (tool.slot == slot) {
                return tool;
            }
        }
        return null;
    }
}
