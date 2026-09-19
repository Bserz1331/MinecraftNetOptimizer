package work.spacecat.twnetoptimizer.profiler;

import java.util.Locale;

public enum PacketCategory {
    CHUNK,
    ENTITY,
    UI,
    OTHER;

    public static PacketCategory classify(String packetName) {
        if (packetName == null || packetName.isBlank()) {
            return OTHER;
        }

        String name = packetName.toUpperCase(Locale.ROOT);

        if (name.contains("CHUNK") || name.contains("LIGHT_UPDATE") || name.contains("UPDATE_LIGHT")) {
            return CHUNK;
        }
        if (name.contains("SCORE") || name.contains("BOSS") || name.contains("TITLE")
                || name.contains("ACTION_BAR") || name.contains("TAB_LIST") || name.contains("PLAYER_INFO")) {
            return UI;
        }
        if (name.contains("ENTITY") || name.contains("SPAWN") || name.contains("MOB")) {
            return ENTITY;
        }
        return OTHER;
    }
}
