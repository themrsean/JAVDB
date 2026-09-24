package backup;

public enum BackupVerificationLevel {
    QUICK,
    FULL;

    public static BackupVerificationLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return QUICK;
        }

        BackupVerificationLevel level = null;

        for (BackupVerificationLevel candidate : values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                level = candidate;
            }
        }

        if (level == null) {
            throw new IllegalArgumentException(
                    "Invalid backup verification level: " + value
            );
        }

        return level;
    }
}
