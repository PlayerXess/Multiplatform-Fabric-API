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

package net.playerxess.mpfapi.api.contentregistries.registry;

import java.util.Map;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.state.property.Properties;

import net.playerxess.mpfapi.mixins.contentregistries.AxeItemAccessor;
import net.playerxess.mpfapi.impl.contentregistries.util.ImmutableCollectionUtils;

/**
 * A registry for axe stripping interactions. A vanilla example is turning logs to stripped logs.
 */
public final class StrippableBlockRegistry {
	private static final Logger LOGGER = LoggerFactory.getLogger(StrippableBlockRegistry.class);

	private StrippableBlockRegistry() {
	}

	/**
	 * Registers a stripping interaction.
	 *
	 * <p>Both blocks must have the {@link Properties#AXIS axis} property.
	 *
	 * @param input    the input block that can be stripped
	 * @param stripped the stripped result block
	 * @throws IllegalArgumentException if the input or the output doesn't have the {@link Properties#AXIS axis} property
	 */
	public static void register(Block input, Block stripped) {
		requireNonNullAndAxisProperty(input, "input block");
		requireNonNullAndAxisProperty(stripped, "stripped block");

		Block old = getRegistry().put(input, stripped);

		if (old != null) {
			LOGGER.debug("Replaced old stripping mapping from {} to {} with {}", input, old, stripped);
		}
	}

	private static void requireNonNullAndAxisProperty(Block block, String name) {
		Objects.requireNonNull(block, name + " cannot be null");

		if (!block.getStateManager().getProperties().contains(Properties.AXIS)) {
			throw new IllegalArgumentException(name + " must have the 'axis' property");
		}
	}

	private static Map<Block, Block> getRegistry() {
		return ImmutableCollectionUtils.getAsMutableMap(AxeItemAccessor::getStrippedBlocks, AxeItemAccessor::setStrippedBlocks);
	}
}
