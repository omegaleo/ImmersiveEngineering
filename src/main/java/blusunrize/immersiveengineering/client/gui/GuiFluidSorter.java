/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.client.gui;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.client.gui.GuiSorter.ButtonSorter;
import blusunrize.immersiveengineering.common.blocks.wooden.TileEntityFluidSorter;
import blusunrize.immersiveengineering.common.gui.ContainerFluidSorter;
import blusunrize.immersiveengineering.common.network.MessageTileSync;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.ArrayList;

public class GuiFluidSorter extends GuiIEContainerBase
{
	public TileEntityFluidSorter tile;
	InventoryPlayer playerInventory;

	public GuiFluidSorter(InventoryPlayer inventoryPlayer, TileEntityFluidSorter tile)
	{
		super(new ContainerFluidSorter(inventoryPlayer, tile));
		this.tile = tile;
		this.playerInventory = inventoryPlayer;
		this.ySize = 244;
	}

	@Override
	public void render(int mx, int my, float partial)
	{
		super.render(mx, my, partial);
		ArrayList<ITextComponent> tooltip = new ArrayList<>();
		for(GuiButton button : this.buttons)
		{
			if(button instanceof ButtonSorter)
				if(mx > button.x&&mx < button.x+18&&my > button.y&&my < button.y+18)
				{
					String[] split = I18n.format(Lib.DESC_INFO+"filter.nbt").split("<br>");
					Style white = new Style().setColor(TextFormatting.WHITE);
					Style gray = new Style().setColor(TextFormatting.WHITE);
					for(int i = 0; i < split.length; i++)
						tooltip.add(new TextComponentString(split[i]).setStyle(i==0?white: gray));
				}
		}
		for(int side = 0; side < 6; side++)
			for(int i = 0; i < 8; i++)
				if(tile.filters[side][i]!=null)
				{
					int x = guiLeft+4+(side/2)*58+(i < 3?i*18: i > 4?(i-5)*18: i==3?0: 36);
					int y = guiTop+22+(side%2)*76+(i < 3?0: i > 4?36: 18);
					if(mx > x&&mx < x+16&&my > y&&my < y+16)
						ClientUtils.addFluidTooltip(tile.filters[side][i], tooltip, 0);
				}
		if(!tooltip.isEmpty())
		{
			ClientUtils.drawHoveringText(tooltip, mx, my, fontRenderer, guiLeft+xSize, -1);
			RenderHelper.enableGUIStandardItemLighting();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton)
	{
		super.mouseClicked(mouseX, mouseY, mouseButton);
		for(int side = 0; side < 6; side++)
			for(int i = 0; i < 8; i++)
			{
				int x = guiLeft+4+(side/2)*58+(i < 3?i*18: i > 4?(i-5)*18: i==3?0: 36);
				int y = guiTop+22+(side%2)*76+(i < 3?0: i > 4?36: 18);
				if(mouseX > x&&mouseX < x+16&&mouseY > y&&mouseY < y+16)
				{
					FluidStack fs = FluidUtil.getFluidContained(playerInventory.getItemStack());
					setFluidInSlot(side, i, fs);
					return true;
				}
			}
		return false;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int mx, int my)
	{
		GlStateManager.color3f(1.0F, 1.0F, 1.0F);
		ClientUtils.bindTexture("immersiveengineering:textures/gui/sorter.png");
		this.drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
		for(int side = 0; side < 6; side++)
		{
			ClientUtils.bindAtlas();
			for(int i = 0; i < 8; i++)
				if(tile.filters[side][i]!=null)
				{
					TextureAtlasSprite sprite = ClientUtils.getSprite(tile.filters[side][i].getFluid().getStill(tile.filters[side][i]));
					if(sprite!=null)
					{
						int x = guiLeft+4+(side/2)*58+(i < 3?i*18: i > 4?(i-5)*18: i==3?0: 36);
						int y = guiTop+22+(side%2)*76+(i < 3?0: i > 4?36: 18);
						int col = tile.filters[side][i].getFluid().getColor(tile.filters[side][i]);
						GlStateManager.color3f((col >> 16&255)/255.0f, (col >> 8&255)/255.0f, (col&255)/255.0f);
						ClientUtils.drawTexturedRect(x, y, 16, 16, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV());
					}
				}
			int x = guiLeft+30+(side/2)*58;
			int y = guiTop+44+(side%2)*76;
			String s = I18n.format("desc.immersiveengineering.info.blockSide."+EnumFacing.byIndex(side).toString()).substring(0, 1);
			GlStateManager.enableBlend();
			ClientUtils.font().drawStringWithShadow(s, x-(ClientUtils.font().getStringWidth(s)/2), y, 0xaacccccc);
		}
		ClientUtils.bindTexture("immersiveengineering:textures/gui/sorter.png");
	}

	@Override
	public void initGui()
	{
		super.initGui();
		this.buttons.clear();
		for(int side = 0; side < 6; side++)
		{
			int x = guiLeft+21+(side/2)*58;
			int y = guiTop+3+(side%2)*76;
			final int sideFinal = side;
			ButtonSorter b = new ButtonSorter(side, x, y, 1)
			{
				@Override
				public void onClick(double mX, double mY)
				{
					tile.sortWithNBT[sideFinal] = (byte)(tile.sortWithNBT[sideFinal]==1?0: 1);

					NBTTagCompound tag = new NBTTagCompound();
					tag.setByteArray("sideConfig", tile.sortWithNBT);
					ImmersiveEngineering.packetHandler.sendToServer(new MessageTileSync(tile, tag));
					initGui();
				}
			};
			b.active = this.tile.doNBT(side);
			this.buttons.add(b);
		}
	}

	public void setFluidInSlot(int side, int slot, FluidStack fluid)
	{
		tile.filters[side][slot] = fluid;
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInt("filter_side", side);
		tag.setInt("filter_slot", slot);
		if(fluid!=null)
			tag.setTag("filter", fluid.writeToNBT(new NBTTagCompound()));
		ImmersiveEngineering.packetHandler.sendToServer(new MessageTileSync(tile, tag));
	}
}
