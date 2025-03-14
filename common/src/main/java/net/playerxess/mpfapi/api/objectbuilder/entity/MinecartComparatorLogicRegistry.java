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

package net.playerxess.mpfapi.api.objectbuilder.entity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.registry.Registries;

/**
 * A registry for {@linkplain MinecartComparatorLogic custom minecart comparator logic}.
 */
public final class MinecartComparatorLogicRegistry {
	private static final Logger LOGGER = LoggerFactory.getLogger(MinecartComparatorLogicRegistry.class);
	private static final Map<EntityType<?>, MinecartComparatorLogic<?>> LOGICS = new IdentityHashMap<>();

	private MinecartComparatorLogicRegistry() {
	}

	/**
	 * Gets the registered custom comparator logic for the specified minecart entity type.
	 *
	 * @param type the entity type
	 * @return the comparator logic, or {@code null} if not registered
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public static MinecartComparatorLogic<AbstractMinecartEntity> getCustomComparatorLogic(EntityType<?> type) {
		return (MinecartComparatorLogic<AbstractMinecartEntity>) LOGICS.get(type);
	}

	/**
	 * Registers a comparator logic for a minecart entity type.
	 *
	 * <p>Registering a second value for an entity type will replace the old logic.
	 *
	 * @param <T>   the handled minecart type
	 * @param type  the minecart entity type
	 * @param logic the logic to register
	 */
	public static <T extends AbstractMinecartEntity> void register(EntityType<T> type, MinecartComparatorLogic<? super T> logic) {
		Objects.requireNonNull(type, "Entity type cannot be null");
		Objects.requireNonNull(logic, "Logic cannot be null");

		if (LOGICS.put(type, logic) != null) {
			LOGGER.warn("Overriding existing minecart comparator logic for entity type {}", Registries.ENTITY_TYPE.getId(type));
		}
	}
}
