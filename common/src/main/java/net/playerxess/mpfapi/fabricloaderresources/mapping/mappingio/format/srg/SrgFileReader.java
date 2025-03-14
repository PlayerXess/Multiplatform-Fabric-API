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

package net.playerxess.mpfapi.fabricloaderresources.mapping.mappingio.format.srg;

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
 * {@linkplain MappingFormat#SRG_FILE SRG file} and
 * {@linkplain MappingFormat#XSRG_FILE XSRG file} reader.
 *
 * <p>Crashes if a second visit pass is requested without
 * {@link MappingFlag#NEEDS_MULTIPLE_PASSES} having been passed beforehand.
 */
public final class SrgFileReader {
	private SrgFileReader() {
	}

	public static void read(Reader reader, MappingVisitor visitor) throws IOException {
		read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
	}

	public static void read(Reader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		read(new ColumnFileReader(reader, '\t', ' '), sourceNs, targetNs, visitor);
	}

	private static void read(ColumnFileReader reader, String sourceNs, String targetNs, MappingVisitor visitor) throws IOException {
		MappingFormat format = MappingFormat.SRG_FILE;
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
				String lastClassSrcName = null;
				String lastClassDstName = null;
				boolean classContentVisitPending = false;

				do {
					boolean isMethod;

					if (reader.nextCol("CL:")) { // class: CL: <src> <dst>
						String srcName = reader.nextCol();
						if (srcName == null || srcName.isEmpty()) throw new IOException("missing class-name-a in line "+reader.getLineNumber());

						if (classContentVisitPending) {
							visitor.visitElementContent(MappedElementKind.CLASS);
							classContentVisitPending = false;
						}

						lastClassSrcName = srcName;

						if (visitor.visitClass(srcName)) {
							String dstName = reader.nextCol();
							if (dstName == null || dstName.isEmpty()) throw new IOException("missing class-name-b in line "+reader.getLineNumber());

							lastClassDstName = dstName;
							visitor.visitDstName(MappedElementKind.CLASS, 0, dstName);
							classContentVisitPending = true;
						}
					} else if ((isMethod = reader.nextCol("MD:")) || reader.nextCol("FD:")) { // method: MD: <cls-a><name-a> <desc-a> <cls-b><name-b> <desc-b> or field: FD: <cls-a><name-a> <cls-b><name-b>
						String src = reader.nextCol();
						if (src == null) throw new IOException("missing class-/name-a in line "+reader.getLineNumber());

						int srcSepPos = src.lastIndexOf('/');
						if (srcSepPos <= 0 || srcSepPos == src.length() - 1) throw new IOException("invalid class-/name-a in line "+reader.getLineNumber());

						String[] cols = new String[3];

						for (int i = 0; i < 3; i++) {
							cols[i] = reader.nextCol();
						}

						if (!isMethod && cols[1] != null && cols[2] != null) format = MappingFormat.XSRG_FILE;
						String srcDesc;
						String dstName;
						String dstDesc;

						if (isMethod || format == MappingFormat.XSRG_FILE) {
							srcDesc = cols[0];
							if (srcDesc == null || srcDesc.isEmpty()) throw new IOException("missing desc-a in line "+reader.getLineNumber());
							dstName = cols[1];
							dstDesc = cols[2];
							if (dstDesc == null || dstDesc.isEmpty()) throw new IOException("missing desc-b in line "+reader.getLineNumber());
						} else {
							srcDesc = null;
							dstName = cols[0];
							dstDesc = null;
						}

						if (dstName == null) throw new IOException("missing class-/name-b in line "+reader.getLineNumber());

						int dstSepPos = dstName.lastIndexOf('/');
						if (dstSepPos <= 0 || dstSepPos == dstName.length() - 1) throw new IOException("invalid class-/name-b in line "+reader.getLineNumber());

						String srcOwner = src.substring(0, srcSepPos);
						String dstOwner = dstName.substring(0, dstSepPos);
						boolean classVisitRequired = !srcOwner.equals(lastClassSrcName) || !dstOwner.equals(lastClassDstName);

						if (classVisitRequired) {
							if (classContentVisitPending) {
								visitor.visitElementContent(MappedElementKind.CLASS);
								classContentVisitPending = false;
							}

							if (!visitor.visitClass(srcOwner)) {
								lastClassSrcName = srcOwner;
								continue;
							}

							classContentVisitPending = true;
						}

						lastClassSrcName = srcOwner;

						if (classVisitRequired) {
							visitor.visitDstName(MappedElementKind.CLASS, 0, dstOwner);
							lastClassDstName = dstOwner;
						}

						if (classContentVisitPending) {
							classContentVisitPending = false;
							if (!visitor.visitElementContent(MappedElementKind.CLASS)) continue;
						}

						String srcName = src.substring(srcSepPos + 1);

						if (isMethod && visitor.visitMethod(srcName, srcDesc)
								|| !isMethod && visitor.visitField(srcName, srcDesc)) {
							MappedElementKind kind = isMethod ? MappedElementKind.METHOD : MappedElementKind.FIELD;
							visitor.visitDstName(kind, 0, dstName.substring(dstSepPos + 1));
							visitor.visitDstDesc(kind, 0, dstDesc);
							visitor.visitElementContent(kind);
						}
					}
				} while (reader.nextLine(0));

				if (classContentVisitPending) {
					visitor.visitElementContent(MappedElementKind.CLASS);
				}
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
}
