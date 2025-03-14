/*
 * Copyright (c) 2021 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.adapter;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;

/**
 * A mapping visitor that filters out elements with missing source descriptors.
 */
public final class MissingDescFilter extends ForwardingMappingVisitor {
	public MissingDescFilter(MappingVisitor next) {
		super(next);
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		if (srcDesc == null) return false;

		return super.visitField(srcName, srcDesc);
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		if (srcDesc == null) return false;

		return super.visitMethod(srcName, srcDesc);
	}
}
