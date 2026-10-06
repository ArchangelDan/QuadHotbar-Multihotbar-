package com.archiangel.quadhotbar.mixin;

import com.archiangel.quadhotbar.client.QuadHotbarClientEvents;
import com.archiangel.quadhotbar.client.QuadHotbarControlButton;
import com.archiangel.quadhotbar.client.QuadHotbarInventoryPageUi;
import com.archiangel.quadhotbar.client.QuadHotbarKeyMappings;
import com.archiangel.quadhotbar.client.QuadHotbarOverviewScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenControlsMixin extends Screen {
    @Shadow private boolean skipNextRelease;
    @Shadow private boolean doubleclick;
    @Shadow private long lastClickTime;
    @Unique private boolean quadhotbar$controlPressed;
    @Unique private QuadHotbarControlButton quadhotbar$overview;

    protected ContainerScreenControlsMixin(Component title) {
        super(title);
    }

    @Unique
    private boolean quadhotbar$genericContainer() {
        return !((Object) this instanceof InventoryScreen
                || (Object) this instanceof CreativeModeInventoryScreen
                || (Object) this instanceof QuadHotbarOverviewScreen);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void quadhotbar$addGenericOverview(CallbackInfo ci) {
        if (!quadhotbar$genericContainer()) return;
        var screen = (AbstractContainerScreen<?>) (Object) this;
        quadhotbar$overview =
                addRenderableWidget(
                        new QuadHotbarControlButton(
                                screen.getGuiLeft() + screen.getXSize() + 3,
                                screen.getGuiTop() + screen.getYSize() - 22,
                                Component.translatable("quadhotbar.overview.title"),
                                b -> QuadHotbarInventoryPageUi.openOverview(),
                                true));
        quadhotbar$overview.visible = false;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void quadhotbar$updateGenericOverview(
            GuiGraphics graphics, int x, int y, float tick, CallbackInfo ci) {
        if (quadhotbar$overview == null) return;
        var screen = (AbstractContainerScreen<?>) (Object) this;
        quadhotbar$overview.visible =
                QuadHotbarInventoryPageUi.showOverviewButton(minecraft)
                        && QuadHotbarClientEvents.serverPolicy().allowContainerOverview()
                        && !screen.getMenu().slots.isEmpty();
        quadhotbar$overview.setX(screen.getGuiLeft() + screen.getXSize() + 3);
        quadhotbar$overview.setY(screen.getGuiTop() + screen.getYSize() - 22);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$genericOverviewKey(
            int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (quadhotbar$genericContainer()
                && !(getFocused() instanceof EditBox)
                && !minecraft.options.keyInventory.matches(key, scan)
                && QuadHotbarKeyMappings.OPEN_OVERVIEW.matches(key, scan)
                && QuadHotbarInventoryPageUi.overviewAvailable(minecraft)
                && QuadHotbarClientEvents.serverPolicy().allowContainerOverview()) {
            QuadHotbarInventoryPageUi.openOverview();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$singleCursor(
            net.minecraft.client.gui.GuiGraphics graphics,
            net.minecraft.world.item.ItemStack stack,
            int x,
            int y,
            String text,
            CallbackInfo ci) {
        if (minecraft.screen instanceof QuadHotbarOverviewScreen overview
                && overview.isBaseScreen(this)) ci.cancel();
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$safeOverlap(
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$clickControl(
            double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        for (var child : children()) {
            if (child instanceof QuadHotbarControlButton control
                    && control.visible
                    && x >= control.getX()
                    && x < control.getX() + control.getWidth()
                    && y >= control.getY()
                    && y < control.getY() + control.getHeight()) {
                // Even right-clicking a control (or clicking a disabled control) must
                // never be interpreted as dropping the cursor outside the inventory.
                control.mouseClicked(x, y, button);
                quadhotbar$controlPressed = true;
                skipNextRelease = true;
                doubleclick = false;
                lastClickTime = 0;
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void quadhotbar$releaseControl(
            double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (quadhotbar$controlPressed) {
            quadhotbar$controlPressed = false;
            skipNextRelease = true;
            cir.setReturnValue(true);
        }
    }
}
