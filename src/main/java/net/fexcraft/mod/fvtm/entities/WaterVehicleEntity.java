package net.fexcraft.mod.fvtm.entities;

import net.fexcraft.mod.addons.gep.attributes.EngineAttribute;
import net.fexcraft.mod.addons.gep.attributes.EngineAttribute.EngineAttributeData;
import net.fexcraft.mod.fvtm.api.Part;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleData;
import net.fexcraft.mod.fvtm.util.config.Config;
import net.fexcraft.mod.lib.util.math.Pos;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class WaterVehicleEntity extends UnboundVehicleEntity {

	public WaterVehicleEntity(World world){
		super(world);
	}

	public WaterVehicleEntity(World world, double x, double y, double z, EntityPlayer player, VehicleData data){
		super(world, x, y, z, player, data);
	}

	@Override
	public void onUpdateMovement(){
		Vec3d atmc = new Vec3d(0, 0, 0);
		boolean canThrustCreatively = !Config.VEHICLE_NEEDS_FUEL || (seats != null && seats[0] != null && seats[0].getControllingPassenger() instanceof EntityPlayer && ((EntityPlayer)seats[0].getControllingPassenger()).capabilities.isCreativeMode);
		boolean consumed = false;
		Part.PartData enginepart = vehicledata.getPart("engine");
		if(enginepart != null && enginepart.getAttributeData(EngineAttributeData.class).isOn() && vehicledata.getFuelTankContent() > enginepart.getPart().getAttribute(EngineAttribute.class).getFuelCompsumption() * throttle){
			double d = (vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getFuelCompsumption() * throttle) / 80;//20, set lower to prevent too fast compsumption.
			consumed = vehicledata.consumeFuel(d > 0 ? d : (vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getFuelCompsumption() / 320));
		}
		for(WheelEntity wheel : wheels){
			if(wheel == null){
				continue;
			}
			onGround = false;
			wheel.onGround = false;
			wheel.rotationYaw = axes.getYaw();
			if(!vehicledata.getVehicle().getDriveType().hasTracks() && (wheel.wheelid == 2 || wheel.wheelid == 3)){
				wheel.rotationYaw += wheelsYaw;
			}
			wheel.motionX *= 0.9F;
			wheel.motionY *= 0.9F;
			wheel.motionZ *= 0.9F;
			wheel.motionY -= 0.98F / 20F;//Gravity
			if(enginepart != null){
				if((canThrustCreatively || consumed)){
					double velocityScale;
					if(vehicledata.getVehicle().getDriveType().hasTracks()){
						boolean left = wheel.wheelid == 0 || wheel.wheelid == 3;
						//
						float turningDrag = 0.02F;
						wheel.motionX *= 1F - (Math.abs(wheelsYaw) * turningDrag);
						wheel.motionZ *= 1F - (Math.abs(wheelsYaw) * turningDrag);
						//
						velocityScale = 0.04F * (throttle > 0 ? vehicledata.getVehicle().getFMMaxPositiveThrottle() : vehicledata.getVehicle().getFMMaxNegativeThrottle()) * vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getEngineSpeed();
						float steeringScale = 0.1F * (wheelsYaw > 0 ? vehicledata.getVehicle().getFMTurnLeftModifier() : vehicledata.getVehicle().getFMTurnRightModifier());
						double effectiveWheelSpeed = (throttle + (wheelsYaw * (left ? 1 : -1) * steeringScale)) * velocityScale;
						wheel.motionX += effectiveWheelSpeed * Math.cos(wheel.rotationYaw * 3.14159265F / 180F);
						wheel.motionZ += effectiveWheelSpeed * Math.sin(wheel.rotationYaw * 3.14159265F / 180F);
					}
					else{
						velocityScale = 0.1F * throttle * (throttle > 0 ? vehicledata.getVehicle().getFMMaxPositiveThrottle() : vehicledata.getVehicle().getFMMaxNegativeThrottle()) * vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getEngineSpeed();
						wheel.motionX += Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
						wheel.motionZ += Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
						//
						if(wheel.wheelid == 2 || wheel.wheelid == 3){
							velocityScale = 0.01F * (wheelsYaw > 0 ? vehicledata.getVehicle().getFMTurnLeftModifier() : vehicledata.getVehicle().getFMTurnRightModifier()) * (throttle > 0 ? 1 : -1);
							wheel.motionX -= wheel.getHorizontalSpeed() * Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
							wheel.motionZ += wheel.getHorizontalSpeed() * Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
						}
						else{
							wheel.motionX *= 0.9F;
							wheel.motionZ *= 0.9F;
						}
					}
				}
			}
			if(world.containsAnyLiquid(wheel.getEntityBoundingBox())){//.isAnyLiquid(wheel.getEntityBoundingBox())){
				wheel.motionY += vehicledata.getVehicle().getBuoyancy();
			}
			wheel.move(MoverType.SELF, wheel.motionX, wheel.motionY, wheel.motionZ);
			//pull wheels back to car
			Pos pos = vehicledata.getWheelPos().get(wheel.wheelid);
			Vec3d targetpos = axes.getRelativeVector(new Vec3d(pos.to16FloatX(), pos.to16FloatY(), pos.to16FloatZ()));
			Vec3d current = new Vec3d(wheel.posX - posX, wheel.posY - posY, wheel.posZ - posZ);
			Vec3d despos = new Vec3d(targetpos.x - current.x, targetpos.y - current.y, targetpos.z - current.z).scale(vehicledata.getVehicle().getFMWheelSpringStrength());
			if(despos.lengthSquared() > 0.001F){
				wheel.move(MoverType.SELF, despos.x, despos.y, despos.z);
				despos.scale(0.5F);
				atmc = atmc.subtract(despos);
			}
		}
		move(MoverType.SELF, atmc.x, atmc.y, atmc.z);
	}
	
}