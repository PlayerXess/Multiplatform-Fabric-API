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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.jobf;

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
 * {@linkplain MappingFormat#JOBF_FILE JOBF file} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public class JobfFileReader {
	private JobfFileReader() {
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
				String lastClass = null;
				boolean visitLastClass = false;

				do {
					boolean isField;

					if (reader.nextCol("c")) { // class: c <pkg>.<cls-name-a> = <cls-name-b>
						String srcName = reader.nextCol();
						if (srcName == null || srcName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());
						srcName = srcName.replace('.', '/');

						lastClass = srcName;
						visitLastClass = visitor.visitClass(srcName);

						if (visitLastClass) {
							readSeparator(reader);

							String dstName = reader.nextCol();
							if (dstName == null || dstName.isEmpty()) throw new IOException("missing class-name-b in line "+reader.getLineNumber());

							String pkg = srcName.substring(0, srcName.lastIndexOf('/') + 1);
							dstName = pkg + dstName;

							visitor.visitDstName(MappedElementKind.CLASS, 0, dstName);
							visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS);
						}
					} else if ((isField = reader.nextCol("f")) || reader.nextCol("m")) {
						// field: f <cls-a>.<name-a>:<desc-a> = <name-b>
						// method: m <cls-a>.<name-a><desc-a> = <name-b>
						String src = reader.nextCol();
						if (src == null || src.isEmpty()) throw new IOException("missing class-/name-/desc-a in line "+reader.getLineNumber());

						int nameSepPos = src.lastIndexOf('.');
						if (nameSepPos <= 0 || nameSepPos == src.length() - 1) throw new IOException("invalid class-/name-/desc-a in line "+reader.getLineNumber());

						int descSepPos = src.lastIndexOf(isField ? ':' : '(');
						if (descSepPos <= 0 || descSepPos == src.length() - 1) throw new IOException("invalid name-/desc-a in line "+reader.getLineNumber());

						readSeparator(reader);

						String dstName = reader.nextCol();
						if (dstName == null || dstName.isEmpty()) throw new IOException("missing name-b in line "+reader.getLineNumber());

						String srcOwner = src.substring(0, nameSepPos).replace('.', '/');

						if (!srcOwner.equals(lastClass)) {
							lastClass = srcOwner;
							visitLastClass = visitor.visitClass(srcOwner) && visitor.visitElementContent(MappedElementKind.CLASS);
						}

						if (visitLastClass) {
							String srcName = src.substring(nameSepPos + 1, descSepPos);
							String srcDesc = src.substring(descSepPos + (isField ? 1 : 0));

							if (isField && visitor.visitField(srcName, srcDesc)
									|| !isField && visitor.visitMethod(srcName, srcDesc)) {
								MappedElementKind kind = isField ? MappedElementKind.FIELD : MappedElementKind.METHOD;
								visitor.visitDstName(kind, 0, dstName);
								visitor.visitElementContent(kind);
							}
						}
					} else if (reader.nextCol("p")) { // package: p <name-a> = <name-b>
						// TODO
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

	private static void readSeparator(ColumnFileReader reader) throws IOException {
		if (!reader.nextCol("=")) {
			throw new IOException("missing separator in line "+reader.getLineNumber()+" (expected \" = \")");
		}
	}
}
