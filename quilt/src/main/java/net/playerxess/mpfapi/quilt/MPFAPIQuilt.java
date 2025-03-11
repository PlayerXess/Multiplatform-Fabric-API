package net.playerxess.mpfapi.quilt;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import net.playerxess.mpfapi.fabriclike.MPFAPIFabricLike;

public final class MPFAPIQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        // Run the Fabric-like setup.
        MPFAPIFabricLike.init();
    }
}
