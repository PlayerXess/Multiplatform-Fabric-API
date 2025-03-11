/*
 * Copyright (c) 2023 FabricMC
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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.simple;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappedElementKind;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingFlag;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingUtil;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.MappingVisitor;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.ColumnFileReader;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.MappingFormat;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.tree.MappingTree;
import net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.tree.MemoryMappingTree;

/**
 * {@linkplain MappingFormat#RECAF_SIMPLE_FILE Recaf Simple file} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class RecafSimpleFileReader {
	private RecafSimpleFileReader() {
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		read(new ColumnFileReader(reader, '\t', ' '), sourceNs, targetNs, visitor);
	}

	private static void read(ColumnFileReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		Set<MappingFlag> flags = visitor.getFlags();
		MappingVisitor parentVisitor = null;
		boolean readerMarked = false;

		if (flags.contains(MappingFlag.NEEDS_ELEMENT_UNIQUENESS)) {
			parentVisitor = visitor;
			visitor = new MemoryMappingTree();
		} else if (flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
			reader.mark();
			readerMarked = true;
		}

		for (;;) {
			if (visitor.visitHeader()) {
				visitor.visitNamespaces(sourceNs, Collections.singletonList(targetNs));
			}

			if (visitor.visitContent()) {
				String line;
				String lastClass = null;
				boolean visitClass = false;

				do {
					line = reader.nextCols(true);

					// Skip comments and empty lines
					if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) continue;

					String[] parts = line.split(" ");

					if (parts.length < 2) {
						insufficientColumnCount(reader);
						continue;
					}

					int dotPos = parts[0].lastIndexOf('.');
					String clsSrcName;
					String clsDstName;
					String memberSrcName = null;
					String memberSrcDesc = null;
					String memberDstName;
					boolean isMethod = false;

					if (dotPos < 0) { // class
						clsSrcName = parts[0];
						clsDstName = parts[1];

						lastClass = clsSrcName;
						visitClass = visitor.visitClass(clsSrcName);

						if (visitClass) {
							visitor.visitDstName(MappedElementKind.CLASS, 0, clsDstName);
							visitClass = visitor.visitElementContent(MappedElementKind.CLASS);
						}
					} else { // member
						clsSrcName = parts[0].substring(0, dotPos);

						if (!clsSrcName.equals(lastClass)) {
							lastClass = clsSrcName;
							visitClass = visitor.visitClass(clsSrcName) && visitor.visitElementContent(MappedElementKind.CLASS);
						}

						if (!visitClass) continue;

						String memberIdentifier = parts[0].substring(dotPos + 1);
						memberDstName = parts[1];

						if (parts.length >= 3) { // field with descriptor
							memberSrcName = memberIdentifier;
							memberSrcDesc = parts[1];
							memberDstName = parts[2];
						} else if (parts.length == 2) { // field without descriptor or method
							int mthDescPos = memberIdentifier.lastIndexOf("(");

							if (mthDescPos < 0) { // field
								memberSrcName = memberIdentifier;
							} else { // method
								isMethod = true;
								memberSrcName = memberIdentifier.substring(0, mthDescPos);
								memberSrcDesc = memberIdentifier.substring(mthDescPos);
							}
						} else {
							insufficientColumnCount(reader);
						}

						if (!isMethod && visitor.visitField(memberSrcName, memberSrcDesc)) {
							visitor.visitDstName(MappedElementKind.FIELD, 0, memberDstName);
							visitor.visitElementContent(MappedElementKind.FIELD);
						} else if (isMethod && visitor.visitMethod(memberSrcName, memberSrcDesc)) {
							visitor.visitDstName(MappedElementKind.METHOD, 0, memberDstName);
							visitor.visitElementContent(MappedElementKind.METHOD);
						}
					}
				} while (reader.nextLine(0));
			}

			if (visitor.visitEnd()) break;

			if (!readerMarked) {
				throw new IllegalStateException("repeated visitation requested without NEEDS_MULTIPLE_PASSES");
			}

			int markIdx = reader.reset();
			assert markIdx == 1;
		}

		if (parentVisitor != null) {
			((MappingTree) visitor).accept(parentVisitor);
		}
	}

	private static void insufficientColumnCount(ColumnFileReader reader) throws IOException {
		throw new IOException("Invalid Recaf Simple line "+reader.getLineNumber()+": Insufficient column count!");
	}
}
