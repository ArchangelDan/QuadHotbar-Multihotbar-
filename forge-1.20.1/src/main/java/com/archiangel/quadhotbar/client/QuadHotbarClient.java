package com.archiangel.quadhotbar.client;

import com.archiangel.quadhotbar.QuadHotbar;
import com.archiangel.quadhotbar.QuadHotbarOverviewMenu;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration: dedicated servers must never load screen classes. */
@Mod.EventBusSubscriber(
        modid = QuadHotbar.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuadHotbarClient {
    private QuadHotbarClient() {}

    public static void rejectIncompatibleServer(
            net.minecraftforge.network.NetworkEvent.Context context) {
        var connection = context.getNetworkManager();
        var data = net.minecraftforge.network.NetworkHooks.getConnectionData(connection);
        var remote = data == null ? null : data.getModData().get(QuadHotbar.MODID);
        String version =
                remote != null && !remote.getRight().isBlank()
                        ? remote.getRight()
                        : net.minecraftforge.fml.ModList.get()
                                .getModContainerById(QuadHotbar.MODID)
                                .orElseThrow()
                                .getModInfo()
                                .getVersion()
                                .toString();
        context.setPacketHandled(true);
        connection.disconnect(
                com.archiangel.quadhotbar.QuadHotbarConnectionGuard.reason(
                        com.archiangel.quadhotbar.QuadHotbarConnectionGuard.Status.INCOMPATIBLE,
                        net.minecraft.client.Minecraft.getInstance().options.languageCode,
                        version));
    }

    @SubscribeEvent
    public static void registerKeys(
            net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        QuadHotbarKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () ->
                        MenuScreens.<QuadHotbarOverviewMenu, QuadHotbarOverviewScreen>register(
                                QuadHotbarOverviewMenu.TYPE.get(), QuadHotbarOverviewScreen::new));
        ModLoadingContext.get()
                .registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () ->
                                new ConfigScreenHandler.ConfigScreenFactory(
                                        (minecraft, parent) -> new QuadHotbarConfigScreen(parent)));
    }
}
