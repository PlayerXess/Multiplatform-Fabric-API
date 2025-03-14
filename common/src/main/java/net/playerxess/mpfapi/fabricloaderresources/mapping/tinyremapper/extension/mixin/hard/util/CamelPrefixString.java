/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.util;

import java.util.Objects;

import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.StringUtility;

public class CamelPrefixString implements IConvertibleString {
	private final String prefix;
	private final String text;

	public CamelPrefixString(String prefix, String text) {
		this.prefix = Objects.requireNonNull(prefix);
		this.text = StringUtility.removeCamelPrefix(prefix, text);
	}

	@Override
	public String getOriginal() {
		return StringUtility.addCamelPrefix(prefix, text);
	}

	@Override
	public String getConverted() {
		return text;
	}

	@Override
	public String getReverted(String newText) {
		return StringUtility.addCamelPrefix(prefix, newText);
	}
}
