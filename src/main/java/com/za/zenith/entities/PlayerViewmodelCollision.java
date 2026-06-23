package com.za.zenith.entities;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.physics.Raycast;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/**
 * Handles Tarkov-style physical collisons between the player viewmodel item/hand and the physical world.
 */
public class PlayerViewmodelCollision {

    public static void applyCollisions(World world, Vector3f position, float eyeHeight, Vector3f camForward, Item heldItem, Vector3f tPos, Quaternionf tRot, Vector3f extF) {
        float reach = 0.6f; 
        if (heldItem != null) reach += heldItem.getViewmodelScale() * 0.4f;
        
        Vector3f eyePos = new Vector3f(position).add(0, eyeHeight, 0);
        RaycastResult hit = Raycast.raycast(world, eyePos, camForward);
        Vector3f probePoint = new Vector3f(eyePos).fma(reach * 0.8f, camForward);
        Block probedBlock = world.getBlock((int)Math.floor(probePoint.x), (int)Math.floor(probePoint.y), (int)Math.floor(probePoint.z));
        boolean colliding = (hit != null && hit.isHit() && hit.getDistance() < reach) || !probedBlock.isAir();
        
        if (colliding) {
            float dist = (hit != null && hit.isHit()) ? hit.getDistance() : reach * 0.5f;
            float factor = (reach - dist) / reach;
            float pf = Math.clamp(factor, 0.0f, 1.0f);
            tPos.z += 0.8f * pf; 
            tPos.x += 0.15f * pf;
            tRot.rotateX((float)Math.toRadians(-75.0f * pf));
            tRot.rotateZ((float)Math.toRadians(15.0f * pf));
            extF.z += 60.0f * pf;
        }
    }
}
