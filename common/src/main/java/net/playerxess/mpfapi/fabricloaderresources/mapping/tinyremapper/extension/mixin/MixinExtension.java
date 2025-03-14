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

package net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.objectweb.asm.ClassVisitor;

import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.InputTag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.TinyRemapper;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.TinyRemapper.Builder;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.api.TrClass;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.api.TrEnvironment;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.common.data.CommonData;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.hard.HardTargetMixinClassVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.tinyremapper.extension.mixin.soft.SoftTargetMixinClassVisitor;

/**
 * A extension for remapping mixin annotation.
 *
 * <h2>Input filtering</h2>
 *
 * <p>The mixin extension can be applied to specific input tags by providing an input tag filter in the constructor.
 * An input with nonnull input tags is only processed if it has a tag matching the filter.
 *
 * <p>If the filter is null, all inputs will be processed.
 */
public class MixinExtension implements TinyRemapper.Extension {
	private final Map<Integer, Collection<Consumer<CommonData>>> tasks;
	private final Set<AnnotationTarget> targets;
	private final /* @Nullable */ Predicate<InputTag> inputTagFilter;

	public static final class CLIProvider implements TinyRemapper.CLIExtensionProvider {
		@Override
		public String name() {
			return "mixin";
		}

		@Override
		public TinyRemapper.Extension provideExtension() {
			return new MixinExtension();
		}
	}

	public enum AnnotationTarget {
		/**
		 * The string literal in mixin annotation. E.g. Mixin, Invoker, Accessor, Inject,
		 * ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.
		 */
		SOFT,
		/**
		 * The field or method with mixin annotation. E.g. Shadow, Overwrite, Accessor,
		 * Invoker, Implements.
		 */
		HARD
	}

	/**
	 * Remap mixin annotation.
	 */
	public MixinExtension() {
		this(EnumSet.allOf(AnnotationTarget.class));
	}

	public MixinExtension(/* @Nullable */ Predicate<InputTag> inputTagFilter) {
		this(EnumSet.allOf(AnnotationTarget.class), inputTagFilter);
	}

	public MixinExtension(Set<AnnotationTarget> targets) {
		this(targets, null);
	}

	public MixinExtension(Set<AnnotationTarget> targets, /* @Nullable */ Predicate<InputTag> inputTagFilter) {
		this.tasks = new ConcurrentHashMap<>();
		this.targets = targets;
		this.inputTagFilter = inputTagFilter;
	}

	@Override
	public void attach(Builder builder) {
		if (targets.contains(AnnotationTarget.HARD)) {
			builder.extraAnalyzeVisitor(new AnalyzeVisitorProvider()).extraStateProcessor(this::stateProcessor);
		}

		if (targets.contains(AnnotationTarget.SOFT)) {
			builder.extraPreApplyVisitor(new PreApplyVisitorProvider());
		}
	}

	private void stateProcessor(TrEnvironment environment) {
		CommonData data = new CommonData(environment);

		for (Consumer<CommonData> task : tasks.getOrDefault(environment.getMrjVersion(), Collections.emptyList())) {
			try {
				task.accept(data);
			} catch (RuntimeException e) {
				environment.getLogger().error(e.getMessage());
			}
		}
	}

	/**
	 * Hard-target: Shadow, Overwrite, Accessor, Invoker, Implements.
	 */
	private final class AnalyzeVisitorProvider implements TinyRemapper.AnalyzeVisitorProvider {
		@Override
		public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next) {
			return new HardTargetMixinClassVisitor(tasks.computeIfAbsent(mrjVersion, k -> new ConcurrentLinkedQueue<>()), next);
		}

		@Override
		public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next, InputTag[] inputTags) {
			if (inputTagFilter == null || inputTags == null) {
				return insertAnalyzeVisitor(mrjVersion, className, next);
			} else {
				for (InputTag tag : inputTags) {
					if (inputTagFilter.test(tag)) {
						return insertAnalyzeVisitor(mrjVersion, className, next);
					}
				}

				return next;
			}
		}

		@Override
		public ClassVisitor insertAnalyzeVisitor(boolean isInput, int mrjVersion, String className, ClassVisitor next, InputTag[] inputTags) {
			if (!isInput) {
				return next;
			}

			return insertAnalyzeVisitor(mrjVersion, className, next, inputTags);
		}
	}

	/**
	 * Soft-target: Mixin, Invoker, Accessor, Inject, ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.
	 */
	private final class PreApplyVisitorProvider implements TinyRemapper.ApplyVisitorProvider {
		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
			return new SoftTargetMixinClassVisitor(new CommonData(cls.getEnvironment()), next);
		}

		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next, InputTag[] inputTags) {
			if (!cls.isInput()) {
				return next;
			} else if (inputTagFilter == null || inputTags == null) {
				return insertApplyVisitor(cls, next);
			} else {
				for (InputTag tag : inputTags) {
					if (inputTagFilter.test(tag)) {
						return insertApplyVisitor(cls, next);
					}
				}

				return next;
			}
		}
	}
}
