package net.tfminecraft.surgery.procedures;

import java.util.Locale;

// ==============================================
// Complications a procedure can carry, each adding pressure during the operation
// ==============================================
public enum Complication {

    // Wound reopens and bleeds every few moves unless sponged
    HAEMORRHAGE,
    // Patient may collapse while under chloroform and need smelling salts
    SHOCK,
    // Patient arrives feverish and the fever keeps climbing
    SEPSIS;

    public static Complication fromConfig(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
