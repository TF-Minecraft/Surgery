package net.tfminecraft.surgery.managers;

import org.bukkit.Material;

// ==============================================
// Constants for the surgery system
// ==============================================
public class SurgeryConstants {

    // ==============================================
    // Menu slot layout
    // ==============================================

    // Info block slots (top row, read-only displays)
    public static final int SLOT_DIAGNOSIS = 10;
    public static final int SLOT_PULSE = 11;
    public static final int SLOT_STATUS = 12;
    public static final int SLOT_TEMPERATURE = 13;
    public static final int SLOT_OPERATION_SITE = 14;
    public static final int SLOT_INCISIONS = 15;
    public static final int SLOT_SKILL_FAIL = 16;
    public static final int INFO_SLOT_FIRST = SLOT_DIAGNOSIS;
    public static final int INFO_SLOT_LAST = SLOT_SKILL_FAIL;

    // Tool slots
    public static final int SLOT_SPONGE = 28;
    public static final int SLOT_SCALPEL = 29;
    public static final int SLOT_STITCHES = 30;
    public static final int SLOT_ANTIBIOTICS = 31;
    public static final int SLOT_ANTISEPTIC = 32;
    public static final int SLOT_SURGICAL_GLOVE = 33;
    public static final int SLOT_ULTRASOUND = 34;
    public static final int SLOT_LAB_KIT = 37;
    public static final int SLOT_ANESTHETIC = 38;
    public static final int SLOT_DEFIBRILLATOR = 39;
    public static final int SLOT_PINS = 40;
    public static final int SLOT_SPLINT = 41;
    public static final int SLOT_CLAMP = 42;
    public static final int SLOT_TRANSFUSION = 43;

    // List of possible patient statuses (with corresponding colors)
    public static final String[] PATIENT_STATUSES = {
        "Awake",         // YELLOW_CONCRETE
        "Unconscious",   // LIME_CONCRETE
        "Heart Stopped", // RED_CONCRETE
        "Coming to"      // ORANGE_CONCRETE
    };
    
    // ==============================================
    // Gets the material color for pulse status
    // ==============================================
    public static Material getPulseColor(String pulseStatus) {
        return switch (pulseStatus) {
            case "Strong" -> Material.LIME_CONCRETE;
            case "Steady" -> Material.YELLOW_CONCRETE;
            case "Weak" -> Material.ORANGE_CONCRETE;
            case "Extremely Weak" -> Material.RED_CONCRETE;
            default -> Material.GRAY_CONCRETE;
        };
    }
    
    // ==============================================
    // Gets the material color for patient status
    // ==============================================
    public static Material getStatusColor(String status) {
        return switch (status) {
            case "Awake" -> Material.YELLOW_CONCRETE;
            case "Unconscious" -> Material.LIME_CONCRETE;
            case "Heart Stopped" -> Material.RED_CONCRETE;
            case "Coming to" -> Material.ORANGE_CONCRETE;
            default -> Material.GRAY_CONCRETE;
        };
    }
    
    // ==============================================
    // Gets the material color for temperature (Fahrenheit)
    // ==============================================
    public static Material getTemperatureColor(double tempF) {
        if (tempF <= 100) {
            return Material.LIME_CONCRETE;
        } else if (tempF <= 104) {
            return Material.YELLOW_CONCRETE;
        } else if (tempF <= 106) {
            return Material.ORANGE_CONCRETE;
        } else {
            return Material.RED_CONCRETE;
        }
    }
    
    // ==============================================
    // Gets the material color for operation site status
    // ==============================================
    public static Material getOperationSiteColor(String status) {
        return switch (status) {
            case "Clean" -> Material.LIME_CONCRETE;
            case "Not sanitized" -> Material.YELLOW_CONCRETE;
            case "Unclean" -> Material.ORANGE_CONCRETE;
            case "Unsanitary" -> Material.RED_CONCRETE;
            default -> Material.GRAY_CONCRETE;
        };
    }
    
    // ==============================================
    // Gets the material color for incision count
    // ==============================================
    public static Material getIncisionColor(int count) {
        return count == 0 ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
    }
    
    // ==============================================
    // Formats temperature for display (Fahrenheit and Celsius)
    // ==============================================
    public static String formatTemperature(double tempF) {
        double tempC = (tempF - 32) * 5 / 9;
        return String.format("%.1f\u00b0F / %.1f\u00b0C", tempF, tempC);
    }
    
    // ==============================================
    // Improves pulse to next better status
    // ==============================================
    public static String improvePulse(String currentPulse) {
        return switch (currentPulse) {
            case "Extremely Weak" -> "Weak";
            case "Weak" -> "Steady";
            case "Steady" -> "Strong";
            default -> "Strong";
        };
    }
    
    // ==============================================
    // Worsens pulse to next worse status
    // ==============================================
    public static String worsenPulse(String currentPulse) {
        return switch (currentPulse) {
            case "Strong" -> "Steady";
            case "Steady" -> "Weak";
            case "Weak" -> "Extremely Weak";
            default -> "Extremely Weak";
        };
    }
}
