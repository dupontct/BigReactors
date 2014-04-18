package erogenousbeef.bigreactors.common.multiblock.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import erogenousbeef.bigreactors.common.BigReactors;
import erogenousbeef.bigreactors.common.multiblock.tileentity.TileEntityReactorRadiationReflector;

public class BlockReactorRadiationReflector extends BlockContainer {

	public BlockReactorRadiationReflector(int id, Material material) {
		super(id, material);

		setHardness(4f);
		setCreativeTab(BigReactors.TAB);
		setUnlocalizedName("radiationReflector");
		setTextureName(BigReactors.TEXTURE_NAME_PREFIX + getUnlocalizedName());
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerIcons(IconRegister par1IconRegister) {
		this.blockIcon = par1IconRegister.registerIcon(BigReactors.TEXTURE_NAME_PREFIX + getUnlocalizedName());
	}

	@Override
	public TileEntity createNewTileEntity(World world) {
		return null;
	}

	@Override
	public TileEntity createTileEntity(World world, int metadata) {
		return new TileEntityReactorRadiationReflector();
	}

	@Override
	public boolean isOpaqueCube() {
		return true;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return true;
	}
}
