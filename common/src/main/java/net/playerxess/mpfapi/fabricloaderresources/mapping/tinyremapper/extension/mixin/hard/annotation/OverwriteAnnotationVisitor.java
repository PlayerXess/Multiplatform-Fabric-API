/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, 2023, FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.AnnotationVisitor;

import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.CommonData;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.Constant;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.MxMember;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.util.ConvertibleMappable;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.util.IConvertibleString;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.util.IdentityString;

/**
 * In case of multi-target, if a remap conflict is detected,
 * an error message will show up and the behaviour is undefined.
 */
public class OverwriteAnnotationVisitor extends AnnotationVisitor {
	private final Collection<Consumer<CommonData>> tasks;
	private final MxMember method;
	private final List<String> targets;

	public OverwriteAnnotationVisitor(Collection<Consumer<CommonData>> tasks, AnnotationVisitor delegate, MxMember method, List<String> targets) {
		super(Constant.ASM_VERSION, delegate);

		this.tasks = Objects.requireNonNull(tasks);
		this.method = Objects.requireNonNull(method);
		this.targets = Objects.requireNonNull(targets);
	}

	@Override
	public void visitEnd() {
		tasks.add(data -> new OverwriteMappable(data, method, targets).result());

		super.visitEnd();
	}

	private static class OverwriteMappable extends ConvertibleMappable {
		OverwriteMappable(CommonData data, MxMember self, Collection<String> targets) {
			super(data, self, targets);
		}

		@Override
		protected IConvertibleString getName() {
			return new IdentityString(self.getName());
		}

		@Override
		protected String getDesc() {
			return self.getDesc();
		}
	}
}
