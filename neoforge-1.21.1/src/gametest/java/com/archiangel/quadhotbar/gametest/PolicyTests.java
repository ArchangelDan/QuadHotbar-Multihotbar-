package com.archiangel.quadhotbar.gametest;

import com.archiangel.quadhotbar.QuadHotbarNetwork;
import com.archiangel.quadhotbar.QuadHotbarPages;
import com.archiangel.quadhotbar.QuadHotbarPolicy;
import com.archiangel.quadhotbar.QuadHotbarServerConfig;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("quadhotbar")
@PrefixGameTestTemplate(false)
public final class PolicyTests {
    private record Setting<T>(ModConfigSpec.ConfigValue<T> value, T previous)
            implements AutoCloseable {
        @SuppressWarnings("unchecked")
        static <T> Setting<T> set(String field, T replacement) throws Exception {
            var declared = QuadHotbarServerConfig.class.getDeclaredField(field);
            declared.setAccessible(true);
            var config = (ModConfigSpec.ConfigValue<T>) declared.get(null);
            var result = new Setting<>(config, config.get());
            config.set(replacement);
            config.clearCache();
            return result;
        }

        @Override
        public void close() {
            value.set(previous);
            value.clearCache();
        }
    }

    @GameTest(template = "empty", batch = "policy")
    public static void exemptionsAndSpecificRules(GameTestHelper helper) throws Exception {
        try (var freedom = Setting.set("ALLOW_FREEDOM", false);
                var pages = Setting.set("MAX_INVENTORY_PAGES", 2);
                var rows = Setting.set("MAX_HOTBAR_ROWS", 4);
                var operators = Setting.set("EXEMPT_OPERATORS", true);
                var names = Setting.set("UNRESTRICTED_PLAYERS", List.of("Archiangel", "Builder"));
                var overrides =
                        Setting.set(
                                "PLAYER_OVERRIDES",
                                List.of(
                                        "Archiangel; maxInventoryPages=2; maxHotbarRows=4;"
                                                + " allowPageOverview=false",
                                        "Operator; allowFreedom=false",
                                        "Normal; allowFreedom=true; maxInventoryPages=3"))) {
            var ordinary = QuadHotbarServerConfig.resolve("Nobody", false);
            helper.assertTrue(
                    !ordinary.allowFreedom() && ordinary.maxInventoryPages() == 1,
                    "Ordinary player bypassed global policy");
            var whitelisted = QuadHotbarServerConfig.resolve("archiANGEL", false);
            helper.assertTrue(
                    whitelisted.allowFreedom()
                            && whitelisted.maxInventoryPages() == 2
                            && whitelisted.maxHotbarRows() == 4
                            && !whitelisted.allowPageOverview(),
                    "Case-insensitive whitelist or explicit restriction failed");
            helper.assertTrue(
                    QuadHotbarServerConfig.resolve("Builder", false)
                            .equals(QuadHotbarPolicy.UNRESTRICTED),
                    "Whitelist did not bypass global bans");
            helper.assertTrue(
                    QuadHotbarServerConfig.resolve("OtherOperator", true)
                            .equals(QuadHotbarPolicy.UNRESTRICTED),
                    "Operator not exempt by default");
            helper.assertTrue(
                    !QuadHotbarServerConfig.resolve("Operator", true).allowFreedom(),
                    "Explicit operator restriction ignored");
            helper.assertTrue(
                    QuadHotbarServerConfig.resolve("Normal", false).maxInventoryPages() == 3,
                    "Ordinary player's explicit override ignored");
            try (var limitedOps = Setting.set("EXEMPT_OPERATORS", false)) {
                helper.assertTrue(
                        !QuadHotbarServerConfig.resolve("OtherOperator", true).allowFreedom(),
                        "Operator exemption cannot be disabled");
                helper.assertTrue(
                        QuadHotbarServerConfig.resolve("Builder", true).allowFreedom(),
                        "Disabling operator exemption broke the independent whitelist");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "policy")
    public static void individualCapsPreserveHiddenItemsAndAlignment(GameTestHelper helper)
            throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "PolicyBuilder");
        var player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        profile,
                        ClientInformation.createDefault());
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        player.initInventoryMenu();
        var state = QuadHotbarPages.state(player);
        state.pageCount = 100;
        state.hotbarRows = 8;
        QuadHotbarPages.switchActivePage(player, 99);
        QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.DIAMOND, 47));
        try (var whitelist = Setting.set("UNRESTRICTED_PLAYERS", List.of("PolicyBuilder"));
                var rules =
                        Setting.set(
                                "PLAYER_OVERRIDES",
                                List.of("PolicyBuilder; maxInventoryPages=5; maxHotbarRows=6"))) {
            QuadHotbarNetwork.enforceServerLimits(player);
            helper.assertTrue(
                    state.pageCount == 3 && state.hotbarRows == 6 && state.activePage == 0,
                    "Individual limits or complete-page alignment bypassed");
            helper.assertTrue(
                    QuadHotbarPages.getPageItem(player, 99, 35).getCount() == 47,
                    "Reducing individualized caps erased hidden items");
            try (var noRules = Setting.set("PLAYER_OVERRIDES", List.<String>of())) {
                var apply =
                        QuadHotbarNetwork.class.getDeclaredMethod(
                                "applyPageSettings", ServerPlayer.class, int.class, int.class);
                apply.setAccessible(true);
                apply.invoke(null, player, 100, 8);
                helper.assertTrue(
                        state.pageCount == 100
                                && state.hotbarRows == 8
                                && QuadHotbarPages.getPageItem(player, 99, 35).getCount() == 47,
                        "Removing a cap failed to restore access to saved items");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "policy")
    public static void invalidOverridesAreRejected(GameTestHelper helper) throws Exception {
        var parse = QuadHotbarServerConfig.class.getDeclaredMethod("parseOverride", String.class);
        parse.setAccessible(true);
        for (String rule :
                List.of(
                        "Name; maxInventoryPages=101",
                        "Name; maxHotbarRows=9",
                        "Name; allowFreedom=yes",
                        "Name; overviewLayout=BAD",
                        "Name; maxOverviewWidth=10",
                        "Name; unknown=true",
                        "Name; maxOverviewHeight=0",
                        "Name; maxInventoryPages=2; maxInventoryPages=4",
                        "; allowFreedom=true",
                        "Name; allowFreedom=true;")) {
            helper.assertTrue(parse.invoke(null, rule) == null, "Invalid rule accepted: " + rule);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "policy")
    public static void demotionAppliesLimitsWithoutRelogging(GameTestHelper helper)
            throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "PolicyOperator");
        var player =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        profile,
                        ClientInformation.createDefault());
        player.connection = new FakePlayer(helper.getLevel(), profile).connection;
        player.initInventoryMenu();
        var list = player.server.getPlayerList();
        try (var pages = Setting.set("MAX_INVENTORY_PAGES", 2);
                var rows = Setting.set("MAX_HOTBAR_ROWS", 4);
                var operators = Setting.set("EXEMPT_OPERATORS", true);
                var names = Setting.set("UNRESTRICTED_PLAYERS", List.<String>of());
                var rules = Setting.set("PLAYER_OVERRIDES", List.<String>of())) {
            list.op(profile);
            helper.assertTrue(
                    QuadHotbarServerConfig.forPlayer(player).equals(QuadHotbarPolicy.UNRESTRICTED),
                    "Actual server operator status was ignored");
            var state = QuadHotbarPages.state(player);
            state.pageCount = 100;
            state.hotbarRows = 8;
            QuadHotbarPages.switchActivePage(player, 99);
            QuadHotbarPages.setPageItem(player, 99, 35, new ItemStack(Items.EMERALD, 31));
            com.archiangel.quadhotbar.QuadHotbarPolicyNetwork.sync(player);
            list.deop(profile);
            helper.assertTrue(
                    !QuadHotbarServerConfig.permitsCurrentPages(player),
                    "Demotion still authorized stale overview clicks before the next sync");
            player.tickCount = 20;
            QuadHotbarNetwork.ForgeEvents.onPlayerTick(
                    new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            helper.assertTrue(
                    state.pageCount == 2 && state.hotbarRows == 4 && state.activePage == 0,
                    "Demotion left unrestricted pages enabled");
            helper.assertTrue(
                    QuadHotbarPages.getPageItem(player, 99, 35).getCount() == 31,
                    "Demotion erased hidden items");
        } finally {
            if (list.isOp(profile)) list.deop(profile);
            com.archiangel.quadhotbar.QuadHotbarPolicyNetwork.forget(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "policy")
    public static void effectivePolicyCodecRoundTrip(GameTestHelper helper) throws Exception {
        var payload = Class.forName("com.archiangel.quadhotbar.QuadHotbarPolicyNetwork$Payload");
        var constructor = payload.getDeclaredConstructor(QuadHotbarPolicy.class);
        constructor.setAccessible(true);
        var field = payload.getDeclaredField("CODEC");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var codec =
                (net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Object>)
                        field.get(null);
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            var original =
                    constructor.newInstance(
                            new QuadHotbarPolicy(
                                    true,
                                    false,
                                    true,
                                    false,
                                    QuadHotbarServerConfig.OverviewLayout.STANDALONE,
                                    22,
                                    7,
                                    9,
                                    13));
            codec.encode(buffer, original);
            helper.assertTrue(
                    original.equals(codec.decode(buffer)), "Effective policy codec lost a field");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
}
