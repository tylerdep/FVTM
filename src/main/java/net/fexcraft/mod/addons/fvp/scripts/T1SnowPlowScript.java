package net.fexcraft.mod.addons.fvp.scripts;

import java.util.TreeMap;

import net.fexcraft.mod.fvtm.api.Vehicle.VehicleData;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleEntity;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleScript;
import net.fexcraft.mod.fvtm.entities.LandVehicleEntity;
import net.fexcraft.mod.lib.util.math.Pos;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;

public class T1SnowPlowScript implements VehicleScript {
	
	private static final TreeMap<String, String> SETTINGS = new TreeMap<String, String>();
	static { SETTINGS.put("T1 Snow Plow", "boolean"); }
	public boolean on = false;
	
	public T1SnowPlowScript(){}

	@Override
	public ResourceLocation getId(){
		return new ResourceLocation("fvp:t1snowplowscript");
	}

	@Override
	public boolean isOn(Side side){
		return true;//is on both sides active
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound){
		compound.setBoolean(this.getId().toString() + "_On", on);
		return compound;
	}

	@Override
	public VehicleScript readFromNBT(NBTTagCompound compound, boolean isRemote) {
		if(compound.hasKey(this.getId().toString() + "_On")){
			on = compound.getBoolean(this.getId().toString() + "_On");
		}
		return this;
	}

	@Override
	public void onDataPacket(Entity entity, VehicleData data, NBTTagCompound compound, Side side) {
		if(side.isServer()){
			this.sendPacketToAllAround(entity, compound);
		}
		if(compound.hasKey("On")){
			on = compound.getBoolean("On");
		}
	}

	@Override
	public void onCreated(Entity entity, VehicleData data){
		return;
	}

	@Override
	public boolean onInteract(Entity entity, VehicleData data, EntityPlayer player){
		return false;
	}

	@Override
	public void onUpdate(Entity entity, VehicleData data){
		LandVehicleEntity vehicle = (LandVehicleEntity) entity;
		if(!vehicle.world.isRemote){
			if(on){
				Vec3d[] pos = new Vec3d[6];
				pos[0] = calculate(vehicle,  2);
				pos[1] = calculate(vehicle,  1);
				pos[2] = calculate(vehicle,  0);
				pos[3] = calculate(vehicle, -1);
				pos[4] = calculate(vehicle, -2);
				pos[5] = calculate(vehicle,  3);
				IBlockState[] states = new IBlockState[6];
				int j = 0;
				for(int i = 0; i < 6; i++){
					BlockPos poss = new BlockPos(new Vec3d(pos[i].x, pos[i].y, pos[i].z));
					states[i] = vehicle.world.getBlockState(poss);
					if(i < 5){
						if(states[i].getMaterial() == Material.SNOW){
							vehicle.world.setBlockState(new BlockPos(poss), Blocks.AIR.getDefaultState(), 2);
							j++;
						}
						else if(states[i].getMaterial().isReplaceable() || states[i].getMaterial() == Material.CACTUS || states[i].getMaterial() == Material.CIRCUITS){
							vehicle.world.setBlockState(new BlockPos(poss), Blocks.AIR.getDefaultState(), 2);
						}
					}
					if(i == 5 && j > 0 && states[i].getMaterial().isReplaceable()){
						vehicle.world.setBlockState(poss, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, j), 2);
					}
				}
			}
			else{
				//
			}
		}
	}
	
	private static final Vec3d calculate(LandVehicleEntity vehicle, int i){
		Pos pos = new Pos(70, 4, i * 16);
		Vec3d rel = vehicle.getAxes().getRelativeVector(pos.to16Double());
		return new Vec3d(vehicle.posX + rel.x, vehicle.posY + rel.y, vehicle.posZ + rel.z);
	}

	@Override
	public void onRemove(Entity entity, VehicleData data){
		//
	}
	
	@Override
	public void onKeyInput(int key, int seat, VehicleEntity ent){
		//
	}

	@Override
	public TreeMap<String, String> getSettingKeys(int seat){
		return seat == 0 ? SETTINGS : new TreeMap<String, String>();
	}

	@Override
	public void onSettingsUpdate(VehicleEntity ent, int seat, String setting, Object value){
		if(setting.equals(SETTINGS.keySet().toArray()[0]) && seat == 0){
			on = value == null ? !on : (boolean)value;
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setBoolean("On", on);
			this.sendPacketToServer(ent.getEntity(), nbt);
		}
	}

	@Override
	public Object getSettingValue(int seat, String setting){
		if(setting.equals(SETTINGS.keySet().toArray()[0])){
			return on;
		}
		return null;
	}
	
}