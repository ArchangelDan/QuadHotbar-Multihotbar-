package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;
import com.archiangel.quadhotbar.client.QuadHotbarControlButton;
import com.archiangel.quadhotbar.client.QuadHotbarInventoryPageUi;
import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.CreativeTabsScreenPage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends Screen {

    @Shadow(remap = false)
    private CreativeTabsScreenPage currentPage;

    @Shadow private EditBox searchBox;

    @Unique private CreativeTabsScreenPage quadhotbar$lastCreativePage;
    @Unique private Button quadhotbar$previous;
    @Unique private Button quadhotbar$next;
    @Unique private Button quadhotbar$overview;

    protected CreativeInventoryScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$safeOverviewOverlap(
            double x,
            double y,
            int left,
            int top,
            int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.screen instanceof QuadHotbarOverviewScreen overview
                && overview.isBaseScreen(this)
                && overview.containsPanel(x, y)) cir.setReturnValue(false);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void quadhotbar$addPageControls(CallbackInfo ci) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        int y = screen.getGuiTop() + 109;
        quadhotbar$previous =
                addRenderableWidget(
                        new QuadHotbarControlButton(
                                screen.getGuiLeft() - 23,
                                y,
                                Component.literal("<"),
                                button -> QuadHotbarClientEvents.requestInventoryPage(-1),
                                false));
        quadhotbar$next =
                addRenderableWidget(
                        new QuadHotbarControlButton(
                                screen.getGuiLeft() + screen.getXSize() + 3,
                                y,
                                Component.literal(">"),
                                button -> QuadHotbarClientEvents.requestInventoryPage(1),
                                false));
        quadhotbar$overview =
                addRenderableWidget(
                        new QuadHotbarControlButton(
                                screen.getGuiLeft() + screen.getXSize() + 3,
                                y - 24,
                                Component.translatable("quadhotbar.overview.title"),
                                button -> QuadHotbarInventoryPageUi.openOverview(),
                                true));
        quadhotbar$lastCreativePage = currentPage;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void quadhotbar$updatePageControls(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (quadhotbar$lastCreativePage != null && currentPage != quadhotbar$lastCreativePage) {
            QuadHotbarClientEvents.hideInventoryPageLabel();
        }
        quadhotbar$lastCreativePage = currentPage;
        if (quadhotbar$previous != null) {
            boolean show = QuadHotbarInventoryPageUi.showButtons(minecraft);
            quadhotbar$previous.visible = show;
            quadhotbar$next.visible = show;
            quadhotbar$overview.visible = QuadHotbarInventoryPageUi.showOverviewButton(minecraft);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void quadhotbar$renderInventoryPageLabel(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        QuadHotbarInventoryPageUi.renderPageLabel(
                graphics,
                minecraft,
                font,
                screen.getGuiLeft() + screen.getXSize() / 2,
                screen.getGuiTop() - 62);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$turnInventoryPageWithHotbarBinds(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if ((searchBox == null || !searchBox.isFocused() || !searchBox.isVisible())
                && QuadHotbarInventoryPageUi.handlePageKey(minecraft, keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }
}
