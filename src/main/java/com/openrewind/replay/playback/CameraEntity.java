package com.openrewind.replay.playback;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * A lightweight camera entity used as Minecraft's render-view entity during
 * replay playback / export. Setting {@code Minecraft.setRenderViewEntity(camera)}
 * makes the world render from this entity's position + rotation, giving us a
 * free camera decoupled from the recorded player.
 *
 * <p>In <b>POV mode</b> the exporter copies the recorded player's position and
 * look each frame into this entity, reproducing the first-person view (so the
 * HUD / hotbar / held item line up). In <b>free mode</b> the exporter drives it
 * along a user camera path.</p>
 */
public class CameraEntity extends Entity {

    public CameraEntity(World world) {
        super(world);
        this.noClip = true;
        this.width = 0.1f;
        this.height = 0.1f;
    }

    /** Place the camera and keep prev/last-tick fields in sync (no interpolation jumps). */
    public void setCamera(double x, double y, double z, float yaw, float pitch) {
        this.prevPosX = this.lastTickPosX = this.posX = x;
        this.prevPosY = this.lastTickPosY = this.posY = y;
        this.prevPosZ = this.lastTickPosZ = this.posZ = z;
        this.prevRotationYaw = this.rotationYaw = yaw;
        this.prevRotationPitch = this.rotationPitch = pitch;
        this.setPosition(x, y, z);
    }

    /** Smoothly move toward a target so sub-tick interpolation stays continuous. */
    public void moveCamera(double x, double y, double z, float yaw, float pitch) {
        this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
        this.prevRotationYaw = this.rotationYaw; this.prevRotationPitch = this.rotationPitch;
        this.posX = x; this.posY = y; this.posZ = z;
        this.rotationYaw = yaw; this.rotationPitch = pitch;
        this.setPosition(x, y, z);
    }

    @Override public float getEyeHeight() { return 0f; }
    @Override protected void entityInit() { }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) { }

    /** The camera is invisible and must never be culled / ticked as a real entity. */
    @Override public boolean isInvisible() { return true; }
}
