/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.wooden;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IInteractionObjectIE;
import blusunrize.immersiveengineering.common.blocks.TileEntityIEBase;
import blusunrize.immersiveengineering.common.util.CapabilityReference;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tags.ItemTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;

public class TileEntitySorter extends TileEntityIEBase implements IInteractionObjectIE
{
	public static TileEntityType<TileEntitySorter> TYPE;

	public SorterInventory filter;
	public int[] sideFilter = {0, 0, 0, 0, 0, 0};//OreDict,nbt,fuzzy
	public static final int filterSlotsPerSide = 8;
	/**
	 * The positions of the routers that have been used in the current "outermost" `routeItem` call.
	 * Necessary to stop "blocks" of routers (and similar setups) from causing massive lag (using just a boolean
	 * results in every possible path to be "tested"). Using a set results in effectively a DFS.
	 */
	private static Set<BlockPos> routed = null;

	private EnumMap<EnumFacing, CapabilityReference<IItemHandler>> neighborCaps = new EnumMap<>(EnumFacing.class);

	public TileEntitySorter()
	{
		super(TYPE);
		filter = new SorterInventory(this);
		for(EnumFacing f : EnumFacing.VALUES)
			neighborCaps.put(f, CapabilityReference.forNeighbor(this, ITEM_HANDLER_CAPABILITY, f));
	}


	public ItemStack routeItem(EnumFacing inputSide, ItemStack stack, boolean simulate)
	{
		if(!world.isRemote&&canRoute())
		{
			boolean first = startRouting();
			EnumFacing[][] validOutputs = getValidOutputs(inputSide, stack);
			stack = doInsert(stack, validOutputs[0], simulate);
			stack = doInsert(stack, validOutputs[1], simulate);
			if(first)
				routed = null;
		}
		return stack;
	}

	private boolean canRoute()
	{
		return routed==null||!routed.contains(pos);
	}

	private boolean startRouting()
	{
		boolean first = routed==null;
		if(first)
			routed = new HashSet<>();
		routed.add(pos);
		return first;
	}

	private ItemStack doInsert(ItemStack stack, EnumFacing[] sides, boolean simulate)
	{
		int lengthFiltered = sides.length;
		while(lengthFiltered > 0 && !stack.isEmpty())
		{
			int rand = Utils.RAND.nextInt(lengthFiltered);
			stack = this.outputItemToInv(stack, sides[rand], simulate);
			sides[rand] = sides[lengthFiltered-1];
			lengthFiltered--;
		}
		return stack;
	}

	public boolean doOredict(int side)
	{
		if(side >= 0&&side < this.sideFilter.length)
			return (this.sideFilter[side]&1)!=0;
		return false;
	}

	public boolean doNBT(int side)
	{
		if(side >= 0&&side < this.sideFilter.length)
			return (this.sideFilter[side]&2)!=0;
		return false;
	}

	public boolean doFuzzy(int side)
	{
		if(side >= 0&&side < this.sideFilter.length)
			return (this.sideFilter[side]&4)!=0;
		return false;
	}

	@Override
	public boolean canUseGui(EntityPlayer player)
	{
		return true;
	}

	@Override
	public ResourceLocation getGuiName()
	{
		return Lib.GUIID_Sorter;
	}

	@Override
	public IInteractionObjectIE getGuiMaster()
	{
		return this;
	}

	@Override
	public void receiveMessageFromClient(NBTTagCompound message)
	{
		if(message.hasKey("sideConfig"))
			this.sideFilter = message.getIntArray("sideConfig");
	}

	public EnumFacing[][] getValidOutputs(EnumFacing inputSide, ItemStack stack)
	{
		if(stack.isEmpty())
			return new EnumFacing[][]{{}, {}, {}, {}};
		List<EnumFacing> validFiltered = new ArrayList<>(6);
		List<EnumFacing> validUnfiltered = new ArrayList<>(6);
		for(EnumFacing side : EnumFacing.values())
			if(side!=inputSide)
			{
				EnumFilterResult result = checkStackAgainstFilter(stack, side);
				if(result==EnumFilterResult.VALID_FILTERED)
						validFiltered.add(side);
				else if(result==EnumFilterResult.VALID_UNFILTERED)
						validUnfiltered.add(side);
			}

		return new EnumFacing[][]{
				validFiltered.toArray(new EnumFacing[0]),
				validUnfiltered.toArray(new EnumFacing[0])
		};
	}

	public ItemStack pullItem(EnumFacing outputSide, int amount, boolean simulate)
	{
		if(!world.isRemote&&canRoute())
		{
			boolean first = startRouting();
			for(EnumFacing side : EnumFacing.values())
				if(side!=outputSide)
				{
					CapabilityReference<IItemHandler> capRef = neighborCaps.get(side);
					IItemHandler itemHandler = capRef.get();
					if(itemHandler!=null)
					{
						Predicate<ItemStack> concatFilter = null;
						for(int i = 0; i < itemHandler.getSlots(); i++)
						{
							ItemStack extractItem = itemHandler.extractItem(i, amount, true);
							if(!extractItem.isEmpty())
							{
								if(concatFilter==null)//Init the filter here, to save on resources
									concatFilter = this.concatFilters(outputSide, side);
								if(concatFilter.test(extractItem))
								{
									if(first)
										routed = null;
									if(!simulate)
										itemHandler.extractItem(i, amount, false);
									return extractItem;
								}
							}
						}
					}
				}
			if(first)
				routed = null;
		}
		return ItemStack.EMPTY;
	}

	private boolean compareStackToFilterstack(ItemStack stack, ItemStack filterStack, boolean fuzzy, boolean oredict, boolean nbt)
	{
		boolean b = ItemStack.areItemsEqual(filterStack, stack);
		if(!b&&fuzzy)
			b = ItemStack.areItemsEqualIgnoreDurability(filterStack, stack);
		if(!b&&oredict)
			for(ResourceLocation name : ItemTags.getCollection().getOwningTags(stack.getItem()))
				if(Utils.isInTag(filterStack, name))
				{
					b = true;
					break;
				}
		if(nbt)
			b &= Utils.compareItemNBT(filterStack, stack);
		return b;
	}

	/**
	 * @param stack         the stack to check
	 * @param side          the side the filter is on
	 * @return If the stack is permitted by the given filter
	 */
	private EnumFilterResult checkStackAgainstFilter(ItemStack stack, EnumFacing side)
	{
		boolean unmapped = true;
		for(ItemStack filterStack : filter.filters[side.ordinal()])
			if(!filterStack.isEmpty())
			{
				unmapped = false;
				if(compareStackToFilterstack(stack, filterStack, doFuzzy(side.ordinal()), doOredict(side.ordinal()), doNBT(side.ordinal())))
					return EnumFilterResult.VALID_FILTERED;
			}
		if(unmapped)
			return EnumFilterResult.VALID_UNFILTERED;
		return EnumFilterResult.INVALID;
	}

	/**
	 * @return A Predicate representing the concatinated filters of two sides.<br>
	 * If one filter is empty, uses the full filter of the other side, else the matching items make up the filter
	 */
	private Predicate<ItemStack> concatFilters(EnumFacing side0, EnumFacing side1)
	{
		final List<ItemStack> concat = new ArrayList<>();
		for(ItemStack filterStack : filter.filters[side0.ordinal()])
			if(!filterStack.isEmpty())
				concat.add(filterStack);

		Predicate<ItemStack> matchFilter = concat.isEmpty()?(stack) -> true: new Predicate<ItemStack>()
		{
			final Set<ItemStack> filter = new HashSet<>(concat);

			@Override
			public boolean test(ItemStack stack)
			{
				for(ItemStack filterStack : filter)
					if(compareStackToFilterstack(stack, filterStack, doFuzzy(side0.ordinal()), doOredict(side0.ordinal()), doNBT(side0.ordinal())))
						return true;
				return false;
			}
		};

		for(ItemStack filterStack : filter.filters[side1.ordinal()])
			if(!filterStack.isEmpty()&&matchFilter.test(filterStack))
				concat.add(filterStack);

		final boolean concatFuzzy = doFuzzy(side0.ordinal())|doFuzzy(side1.ordinal());
		final boolean concatOredict = doOredict(side0.ordinal())|doOredict(side1.ordinal());
		final boolean concatNBT = doNBT(side0.ordinal())|doNBT(side1.ordinal());

		return concat.isEmpty()?stack -> true: stack -> {
			for(ItemStack filterStack : concat)
				if(compareStackToFilterstack(stack, filterStack, concatFuzzy, concatOredict, concatNBT))
					return true;
			return false;
		};
	}

	public ItemStack outputItemToInv(ItemStack stack, EnumFacing side, boolean simulate)
	{
		TileEntity inventory = Utils.getExistingTileEntity(world, getPos().offset(side));
		return Utils.insertStackIntoInventory(inventory, stack, side.getOpposite(), simulate);
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		sideFilter = nbt.getIntArray("sideFilter");
		if(!descPacket)
		{
			NBTTagList filterList = nbt.getList("filter", 10);
			filter = new SorterInventory(this);
			filter.readFromNBT(filterList);


		}
	}

	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		nbt.setIntArray("sideFilter", sideFilter);
		if(!descPacket)
		{
			NBTTagList filterList = new NBTTagList();
			filter.writeToNBT(filterList);
			nbt.setTag("filter", filterList);
		}
	}

	private EnumMap<EnumFacing, LazyOptional<IItemHandler>> insertionHandlers = new EnumMap<>(EnumFacing.class);

	{
		for(EnumFacing f : EnumFacing.VALUES)
		{
			LazyOptional<IItemHandler> forSide = registerConstantCap(new SorterInventoryHandler(this, f));
			insertionHandlers.put(f, forSide);
		}
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing)
	{
		if(capability==ITEM_HANDLER_CAPABILITY&&facing!=null)
			return insertionHandlers.get(facing).cast();
		return super.getCapability(capability, facing);
	}

	@Override
	public boolean receiveClientEvent(int id, int arg)
	{
		return id==0;
	}

	public static class SorterInventoryHandler implements IItemHandlerModifiable
	{
		TileEntitySorter sorter;
		EnumFacing side;

		public SorterInventoryHandler(TileEntitySorter sorter, EnumFacing side)
		{
			this.sorter = sorter;
			this.side = side;
		}

		@Override
		public int getSlots()
		{
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot)
		{
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
		{
			return sorter.routeItem(this.side, stack, simulate);
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate)
		{
			return sorter.pullItem(this.side, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot)
		{
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack)
		{
			return true;
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack)
		{
		}
	}


	public static class SorterInventory implements IInventory
	{
		public ItemStack[][] filters = new ItemStack[6][filterSlotsPerSide];
		final TileEntitySorter tile;

		public SorterInventory(TileEntitySorter tile)
		{
			this.tile = tile;
			clear();
		}

		@Override
		public int getSizeInventory()
		{
			return 6*filterSlotsPerSide;
		}

		@Override
		public boolean isEmpty()
		{
			for(int i = 0; i < 6; ++i)
			{
				for(int j = 0; j < filterSlotsPerSide; ++j)
				{
					if(!filters[i][j].isEmpty())
					{
						return false;
					}
				}
			}
			return true;
		}

		@Override
		public ItemStack getStackInSlot(int slot)
		{
			return filters[slot/filterSlotsPerSide][slot%filterSlotsPerSide];
		}

		@Override
		public ItemStack decrStackSize(int slot, int amount)
		{
			ItemStack stack = getStackInSlot(slot);
			if(!stack.isEmpty())
				if(stack.getCount() <= amount)
					setInventorySlotContents(slot, null);
				else
				{
					stack = stack.split(amount);
					if(stack.getCount()==0)
						setInventorySlotContents(slot, null);
				}
			return stack;
		}

		@Override
		public ItemStack removeStackFromSlot(int slot)
		{
			ItemStack stack = getStackInSlot(slot);
			if(!stack.isEmpty())
				setInventorySlotContents(slot, null);
			return stack;
		}

		@Override
		public void setInventorySlotContents(int slot, ItemStack stack)
		{
			filters[slot/filterSlotsPerSide][slot%filterSlotsPerSide] = stack;
			if(!stack.isEmpty()&&stack.getCount() > getInventoryStackLimit())
				stack.setCount(getInventoryStackLimit());
		}

		@Override
		public void clear()
		{
			for(int i = 0; i < filters.length; i++)
				for(int j = 0; j < filters[i].length; j++)
					filters[i][j] = ItemStack.EMPTY;
		}

		@Override
		public ITextComponent getName()
		{
			return new TextComponentString("IESorterLayout");
		}

		@Override
		public boolean hasCustomName()
		{
			return false;
		}

		@Nullable
		@Override
		public ITextComponent getCustomName()
		{
			return null;
		}

		@Override
		public int getInventoryStackLimit()
		{
			return 1;
		}

		@Override
		public boolean isUsableByPlayer(EntityPlayer player)
		{
			return true;
		}

		@Override
		public void openInventory(EntityPlayer player)
		{
		}

		@Override
		public void closeInventory(EntityPlayer player)
		{
		}

		@Override
		public boolean isItemValidForSlot(int slot, ItemStack stack)
		{
			return true;
		}

		@Override
		public void markDirty()
		{
			this.tile.markDirty();
		}

		public void writeToNBT(NBTTagList list)
		{
			for(int i = 0; i < this.filters.length; i++)
				for(int j = 0; j < this.filters[i].length; j++)
					if(!this.filters[i][j].isEmpty())
					{
						NBTTagCompound itemTag = new NBTTagCompound();
						itemTag.setByte("Slot", (byte)(i*filterSlotsPerSide+j));
						this.filters[i][j].write(itemTag);
						list.add(itemTag);
					}

		}

		public void readFromNBT(NBTTagList list)
		{
			for(int i = 0; i < list.size(); i++)
			{
				NBTTagCompound itemTag = list.getCompound(i);
				int slot = itemTag.getByte("Slot")&255;
				if(slot < getSizeInventory())
					this.filters[slot/filterSlotsPerSide][slot%filterSlotsPerSide] = ItemStack.read(itemTag);
			}
		}

		@Override
		public int getField(int id)
		{
			return 0;
		}

		@Override
		public void setField(int id, int value)
		{
		}

		@Override
		public int getFieldCount()
		{
			return 0;
		}
	}

	private enum EnumFilterResult
	{
		INVALID,
		VALID_FILTERED,
		VALID_UNFILTERED
	}
}