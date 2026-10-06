package com.archiangel.quadhotbar.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.FriendlyByteBuf;

public final class ByteBufCodecs {
    private ByteBufCodecs() {}

    public static final StreamCodec<ByteBuf, Integer> VAR_INT =
            new StreamCodec<>() {
                public Integer decode(ByteBuf buffer) {
                    return new FriendlyByteBuf(buffer).readVarInt();
                }

                public void encode(ByteBuf buffer, Integer value) {
                    new FriendlyByteBuf(buffer).writeVarInt(value);
                }
            };
    public static final StreamCodec<ByteBuf, Boolean> BOOL =
            new StreamCodec<>() {
                public Boolean decode(ByteBuf buffer) {
                    return buffer.readBoolean();
                }

                public void encode(ByteBuf buffer, Boolean value) {
                    buffer.writeBoolean(value);
                }
            };
}
