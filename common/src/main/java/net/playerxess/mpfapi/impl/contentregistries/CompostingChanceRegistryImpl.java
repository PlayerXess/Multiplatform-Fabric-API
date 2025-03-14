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

package net.playerxess.mpfapi.impl.contentregistries;

import net.minecraft.block.ComposterBlock;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;

import net.playerxess.mpfapi.api.contentregistries.registry.CompostingChanceRegistry;

public class CompostingChanceRegistryImpl implements CompostingChanceRegistry {
	@Override
	public Float get(ItemConvertible item) {
		return ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getOrDefault(item.asItem(), 0.0F);
	}

	@Override
	public void add(ItemConvertible item, Float value) {
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.put(item.asItem(), value);
	}

	@Override
	public void add(TagKey<Item> tag, Float value) {
		throw new UnsupportedOperationException("Tags currently not supported!");
	}

	@Override
	public void remove(ItemConvertible item) {
		ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.removeFloat(item.asItem());
	}

	@Override
	public void remove(TagKey<Item> tag) {
		throw new UnsupportedOperationException("Tags currently not supported!");
	}

	@Override
	public void clear(ItemConvertible item) {
		throw new UnsupportedOperationException("CompostingChanceRegistry operates directly on the vanilla map - clearing not supported!");
	}

	@Override
	public void clear(TagKey<Item> tag) {
		throw new UnsupportedOperationException("CompostingChanceRegistry operates directly on the vanilla map - clearing not supported!");
	}
}
