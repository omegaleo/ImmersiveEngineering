/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering;

import blusunrize.immersiveengineering.api.IEApi;
import blusunrize.immersiveengineering.api.shader.ShaderRegistry;
import blusunrize.immersiveengineering.api.tool.ExcavatorHandler;
import blusunrize.immersiveengineering.common.CommonProxy;
import blusunrize.immersiveengineering.common.Config.IEConfig;
import blusunrize.immersiveengineering.common.EventHandler;
import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.IESaveData;
import blusunrize.immersiveengineering.common.blocks.IEBlocks.MetalDecoration;
import blusunrize.immersiveengineering.common.gui.GuiHandler;
import blusunrize.immersiveengineering.common.items.ItemRevolver;
import blusunrize.immersiveengineering.common.network.*;
import blusunrize.immersiveengineering.common.util.IEIMCHandler;
import blusunrize.immersiveengineering.common.util.IELogger;
import blusunrize.immersiveengineering.common.util.IESounds;
import blusunrize.immersiveengineering.common.util.advancements.IEAdvancements;
import blusunrize.immersiveengineering.common.util.commands.CommandHandler;
import blusunrize.immersiveengineering.common.util.compat.IECompatModule;
import blusunrize.immersiveengineering.common.world.IEWorldGen;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nonnull;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

@Mod(ImmersiveEngineering.MODID)
public class ImmersiveEngineering
{
	public static final String MODID = "immersiveengineering";
	public static final String MODNAME = "Immersive Engineering";
	public static final String VERSION = "${version}";
	public static final int DATA_FIXER_VERSION = 1;
	public static final String NETWORK_VERSION = "1";
	public static CommonProxy proxy;

	public static final SimpleChannel packetHandler = NetworkRegistry.ChannelBuilder
			.named(new ResourceLocation(MODID, "main"))
			.networkProtocolVersion(() -> NETWORK_VERSION)
			.serverAcceptedVersions(NETWORK_VERSION::equals)
			.clientAcceptedVersions(NETWORK_VERSION::equals)
			.simpleChannel();

	public ImmersiveEngineering()
	{
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::wrongSignature);
		//TODO right bus?
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStarting);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStarted);
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.GUIFACTORY, () -> GuiHandler::createGui);
	}

	static
	{
		FluidRegistry.enableUniversalBucket();
	}

	public void setup(FMLCommonSetupEvent event)
	{
		//Previously in PREINIT
		IELogger.logger = LogManager.getLogger(MODID);
		//TODO Config.preInit(event);

		IEContent.preInit();
		proxy.preInit();

		IEAdvancements.preInit();


		for(int b : IEConfig.Ores.oreDimBlacklist)
			IEWorldGen.oreDimBlacklist.add(b);
		IEApi.modPreference = Arrays.asList(IEConfig.preferredOres);
		IEApi.prefixToIngotMap.put("ingot", new Integer[]{1, 1});
		IEApi.prefixToIngotMap.put("nugget", new Integer[]{1, 9});
		IEApi.prefixToIngotMap.put("block", new Integer[]{9, 1});
		IEApi.prefixToIngotMap.put("plate", new Integer[]{1, 1});
		IEApi.prefixToIngotMap.put("wire", new Integer[]{1, 1});
		IEApi.prefixToIngotMap.put("gear", new Integer[]{4, 1});
		IEApi.prefixToIngotMap.put("rod", new Integer[]{2, 1});
		IEApi.prefixToIngotMap.put("fence", new Integer[]{5, 3});
		IECompatModule.doModulesPreInit();

		new ThreadContributorSpecialsDownloader();

		IEContent.preInitEnd();

		//Previously in INIT

		proxy.preInitEnd();
		IEContent.init();
		IEWorldGen ieWorldGen = new IEWorldGen();
		//TODO GameRegistry.registerWorldGenerator(ieWorldGen, 0);
		MinecraftForge.EVENT_BUS.register(ieWorldGen);

		MinecraftForge.EVENT_BUS.register(new EventHandler());
		//TODO NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
		proxy.init();

		IESounds.init();

		IECompatModule.doModulesInit();
		proxy.initEnd();
		int messageId = 0;
		packetHandler.registerMessage(messageId++, MessageMineralListSync.class, MessageMineralListSync::toBytes,
				MessageMineralListSync::new, MessageMineralListSync::process);
		packetHandler.registerMessage(messageId++, MessageTileSync.class, MessageTileSync::toBytes,
				MessageTileSync::new, MessageTileSync::process);
		packetHandler.registerMessage(messageId++, MessageTileSync.class, MessageTileSync::toBytes,
				MessageTileSync::new, MessageTileSync::process);
		packetHandler.registerMessage(messageId++, MessageSpeedloaderSync.class, MessageSpeedloaderSync::toBytes,
				MessageSpeedloaderSync::new, MessageSpeedloaderSync::process);
		packetHandler.registerMessage(messageId++, MessageSkyhookSync.class, MessageSkyhookSync::toBytes,
				MessageSkyhookSync::new, MessageSkyhookSync::process);
		packetHandler.registerMessage(messageId++, MessageMinecartShaderSync.class, MessageMinecartShaderSync::toBytes,
				MessageMinecartShaderSync::new, MessageMinecartShaderSync::process);
		packetHandler.registerMessage(messageId++, MessageMinecartShaderSync.class, MessageMinecartShaderSync::toBytes,
				MessageMinecartShaderSync::new, MessageMinecartShaderSync::process);
		packetHandler.registerMessage(messageId++, MessageRequestBlockUpdate.class, MessageRequestBlockUpdate::toBytes,
				MessageRequestBlockUpdate::new, MessageRequestBlockUpdate::process);
		packetHandler.registerMessage(messageId++, MessageNoSpamChatComponents.class, MessageNoSpamChatComponents::toBytes,
				MessageNoSpamChatComponents::new, MessageNoSpamChatComponents::process);
		packetHandler.registerMessage(messageId++, MessageShaderManual.class, MessageShaderManual::toBytes,
				MessageShaderManual::new, MessageShaderManual::process);
		packetHandler.registerMessage(messageId++, MessageShaderManual.class, MessageShaderManual::toBytes,
				MessageShaderManual::new, MessageShaderManual::process);
		packetHandler.registerMessage(messageId++, MessageBirthdayParty.class, MessageBirthdayParty::toBytes,
				MessageBirthdayParty::new, MessageBirthdayParty::process);
		packetHandler.registerMessage(messageId++, MessageMagnetEquip.class, MessageMagnetEquip::toBytes,
				MessageMagnetEquip::new, MessageMagnetEquip::process);
		packetHandler.registerMessage(messageId++, MessageChemthrowerSwitch.class, MessageChemthrowerSwitch::toBytes,
				MessageChemthrowerSwitch::new, MessageChemthrowerSwitch::process);
		packetHandler.registerMessage(messageId++, MessageObstructedConnection.class, MessageObstructedConnection::toBytes,
				MessageObstructedConnection::new, MessageObstructedConnection::process);
		packetHandler.registerMessage(messageId++, MessageSetGhostSlots.class, MessageSetGhostSlots::toBytes,
				MessageSetGhostSlots::new, MessageSetGhostSlots::process);
		packetHandler.registerMessage(messageId++, MessageWireSync.class, MessageWireSync::toBytes,
				MessageWireSync::new, MessageWireSync::process);
		packetHandler.registerMessage(messageId++, MessageMaintenanceKit.class, MessageMaintenanceKit::toBytes,
				MessageMaintenanceKit::new, MessageMaintenanceKit::process);

		IEIMCHandler.init();
		//TODO IEIMCHandler.handleIMCMessages(FMLInterModComms.fetchRuntimeMessages(this));

		//Previously in POSTINIT

		IEContent.postInit();
		ExcavatorHandler.recalculateChances(true);
		proxy.postInit();
		IECompatModule.doModulesPostInit();
		proxy.postInitEnd();
		ShaderRegistry.compileWeight();
	}

	public void loadComplete(FMLLoadCompleteEvent event)
	{
		IECompatModule.doModulesLoadComplete();
	}

	private static final String[] alternativeCerts = {
			"7e11c175d1e24007afec7498a1616bef0000027d",// malte0811
			"MavenKeyHere"//TODO maven
	};

	public void wrongSignature(FMLFingerprintViolationEvent event)
	{
		System.out.println("[Immersive Engineering/Error] THIS IS NOT AN OFFICIAL BUILD OF IMMERSIVE ENGINEERING! Found these fingerprints: "+event.getFingerprints());
		for(String altCert : alternativeCerts)
			if(event.getFingerprints().contains(altCert))
			{
				System.out.println("[Immersive Engineering/Error] "+altCert+" is considered an alternative certificate (which may be ok to use in some cases). "+
						"If you thought this was an official build you probably shouldn't use it.");
				break;
			}
	}


	public void serverStarting(FMLServerStartingEvent event)
	{
		proxy.serverStarting();
		//TODO do client commands exist yet? I don't think so
		CommandHandler.registerServer(event.getCommandDispatcher());
	}

	public void serverStarted(FMLServerStartedEvent event)
	{
		//TODO isn't this always true? if(FMLCommonHandler.instance().getEffectiveSide()==Side.SERVER)
		{
			//TODO hardcoding DimensionType.OVERWORLD seems hacky/broken
			World world = event.getServer().getWorld(DimensionType.OVERWORLD);
			if(!world.isRemote)
			{
				IESaveData worldData = world.func_212411_a(DimensionType.OVERWORLD, IESaveData::new, IESaveData.dataName);

				if(worldData==null)
				{
					IELogger.info("WorldData not found");
					worldData = new IESaveData(IESaveData.dataName);
					world.func_212409_a(DimensionType.OVERWORLD, IESaveData.dataName, worldData);
				}
				else
					IELogger.info("WorldData retrieved");
				IESaveData.setInstance(world.getDimension().getType(), worldData);
			}
		}
		IEContent.refreshFluidReferences();
	}

	public static ItemGroup itemGroup = new ItemGroup(MODID)
	{
		@Override
		@Nonnull
		public ItemStack createIcon()
		{
			return ItemStack.EMPTY;
		}


		@Override
		public ItemStack getIcon()
		{
			return new ItemStack(MetalDecoration.lVCoil, 1);
		}
	};

	public static class ThreadContributorSpecialsDownloader extends Thread
	{
		public static ThreadContributorSpecialsDownloader activeThread;

		public ThreadContributorSpecialsDownloader()
		{
			setName("Immersive Engineering Contributors Thread");
			setDaemon(true);
			start();
			activeThread = this;
		}

		@Override
		public void run()
		{
			Gson gson = new Gson();
			try
			{
				IELogger.info("Attempting to download special revolvers from GitHub");
				URL url = new URL("https://raw.githubusercontent.com/BluSunrize/ImmersiveEngineering/master/contributorRevolvers.json");
				JsonStreamParser parser = new JsonStreamParser(new InputStreamReader(url.openStream()));
				while(parser.hasNext())
				{
					try
					{
						JsonElement je = parser.next();
						ItemRevolver.SpecialRevolver revolver = gson.fromJson(je, ItemRevolver.SpecialRevolver.class);
						if(revolver!=null)
						{
							if(revolver.uuid!=null)
								for(String uuid : revolver.uuid)
									ItemRevolver.specialRevolvers.put(uuid, revolver);
							ItemRevolver.specialRevolversByTag.put(!revolver.tag.isEmpty()?revolver.tag: revolver.flavour, revolver);
						}
					} catch(Exception excepParse)
					{
						IELogger.warn("Error on parsing a SpecialRevolver");
					}
				}
			} catch(Exception e)
			{
				IELogger.info("Could not load contributor+special revolver list.");
				e.printStackTrace();
			}
		}
	}
}
