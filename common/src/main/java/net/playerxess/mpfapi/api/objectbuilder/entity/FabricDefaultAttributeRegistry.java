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

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.registry.Registries;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;

import net.playerxess.mpfapi.mixins.objectbuilder.DefaultAttributeRegistryAccessor;

/**
 * Allows registering custom default attributes for living entities.
 *
 * <p>All living entity types must have default attributes registered. See {@link
 * FabricEntityTypeBuilder} for utility on entity type registration in general.</p>
 *
 * <p>A registered default attribute for an entity type can be retrieved through
 * {@link net.minecraft.entity.attribute.DefaultAttributeRegistry#get(EntityType)}.</p>
 *
 * @see net.minecraft.entity.attribute.DefaultAttributeRegistry
 */
public final class FabricDefaultAttributeRegistry {
	/**
	 * Private logger, not exposed.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(FabricDefaultAttributeRegistry.class);

	private FabricDefaultAttributeRegistry() {
	}

	/**
	 * Registers a default attribute for a type of living entity.
	 *
	 * @param type    the entity type
	 * @param builder the builder that creates the default attribute
	 * @see	FabricDefaultAttributeRegistry#register(EntityType, DefaultAttributeContainer)
	 */
	public static void register(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder) {
		register(type, builder.build());
	}

	/**
	 * Registers a default attribute for a type of living entity.
	 *
	 * <p>It can be used in a fashion similar to this:
	 * <blockquote><pre>
	 * EntityAttributeRegistry.INSTANCE.register(type, LivingEntity.createLivingAttributes());
	 * </pre></blockquote>
	 * </p>
	 *
	 * <p>If a registration overrides another, a debug log message will be emitted. Existing registrations
	 * can be checked at {@link net.minecraft.entity.attribute.DefaultAttributeRegistry#hasDefinitionFor(EntityType)}.</p>
	 *
	 * <p>For convenience, this can also be done on the {@link FabricEntityTypeBuilder} to simplify the building process.
	 *
	 * @param type      the entity type
	 * @param container the container for the default attribute
	 * @see	FabricEntityTypeBuilder.Living#defaultAttributes(Supplier)
	 */
	public static void register(EntityType<? extends LivingEntity> type, DefaultAttributeContainer container) {
		if (DefaultAttributeRegistryAccessor.getRegistry().put(type, container) != null) {
			LOGGER.debug("Overriding existing registration for entity type {}", Registries.ENTITY_TYPE.getId(type));
		}
	}
}
