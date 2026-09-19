package work.spacecat.twnetoptimizer.optimizer;

import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;

final class PayloadBuffer {
    private PayloadBuffer() {
    }

    static boolean matches(Object byteBuf, byte[] expected) {
        if (byteBuf == null || expected == null) {
            return false;
        }

        try {
            int readable = ByteBufHelper.readableBytes(byteBuf);

            if (readable != expected.length) {
                return false;
            }

            int start = ByteBufHelper.readerIndex(byteBuf);

            for (int i = 0; i < readable; i++) {
                int actual = ByteBufHelper.getUnsignedByte(
                        byteBuf,
                        start + i
                );

                if ((byte) actual != expected[i]) {
                    return false;
                }
            }

            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static byte[] copy(Object byteBuf) {
        if (byteBuf == null) {
            return null;
        }

        try {
            return ByteBufHelper.copyBytes(byteBuf);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
