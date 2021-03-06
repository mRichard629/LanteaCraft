package lc.common.base;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lc.LCRuntime;
import lc.api.audio.ISoundController;
import lc.api.audio.channel.IMixer;
import lc.api.audio.streaming.ISound;
import lc.api.audio.streaming.ISoundProperties;
import lc.api.audio.streaming.ISoundServer;
import lc.api.event.IBlockEventHandler;
import lc.client.openal.StreamingSoundPosition;
import lc.common.LCLog;
import lc.common.configuration.IConfigure;
import lc.common.network.IPacketHandler;
import lc.common.network.LCNetworkException;
import lc.common.network.LCPacket;
import lc.common.network.packets.LCClientUpdate;
import lc.common.network.packets.LCTileSync;
import lc.common.util.java.DestructableReferenceQueue;
import lc.common.util.math.DimensionPos;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.relauncher.Side;

/**
 * Generic tile-entity implementation with default handlers.
 *
 * @author AfterLifeLochie
 */
public abstract class LCTile extends TileEntity implements IInventory, IPacketHandler, IBlockEventHandler, IConfigure {

	private static HashMap<Class<? extends LCTile>, HashMap<String, ArrayList<String>>> callbacks = new HashMap<Class<? extends LCTile>, HashMap<String, ArrayList<String>>>();

	/**
	 * Register an event callback on a class. Must provide the method name, the
	 * event name and the self class.
	 *
	 * @param me
	 *            The self class.
	 * @param method
	 *            The method to invoke.
	 * @param event
	 *            The event to invoke for.
	 */
	@SuppressWarnings("unchecked")
	public static void registerCallback(Class<?> me, String method, String event) {
		Class<? extends LCTile> tile = (Class<? extends LCTile>) me;
		if (!callbacks.containsKey(tile))
			callbacks.put(tile, new HashMap<String, ArrayList<String>>());
		HashMap<String, ArrayList<String>> me_calls = callbacks.get(tile);
		if (!me_calls.containsKey(event))
			me_calls.put(event, new ArrayList<String>());
		if (!me_calls.get(event).contains(method))
			me_calls.get(event).add(method);
		LCLog.debug("Driver adding callback on class %s event %s: %s", me.getName(), event, method);
	}

	/**
	 * Perform a set of callbacks now, if any are registered for an event.
	 *
	 * @param me
	 *            The self object
	 * @param type
	 *            The type of event
	 */
	public static void doCallbacksNow(Object me, String type) {
		@SuppressWarnings("unchecked")
		Class<? extends LCTile> meClazz = (Class<? extends LCTile>) me.getClass();
		ArrayList<String> cbs = getCallbacks(meClazz, type);
		if (cbs == null)
			return;
		doCallbacks(meClazz, me, cbs);
	}

	/**
	 * Get a list of callbacks for the self class.
	 *
	 * @param me
	 *            The self class
	 * @param type
	 *            The type of event
	 * @return A list of callable methods for event handling
	 */
	public static ArrayList<String> getCallbacks(Class<? extends LCTile> me, String type) {
		if (!callbacks.containsKey(me))
			return null;
		HashMap<String, ArrayList<String>> me_calls = callbacks.get(me);
		if (!me_calls.containsKey(type))
			return null;
		return me_calls.get(type);
	}

	/**
	 * Perform a list of callbacks on a provided type, the self class and a list
	 * of methods.
	 *
	 * @param me
	 *            The self class
	 * @param meObject
	 *            The self object
	 * @param methods
	 *            The methods to invoke
	 */
	public static void doCallbacks(Class<? extends LCTile> me, Object meObject, ArrayList<String> methods) {
		Method[] meMethods = me.getMethods();
		for (String methodName : methods)
			for (Method invoke : meMethods)
				if (invoke.getName().equalsIgnoreCase(methodName)) {
					try {
						invoke.invoke(meObject, new Object[] { (LCTile) meObject });
					} catch (Throwable t) {
						LCLog.warn("Error when processing callback %s!", methodName, t);
					}
					break;
				}
	}

	/**
	 * The tile entity's NBT compound. If you modify this, you should call
	 * {@link LCTile#markNbtDirty()} to send the changes to the client.
	 */
	protected NBTTagCompound compound;
	private boolean nbtDirty;
	private boolean clientDataDirty = true;
	private int clientDataCooldown;
	private IMixer clientMixer;

	/**
	 * Get the hasInventory of the tile. If the tile has no hasInventory,
	 * <code>null</code> may be returned.
	 *
	 * @return The tile's hasInventory, or <code>null</code>
	 */
	public abstract IInventory getInventory();

	/**
	 * <p>
	 * Called to perform update logic on the client. You can perform any
	 * extended operations here on the tile entity.
	 * </p>
	 * <p>
	 * Invocation of update methods is as follows:
	 * 
	 * <pre>
	 * updateEntity() [Minecraft] {
	 * - thinkClient()
	 * - thinkClientPost()
	 * - requestUpdatePacket()
	 * }
	 * </pre>
	 * 
	 * </p>
	 */
	public abstract void thinkClient();

	/**
	 * <p>
	 * Called to perform delayed update logic on the client. You can perform any
	 * extended delayed operations here on the tile entity.
	 * </p>
	 * <p>
	 * Invocation of update methods is as follows:
	 * 
	 * <pre>
	 * updateEntity() [Minecraft] {
	 * - thinkClient()
	 * - thinkClientPost()
	 * - requestUpdatePacket()
	 * }
	 * </pre>
	 */
	public void thinkClientPost() {
		/* No action by default */
	}

	/**
	 * <p>
	 * Called to perform update logic on the server. You can perform any
	 * extended operations here on the tile entity.
	 * </p>
	 * <p>
	 * Invocation of update methods is as follows:
	 * 
	 * <pre>
	 * updateEntity() [Minecraft] {
	 * - thinkServer()
	 * - thinkServerPost()
	 * - sendUpdatePackets()
	 * }
	 * </pre>
	 */
	public abstract void thinkServer();

	/**
	 * <p>
	 * Called to perform delayed update logic on the server. You can perform any
	 * extended delayed operations here on the tile entity.
	 * </p>
	 * <p>
	 * Invocation of update methods is as follows:
	 * 
	 * <pre>
	 * updateEntity() [Minecraft] {
	 * - thinkServer()
	 * - thinkServerPost()
	 * - sendUpdatePackets()
	 * }
	 * </pre>
	 */
	public void thinkServerPost() {
		/* No action by default */
	}

	/**
	 * Called to handle a packet.
	 *
	 * @param packet
	 *            The packet element
	 * @param player
	 *            The player
	 * @throws LCNetworkException
	 *             Any network exception when handling the packet
	 */
	public abstract void thinkPacket(LCPacket packet, EntityPlayer player) throws LCNetworkException;

	/**
	 * Called to get a description packet from the server. You should add any
	 * packets you want to send to the client to the list.
	 *
	 * @param packets
	 *            The list of packets to be sent.
	 * @throws LCNetworkException
	 *             Any network exception when preparing the packets
	 */
	public void sendPackets(List<LCPacket> packets) throws LCNetworkException {
		packets.add(new LCTileSync(new DimensionPos(this), compound));
	}

	/**
	 * Called to ask if the tile entity should render. This result is cached, so
	 * you should not return false unless you never want the tile to be
	 * rendered.
	 * 
	 * @return If the tile entity should be rendered.
	 */
	public abstract boolean shouldRender();

	/**
	 * Called to request the save of any tile data to a provided NBT compound.
	 *
	 * @param compound
	 *            The compound to save to.
	 */
	public abstract void save(NBTTagCompound compound);

	/**
	 * Called to request the load of any tile data from a provided NBT compound.
	 *
	 * @param compound
	 *            The compound to load from.
	 */
	public abstract void load(NBTTagCompound compound);

	/**
	 * Called to request debugging information about the tile.
	 *
	 * @param side
	 *            The side the debug is being called on.
	 * @return Debugging information about the tile.
	 */
	public abstract String[] debug(Side side);

	/**
	 * Get the default compound.
	 *
	 * @return The default NBT compound for the tile.
	 */
	public NBTTagCompound getBaseCompound() {
		return compound;
	}

	/**
	 * Get the canRotate of the block. The default is NORTH.
	 *
	 * @return The canRotate of the block.
	 */
	public ForgeDirection getRotation() {
		if (compound == null || !compound.hasKey("canRotate"))
			return ForgeDirection.NORTH;
		return ForgeDirection.getOrientation(compound.getInteger("canRotate"));
	}

	/**
	 * Set the canRotate of the block.
	 *
	 * @param direction
	 *            The canRotate.
	 */
	public void setRotation(ForgeDirection direction) {
		if (compound == null)
			compound = new NBTTagCompound();
		compound.setInteger("canRotate", direction.ordinal());
		markNbtDirty();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		LCTile.doCallbacksNow(this, "tileInvalidated");
		if (clientMixer != null)
			clientMixer.shutdown(true);
		DestructableReferenceQueue.queue(this);
	}

	@Override
	public void onChunkUnload() {
		LCTile.doCallbacksNow(this, "tileUnload");
		if (clientMixer != null)
			clientMixer.shutdown(true);
		DestructableReferenceQueue.queue(this);
	}

	@Override
	public void blockPlaced() {
		LCTile.doCallbacksNow(this, "blockPlace");
	}

	@Override
	public void blockBroken() {
		LCTile.doCallbacksNow(this, "blockBreak");
		if (clientMixer != null)
			clientMixer.shutdown(true);
		DestructableReferenceQueue.queue(this);
	}

	@Override
	public void neighborChanged() {
		LCTile.doCallbacksNow(this, "neighborChanged");
	}

	/**
	 * Mark the tile's NBT as dirty. On the next network update, the tile NBT
	 * data will be re-sent to all clients in range of the tile.
	 */
	protected void markNbtDirty() {
		nbtDirty = true;
	}

	/**
	 * Mark the tile's client data as dirty. On the next tick, the client tile
	 * will request new data from the server.
	 */
	protected void markClientDataDirty() {
		clientDataDirty = true;
	}

	private void sendUpdatesToClients() {
		try {
			ArrayList<LCPacket> packets = new ArrayList<LCPacket>();
			sendPackets(packets);
			for (LCPacket packet : packets)
				LCRuntime.runtime.network().sendScoped(packet, 128.0d);
		} catch (LCNetworkException e) {
			LCLog.warn("Error sending network update.", e);
		}
	}

	private void sendUpdatesToClient(EntityPlayerMP player) {
		try {
			ArrayList<LCPacket> packets = new ArrayList<LCPacket>();
			sendPackets(packets);
			for (LCPacket packet : packets)
				LCRuntime.runtime.network().sendTo(packet, player);
		} catch (LCNetworkException e) {
			LCLog.warn("Error sending network update.", e);
		}
	}

	@Override
	public void handlePacket(LCPacket packetOf, EntityPlayer player) throws LCNetworkException {
		if (packetOf instanceof LCTileSync)
			if (worldObj.isRemote) {
				clientDataDirty = false;
				compound = ((LCTileSync) packetOf).compound;
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
		if (packetOf instanceof LCClientUpdate)
			if (!worldObj.isRemote)
				sendUpdatesToClient((EntityPlayerMP) player);
			else
				throw new LCNetworkException("Can't handle LCClientUpdates on the client!");
		thinkPacket(packetOf, player);
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	/**
	 * <p>
	 * Called to update the tile entity. <b>You should not override this method
	 * in your implementation in LCTile;</b> use {@link LCTile#thinkClient} and
	 * {@link LCTile#thinkServer} instead.
	 * </p>
	 */
	@Override
	public void updateEntity() {
		if (worldObj != null)
			if (worldObj.isRemote) {
				thinkClient();
				thinkClientPost();
				if (clientDataDirty) {
					if (clientDataCooldown > 0)
						clientDataCooldown--;
					if (clientDataCooldown <= 0) {
						LCRuntime.runtime.network().sendToServer(new LCClientUpdate(new DimensionPos(this)));
						clientDataCooldown += (30 * 20);
					}
				}
			} else {
				thinkServer();
				thinkServerPost();
				if (nbtDirty) {
					nbtDirty = false;
					LCTileSync packet = new LCTileSync(new DimensionPos(this), compound);
					LCRuntime.runtime.network().sendToAllAround(packet, packet.target, 128.0d);
				}
			}
	}

	@Override
	public void readFromNBT(NBTTagCompound p_145839_1_) {
		super.readFromNBT(p_145839_1_);
		if (p_145839_1_.hasKey("base-tag"))
			compound = p_145839_1_.getCompoundTag("base-tag");
		else
			compound = new NBTTagCompound();
		markNbtDirty();
		try {
			load(p_145839_1_);
		} catch (Throwable t) {
			LCLog.warn("Failed when loading data from NBT for tile.", t);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound p_145841_1_) {
		super.writeToNBT(p_145841_1_);
		if (compound != null)
			p_145841_1_.setTag("base-tag", compound);
		try {
			save(p_145841_1_);
		} catch (Throwable t) {
			LCLog.warn("Failed when saving data to NBT for tile.", t);
		}
	}

	@Override
	public int getSizeInventory() {
		if (getInventory() == null)
			return 0;
		return getInventory().getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int p_70301_1_) {
		if (getInventory() == null)
			return null;
		return getInventory().getStackInSlot(p_70301_1_);
	}

	@Override
	public ItemStack decrStackSize(int p_70298_1_, int p_70298_2_) {
		if (getInventory() == null)
			return null;
		return getInventory().decrStackSize(p_70298_1_, p_70298_2_);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int p_70304_1_) {
		if (getInventory() == null)
			return null;
		return getInventory().getStackInSlotOnClosing(p_70304_1_);
	}

	@Override
	public void setInventorySlotContents(int p_70299_1_, ItemStack p_70299_2_) {
		if (getInventory() == null)
			return;
		getInventory().setInventorySlotContents(p_70299_1_, p_70299_2_);
	}

	@Override
	public String getInventoryName() {
		if (getInventory() == null)
			return null;
		return getInventory().getInventoryName();
	}

	@Override
	public boolean hasCustomInventoryName() {
		if (getInventory() == null)
			return false;
		return getInventory().hasCustomInventoryName();
	}

	@Override
	public int getInventoryStackLimit() {
		if (getInventory() == null)
			return 0;
		return getInventory().getInventoryStackLimit();
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer p_70300_1_) {
		if (getInventory() == null)
			return false;
		return getInventory().isUseableByPlayer(p_70300_1_);
	}

	@Override
	public void openInventory() {
		if (getInventory() == null)
			return;
		getInventory().openInventory();
	}

	@Override
	public void closeInventory() {
		if (getInventory() == null)
			return;
		getInventory().closeInventory();
	}

	@Override
	public boolean isItemValidForSlot(int p_94041_1_, ItemStack p_94041_2_) {
		if (getInventory() == null)
			return false;
		return getInventory().isItemValidForSlot(p_94041_1_, p_94041_2_);
	}

	@Override
	public Packet getDescriptionPacket() {
		if (worldObj.isRemote)
			LCRuntime.runtime.network().sendToServer(new LCClientUpdate(new DimensionPos(this)));
		return null;
	}

	/**
	 * Get the mixer for this tile-entity. If the sound engine is ready, a mixer
	 * is returned; else null is returned.
	 * 
	 * @return The sound mixer for this tile-entity, or null if no mixer is
	 *         available.
	 */
	protected IMixer mixer() {
		if (clientMixer != null)
			return clientMixer;
		ISoundController sys = LCRuntime.runtime.hints().audio();
		if (sys == null || !sys.ready())
			return null;
		clientMixer = sys.findMixer(this);
		return clientMixer;
	}

	/**
	 * Ask the sound engine to provide a handle for a sound. The filename and
	 * playback properties must be specified and not null. If the sound engine
	 * is not ready, null is returned.
	 * 
	 * @param filename
	 *            The name of the sound to open.
	 * @param properties
	 *            The properties to apply to the sound once open.
	 * @return The sound, or null if the sound engine is not ready.
	 */
	protected ISound sound(String filename, ISoundProperties properties) {
		ISoundController sys = LCRuntime.runtime.hints().audio();
		if (sys == null || !sys.ready())
			return null;
		ISoundServer server = sys.getSoundService();
		if (server == null || !server.ready())
			return null;
		return server.assign(this, filename, new StreamingSoundPosition(new DimensionPos(this)), properties);
	}

}
