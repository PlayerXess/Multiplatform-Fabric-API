/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.playerxess.mpfapi.mixins.contentregistries;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.Item;

import net.playerxess.mpfapi.api.contentregistries.registry.FuelRegistry;
import net.playerxess.mpfapi.impl.contentregistries.FuelRegistryImpl;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {
	@Inject(at = @At("RETURN"), method = "createFuelTimeMap")
	private static void fuelTimeMapHook(CallbackInfoReturnable<Map<Item, Integer>> info) {
		((FuelRegistryImpl) FuelRegistry.INSTANCE).apply(info.getReturnValue());
	}

	@Redirect(method = "canUseAsFuel", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;createFuelTimeMap()Ljava/util/Map;"))
	private static Map<Item, Integer> canUseAsFuelRedirect() {
		return ((FuelRegistryImpl) FuelRegistry.INSTANCE).getFuelTimes();
	}

	@Redirect(method = "getFuelTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;createFuelTimeMap()Ljava/util/Map;"))
	private Map<Item, Integer> getFuelTimeRedirect() {
		return ((FuelRegistryImpl) FuelRegistry.INSTANCE).getFuelTimes();
	}
}
