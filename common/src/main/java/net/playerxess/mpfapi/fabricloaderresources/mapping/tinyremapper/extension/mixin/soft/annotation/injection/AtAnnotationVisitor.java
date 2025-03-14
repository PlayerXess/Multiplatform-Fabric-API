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

package net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.soft.annotation.injection;

import java.util.Objects;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.AnnotationNode;

import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.IMappable;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.Annotation;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.AnnotationElement;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.CommonData;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.Constant;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.soft.data.MemberInfo;

/**
 * {@code @At} require fully-qualified {@link net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.soft.data.MemberInfo} unless
 * {@code value = NEW}, in which case a special set of rule applies.
 */
class AtAnnotationVisitor extends AnnotationNode {
	private final CommonData data;
	private final AnnotationVisitor delegate;

	private String value;

	AtAnnotationVisitor(CommonData data, AnnotationVisitor delegate) {
		super(Constant.ASM_VERSION, Annotation.AT);

		this.data = Objects.requireNonNull(data);
		this.delegate = Objects.requireNonNull(delegate);
	}

	@Override
	public void visit(String name, Object value) {
		if (name.equals(AnnotationElement.VALUE)) {
			this.value = Objects.requireNonNull((String) value);
		}

		super.visit(name, value);
	}

	@Override
	public void visitEnd() {
		accept(new AtSecondPassAnnotationVisitor(data, delegate, value));

		super.visitEnd();
	}

	private static class AtSecondPassAnnotationVisitor extends AnnotationVisitor {
		private final CommonData data;
		private final String value;

		AtSecondPassAnnotationVisitor(CommonData data, AnnotationVisitor delegate, String value) {
			super(Constant.ASM_VERSION, delegate);

			this.data = Objects.requireNonNull(data);
			this.value = Objects.requireNonNull(value);
		}

		@Override
		public void visit(String name, Object value) {
			if (name.equals(AnnotationElement.TARGET)) {
				MemberInfo info = MemberInfo.parse(Objects.requireNonNull((String) value).replaceAll("\\s", ""));

				if (info != null) {
					if (this.value.equals("NEW")) {
						value = new AtConstructorMappable(data, info).result().toString();
					} else {
						value = new AtMemberMappable(data, info).result().toString();
					}
				}
			}

			super.visit(name, value);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor av = super.visitArray(name);

			if (name.equals(AnnotationElement.ARGS) && this.value.equals("NEW")) {
				final String prefix = "class=";

				av = new AnnotationVisitor(Constant.ASM_VERSION, av) {
					@Override
					public void visit(String name, Object value) {
						String argument = Objects.requireNonNull((String) value);
						MemberInfo info;

						if (argument.startsWith(prefix)
								&& (info = MemberInfo.parse(argument.substring(prefix.length()).replaceAll("\\s", ""))) != null) {
							value = prefix + new AtConstructorMappable(data, info).result().toString();
						}

						super.visit(name, value);
					}
				};
			}

			return av;
		}
	}

	private static class AtConstructorMappable implements IMappable<MemberInfo> {
		private final CommonData data;
		private final MemberInfo info;

		AtConstructorMappable(CommonData data, MemberInfo info) {
			this.data = Objects.requireNonNull(data);
			this.info = Objects.requireNonNull(info);
		}

		@Override
		public MemberInfo result() {
			if (info.getDesc().isEmpty()) {
				// remap owner only
				return new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), info.getName(), info.getQuantifier(), "");
			} else if (info.getDesc().endsWith(")V")) {
				// remap owner and desc
				return new MemberInfo(data.mapper.asTrRemapper().map(info.getOwner()), info.getName(), info.getQuantifier(), data.mapper.asTrRemapper().mapMethodDesc(info.getDesc()));
			} else {
				// remap desc only
				return new MemberInfo(info.getOwner(), info.getName(), info.getQuantifier(), data.mapper.asTrRemapper().mapMethodDesc(info.getDesc()));
			}
		}
	}
}
