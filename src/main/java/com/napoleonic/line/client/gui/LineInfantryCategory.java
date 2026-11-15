package com.napoleonic.line.client.gui;

import com.napoleonic.line.client.VolleyAimingClientHandler;
import com.napoleonic.line.entity.LineInfantryMode;
import com.napoleonic.line.network.C2SLineCommandPacket;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.talhanation.recruits.client.gui.CommandScreen;
import com.talhanation.recruits.client.gui.commandscreen.ICommandCategory;
import com.talhanation.recruits.client.gui.group.RecruitsCommandButton;
import com.napoleonic.line.compat.MusketModCompat;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class LineInfantryCategory implements ICommandCategory {
    private static final Component TOOLTIP_CATEGORY = Component.translatable("gui.napoleonic_line.category.tooltip");
    private static final Component TEXT_SHOULDER_ARMS = Component.translatable("gui.napoleonic_line.shoulder_arms");
    private static final Component TIP_SHOULDER_ARMS = Component.translatable("gui.napoleonic_line.shoulder_arms.tooltip");
    private static final Component TEXT_PRESENT_ARMS = Component.translatable("gui.napoleonic_line.present_arms");
    private static final Component TIP_PRESENT_ARMS = Component.translatable("gui.napoleonic_line.present_arms.tooltip");
    private static final Component TEXT_VOLLEY = Component.translatable("gui.napoleonic_line.volley");
    private static final Component TIP_VOLLEY = Component.translatable("gui.napoleonic_line.volley.tooltip");
    private static final Component TEXT_FIRE_AT_WILL = Component.translatable("gui.napoleonic_line.fire_at_will");
    private static final Component TIP_FIRE_AT_WILL = Component.translatable("gui.napoleonic_line.fire_at_will.tooltip");
    private static final Component TEXT_RELOAD = Component.translatable("gui.napoleonic_line.reload");
    private static final Component TIP_RELOAD = Component.translatable("gui.napoleonic_line.reload.tooltip");
    private static final Component TEXT_CEASE_FIRE = Component.translatable("gui.napoleonic_line.cease_fire");
    private static final Component TIP_CEASE_FIRE = Component.translatable("gui.napoleonic_line.cease_fire.tooltip");
    private static final Component TEXT_OPEN_SCREEN = Component.translatable("gui.napoleonic_line.open_screen");

    @Override
    public Component getToolTipName() {
        return TOOLTIP_CATEGORY;
    }

    @Override
    public ItemStack getIcon() {
        ItemStack musket = MusketModCompat.icon();
        if (!musket.isEmpty()) {
            return musket;
        }
        Item arquebus = ForgeRegistries.ITEMS.getValue(new ResourceLocation("medieval_boomsticks", "arquebus"));
        if (arquebus != null && arquebus != Items.AIR) {
            return new ItemStack(arquebus);
        }
        return new ItemStack(Items.CROSSBOW);
    }

    @Override
    public void createButtons(CommandScreen screen, int centerX, int centerY, List<RecruitsGroup> groups, Player player) {
        boolean hasActiveGroup = groups != null && groups.stream().anyMatch(g -> !g.isDisabled());

        // a) На плечо (Shoulder Arms)
        RecruitsCommandButton shoulderArmsBtn = new RecruitsCommandButton(centerX - 100, centerY - 38, TEXT_SHOULDER_ARMS,
                btn -> sendModeCommand(groups, LineInfantryMode.SHOULDER_ARMS));
        shoulderArmsBtn.setTooltip(Tooltip.create(TIP_SHOULDER_ARMS));
        shoulderArmsBtn.active = hasActiveGroup;
        screen.addRenderableWidget(shoulderArmsBtn);

        // b) В руки / На караул (Present Arms)
        RecruitsCommandButton presentArmsBtn = new RecruitsCommandButton(centerX, centerY - 38, TEXT_PRESENT_ARMS,
                btn -> sendModeCommand(groups, LineInfantryMode.PRESENT_ARMS));
        presentArmsBtn.setTooltip(Tooltip.create(TIP_PRESENT_ARMS));
        presentArmsBtn.active = hasActiveGroup;
        screen.addRenderableWidget(presentArmsBtn);

        // c) Залп по точке (Volley)
        RecruitsCommandButton volleyBtn = new RecruitsCommandButton(centerX + 100, centerY - 38, TEXT_VOLLEY,
                btn -> {
                    List<UUID> activeIds = getActiveGroupIds(groups);
                    String summary = getActiveGroupNames(groups);
                    screen.onClose();
                    VolleyAimingClientHandler.startAiming(activeIds, summary);
                });
        volleyBtn.setTooltip(Tooltip.create(TIP_VOLLEY));
        volleyBtn.active = hasActiveGroup;
        screen.addRenderableWidget(volleyBtn);

        // d) Свободный огонь (Fire at Will)
        RecruitsCommandButton fireAtWillBtn = new RecruitsCommandButton(centerX - 100, centerY + 12, TEXT_FIRE_AT_WILL,
                btn -> sendModeCommand(groups, LineInfantryMode.FIRE_AT_WILL));
        fireAtWillBtn.setTooltip(Tooltip.create(TIP_FIRE_AT_WILL));
        fireAtWillBtn.active = hasActiveGroup;
        screen.addRenderableWidget(fireAtWillBtn);

        // e) Перезарядка (Reload)
        RecruitsCommandButton reloadBtn = new RecruitsCommandButton(centerX, centerY + 12, TEXT_RELOAD,
                btn -> sendModeCommand(groups, LineInfantryMode.RELOAD));
        reloadBtn.setTooltip(Tooltip.create(TIP_RELOAD));
        reloadBtn.active = hasActiveGroup;
        screen.addRenderableWidget(reloadBtn);

        // f) Прекратить огонь / Вольно (Cease Fire)
        RecruitsCommandButton ceaseFireBtn = new RecruitsCommandButton(centerX + 100, centerY + 12, TEXT_CEASE_FIRE,
                btn -> sendModeCommand(groups, LineInfantryMode.CEASE_FIRE));
        ceaseFireBtn.setTooltip(Tooltip.create(TIP_CEASE_FIRE));
        ceaseFireBtn.active = hasActiveGroup;
        screen.addRenderableWidget(ceaseFireBtn);

        // Button to switch to the dedicated Line Infantry screen
        RecruitsCommandButton openScreenBtn = new RecruitsCommandButton(centerX, centerY + 55, TEXT_OPEN_SCREEN,
                btn -> Minecraft.getInstance().setScreen(new LineInfantryScreen(player)));
        openScreenBtn.setTooltip(Tooltip.create(TOOLTIP_CATEGORY));
        screen.addRenderableWidget(openScreenBtn);
    }

    public static void sendModeCommand(List<RecruitsGroup> groups, LineInfantryMode mode) {
        List<UUID> ids = getActiveGroupIds(groups);
        if (ids.isEmpty()) return;
        String summary = getActiveGroupNames(groups);
        NapoleonicNetwork.CHANNEL.sendToServer(new C2SLineCommandPacket(mode, ids, summary));
    }

    public static List<UUID> getActiveGroupIds(List<RecruitsGroup> groups) {
        List<UUID> ids = new ArrayList<>();
        if (groups != null) {
            for (RecruitsGroup g : groups) {
                if (!g.isDisabled()) {
                    ids.add(g.getUUID());
                }
            }
        }
        return ids;
    }

    public static String getActiveGroupNames(List<RecruitsGroup> groups) {
        if (groups == null || groups.isEmpty()) return "*";
        List<String> names = new ArrayList<>();
        for (RecruitsGroup g : groups) {
            if (!g.isDisabled()) {
                names.add(g.getName());
            }
        }
        if (names.isEmpty()) return "-";
        if (names.size() >= 9) return "*";
        return String.join(", ", names);
    }
}
