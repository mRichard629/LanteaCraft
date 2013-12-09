package pcl.lc.network;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import pcl.common.network.ModPacket;
import pcl.lc.tileentity.TileEntityStargateBase;
import cpw.mods.fml.common.network.Player;

public class ClientPacketHandler {

	public void handlePacket(ModPacket packet, Player player) {
		if (packet.getType().equals("LanteaPacket.TileUpdate")) {
			int worldName = (Integer) packet.getValue("DimensionID");
			int currentWorld = Minecraft.getMinecraft().theWorld.provider.dimensionId;
			if (worldName == currentWorld) {
				int x = (Integer) packet.getValue("WorldX");
				int y = (Integer) packet.getValue("WorldY");
				int z = (Integer) packet.getValue("WorldZ");
				World w = Minecraft.getMinecraft().theWorld;
				TileEntity tile = w.getBlockTileEntity(x, y, z);
				if (tile instanceof TileEntityStargateBase) {
					TileEntityStargateBase base = (TileEntityStargateBase) tile;
					base.getAsStructure().unpack(packet);
					w.markBlockForRenderUpdate(x, y, z);
				}
			}
		}
	}

}