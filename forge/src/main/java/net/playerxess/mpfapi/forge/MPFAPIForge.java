package net.playerxess.mpfapi.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.playerxess.mpfapi.MPFAPI;

@Mod(MPFAPI.MOD_ID)
public final class MPFAPIForge {
    public MPFAPIForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(MPFAPI.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        MPFAPI.init();
    }
}
