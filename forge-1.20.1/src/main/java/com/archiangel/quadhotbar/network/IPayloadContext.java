package com.archiangel.quadhotbar.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.CompletableFuture;

public interface IPayloadContext {
    Player player();

    CompletableFuture<Void> enqueueWork(Runnable action);

    static IPayloadContext of(NetworkEvent.Context context) {
        return new IPayloadContext() {
            public Player player() {
                return context.getSender();
            }

            public CompletableFuture<Void> enqueueWork(Runnable action) {
                return context.enqueueWork(action);
            }
        };
    }
}
