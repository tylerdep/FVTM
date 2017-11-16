package net.fexcraft.mod.fvtm.entities;

import io.netty.buffer.ByteBuf;
import net.fexcraft.mod.fvtm.FVTM;
import net.fexcraft.mod.fvtm.api.Fuel.FuelItem;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleData;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleEntity;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleItem;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleScript;
import net.fexcraft.mod.fvtm.api.Vehicle.VehicleType;
import net.fexcraft.mod.fvtm.gui.GuiHandler;
import net.fexcraft.mod.fvtm.util.FvtmPermissions;
import net.fexcraft.mod.fvtm.util.Resources;
import net.fexcraft.mod.fvtm.util.VehicleAxes;
import net.fexcraft.mod.fvtm.util.packets.PacketVehicleControl;
import net.fexcraft.mod.fvtm.util.packets.PacketVehicleKeyPress;
import net.fexcraft.mod.lib.api.common.LockableObject;
import net.fexcraft.mod.lib.api.item.KeyItem;
import net.fexcraft.mod.lib.api.network.IPacketReceiver;
import net.fexcraft.mod.lib.network.PacketHandler;
import net.fexcraft.mod.lib.network.packet.PacketEntityUpdate;
import net.fexcraft.mod.lib.perms.PermManager;
import net.fexcraft.mod.lib.perms.player.PlayerPerms;
import net.fexcraft.mod.lib.util.common.Print;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class LandVehicleTrailer extends Entity implements VehicleEntity, IEntityAdditionalSpawnData, LockableObject, IPacketReceiver<PacketEntityUpdate> {
	
	public boolean sync = true;
	public int serverPositionTransitionTicker, parentid;
	public double serverPosX, serverPosY, serverPosZ;
	public double serverYaw, serverPitch, serverRoll;
	public VehicleData vehicledata;
	public double throttle;
	public SeatEntity[] seats;
	public WheelEntity[] wheels;
	public float prevRotationRoll;
	public Vec3d angularVelocity = new Vec3d(0F, 0F, 0F);
	public VehicleAxes prevAxes, axes;
	private float yOffset;
	//
	public VehicleEntity parent;
	
	public LandVehicleTrailer(World world){
		super(world);
		axes = new VehicleAxes();
		prevAxes = new VehicleAxes();
		preventEntitySpawning = true;
		setSize(1F, 1F);
		yOffset = 6F / 16F;
		ignoreFrustumCheck = true;
		if(world.isRemote){
			setRenderDistanceWeight(200D);
		}
		//
		stepHeight = 1.0F;
	}
	
	private LandVehicleTrailer(World world, VehicleData data){
		this(world);
		vehicledata = data;
	}
	
	//From Item;
	public LandVehicleTrailer(World world, VehicleData data, VehicleEntity parent){
		this(world, data);
		stepHeight = 1.0F;
		Vec3d vec = parent.getAxes().getRelativeVector(parent.getVehicleData().getRearConnector().to16Double());
		setPosition(parent.getEntity().posX + vec.x, parent.getEntity().posY + vec.y, parent.getEntity().posZ + vec.z);
		//TODO rotateYaw(placer.rotationYaw + 90F);
		this.parentid = parent.getEntity().getEntityId();
		this.axes = parent.getAxes();
		initType(data, false);
		Print.debug("SPAWNING TRAILER");
	}

	@Override
	public void writeSpawnData(ByteBuf buffer){
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("ParentId", this.parentid);
		ByteBufUtils.writeTag(buffer, axes.write(this, vehicledata.writeToNBT(nbt)));
	}

	@Override
	public void readSpawnData(ByteBuf buffer){
		try{
			NBTTagCompound compound = ByteBufUtils.readTag(buffer);
			vehicledata = Resources.getVehicleData(compound, world.isRemote);
			axes = VehicleAxes.read(this, compound);
			prevRotationYaw = axes.getYaw();
			prevRotationPitch = axes.getPitch();
			prevRotationRoll = axes.getRoll();
			this.parentid = compound.getInteger("ParentId");
			initType(vehicledata, true);
		}
		catch(Exception e){
			e.printStackTrace();
			Print.debug("Failed to receive additional spawn data for this trailer!");
		}
		
	}
	
	protected void initType(VehicleData type, boolean remote){
		seats = new SeatEntity[type.getSeats().size()];
		wheels = new WheelEntity[type.getWheelPos().size()];
		if(!remote){
			for(int i = 0; i < type.getSeats().size(); i++){
				world.spawnEntity(seats[i] = new SeatEntity(world, this, i));
			}
			for(int i = 0; i < type.getWheelPos().size(); i++){
				world.spawnEntity(wheels[i] = new WheelEntity(world, this, i));
			}
		}
		stepHeight = type.getVehicle().getFMWheelStepHeight();
		yOffset = 10F / 16F;//TODO check dis
		vehicledata.getScripts().forEach((script) -> script.onCreated(this, vehicledata));
	}

	@Override
	protected void entityInit(){
		//
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound tag){
		tag = vehicledata.writeToNBT(tag);
		axes.write(this, tag);
		tag.setInteger("ParentId", parentid);
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound tag){
		if(vehicledata == null){
			vehicledata = Resources.getVehicleData(tag, world.isRemote);
		}
		else{
			vehicledata.readFromNBT(tag, world.isRemote);
		}
		prevRotationYaw = tag.getFloat("RotationYaw");
		prevRotationPitch = tag.getFloat("RotationPitch");
		prevRotationRoll = tag.getFloat("RotationRoll");
		axes = VehicleAxes.read(this, tag);
		parentid = tag.getInteger("ParentId");
		initType(vehicledata, false);
	}
	
	public boolean isSeatFree(int seat){
		return vehicledata.getSeats().size() >= seat && seats[seat].getControllingPassenger() == null;
	}
	
	@Override
	protected boolean canTriggerWalking(){
		return false;
	}
	
	@Override
	public AxisAlignedBB getCollisionBox(Entity entity){
		return entity.getEntityBoundingBox();
	}

	@Override
    public boolean canBePushed(){
        return false;
    }

	@Override
	public double getMountedYOffset(){
		return 0D;
	}
	
	@Override
    public double getYOffset(){
    	return yOffset;
    }
	
	@Override
	public void setDead(){
		super.setDead();
		for(SeatEntity seat : seats){
			if(seat != null){
				seat.setDead();
			}
		}
		for(WheelEntity wheel : wheels){
			if(wheel != null){
				wheel.setDead();
			}
		}
		vehicledata.getScripts().forEach((script) -> script.onRemove(this, vehicledata));
	}
	
	@Override
	public void onCollideWithPlayer(EntityPlayer par1EntityPlayer){
		//
	}

	@Override
	public boolean canBeCollidedWith(){
		return !isDead;
	}
	
	@Override
	public void applyEntityCollision(Entity entity){
		if(!isPartOfThis(entity)){
			super.applyEntityCollision(entity);
		}
	}
	
	@Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport){
		if(ticksExisted > 1){
			return;
		}
		if(!this.parent.getEntity().getPassengers().isEmpty() && this.parent.getEntity().getControllingPassenger() instanceof EntityPlayer){
			//
		}
		else{				
			if(sync){
				serverPositionTransitionTicker = posRotationIncrements + 5;
			}
			else{
				double var10 = x - posX; double var12 = y - posY; double var14 = z - posZ;
				double var16 = var10 * var10 + var12 * var12 + var14 * var14;
				if (var16 <= 1.0D){
					return;
				}
				serverPositionTransitionTicker = 3;
			}
			serverPosX = x;
			serverPosY = y;
			serverPosZ = z;
			serverYaw = yaw;
			serverPitch = pitch;
		}
	}
	
	public void setPositionRotationAndMotion(double x, double y, double z, float yaw, float pitch, float roll, double motX, double motY, double motZ, double avelx, double avely, double avelz, double throttle2, float steeringYaw){
		if(world.isRemote){
			serverPosX = x;
			serverPosY = y;
			serverPosZ = z;
			serverYaw = yaw;
			serverPitch = pitch;
			serverRoll = roll;
			serverPositionTransitionTicker = 5;
		}
		else{
			setPosition(x, y, z);
			prevRotationYaw = yaw;
			prevRotationPitch = pitch;
			prevRotationRoll = roll;
			setRotation(yaw, pitch, roll);
		}
		motionX = motX;
		motionY = motY;
		motionZ = motZ;
		angularVelocity = new Vec3d(avelx, avely, avelz);
		throttle = throttle2;
		//wheelsYaw = steeringYaw;
	}
	

	@Override
	public void setVelocity(double d, double d1, double d2){
		motionX = d;
		motionY = d1;
		motionZ = d2;
	}
	
	@Override
	public boolean processInitialInteract(EntityPlayer player, EnumHand hand){
		if(isDead || world.isRemote){
			return false;
		}
		ItemStack stack = player.getHeldItem(hand);
		if(!stack.isEmpty() && stack.getItem() instanceof KeyItem){
			if(this.isLocked()){
				this.unlock(world, player, stack, (KeyItem)stack.getItem());
			}
			else{
				this.lock(world, player, stack, (KeyItem)stack.getItem());
			}
			return true;
		}
		if(vehicledata.isLocked()){
			Print.chat(player, "Trailer is locked.");
			return true;
		}
		if(!stack.isEmpty()){
			if(stack.getItem() instanceof FuelItem){
				player.openGui(FVTM.getInstance(), GuiHandler.VEHICLE_INVENTORY, world, 1, 0, 0);//Inventory.
				return true;
			}
			if(stack.getItem() instanceof VehicleItem){
				if(this.vehicledata.getRearConnector() != null && this.getEntityAtRear() == null && ((VehicleItem)stack.getItem()).getVehicle(stack).getVehicle().isTrailerOrWagon()){
					/*Print.chat(player, "Connecting...");
					LandVehicleTrailer trailer = new LandVehicleTrailer(world, ((VehicleItem)stack.getItem()).getVehicle(stack), this);
					world.spawnEntity(trailer);
					stack.shrink(1);*/
					//TODO
					return true;
				}
			}
		}
		//TODO Item interaction
		for(int i = 0; i <= vehicledata.getSeats().size(); i++){
			if(seats[i] != null && seats[i].processInitialInteract(player, hand)){
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean onKeyPress(int key, int seat, EntityPlayer player){
		//Print.debug("T: " + key + " " + seat + " " + player.getName() + " [" + Time.getDate() + "];");
		try{
			if(world.isRemote && key >= 6){
				PacketHandler.getInstance().sendToServer(new PacketVehicleKeyPress(key));
				return true;
			}
			switch(key){
				case 6:{//Exit
					player.dismountRidingEntity();
			  		return true;
				}
				case 7:{//Inventory
					if(!world.isRemote){
						player.openGui(FVTM.getInstance(), GuiHandler.VEHICLE_INVENTORY, world, 0, 0, 0);
						//open inventory
					}
					return true;
				}
			}
			return false;
		}
		catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
		
	@Override
    public void onUpdate(){
        super.onUpdate();
        if(parent == null){
        	try{
            	parent = (VehicleEntity)world.getEntityByID(parentid);
        		((LandVehicleEntity)parent).trailer = this;
        		//
        		this.posX = parent.getEntity().posX;
        		this.posY = parent.getEntity().posY;
        		this.posZ = parent.getEntity().posZ;
        		Print.debug("Found vehicle. ");
        	}
        	catch(Exception e){
        		e.printStackTrace();
        	}
        }
        if(parent == null){
        	Print.debug("Vehicle which this trailer was connected to not found.");
    		vehicledata.getScripts().forEach((script) -> script.onRemove(this, vehicledata));
			ItemStack stack = vehicledata.getVehicle().getItemStack(vehicledata);
			entityDropItem(stack, 0.5F);
	 		setDead();
        	return;
        }
        //
        if(!world.isRemote){
        	for(int i = 0; i < vehicledata.getSeats().size(); i++){
        		if(seats[i] == null || !seats[i].addedToChunk){
    				world.spawnEntity(seats[i] = new SeatEntity(world, this, i));
        		}
        	}
        	for(int i = 0; i < vehicledata.getWheelPos().size(); i++){
        		if(wheels[i] == null || !wheels[i].addedToChunk){
    				world.spawnEntity(wheels[i] = new WheelEntity(world, this, i));
        		}
        	}
        }
        //
		prevRotationYaw = axes.getYaw();
		prevRotationPitch = axes.getPitch();
		prevRotationRoll = axes.getRoll();		
		prevAxes = axes.clone();
		this.ticksExisted++;
		//
		boolean drivenByPlayer = world.isRemote && parent.getSeats()[0] != null && parent.getSeats()[0].getControllingPassenger() instanceof EntityPlayer;
		//
		//Vec3d atmc = new Vec3d(0, 0, 0);
		for(WheelEntity wheel : wheels){
			if(wheel != null && world != null){
				wheel.prevPosX = wheel.posX;
				wheel.prevPosY = wheel.posY;
				wheel.prevPosZ = wheel.prevPosZ;
			}
		}
		//
		if(wheels.length == 2 && world.isRemote && ticksExisted % 100 == 0){
			if(!(wheels[0] == null || wheels[1] == null)){
				//DEfault Trailer
				Vec3d axle = new Vec3d((wheels[0].posX + wheels[1].posX) / 2F, (wheels[0].posY + wheels[1].posY) / 2F, (wheels[0].posZ + wheels[1].posZ) / 2F);
				Vec3d conn = parent.getVehicleData().getRearConnector().to16Double();
				axes.rotYaw((float)(Math.atan2(axle.x - conn.x, axle.z - conn.z)));
				//axes.setAngles((Math.atan2(conn.x - axle.x, conn.z - axle.z) * 180 / Math.PI) + 180, 0, 0);
				//
				Vec3d vec = parent.getAxes().getRelativeVector(conn);
				//this.move(MoverType.SELF, parent.getEntity().posX + vec.x, parent.getEntity().posY + vec.y, parent.getEntity().posZ + vec.z);
				prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
				posX = parent.getEntity().posX + vec.x;
				posY = parent.getEntity().posY + vec.y;
				posZ = parent.getEntity().posZ + vec.z;
				//
				Vec3d w0 = this.axes.getRelativeVector(this.vehicledata.getWheelPos().get(0).to16Double());
				wheels[0].move(MoverType.SELF, posX + w0.x, posY + w0.y, posZ + w0.z);
				Vec3d w1 = this.axes.getRelativeVector(this.vehicledata.getWheelPos().get(1).to16Double());
				wheels[1].move(MoverType.SELF, posX + w1.x, posY + w1.y, posZ + w1.z);
				//
				Print.debug(vec, this.getPositionVector().toString());
				//PacketHandler.getInstance().sendToServer(new PacketVehicleControl(this));
			}
			else{
				Print.debug("Wheels are null!");
			}
		}
		else if(wheels.length == 4){
			//4 Wheeled Trailer
			//TODO
		}
		/*for(WheelEntity wheel : wheels){
			if(wheel == null){
				continue;
			}
			onGround = true;
			wheel.onGround = true;
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
					else if(vehicledata.getVehicle().getDriveType().isFWD() || vehicledata.getVehicle().getDriveType().is4WD()){
						if(wheel.wheelid == 2 || wheel.wheelid == 3){
							velocityScale = 0.1F * throttle * (throttle > 0 ? vehicledata.getVehicle().getFMMaxPositiveThrottle() : vehicledata.getVehicle().getFMMaxNegativeThrottle()) * vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getEngineSpeed();
							wheel.motionX += Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
							wheel.motionZ += Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
							velocityScale = 0.01F * (wheelsYaw > 0 ? vehicledata.getVehicle().getFMTurnLeftModifier() : vehicledata.getVehicle().getFMTurnRightModifier()) * (throttle > 0 ? 1 : -1);
							wheel.motionX -= wheel.getHorizontalSpeed() * Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
							wheel.motionZ += wheel.getHorizontalSpeed() * Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
						}
						else{
							wheel.motionX *= 0.9F;
							wheel.motionZ *= 0.9F;
						}
					}
					else if(vehicledata.getVehicle().getDriveType().isRWD()){
						if(wheel.wheelid == 0 || wheel.wheelid == 1){
							velocityScale = 0.1F * throttle * (throttle > 0 ? vehicledata.getVehicle().getFMMaxPositiveThrottle() : vehicledata.getVehicle().getFMMaxNegativeThrottle()) * vehicledata.getPart("engine").getPart().getAttribute(EngineAttribute.class).getEngineSpeed();
							wheel.motionX += Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
							wheel.motionZ += Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale;
						}
						if(wheel.wheelid == 2 || wheel.wheelid == 3){
							velocityScale = 0.01F * ((wheelsYaw > 0 ? vehicledata.getVehicle().getFMTurnLeftModifier() : vehicledata.getVehicle().getFMTurnRightModifier()) * 16) * (throttle > 0 ? 1 : -1);
							wheel.motionX = wheels[wheel.wheelid - 2].motionX;
							wheel.motionZ = wheels[wheel.wheelid - 2].motionZ;
							wheel.motionX -= wheel.getHorizontalSpeed() * Math.sin(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
							wheel.motionZ += wheel.getHorizontalSpeed() * Math.cos(wheel.rotationYaw * 3.14159265F / 180F) * velocityScale * wheelsYaw;
							//wheels[wheel.wheelid - 2].motionX *= 0.9F;
							//wheels[wheel.wheelid - 2].motionZ *= 0.9F;
						}
						//This is surely wrong.
					}
					else{
						//
					}
				}
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
		
		if(wheels[0] != null && wheels[1] != null && wheels[2] != null && wheels[3] != null){
			Vec3d front = new Vec3d((wheels[2].posX + wheels[3].posX) / 2F, (wheels[2].posY + wheels[3].posY) / 2F, (wheels[2].posZ + wheels[3].posZ) / 2F); 
			Vec3d back  = new Vec3d((wheels[0].posX + wheels[1].posX) / 2F, (wheels[0].posY + wheels[1].posY) / 2F, (wheels[0].posZ + wheels[1].posZ) / 2F); 
			Vec3d left = new Vec3d((wheels[0].posX + wheels[3].posX) / 2F, (wheels[0].posY + wheels[3].posY) / 2F, (wheels[0].posZ + wheels[3].posZ) / 2F); 
			Vec3d right = new Vec3d((wheels[1].posX + wheels[2].posX) / 2F, (wheels[1].posY + wheels[2].posY) / 2F, (wheels[1].posZ + wheels[2].posZ) / 2F); 
			//
			double dx = front.x - back.x, dy = front.y - back.y, dz = front.z - back.z;
			double drx = left.x - right.x, dry = left.y - right.y, drz = left.z - right.z;
			double dxz = Math.sqrt(dx * dx + dz * dz);
			double drxz = Math.sqrt(drx * drx + drz * drz);
			//
			double yaw = Math.atan2(dz, dx);
			double pitch = -Math.atan2(dy, dxz);
			double roll = 0F;
			roll = -(float)Math.atan2(dry, drxz);
			//
			if(vehicledata.getVehicle().getDriveType().hasTracks()){
				yaw = (float)Math.atan2(wheels[3].posZ - wheels[2].posZ, wheels[3].posX - wheels[2].posX) + (float)Math.PI / 2F;
			}
			axes.setAngles(yaw * 180F / 3.14159F, pitch * 180F / 3.14159F, roll * 180F / 3.14159F);
		}
		checkForCollisions();
		for(SeatEntity seat : seats){
			if(seat != null){
				seat.updatePosition();
			}
		}*/
		if(drivenByPlayer){
			PacketHandler.getInstance().sendToServer(new PacketVehicleControl(this));
			serverPosX = posX;
			serverPosY = posY;
			serverPosZ = posZ;
			serverYaw = axes.getYaw();
		}
		if(!world.isRemote && ticksExisted % 5 == 0){
			PacketHandler.getInstance().sendToAllAround(new PacketVehicleControl(this), Resources.getTargetPoint(this));
		}
		vehicledata.getScripts().forEach((script) -> script.onUpdate(this, vehicledata));
	}
	
	public boolean attackEntityFrom(DamageSource damagesource, float i){
		if(world.isRemote || isDead){
			return true;
		}
		if(damagesource.damageType.equals("player") && damagesource.getImmediateSource().onGround && (seats[0] == null || seats[0].getControllingPassenger() == null)){
			if(vehicledata.isLocked()){
				Print.chat(damagesource.getImmediateSource(), "Vehicle is locked. Unlock to remove it.");
				return false;
			}
			else{
				PlayerPerms pp = PermManager.getPlayerPerms((EntityPlayer)damagesource.getImmediateSource());
				vehicledata.getScripts().forEach((script) -> script.onRemove(this, vehicledata));
				ItemStack stack = vehicledata.getVehicle().getItemStack(vehicledata);
				boolean brk = pp.hasPermission(FvtmPermissions.LAND_VEHICLE_BREAK) ? pp.hasPermission(FvtmPermissions.permBreak(stack)) : false;
				if(brk){
					entityDropItem(stack, 0.5F);
			 		setDead();
			 		Print.debug(stack.toString());
			 		return true;
				}
				else{
					Print.chat(damagesource.getImmediateSource(), "No permission to break this vehicle/type.");
			 		Print.debug(stack.toString());
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean isLocked(){
		return vehicledata.isLocked();
	}

	@Override
	public boolean unlock(World world, EntityPlayer entity, ItemStack stack, KeyItem item){
		if(!stack.hasTagCompound()){
			Print.chat(entity, "[ERROR] Key don't has a NBT Tag Compound!");
			return false;
		}
		else{
			switch(item.getType(stack)){
				case PRIVATE:
					if(entity.getGameProfile().getId().toString().equals(item.getCreator(stack).toString())){
						Print.chat(entity, "This key can only be used by the Owner;");
						return false;
					}
					else{
						if(item.getCode(stack).equals(vehicledata.getLockCode())){
							vehicledata.setLocked(false);
							Print.chat(entity, "Vehicle is now unlocked.");
							return true;
						}
						else{
							Print.chat(entity, "Wrong key.\n[V:" + vehicledata.getLockCode().toUpperCase() + "] != [K:" + item.getCode(stack).toUpperCase() + "]");
							return false;
						}
					}
				case COMMON:
					if(item.getCode(stack).equals(vehicledata.getLockCode())){
						vehicledata.setLocked(false);
						Print.chat(entity, "Vehicle is now unlocked.");
						return true;
					}
					else{
						Print.chat(entity, "Wrong key.\n[V:" + vehicledata.getLockCode().toUpperCase() + "] != [K:" + item.getCode(stack).toUpperCase() + "]");
						return false;
					}
				case ADMIN:
					vehicledata.setLocked(false);
					Print.chat(entity, "[SU] Vehicle is now unlocked.");
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean lock(World world, EntityPlayer entity, ItemStack stack, KeyItem item) {
		if(!vehicledata.allowsLocking()){
			Print.chat(entity, "This vehicle doesn't allow locking.");
			return false;
		}
		else{
			if(!stack.hasTagCompound()){
				Print.chat(entity, "[ERROR] Key don't has a NBT Tag Compound!");
				return false;
			}
			else{
				switch(item.getType(stack)){
					case PRIVATE:
						if(entity.getGameProfile().getId().toString().equals(item.getCreator(stack).toString())){
							Print.chat(entity, "This key can only be used by the Owner;");
							return false;
						}
						else{
							if(item.getCode(stack).equals(vehicledata.getLockCode())){
								vehicledata.setLocked(true);
								Print.chat(entity, "Vehicle is now locked.");
								return true;
							}
							else{
								Print.chat(entity, "Wrong key.\n[V:" + vehicledata.getLockCode().toUpperCase() + "] != [K:" + item.getCode(stack).toUpperCase() + "]");
								return false;
							}
						}
					case COMMON:
						if(item.getCode(stack).equals(vehicledata.getLockCode())){
							vehicledata.setLocked(true);
							Print.chat(entity, "Vehicle is now locked.");
							return true;
						}
						else{
							Print.chat(entity, "Wrong key.\n[V:" + vehicledata.getLockCode().toUpperCase() + "] != [K:" + item.getCode(stack).toUpperCase() + "]");
							return false;
						}
					case ADMIN:
						vehicledata.setLocked(true);
						Print.chat(entity, "[SU] Vehicle is now locked.");
						return true;
				}
			}
		}
		return false;
	}

	@Override
    public void fall(float distance, float damageMultiplier){
		//
	}
		
	public void checkForCollisions(){
		return;
	}
	
	public Vec3d rotate(Vec3d inVec){
		return axes.getRelativeVector(inVec);
	}
	
	public void rotateYaw(float rotateBy){
		if(Math.abs(rotateBy) < 0.01F){
			return;
		}
		axes.rotYaw(rotateBy);
		updatePrevAngles();
	}
	
	public void rotatePitch(float rotateBy){
		if(Math.abs(rotateBy) < 0.01F){
			return;
		}
		axes.rotPitch(rotateBy);
		updatePrevAngles();
	}

	public void rotateRoll(float rotateBy){
		if(Math.abs(rotateBy) < 0.01F)
			return;
		axes.rotRoll(rotateBy);
		updatePrevAngles();
	}
		
	public void updatePrevAngles(){
		double yaw = axes.getYaw() - prevRotationYaw;
		if(yaw > 180){ prevRotationYaw += 360F; }
		if(yaw < -180){ prevRotationYaw -= 360F; }
		double pitch = axes.getPitch() - prevRotationPitch;
		if(pitch > 180){ prevRotationPitch += 360F; }
		if(pitch < -180){ prevRotationPitch -= 360F; }
		double roll = axes.getRoll() - prevRotationRoll;
		if(roll > 180){ prevRotationRoll += 360F; }
		if(roll < -180){ prevRotationRoll -= 360F; }
	}
	
	public void setRotation(float rotYaw, float rotPitch, float rotRoll){
		axes.setAngles(rotYaw, rotPitch, rotRoll);
	}
	
	public boolean isPartOfThis(Entity ent){
		for(SeatEntity seat : seats){
			if(seat == null){
				continue;
			}
			if(ent == seat || seats[0].getControllingPassenger() == ent){
				return true;
			}
		}
		return ent == this;	
	}
	
	@Override
	public ItemStack getPickedResult(RayTraceResult target){
		ItemStack stack = vehicledata.getVehicle().getItemStack(vehicledata);
		stack.setItemDamage(0);
		return stack;
	}
	
	public double get3DSpeed(){
		return Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
	}
	
	public double getHorizontalSpeed(){
		return Math.sqrt(motionX * motionX + motionZ * motionZ);
	}
	
	public float getPlayerRoll(){
		return axes.getRoll();
	}
	
	public float getPrevPlayerRoll() {
		return prevAxes.getRoll();
	}
	
	public float getCameraDistance(){
		return vehicledata.getVehicle().getFMCameraDistance();
	}
	
	@Override
	public String getName(){
		return vehicledata.getVehicle().getName();
	}
	
	@SideOnly(Side.CLIENT)
	public boolean showInventory(int seat){
		return true;
	}

	@Override
	public Entity getCamera(){
		return parent.getCamera();
	}

	public VehicleData getData(){
		return vehicledata;
	}
	
	@Override
	public void processServerPacket(PacketEntityUpdate pkt){
		if(pkt.nbt.hasKey("ScriptId")){
			for(VehicleScript script : vehicledata.getScripts()){
				if(script.getId().toString().equals(pkt.nbt.getString("ScriptId"))){
					script.onDataPacket(this, vehicledata, pkt.nbt, Side.SERVER);
				}
			}
		}
		if(pkt.nbt.hasKey("task")){
			switch(pkt.nbt.getString("task")){
				//
			}
		}
	}
	
	@Override
	public void processClientPacket(PacketEntityUpdate pkt){
		if(pkt.nbt.hasKey("ScriptId")){
			for(VehicleScript script : vehicledata.getScripts()){
				if(script.getId().toString().equals(pkt.nbt.getString("ScriptId"))){
					script.onDataPacket(this, vehicledata, pkt.nbt, Side.SERVER);
				}
			}
		}
		if(pkt.nbt.hasKey("task")){
			switch(pkt.nbt.getString("task")){
				//
			}
		}
	}

	@Override
	public VehicleData getVehicleData(){
		return this.vehicledata;
	}

	@Override
	public VehicleType getVehicleType(){
		return VehicleType.LAND;
	}

	@Override
	public Entity getEntity(){
		return this;
	}

	@Override
	public VehicleAxes getAxes(){
		return this.axes;
	}

	@Override
	public WheelEntity[] getWheels(){
		return wheels;
	}

	@Override
	public SeatEntity[] getSeats(){
		return seats;
	}

	@Override
	public double getThrottle(){
		return throttle;
	}

	@Override
	public VehicleEntity getEntityAtFront(){
		return parent;
	}

	@Override
	public VehicleEntity getEntityAtRear(){
		return null;
	}

	@Override
	public Vec3d getAngularVelocity(){
		return this.angularVelocity;
	}

	@Override
	public float getWheelsYaw(){
		return this.parent.getWheelsYaw();
	}
	
}