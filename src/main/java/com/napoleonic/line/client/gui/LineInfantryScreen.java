package com.napoleonic.line.client.gui;

import com.napoleonic.line.client.ClientSetup;
import com.napoleonic.line.client.VolleyAimingClientHandler;
import com.napoleonic.line.entity.LineInfantryMode;
import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.gui.group.RecruitsGroupButton;
import com.talhanation.recruits.client.gui.group.RecruitsGroupListScreen;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Line Infantry orders. The panel and both columns are measured from the
 * current GUI size so the labels stay intact at any resolution.
 */
@OnlyIn(Dist.CLIENT)
public class LineInfantryScreen extends Screen {
    private static final int GOLD = 0xFFC6A15A;
    private static final int PANEL = 0xE0101214;
    private static final int PANEL_INNER = 0xC0181C20;

    private final Player player;
    private LineInfantryMode lastOrderedMode = null;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public LineInfantryScreen(Player player) {
        super(Component.translatable("gui.napoleonic_line.title"));
        this.player = player;
    }

    @Override
    protected void init() {
        super.init();
        ClientManager.updateGroups();
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        ClientManager.updateGroups();
    }

    private List<RecruitsGroup> getGroups() {
        return ClientManager.groups != null ? ClientManager.groups : new ArrayList<>();
    }

    private void rebuildButtons() {
        this.clearWidgets();
        List<RecruitsGroup> groups = getGroups();
        boolean hasActiveGroup = groups.stream().anyMatch(g -> !g.isDisabled());

        Component shoulder = Component.translatable("gui.napoleonic_line.shoulder_arms");
        Component present = Component.translatable("gui.napoleonic_line.present_arms");
        Component volley = Component.translatable("gui.napoleonic_line.volley");
        Component fire = Component.translatable("gui.napoleonic_line.fire_at_will");
        Component reload = Component.translatable("gui.napoleonic_line.reload");
        Component cease = Component.translatable("gui.napoleonic_line.cease_fire");

        int textW = this.font.width(shoulder);
        textW = Math.max(textW, this.font.width(present));
        textW = Math.max(textW, this.font.width(volley));
        textW = Math.max(textW, this.font.width(fire));
        textW = Math.max(textW, this.font.width(reload));
        textW = Math.max(textW, this.font.width(cease));

        int margin = 8;
        int availW = Math.max(160, this.width - margin * 2);
        int groupGap = 4;
        int groupW = 36;
        int groupH = 36;
        int shownGroups = Math.min(groups.size(), 9);
        int slots = shownGroups + 1;
        int perRow = Math.max(1, (availW + groupGap) / (groupW + groupGap));
        int groupRows = (slots + perRow - 1) / perRow;
        int groupBlockH = groupRows * groupH + Math.max(0, groupRows - 1) * groupGap;

        int colGap = 8;
        int rowGap = 6;
        int buttonH = 20;
        int padX = 14;
        int header = 26;
        int footer = 18;
        int buttonW = Math.min(180, Math.max(108, textW + 16));
        this.panelW = padX * 2 + buttonW * 2 + colGap;
        if (this.panelW > availW) {
            buttonW = Math.max(72, (availW - padX * 2 - colGap) / 2);
            this.panelW = padX * 2 + buttonW * 2 + colGap;
        }
        this.panelH = header + buttonH * 3 + rowGap * 2 + footer;

        int stackH = groupBlockH + 10 + this.panelH;
        int top = Math.max(margin, (this.height - stackH) / 2);
        if (top + stackH > this.height - margin) {
            top = margin;
        }

        for (int i = 0; i < slots; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int itemsInRow = Math.min(perRow, slots - row * perRow);
            int rowW = itemsInRow * groupW + (itemsInRow - 1) * groupGap;
            int x = (this.width - rowW) / 2 + col * (groupW + groupGap);
            int y = top + row * (groupH + groupGap);
            if (i < shownGroups) {
                RecruitsGroup group = groups.get(i);
                RecruitsGroupButton groupButton = new RecruitsGroupButton(
                        group, x, y, groupW, groupH,
                        Component.literal(group.getName()),
                        button -> {
                            group.setDisabled(!group.isDisabled());
                            this.rebuildButtons();
                        }
                );
                groupButton.active = !group.isDisabled();
                this.addRenderableWidget(groupButton);
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("+/-"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new RecruitsGroupListScreen(this.player));
                    }
                }).bounds(x, y, groupW, groupH).build());
            }
        }

        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = top + groupBlockH + 10;
        int x0 = this.panelX + padX;
        int x1 = x0 + buttonW + colGap;
        int y0 = this.panelY + header;

        addOrder(x0, y0, buttonW, buttonH, shoulder, "gui.napoleonic_line.shoulder_arms.tooltip", hasActiveGroup,
                () -> send(groups, LineInfantryMode.SHOULDER_ARMS));
        addOrder(x1, y0, buttonW, buttonH, present, "gui.napoleonic_line.present_arms.tooltip", hasActiveGroup,
                () -> send(groups, LineInfantryMode.PRESENT_ARMS));
        addOrder(x0, y0 + buttonH + rowGap, buttonW, buttonH, volley, "gui.napoleonic_line.volley.tooltip", hasActiveGroup,
                () -> {
                    this.lastOrderedMode = LineInfantryMode.VOLLEY;
                    List<UUID> activeIds = LineInfantryCategory.getActiveGroupIds(groups);
                    String summary = LineInfantryCategory.getActiveGroupNames(groups);
                    this.onClose();
                    VolleyAimingClientHandler.startAiming(activeIds, summary);
                });
        addOrder(x1, y0 + buttonH + rowGap, buttonW, buttonH, fire, "gui.napoleonic_line.fire_at_will.tooltip", hasActiveGroup,
                () -> send(groups, LineInfantryMode.FIRE_AT_WILL));
        addOrder(x0, y0 + (buttonH + rowGap) * 2, buttonW, buttonH, reload, "gui.napoleonic_line.reload.tooltip", hasActiveGroup,
                () -> send(groups, LineInfantryMode.RELOAD));
        addOrder(x1, y0 + (buttonH + rowGap) * 2, buttonW, buttonH, cease, "gui.napoleonic_line.cease_fire.tooltip", hasActiveGroup,
                () -> send(groups, LineInfantryMode.CEASE_FIRE));
    }

    private void send(List<RecruitsGroup> groups, LineInfantryMode mode) {
        this.lastOrderedMode = mode;
        LineInfantryCategory.sendModeCommand(groups, mode);
    }

    private void addOrder(int x, int y, int w, int h, Component label, String tooltip, boolean active, Runnable action) {
        Button button = Button.builder(label, btn -> action.run())
                .bounds(x, y, w, h)
                .tooltip(Tooltip.create(Component.translatable(tooltip)))
                .build();
        button.active = active;
        this.addRenderableWidget(button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x66000000);
        guiGraphics.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, PANEL);
        guiGraphics.fill(this.panelX + 3, this.panelY + 3, this.panelX + this.panelW - 3, this.panelY + 22, PANEL_INNER);
        drawFrame(guiGraphics, this.panelX, this.panelY, this.panelW, this.panelH);

        guiGraphics.drawCenteredString(this.font, this.title, this.panelX + this.panelW / 2, this.panelY + 8, GOLD);

        if (this.lastOrderedMode != null) {
            String key = "gui.napoleonic_line." + this.lastOrderedMode.getSerializedName();
            guiGraphics.drawCenteredString(
                    this.font,
                    Component.translatable(key),
                    this.panelX + this.panelW / 2,
                    this.panelY + this.panelH - 14,
                    0xFFA8E6A1
            );
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static void drawFrame(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + 2, GOLD);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, GOLD);
        guiGraphics.fill(x, y, x + 2, y + h, GOLD);
        guiGraphics.fill(x + w - 2, y, x + w, y + h, GOLD);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientSetup.OPEN_MENU_KEY != null && ClientSetup.OPEN_MENU_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
