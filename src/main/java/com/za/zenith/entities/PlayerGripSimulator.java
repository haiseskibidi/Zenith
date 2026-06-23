package com.za.zenith.entities;

import org.joml.Vector3f;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.graphics.model.ModelNode;
import com.za.zenith.engine.graphics.model.GripDefinition;

/**
 * Handles physical grip state simulation and bone rotation updates for player viewmodel hands.
 */
public class PlayerGripSimulator {
    private final Vector3f gripThumbR = new Vector3f();
    private final Vector3f gripThumbTipR = new Vector3f();
    private final Vector3f gripIndexR = new Vector3f();
    private final Vector3f gripIndexTipR = new Vector3f();
    private final Vector3f gripFingersR = new Vector3f();
    private final Vector3f gripFingersTipR = new Vector3f();
    
    private final Vector3f gripThumbL = new Vector3f();
    private final Vector3f gripThumbTipL = new Vector3f();
    private final Vector3f gripIndexL = new Vector3f();
    private final Vector3f gripIndexTipL = new Vector3f();
    private final Vector3f gripFingersL = new Vector3f();
    private final Vector3f gripFingersTipL = new Vector3f();

    public PlayerGripSimulator() {}

    public void updateGrips(ItemStack mainHandStack, ItemStack offHandStack, float deltaTime) {
        GripDefinition mainGrip = null;
        GripDefinition offGrip = null;
        
        if (mainHandStack != null && mainHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class) != null) {
            mainGrip = mainHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class).grip();
        }
        if (offHandStack != null && offHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class) != null) {
            offGrip = offHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class).grip();
        }

        GripDefinition activeGripR = mainGrip != null ? mainGrip : 
            (mainHandStack != null ? GripDefinition.createAuto(mainHandStack.getItem()) : GripDefinition.createRelaxed());
            
        GripDefinition activeGripL = offGrip != null ? offGrip : 
            (offHandStack != null ? GripDefinition.createAuto(offHandStack.getItem()) : GripDefinition.createRelaxed());
        
        float gripLerpSpeed = 12.0f * deltaTime;
        gripThumbR.x += (activeGripR.thumb()[0] - gripThumbR.x) * gripLerpSpeed;
        gripThumbR.y += (activeGripR.thumb()[1] - gripThumbR.y) * gripLerpSpeed;
        gripThumbR.z += (activeGripR.thumb()[2] - gripThumbR.z) * gripLerpSpeed;
        gripThumbTipR.x += (activeGripR.thumb_tip()[0] - gripThumbTipR.x) * gripLerpSpeed;
        gripThumbTipR.y += (activeGripR.thumb_tip()[1] - gripThumbTipR.y) * gripLerpSpeed;
        gripThumbTipR.z += (activeGripR.thumb_tip()[2] - gripThumbTipR.z) * gripLerpSpeed;

        gripIndexR.x += (activeGripR.index()[0] - gripIndexR.x) * gripLerpSpeed;
        gripIndexR.y += (activeGripR.index()[1] - gripIndexR.y) * gripLerpSpeed;
        gripIndexR.z += (activeGripR.index()[2] - gripIndexR.z) * gripLerpSpeed;
        gripIndexTipR.x += (activeGripR.index_tip()[0] - gripIndexTipR.x) * gripLerpSpeed;
        gripIndexTipR.y += (activeGripR.index_tip()[1] - gripIndexTipR.y) * gripLerpSpeed;
        gripIndexTipR.z += (activeGripR.index_tip()[2] - gripIndexTipR.z) * gripLerpSpeed;

        gripFingersR.x += (activeGripR.fingers()[0] - gripFingersR.x) * gripLerpSpeed;
        gripFingersR.y += (activeGripR.fingers()[1] - gripFingersR.y) * gripLerpSpeed;
        gripFingersR.z += (activeGripR.fingers()[2] - gripFingersR.z) * gripLerpSpeed;
        gripFingersTipR.x += (activeGripR.fingers_tip()[0] - gripFingersTipR.x) * gripLerpSpeed;
        gripFingersTipR.y += (activeGripR.fingers_tip()[1] - gripFingersTipR.y) * gripLerpSpeed;
        gripFingersTipR.z += (activeGripR.fingers_tip()[2] - gripFingersTipR.z) * gripLerpSpeed;
        
        gripThumbL.x += (activeGripL.thumb()[0] - gripThumbL.x) * gripLerpSpeed;
        gripThumbL.y += (activeGripL.thumb()[1] - gripThumbL.y) * gripLerpSpeed;
        gripThumbL.z += (activeGripL.thumb()[2] - gripThumbL.z) * gripLerpSpeed;
        gripThumbTipL.x += (activeGripL.thumb_tip()[0] - gripThumbTipL.x) * gripLerpSpeed;
        gripThumbTipL.y += (activeGripL.thumb_tip()[1] - gripThumbTipL.y) * gripLerpSpeed;
        gripThumbTipL.z += (activeGripL.thumb_tip()[2] - gripThumbTipL.z) * gripLerpSpeed;

        gripIndexL.x += (activeGripL.index()[0] - gripIndexL.x) * gripLerpSpeed;
        gripIndexL.y += (activeGripL.index()[1] - gripIndexL.y) * gripLerpSpeed;
        gripIndexL.z += (activeGripL.index()[2] - gripIndexL.z) * gripLerpSpeed;
        gripIndexTipL.x += (activeGripL.index_tip()[0] - gripIndexTipL.x) * gripLerpSpeed;
        gripIndexTipL.y += (activeGripL.index_tip()[1] - gripIndexTipL.y) * gripLerpSpeed;
        gripIndexTipL.z += (activeGripL.index_tip()[2] - gripIndexTipL.z) * gripLerpSpeed;

        gripFingersL.x += (activeGripL.fingers()[0] - gripFingersL.x) * gripLerpSpeed;
        gripFingersL.y += (activeGripL.fingers()[1] - gripFingersL.y) * gripLerpSpeed;
        gripFingersL.z += (activeGripL.fingers()[2] - gripFingersL.z) * gripLerpSpeed;
        gripFingersTipL.x += (activeGripL.fingers_tip()[0] - gripFingersTipL.x) * gripLerpSpeed;
        gripFingersTipL.y += (activeGripL.fingers_tip()[1] - gripFingersTipL.y) * gripLerpSpeed;
        gripFingersTipL.z += (activeGripL.fingers_tip()[2] - gripFingersTipL.z) * gripLerpSpeed;
    }

    public void applyGripToBones(ModelNode node, boolean isLeft) {
        boolean isThumb = node.name.startsWith("thumb");
        boolean isIndex = node.name.startsWith("index");
        boolean isFingers = node.name.startsWith("fingers");
        
        if (isThumb || isIndex || isFingers) {
            boolean isTip = node.name.contains("tip");
            Vector3f state = isLeft ? 
                (isThumb ? (isTip ? gripThumbTipL : gripThumbL) : (isIndex ? (isTip ? gripIndexTipL : gripIndexL) : (isTip ? gripFingersTipL : gripFingersL))) :
                (isThumb ? (isTip ? gripThumbTipR : gripThumbR) : (isIndex ? (isTip ? gripIndexTipR : gripIndexR) : (isTip ? gripFingersTipR : gripFingersR)));
                
            float signX = 1.0f;
            float signY = isLeft ? -1.0f : 1.0f;
            float signZ = isLeft ? -1.0f : 1.0f;
            
            node.animRotation.x += (float)Math.toRadians(state.x * signX);
            node.animRotation.y += (float)Math.toRadians(state.y * signY);
            node.animRotation.z += (float)Math.toRadians(state.z * signZ);
        }
    }
}
