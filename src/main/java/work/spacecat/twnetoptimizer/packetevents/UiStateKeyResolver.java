package work.spacecat.twnetoptimizer.packetevents;

import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;

import java.nio.charset.StandardCharsets;

final class UiStateKeyResolver {
    private static final int MAX_STRING_BYTES = 1_048_576;

    private UiStateKeyResolver() {
    }

    static String resolve(String packetName, Object byteBuf) {
        if (packetName == null || byteBuf == null) {
            return null;
        }

        try {
            Object duplicate = ByteBufHelper.duplicate(byteBuf);

            return switch (packetName) {
                case "BOSS_BAR" -> {
                    long most = ByteBufHelper.readLong(duplicate);
                    long least = ByteBufHelper.readLong(duplicate);
                    yield "boss:" + most + ":" + least;
                }

                case "SCOREBOARD_OBJECTIVE" ->
                        "objective:" + readString(duplicate);

                case "UPDATE_SCORE" -> {
                    String entity = readString(duplicate);
                    String objective = readString(duplicate);
                    yield "score:" + entity + "\u0000" + objective;
                }

                case "DISPLAY_SCOREBOARD" ->
                        "display:" + ByteBufHelper.readVarInt(duplicate);

                case "TEAMS" ->
                        "team:" + readString(duplicate);

                case "PLAYER_LIST_HEADER_AND_FOOTER" ->
                        "tab:header-footer";

                default -> null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String readString(Object byteBuf) {
        int length = ByteBufHelper.readVarInt(byteBuf);

        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Invalid string length: " + length);
        }

        byte[] bytes = new byte[length];
        ByteBufHelper.readBytes(byteBuf, bytes);

        return new String(bytes, StandardCharsets.UTF_8);
    }
}
