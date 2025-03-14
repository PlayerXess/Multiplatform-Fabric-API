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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;

/**
 * A mapping visitor that completes missing destination names.
 *
 * <p>Some mapping formats allow omitting destination names if equal to the source name.
 * This visitor fills in these "holes" by copying names from another namespace.
 */
public final class MappingNsCompleter extends ForwardingMappingVisitor {
	/**
	 * Constructs a new {@link MappingNsCompleter} which completes all destination namespaces.
	 *
	 * @param next The next visitor to forward the data to.
	 */
	public MappingNsCompleter(MappingVisitor next) {
		this(next, null, false);
	}

	/**
	 * @param next The next visitor to forward the data to.
	 * @param alternatives A map of which namespaces should copy from which others.
	 * Passing {@code null} causes all destination namespaces to be completed.
	 */
	public MappingNsCompleter(MappingVisitor next, @Nullable Map<String, String> alternatives) {
		this(next, alternatives, false);
	}

	/**
	 * @param next The next visitor to forward the data to.
	 * @param alternatives A map of which namespaces should copy from which others.
	 * Passing {@code null} causes all destination namespaces to be completed.
	 * @param addMissingNs Whether to copy namespaces from the alternatives key set if not already present.
	 */
	public MappingNsCompleter(MappingVisitor next, @Nullable Map<String, String> alternatives, boolean addMissingNs) {
		super(next);

		this.alternatives = alternatives;
		this.addMissingNs = addMissingNs;
	}

	@Override
	public boolean visitHeader() throws IOException {
		relayHeaderOrMetadata = next.visitHeader();

		return true;
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		if (alternatives == null) {
			alternatives = new HashMap<>(dstNamespaces.size());

			for (String ns : dstNamespaces) {
				alternatives.put(ns, srcNamespace);
			}
		}

		if (addMissingNs) {
			boolean copied = false;

			for (String ns : alternatives.keySet()) {
				if (ns.equals(srcNamespace) || dstNamespaces.contains(ns)) {
					continue;
				}

				if (!copied) {
					dstNamespaces = new ArrayList<>(dstNamespaces);
					copied = true;
				}

				dstNamespaces.add(ns);
			}
		}

		int count = dstNamespaces.size();
		alternativesMapping = new int[count];
		dstNames = new String[count];

		for (int i = 0; i < count; i++) {
			String src = alternatives.get(dstNamespaces.get(i));
			int srcIdx;

			if (src == null) {
				srcIdx = i;
			} else if (src.equals(srcNamespace)) {
				srcIdx = -1;
			} else {
				srcIdx = dstNamespaces.indexOf(src);
				if (srcIdx < 0) throw new RuntimeException("invalid alternative mapping ns "+src+": not in "+dstNamespaces+" or "+srcNamespace);
			}

			alternativesMapping[i] = srcIdx;
		}

		if (relayHeaderOrMetadata) next.visitNamespaces(srcNamespace, dstNamespaces);
	}

	@Override
	public void visitMetadata(String key, @Nullable String value) throws IOException {
		if (relayHeaderOrMetadata) next.visitMetadata(key, value);
	}

	@Override
	public boolean visitContent() throws IOException {
		relayHeaderOrMetadata = true; // for in-content metadata

		return next.visitContent();
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitClass(srcName);
	}

	@Override
	public boolean visitField(String srcName, @Nullable String srcDesc) throws IOException {
		this.srcName = srcName;

		return next.visitField(srcName, srcDesc);
	}

	@Override
	public boolean visitMethod(String srcName, @Nullable String srcDesc) throws IOException {
		this.srcName = srcName;

		return next.visitMethod(srcName, srcDesc);
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, @Nullable String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitMethodArg(argPosition, lvIndex, srcName);
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		this.srcName = srcName;

		return next.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, endOpIdx, srcName);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		dstNames[namespace] = name;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		nsLoop: for (int i = 0; i < dstNames.length; i++) {
			String name = dstNames[i];

			if (name == null) {
				int src = i;
				long visited = 1L << src;

				do {
					int newSrc = alternativesMapping[src];

					if (newSrc < 0) { // mapping to src name
						name = srcName;
						break; // srcName must never be null
					} else if (newSrc == src) { // no-op (identity) mapping, explicit in case src > 64
						continue nsLoop; // always null
					} else if ((visited & 1L << newSrc) != 0) { // cyclic mapping
						continue nsLoop; // always null
					} else {
						src = newSrc;
						name = dstNames[src];
						visited |= 1L << src;
					}
				} while (name == null);

				assert name != null;
			}

			next.visitDstName(targetKind, i, name);
		}

		Arrays.fill(dstNames, null);

		return next.visitElementContent(targetKind);
	}

	private final boolean addMissingNs;
	private Map<String, String> alternatives;
	private int[] alternativesMapping;

	private String srcName;
	private String[] dstNames;

	private boolean relayHeaderOrMetadata;
}
