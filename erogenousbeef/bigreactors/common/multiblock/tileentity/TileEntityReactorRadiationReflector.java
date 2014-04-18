package erogenousbeef.bigreactors.common.multiblock.tileentity;

import net.minecraftforge.common.ForgeDirection;
import erogenousbeef.bigreactors.api.IHeatEntity;
import erogenousbeef.bigreactors.api.IRadiationModerator;
import erogenousbeef.bigreactors.common.data.RadiationData;
import erogenousbeef.bigreactors.common.data.RadiationPacket;
import erogenousbeef.bigreactors.common.multiblock.MultiblockReactor;
import erogenousbeef.bigreactors.common.multiblock.helpers.RadiationHelper;
import erogenousbeef.bigreactors.common.multiblock.helpers.ReactorInteriorData;
import erogenousbeef.core.multiblock.MultiblockValidationException;
import erogenousbeef.core.multiblock.rectangular.RectangularMultiblockTileEntityBase;
import erogenousbeef.core.common.CoordTriplet;

public class TileEntityReactorRadiationMirror extends TileEntityReactorPartBase implements IRadiationModerator, IHeatEntity {
	public static final ReactorInteriorData moderatorData = new ReactorInteriorData(1f/3f, 0.95f, 6.0f, IHeatEntity.conductivityDiamond);

	public TileEntityReactorRadiationMirror() {
		super();
	}

	// IRadiationModerator
	@Override
	public void moderateRadiation(RadiationData data, RadiationPacket radiation) {
		// if not connect to Multiblock Machine do nothing.
		if(!isConnected()) { return; }

		// Do moderation Stuff
		RadiationHelper.applyModerationFactors(data, radiation, moderatorData);


		// Split radiation into mirrored and non-mirrored components using reflection factor.
		float reflectedRadiation = radiation.intensity * getReflectionFactor();

		RadiationPacket reflect = new RadiationPacket();
		reflect.dir = radiation.dir.getOpposite();
		reflect.intensity = reflectedRadiation;
		reflect.hardness = radiation.hardness;
		reflect.ttl = radiation.ttl;

		currentCoord = this.getWorldLocation.copy();

		// Propagate reflected radiation
		RadiationHelper.propagateRadiation(worldObj, data, radiation, currentCoord);

		// Deduct refracted radiation and move on.
		radiation.intensity = Math.max(0f, radiation.intensity - refractedRadiation);
	}

	// 0..1. How well does this block reflect radiation?
        private float getReflectionFactor() {
                return 0.5f;
        }

	// IHeatEntity
	@Override
	public float getThermalConductivity() {
		return moderatorData.heatConductivity;
	}

	// RectangularMultiblockTileEntityBase
	@Override
	public void isGoodForFrame() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - radiation reflectors may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForSides() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - radiation reflectors may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForTop() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - radiation reflectors may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForBottom() throws MultiblockValidationException {
		throw new MultiblockValidationException(String.format("%d, %d, %d - radiation mirrors may only be placed in the reactor interior", xCoord, yCoord, zCoord));
	}

	@Override
	public void isGoodForInterior() throws MultiblockValidationException {
	}

	@Override
	public void onMachineActivated() {
	}

	@Override
	public void onMachineDeactivated() {
	}
}
