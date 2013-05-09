package erogenousbeef.bigreactors.common.multiblock;

import java.util.LinkedList;
import java.util.List;

import cpw.mods.fml.common.network.PacketDispatcher;

import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.liquids.LiquidDictionary;
import net.minecraftforge.liquids.LiquidStack;

import erogenousbeef.bigreactors.common.BigReactors;
import erogenousbeef.bigreactors.common.block.BlockReactorPart;
import erogenousbeef.bigreactors.common.tileentity.TileEntityFuelRod;
import erogenousbeef.bigreactors.common.tileentity.TileEntityReactorAccessPort;
import erogenousbeef.bigreactors.common.tileentity.TileEntityReactorPart;
import erogenousbeef.bigreactors.common.tileentity.TileEntityReactorPowerTap;
import erogenousbeef.bigreactors.net.PacketWrapper;
import erogenousbeef.bigreactors.net.Packets;
import erogenousbeef.core.common.CoordTriplet;
import erogenousbeef.core.multiblock.IMultiblockPart;
import erogenousbeef.core.multiblock.MultiblockControllerBase;
import erogenousbeef.core.multiblock.MultiblockRegistry;

public class MultiblockReactor extends MultiblockControllerBase {
	// Game stuff
	protected boolean active;
	private double latentHeat;
	private LinkedList<CoordTriplet> activePowerTaps;
	// Highest internal Y-coordinate in the fuel column
	private LinkedList<CoordTriplet> attachedControlRods;
	private LinkedList<CoordTriplet> attachedAccessPorts;
	private LinkedList<CoordTriplet> attachedControllers;

	public MultiblockReactor(World world) {
		super(world);

		// Game stuff
		active = false;
		latentHeat = 0.0;
		activePowerTaps = new LinkedList<CoordTriplet>();
		attachedControlRods = new LinkedList<CoordTriplet>();
		attachedAccessPorts = new LinkedList<CoordTriplet>();
		attachedControllers = new LinkedList<CoordTriplet>();
	}
	
	@Override
	protected void onBlockAdded(IMultiblockPart part) {
		if(part instanceof TileEntityReactorAccessPort) {
			CoordTriplet coord = part.getWorldLocation();
			if(!attachedAccessPorts.contains(coord)) {
				attachedAccessPorts.add(coord);
			}
		}
		else if(part instanceof TileEntityReactorPart) {
			int metadata = ((TileEntityReactorPart)part).getBlockMetadata();
			CoordTriplet coord = part.getWorldLocation();
			if(BlockReactorPart.isControlRod(metadata) && !attachedControlRods.contains(coord)) {
				attachedControlRods.add(coord);
			}
			else if(BlockReactorPart.isController(metadata) && !attachedControllers.contains(coord)) {
				attachedControllers.add(coord);
			}
		}
		
	}
	
	@Override
	protected void onBlockRemoved(IMultiblockPart part) {
		if(part instanceof TileEntityReactorAccessPort) {
			CoordTriplet coord = part.getWorldLocation();
			if(attachedAccessPorts.contains(coord)) {
				attachedAccessPorts.remove(coord);
			}
		}
		else if(part instanceof TileEntityReactorPart) {
			int metadata = ((TileEntityReactorPart)part).getBlockMetadata();
			CoordTriplet coord = part.getWorldLocation();
			if(BlockReactorPart.isControlRod(metadata) && attachedControlRods.contains(coord)) {
				attachedControlRods.remove(coord);
			}
			else if(BlockReactorPart.isController(metadata) && attachedControllers.contains(coord)) {
				attachedControllers.remove(coord);
			}
		}
	}
	
	@Override
	protected void assembleMachine() {
		this.active = false;
		super.assembleMachine();
	}
	
	@Override
	protected void disassembleMachine() {
		super.disassembleMachine();
	}

	@Override
	protected boolean isMachineWhole() {
		// Ensure that there is at least one controller and control rod attached.
		if(attachedControlRods.size() < 1) {
			return false;
		}
		
		if(attachedControllers.size() < 1) {
			return false;
		}
		
		return super.isMachineWhole();
	}
	
	@Override
	public void updateMultiblockEntity() {		
		super.updateMultiblockEntity();
		double oldHeat = this.getHeat();

		// How much waste do we have?
		int wasteAmt = 0;
		int freeFuelSpace = 0;

		// Look for waste and run radiation simulation
		TileEntityFuelRod fuelRod;
		for(CoordTriplet coord : attachedControlRods) {
			CoordTriplet c = coord.copy();
			c.y = c.y - 1;
			int blockType = worldObj.getBlockId(c.x, c.y, c.z);			
			while(blockType == BigReactors.blockYelloriumFuelRod.blockID) {
				// Do we have waste?
				fuelRod = (TileEntityFuelRod)worldObj.getBlockTileEntity(c.x, c.y, c.z);
				if(fuelRod.hasWaste()) {
					wasteAmt += fuelRod.getWaste().amount;
				}
				
				freeFuelSpace += fuelRod.maxTotalLiquid - fuelRod.getTotalLiquid();
				
				// If we're active, radiate, produce heatz
				if(this.isActive()) {
					fuelRod.radiate();
				}
				
				// Move down a block
				c.y = c.y - 1;
				blockType = worldObj.getBlockId(c.x, c.y, c.z);
			}
			
		}
		
		// If we can, poop out waste and inject new fuel
		if(freeFuelSpace >= 1000 || wasteAmt >= 1000) {
			
			ItemStack wasteToDistribute = null;
			if(wasteAmt >= 1000) {
				wasteToDistribute = new ItemStack(BigReactors.ingotYellorium, wasteAmt/1000, 1);
			}

			int fuelIngotsToConsume = freeFuelSpace / 1000;
			int fuelIngotsConsumed = 0;
			
			// Distribute waste, slurp in ingots.
			for(CoordTriplet coord : attachedAccessPorts) {
				if(fuelIngotsToConsume <= 0 && (wasteToDistribute == null || wasteToDistribute.stackSize <= 0)) {
					break;
				}

				TileEntityReactorAccessPort port = (TileEntityReactorAccessPort)worldObj.getBlockTileEntity(coord.x, coord.y, coord.z);
				ItemStack fuelStack = port.getStackInSlot(TileEntityReactorAccessPort.SLOT_INLET);
				
				if(fuelStack != null) {
					if(fuelStack.stackSize >= fuelIngotsToConsume) {
						fuelStack.stackSize -= fuelIngotsToConsume;
						fuelIngotsConsumed = fuelIngotsToConsume;
						fuelIngotsToConsume = 0;
					}
					else {
						fuelIngotsConsumed += fuelStack.stackSize;
						fuelIngotsToConsume -= fuelStack.stackSize;
						port.setInventorySlotContents(TileEntityReactorAccessPort.SLOT_INLET, null);
					}
				}

				if(wasteToDistribute != null && wasteToDistribute.stackSize > 0) {
					tryDistributeWaste(port, coord, wasteToDistribute, false);
				}
			}
			
			// If we have waste leftover and we have multiple ports, go back over them for the
			// outlets.
			if(wasteToDistribute != null && wasteToDistribute.stackSize > 0 && attachedAccessPorts.size() > 1) {
				for(CoordTriplet coord : attachedAccessPorts) {
					if(wasteToDistribute == null || wasteToDistribute.stackSize <= 0) {
						break;
					}

					TileEntityReactorAccessPort port = (TileEntityReactorAccessPort)worldObj.getBlockTileEntity(coord.x, coord.y, coord.z);
					tryDistributeWaste(port, coord, wasteToDistribute, true);
				}
			}
			
			// Okay... let's modify the fuel rods now
			if((wasteToDistribute != null && wasteToDistribute.stackSize != wasteAmt / 1000) || fuelIngotsConsumed > 0) {
				LiquidStack fuelToDistribute = LiquidDictionary.getLiquid("yellorium", fuelIngotsConsumed * 1000);
				int wasteToConsume = 0;
				if(wasteToDistribute != null) {
					wasteToConsume = (wasteAmt/1000) - wasteToDistribute.stackSize;
				}
				
				for(CoordTriplet coord : attachedControlRods) {
					if(wasteToConsume <= 0 && fuelToDistribute.amount <= 0) { break; }
					
					CoordTriplet c = coord.copy();
					c.y = c.y - 1;
					int blockType = worldObj.getBlockId(c.x, c.y, c.z);			
					while(blockType == BigReactors.blockYelloriumFuelRod.blockID) {
						// Do we have waste?
						fuelRod = (TileEntityFuelRod)worldObj.getBlockTileEntity(c.x, c.y, c.z);
						
						if(wasteToConsume > 0) {
							LiquidStack drained = fuelRod.drain(1, wasteToConsume, true);
							if(drained != null) {
								wasteToConsume -= drained.amount;
							}
						}
						
						if(fuelToDistribute.amount > 0) {
							fuelRod.fill(0, fuelToDistribute, true);
						}
						
						// Move down a block
						c.y = c.y - 1;
						blockType = worldObj.getBlockId(c.x, c.y, c.z);
					}
				}
			}
		}

		if(this.isActive()) {
			// TODO: Balance this.
			// Produce energy from heat
			int energyAvailable = getAvailableEnergy();
			int energyRemaining = energyAvailable;
			if(activePowerTaps.size() > 0) {
				for(CoordTriplet coord : activePowerTaps) {
					if(energyRemaining <= 0) { break; }
					
					TileEntityReactorPowerTap tap = (TileEntityReactorPowerTap)this.worldObj.getBlockTileEntity(coord.x, coord.y, coord.z);
					energyRemaining = tap.onProvidePower(energyRemaining);
				}
			}
			
			produceEnergy(energyAvailable - energyRemaining);		
		}

		// leak 1% of heat to the environment
		double latentHeatLoss = Math.max(2.0, this.latentHeat * 0.99);
		latentHeat -= latentHeatLoss;
		if(latentHeat < 0.0) { latentHeat = 0.0; }
		
		// TODO: Reduce the frequency of these calls? This is very spammy.
		if(oldHeat != this.getHeat()) {
			sendTickUpdate();
		}
		// TODO: Overload?
	}
	
	public void onPowerTapConnectionChanged(int x, int y, int z, int numConnections) {
		CoordTriplet coord = new CoordTriplet(x, y, z);
		int prevActive = activePowerTaps.size();
		
		if(numConnections > 0) {
			// Tap has connected
			if(!activePowerTaps.contains(coord)) {
				activePowerTaps.add(coord);
			}
		} else {
			// Tap has disconnected
			activePowerTaps.remove(coord);
		}
	}

	// Returns available energy based on reactor heat. 10energy = 1MJ
	protected int getAvailableEnergy() { return (int)latentHeat; }

	protected void produceEnergy(int amountProduced) {
		latentHeat -= (double)amountProduced;
		if(latentHeat < 0) {
			latentHeat = 0;
		}
	}
	
	public void addLatentHeat(double newCasingHeat) {
		latentHeat += newCasingHeat;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean act) {
		if(act == this.active) { return; }
		this.active = act;
		
		TileEntity te = null; 
		IMultiblockPart part = null;
		for(CoordTriplet coord : connectedBlocks) {
			te = this.worldObj.getBlockTileEntity(coord.x, coord.y, coord.z);
			if(te != null && te instanceof IMultiblockPart) {
				part = (IMultiblockPart)te;
				if(this.active) { part.onMachineActivated(); }
				else { part.onMachineDeactivated(); }
			}
			else {
			}
		}
		
		this.sendFullUpdate();
	}

	public double getHeat() {
		return latentHeat;
	}
	
	public void setHeat(double newHeat) {
		latentHeat = newHeat;
	}

	public int getFuelColumnCount() {
		return attachedControlRods.size();
	}


	// Static validation helpers
	// Yellorium fuel rods, water and air.
	protected boolean isBlockGoodForInterior(World world, int x, int y, int z) {
		Material material = world.getBlockMaterial(x, y, z);
		if(material == net.minecraft.block.material.MaterialLiquid.water ||
			material == net.minecraft.block.material.Material.air) {
			return true;
		}
		else if(world.getBlockId(x, y, z) == BigReactors.blockYelloriumFuelRod.blockID) {
			// Ensure that the block above is either a fuel rod or a control rod
			int blockTypeAbove = world.getBlockId(x, y+1, z);
			int blockMetaAbove = world.getBlockMetadata(x,  y+1, z);
			if(blockTypeAbove != BigReactors.blockYelloriumFuelRod.blockID &&
				!(blockTypeAbove == BigReactors.blockReactorPart.blockID && BlockReactorPart.isControlRod(blockMetaAbove))) {
				return false;
			}
			// It is, ok.
			
			// This will always require fuel rods to run the entire height of the reactor.
			// You can prove it by induction.
			// Go on, do it. I'm not going to put that shit in a comment.
			// also i'm drunk
			
			// Ensure that the block below is either a fuel rod or casing.
			int blockTypeBelow = world.getBlockId(x, y-1, z);
			int blockMetaBelow = world.getBlockMetadata(x, y-1, z);
			if(blockTypeBelow != BigReactors.blockYelloriumFuelRod.blockID &&
					!(blockTypeBelow == BigReactors.blockReactorPart.blockID && BlockReactorPart.isCasing(blockMetaBelow))) {
					return false;
				}
			
			return true;
		}

		return false;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound data) {
		data.setBoolean("reactorActive", this.active);
		data.setDouble("heat", this.latentHeat);
	}

	@Override
	public void readFromNBT(NBTTagCompound data) {
		data.getBoolean("reactorActive");
		data.getDouble("heat");
	}

	@Override
	protected int getMinimumNumberOfBlocksForAssembledMachine() {
		// Hollow cube.
		return 26;
	}

	@Override
	public void formatDescriptionPacket(NBTTagCompound data) {
		data.setBoolean("reactorActive", this.active);
		data.setDouble("heat", this.latentHeat);
	}

	@Override
	public void decodeDescriptionPacket(NBTTagCompound data) {
		data.getBoolean("reactorActive");
		data.getDouble("heat");
	}

	/**
	 * Sends a full state update to all connected players.
	 * Use this sparingly.
	 */
	protected void sendFullUpdate() {
		if(this.worldObj.isRemote) { return; }

		Packet data = PacketWrapper.createPacket(BigReactors.CHANNEL,
												 Packets.ReactorControllerFullUpdate,
												 new Object[] { referenceCoord.x,
																referenceCoord.y,
																referenceCoord.z,
																this.active,
																this.latentHeat});

		CoordTriplet min, max;
		min = getMinimumCoord();
		max = getMaximumCoord();
		int midX, midY, midZ;
		int sizeX, sizeY, sizeZ;
		sizeX = (min.x - max.x) / 2;
		sizeY = (min.y - max.y) / 2;
		sizeZ = (min.z - max.z) / 2;
		midX = min.x + sizeX;
		midY = min.y + sizeY;
		midZ = min.z + sizeZ;
		
		PacketDispatcher.sendPacketToAllAround(midX, midY, midZ, 64, worldObj.provider.dimensionId, data);
	}
	
	/**
	 * Send an update to any clients nearby
	 * TODO: Make this broadcast only to players viewing the UI
	 */
	protected void sendTickUpdate() {
		if(this.worldObj.isRemote) { return; }
		
		Packet data = PacketWrapper.createPacket(BigReactors.CHANNEL,
				 Packets.ReactorControllerTickUpdate,
				 new Object[] { referenceCoord.x,
								referenceCoord.y,
								referenceCoord.z,
								this.latentHeat});

		CoordTriplet min, max;
		min = getMinimumCoord();
		max = getMaximumCoord();
		int midX, midY, midZ;
		int sizeX, sizeY, sizeZ;
		sizeX = (min.x - max.x) / 2;
		sizeY = (min.y - max.y) / 2;
		sizeZ = (min.z - max.z) / 2;
		midX = min.x + sizeX;
		midY = min.y + sizeY;
		midZ = min.z + sizeZ;
		
		PacketDispatcher.sendPacketToAllAround(midX, midY, midZ, 64, worldObj.provider.dimensionId, data);
	}
	
	private void tryDistributeWaste(TileEntityReactorAccessPort port, CoordTriplet coord, ItemStack wasteToDistribute, boolean distributeToInputs) {
		ItemStack wasteStack = port.getStackInSlot(TileEntityReactorAccessPort.SLOT_OUTLET);
		int metadata = worldObj.getBlockMetadata(coord.x, coord.y, coord.z);

		if(metadata == BlockReactorPart.ACCESSPORT_OUTLET || (distributeToInputs || attachedAccessPorts.size() < 2)) {
			// Dump waste preferentially to outlets, unless we only have one access port
			if(wasteStack == null) {
				if(wasteToDistribute.stackSize > port.getInventoryStackLimit()) {
					ItemStack newStack = wasteToDistribute.splitStack(port.getInventoryStackLimit());
					port.setInventorySlotContents(TileEntityReactorAccessPort.SLOT_OUTLET, newStack);
				}
				else {
					port.setInventorySlotContents(TileEntityReactorAccessPort.SLOT_OUTLET, wasteToDistribute);
					wasteToDistribute.stackSize = 0;
				}
			}
			else {
				ItemStack existingStack = port.getStackInSlot(TileEntityReactorAccessPort.SLOT_OUTLET);
				if(existingStack.isItemEqual(wasteToDistribute)) {
					if(existingStack.stackSize + wasteToDistribute.stackSize <= existingStack.getMaxStackSize()) {
						existingStack.stackSize += wasteToDistribute.stackSize;
						wasteToDistribute.stackSize = 0;
					}
					else {
						int amt = existingStack.getMaxStackSize() - existingStack.stackSize;
						wasteToDistribute.stackSize -= existingStack.getMaxStackSize() - existingStack.stackSize;
						existingStack.stackSize += amt;
					}
				}
			}
		}
	}
}