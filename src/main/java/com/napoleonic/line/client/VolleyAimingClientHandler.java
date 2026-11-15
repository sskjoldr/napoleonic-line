package com.napoleonic.line.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.napoleonic.line.ai.LineInfantryHelper;
import com.napoleonic.line.config.NapoleonicServerConfig;
import com.napoleonic.line.network.C2SVolleyFirePacket;
import com.napoleonic.line.network.C2SVolleyGlancePacket;
import com.napoleonic.line.network.NapoleonicNetwork;
import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public final class VolleyAimingClientHandler {
    private static boolean aimingActive = false;
    private static int glanceCooldown = 0;
    private static List<UUID> pendingGroupUUIDs = List.of();
    private static String pendingGroupSummary = "";
    private static Vec3 currentTargetPoint = null;

    public static void startAiming(List<UUID> groupUUIDs, String groupSummary) {
        if (groupUUIDs == null || groupUUIDs.isEmpty()) {
            return;
        }
        pendingGroupUUIDs = new ArrayList<>(groupUUIDs);
        pendingGroupSummary = groupSummary != null ? groupSummary : "";
        aimingActive = true;
        currentTargetPoint = null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("msg.napoleonic_line.volley_aiming"), true);
        }
    }

    public static void cancelAiming(boolean notify) {
        if (!aimingActive) return;
        aimingActive = false;
        pendingGroupUUIDs = List.of();
        currentTargetPoint = null;
        if (notify && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("msg.napoleonic_line.volley_cancelled"), true);
        }
    }

    public static boolean isAimingActive() {
        return aimingActive;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !aimingActive) {
            return;
        }
        if (glanceCooldown > 0) {
            glanceCooldown--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || pendingGroupUUIDs.isEmpty()) {
            return;
        }
        Vec3 hit = raycastTargetPoint(mc.player, 1.0F);
        if (hit == null) {
            return;
        }
        glanceCooldown = 4;
        NapoleonicNetwork.CHANNEL.sendToServer(new C2SVolleyGlancePacket(pendingGroupUUIDs, hit));
    }

    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!aimingActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) {
            cancelAiming(false);
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Vec3 hit = raycastTargetPoint(mc.player, 1.0F);
            if (hit != null && !pendingGroupUUIDs.isEmpty()) {
                NapoleonicNetwork.CHANNEL.sendToServer(
                        new C2SVolleyFirePacket(pendingGroupUUIDs, hit, pendingGroupSummary)
                );
            }
            cancelAiming(false);
            event.setCanceled(true);
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cancelAiming(true);
            event.setCanceled(true);
        }
    }

    public static void onKeyInput(InputEvent.Key event) {
        if (!aimingActive) return;
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            cancelAiming(true);
        }
    }

    public static Vec3 raycastTargetPoint(LocalPlayer player, float partialTick) {
        double maxDist = 128.0D;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                maxDist = NapoleonicServerConfig.VOLLEY_MAX_RAYCAST_DISTANCE.get();
            }
        } catch (Exception ignored) {
        }

        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 lookVec = player.getViewVector(partialTick);
        Vec3 endPos = eyePos.add(lookVec.scale(maxDist));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));
        double closestDistSqr = blockHit.getType() != HitResult.Type.MISS
                ? eyePos.distanceToSqr(blockHit.getLocation())
                : maxDist * maxDist;
        Vec3 bestPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : null;

        AABB rayBox = player.getBoundingBox().expandTowards(lookVec.scale(maxDist)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyePos, endPos, rayBox,
                e -> !e.isSpectator() && e.isPickable() && e != player,
                closestDistSqr
        );
        if (entityHit != null) {
            bestPos = entityHit.getEntity().position();
        }
        return bestPos;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!aimingActive || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancelAiming(false);
            return;
        }

        Vec3 hit = raycastTargetPoint(mc.player, event.getPartialTick());
        currentTargetPoint = hit;
        if (hit == null) {
            return;
        }

        // Compute group centroid and frontWidth on client for accurate ellipse preview
        Set<UUID> groupSet = new HashSet<>(pendingGroupUUIDs);
        UUID ownerUUID = mc.player.getUUID();
        List<AbstractRecruitEntity> groupRecruits = mc.level.getEntitiesOfClass(
                AbstractRecruitEntity.class,
                mc.player.getBoundingBox().inflate(128.0D),
                r -> r.isAlive()
                        && ownerUUID.equals(r.getOwnerUUID())
                        && r.getGroup() != null
                        && groupSet.contains(r.getGroup())
                        && LineInfantryHelper.hasMusketAnywhere(r)
        );

        Vec3 centroid;
        if (!groupRecruits.isEmpty()) {
            double sx = 0.0D, sy = 0.0D, sz = 0.0D;
            for (AbstractRecruitEntity r : groupRecruits) {
                sx += r.getX();
                sy += r.getY();
                sz += r.getZ();
            }
            double n = groupRecruits.size();
            centroid = new Vec3(sx / n, sy / n, sz / n);
        } else {
            centroid = mc.player.position();
        }

        Vec3 horizToTarget = new Vec3(hit.x - centroid.x, 0.0D, hit.z - centroid.z);
        Vec3 forwardAxis = horizToTarget.lengthSqr() > 1.0E-4D
                ? horizToTarget.normalize()
                : new Vec3(mc.player.getLookAngle().x, 0.0D, mc.player.getLookAngle().z).normalize();
        Vec3 rightAxis = new Vec3(-forwardAxis.z, 0.0D, forwardAxis.x);

        double frontWidth = 0.0D;
        if (groupRecruits.size() > 1) {
            double minP = Double.POSITIVE_INFINITY;
            double maxP = Double.NEGATIVE_INFINITY;
            for (AbstractRecruitEntity r : groupRecruits) {
                double p = (r.getX() - centroid.x) * rightAxis.x + (r.getZ() - centroid.z) * rightAxis.z;
                if (p < minP) minP = p;
                if (p > maxP) maxP = p;
            }
            if (maxP > minP) {
                frontWidth = Mth.clamp(maxP - minP, 0.0D, 64.0D);
            }
        }

        double extraW = 4.0D;
        double depth = 3.0D;
        try {
            if (NapoleonicServerConfig.SPEC.isLoaded()) {
                extraW = NapoleonicServerConfig.VOLLEY_EXTRA_WIDTH.get();
                depth = NapoleonicServerConfig.VOLLEY_DEPTH.get();
            }
        } catch (Exception ignored) {
        }

        double semiMajorA = Math.max(2.0D, (frontWidth + extraW) * 0.5D);
        double semiMinorB = Math.max(0.5D, depth * 0.5D);

        renderEllipseZone(event.getPoseStack(), mc.gameRenderer.getMainCamera(), hit, forwardAxis, rightAxis, semiMajorA, semiMinorB);
    }

    private static void renderEllipseZone(PoseStack poseStack, Camera camera, Vec3 center,
                                          Vec3 forwardAxis, Vec3 rightAxis, double a, double b) {
        Vec3 camPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(center.x - camPos.x, center.y - camPos.y + 0.08D, center.z - camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        int segments = 48;

        // 1. Translucent gold-amber fill
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).color(255, 195, 65, 70).endVertex();
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0D * Math.PI / segments);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float px = (float) (rightAxis.x * (cos * a) + forwardAxis.x * (sin * b));
            float pz = (float) (rightAxis.z * (cos * a) + forwardAxis.z * (sin * b));
            buffer.vertex(matrix, px, 0.0F, pz).color(235, 90, 40, 38).endVertex();
        }
        tesselator.end();

        // 2. Bright perimeter ribbon (thick ring between 0.94..1.00 of radius)
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0D * Math.PI / segments);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float ox = (float) (rightAxis.x * (cos * a) + forwardAxis.x * (sin * b));
            float oz = (float) (rightAxis.z * (cos * a) + forwardAxis.z * (sin * b));
            float ix = ox * 0.93F;
            float iz = oz * 0.93F;
            buffer.vertex(matrix, ox, 0.02F, oz).color(255, 215, 85, 220).endVertex();
            buffer.vertex(matrix, ix, 0.02F, iz).color(255, 120, 45, 180).endVertex();
        }
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    public static List<UUID> collectActiveGroupUUIDs() {
        List<UUID> result = new ArrayList<>();
        if (ClientManager.groups != null) {
            for (RecruitsGroup group : ClientManager.groups) {
                if (!group.isDisabled()) {
                    result.add(group.getUUID());
                }
            }
        }
        return result;
    }

    public static String buildActiveGroupSummary() {
        if (ClientManager.groups == null || ClientManager.groups.isEmpty()) {
            return "*";
        }
        List<String> names = new ArrayList<>();
        for (RecruitsGroup group : ClientManager.groups) {
            if (!group.isDisabled()) {
                names.add(group.getName());
            }
        }
        if (names.isEmpty()) {
            return "-";
        }
        if (names.size() >= 9) {
            return "*";
        }
        return String.join(", ", names);
    }

    private VolleyAimingClientHandler() {
    }
}
